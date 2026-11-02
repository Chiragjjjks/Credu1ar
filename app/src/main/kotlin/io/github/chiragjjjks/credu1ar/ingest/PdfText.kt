package io.github.chiragjjjks.credu1ar.ingest

import android.content.Context
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import java.io.FileDescriptor
import java.io.IOException
import java.security.MessageDigest

/**
 * The text of a picked PDF, page by page, from Android's own PdfRenderer text API: built in on Android 15+, and on
 * Android 12–14 through PdfRendererPreV when the phone's system updates include SDK extension 13. Adds nothing to the APK.
 *
 * Privacy: the file is read in place and never copied to disk (a pipe is copied into anonymous memory, `memfd`, and
 * closed when done). The password is passed to the renderer and dropped. Nothing here logs text, amounts or file names.
 */
internal object PdfText {
    sealed interface Result {
        /** One string per page: that page's text blocks joined with "\n". [sha256] is of the file's bytes (the import's identity). */
        class Pages(val pages: List<String>, val sha256: String) : Result

        /** The PDF is locked. [wrongPassword] = a password was given and didn't open it. */
        data class Locked(val wrongPassword: Boolean) : Result

        data class Failed(val problem: ImportProblem) : Result
    }

    /** Bigger than any bank statement; stops a wrong pick from filling memory. */
    private const val MAX_BYTES = 25L * 1024 * 1024
    private const val CHUNK = 64 * 1024

    /** Whether this phone has the PDF text API at all. */
    fun available(): Boolean =
        Build.VERSION.SDK_INT >= 35 || (Build.VERSION.SDK_INT >= 31 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13)

    /** Blocking. [onPage] gets (page being read, page count), 1-based. */
    fun read(context: Context, uri: Uri, password: String?, onPage: (Int, Int) -> Unit = { _, _ -> }): Result {
        if (Build.VERSION.SDK_INT < 31 || !available()) return Result.Failed(ImportProblem.PHONE_TOO_OLD)
        val opened = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) { // gone, permission revoked, provider crashed
            null
        } ?: return Result.Failed(ImportProblem.UNREADABLE)
        var memory: ParcelFileDescriptor? = null
        try {
            val sha: String
            val source: ParcelFileDescriptor
            if (seekable(opened.fileDescriptor)) {
                if (opened.statSize > MAX_BYTES) return Result.Failed(ImportProblem.UNREADABLE)
                sha = hashInPlace(opened.fileDescriptor) ?: return Result.Failed(ImportProblem.UNREADABLE)
                source = opened
            } else {
                // PdfRenderer needs random access; a pipe (some cloud providers) is copied into memory only.
                val (copy, hash) = copyToMemory(opened.fileDescriptor) ?: return Result.Failed(ImportProblem.UNREADABLE)
                memory = copy
                sha = hash
                source = copy
            }
            if (!startsLikePdf(source.fileDescriptor)) return Result.Failed(ImportProblem.NOT_PDF)
            return render(source, password, sha, onPage)
        } finally {
            closeQuietly(opened)
            memory?.let(::closeQuietly)
        }
    }

    private fun render(fd: ParcelFileDescriptor, password: String?, sha: String, onPage: (Int, Int) -> Unit): Result =
        try {
            // The renderer owns fd from here and closes it; closing it again in read() is a no-op.
            when {
                Build.VERSION.SDK_INT >= 35 -> Result.Pages(pagesV(fd, password, onPage), sha)
                Build.VERSION.SDK_INT >= 31 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 ->
                    Result.Pages(pagesPreV(fd, password, onPage), sha)
                else -> Result.Failed(ImportProblem.PHONE_TOO_OLD)
            }
        } catch (_: SecurityException) { // locked, or the password didn't open it
            Result.Locked(wrongPassword = password != null)
        } catch (_: IOException) { // "not in PDF format or corrupted"
            Result.Failed(ImportProblem.NOT_PDF)
        } catch (_: RuntimeException) {
            Result.Failed(ImportProblem.UNREADABLE)
        }

    /** Android 15+: the built-in renderer. */
    @RequiresApi(35)
    private fun pagesV(fd: ParcelFileDescriptor, password: String?, onPage: (Int, Int) -> Unit): List<String> {
        val params = LoadParams.Builder().apply { if (password != null) setPassword(password) }.build()
        return PdfRenderer(fd, params).use { r ->
            val n = r.pageCount
            List(n) { i -> onPage(i + 1, n); r.openPage(i).use { p -> p.textContents.joinToString("\n") { it.text } } }
        }
    }

    /** Android 12–14 whose system updates brought SDK extension 13: the same renderer, shipped as PdfRendererPreV. */
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun pagesPreV(fd: ParcelFileDescriptor, password: String?, onPage: (Int, Int) -> Unit): List<String> {
        val params = LoadParams.Builder().apply { if (password != null) setPassword(password) }.build()
        return PdfRendererPreV(fd, params).use { r ->
            val n = r.pageCount
            List(n) { i -> onPage(i + 1, n); r.openPage(i).use { p -> p.textContents.joinToString("\n") { it.text } } }
        }
    }

    private fun seekable(fd: FileDescriptor): Boolean = try {
        Os.lseek(fd, 0, OsConstants.SEEK_CUR); true
    } catch (_: ErrnoException) { false }

    /** SHA-256 of a seekable file, read with pread so the offset the renderer sees is untouched. */
    private fun hashInPlace(fd: FileDescriptor): String? {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(CHUNK)
            var offset = 0L
            while (true) {
                val n = Os.pread(fd, buf, 0, buf.size, offset)
                if (n <= 0) break
                md.update(buf, 0, n)
                offset += n
                if (offset > MAX_BYTES) return null
            }
            return hex(md.digest())
        } catch (_: ErrnoException) {
            return null
        } catch (_: IOException) {
            return null
        }
    }

    /** Copies a pipe into an anonymous in-memory file (never on disk), hashing as it goes. Null if it is too big or fails. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun copyToMemory(src: FileDescriptor): Pair<ParcelFileDescriptor, String>? {
        val mem = try { Os.memfd_create("credu1ar-pdf", 0) } catch (_: ErrnoException) { return null }
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(CHUNK)
            var total = 0L
            while (true) {
                val n = Os.read(src, buf, 0, buf.size)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) return null
                md.update(buf, 0, n)
                var written = 0
                while (written < n) written += Os.write(mem, buf, written, n - written)
            }
            Os.lseek(mem, 0, OsConstants.SEEK_SET)
            return ParcelFileDescriptor.dup(mem) to hex(md.digest())
        } catch (_: ErrnoException) {
            return null
        } catch (_: IOException) {
            return null
        } finally {
            runCatching { Os.close(mem) } // the dup (if any) keeps the memory alive until read() closes it
        }
    }

    /** "%PDF-" within the first KiB, as the PDF spec allows. */
    private fun startsLikePdf(fd: FileDescriptor): Boolean = try {
        val head = ByteArray(1024)
        val n = Os.pread(fd, head, 0, head.size, 0)
        n > 0 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
    } catch (_: ErrnoException) { false } catch (_: IOException) { false }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    private fun closeQuietly(p: ParcelFileDescriptor) { runCatching { p.close() } }
}
