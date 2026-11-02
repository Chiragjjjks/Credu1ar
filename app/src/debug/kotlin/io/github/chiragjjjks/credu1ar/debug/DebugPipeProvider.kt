package io.github.chiragjjjks.credu1ar.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * DEBUG ONLY (lives in src/debug, not exported). Serves a file from the app's files dir through a pipe, like some cloud
 * document providers do, so the statement import's "unseekable handle → in-memory copy" path can be tested on an emulator.
 */
class DebugPipeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        val file = File(ctx.filesDir, name).takeIf { it.isFile && it.parentFile == ctx.filesDir } ?: throw FileNotFoundException()
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "credu1ar-debug-pipe") {
            // The reader may stop early (too big, not a PDF): a broken pipe here is expected, not a crash.
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { out -> file.inputStream().use { it.copyTo(out) } } }
        }
        return read
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/pdf"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        fun uri(context: Context, name: String): Uri = Uri.parse("content://${context.packageName}.debugpipe/$name")
    }
}
