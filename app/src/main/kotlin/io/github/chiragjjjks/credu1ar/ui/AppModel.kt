package io.github.chiragjjjks.credu1ar.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import io.github.chiragjjjks.credu1ar.BuildConfig
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.HistoryWindow
import io.github.chiragjjjks.credu1ar.core.insights.CoveredRange
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.insights.Reports
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Money
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.core.sample.SampleData
import io.github.chiragjjjks.credu1ar.data.Repo
import io.github.chiragjjjks.credu1ar.data.StatementRecord
import io.github.chiragjjjks.credu1ar.data.TxnDetail
import io.github.chiragjjjks.credu1ar.data.Unparsed
import io.github.chiragjjjks.credu1ar.ingest.CatchUp
import io.github.chiragjjjks.credu1ar.ingest.FileResult
import io.github.chiragjjjks.credu1ar.ingest.ImportOutcome
import io.github.chiragjjjks.credu1ar.ingest.ImportProblem
import io.github.chiragjjjks.credu1ar.ingest.ImportProgress
import io.github.chiragjjjks.credu1ar.ingest.Ingest
import io.github.chiragjjjks.credu1ar.ingest.NotificationCaptureService
import io.github.chiragjjjks.credu1ar.ingest.StatementImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.YearMonth

/** Everything the screens read. Recomputed off the main thread whenever the database changes. */
class AppModel(private val app: Context) {
    private val repo = Graph.repo(app)
    private val settings = Graph.settings(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class Access(val sms: Boolean, val notifListener: Boolean, val postNotif: Boolean, val battery: Boolean)

    data class Diag(
        val lastSmsAt: Long, val lastNotifAt: Long, val lastScanAt: Long, val lastScanRead: Long,
        val listenerUpAt: Long, val listenerDownAt: Long,
        val nSms: Long, val nNotif: Long, val nAccepted: Long, val nIgnored: Long, val nDuplicate: Long, val nUnreadable: Long,
    )

    /** Payments to one unsure payee, shown once: filing it teaches the rule for all of them. */
    data class ReviewGroup(val key: String, val name: String, val count: Int, val totalPaise: Long, val latest: Txn)

    data class Prefs(val privacy: Boolean, val secure: Boolean, val notify: Boolean, val keepRaw: Boolean, val onboarded: Boolean)

    /** The SMS-history read the owner is watching: the first link, or one "Add <month>" tap. */
    sealed interface History {
        data object Idle : History
        data class Reading(val fromMillis: Long, val read: Int, val total: Int) : History
        data class Done(val fromMillis: Long, val read: Int, val payments: Int) : History
    }

    /** How Pulse slices time once a bank statement exists: one statement period, or one calendar month. */
    enum class PulseBy { STATEMENT, MONTH }

    /**
     * A bank-statement import the owner is watching: reading (with page progress), waiting for a password, or done.
     * The picked files' URIs stay in memory only for the password retry; nothing about the file is stored.
     */
    sealed interface Import {
        data object Idle : Import
        data class Reading(val progress: ImportProgress?) : Import
        data class Locked(val fileIndex: Int, val files: Int, val wrongPassword: Boolean) : Import
        data class Done(val files: List<FileResult>) : Import
    }

    data class UiState(
        val ready: Boolean = false,
        val now: Long = System.currentTimeMillis(),
        val month: YearMonth = YearMonth.now(Insights.IST),
        val txns: List<Txn> = emptyList(),
        val summary: Insights.MonthSummary? = null,
        val split: Insights.GroceriesSplit = Insights.GroceriesSplit(0, 0, 0, 0),
        val headline: Insights.Headline = Insights.Headline("", ""),
        val movers: List<Insights.Mover> = emptyList(),
        val leaks: List<Insights.Leak> = emptyList(),
        val recurring: List<Insights.Recurring> = emptyList(),
        val rhythm: Insights.Rhythm = Insights.rhythm(emptyList(), 0),
        val review: List<Txn> = emptyList(),
        val reviewGroups: List<ReviewGroup> = emptyList(),
        val ticker: List<String> = emptyList(),
        val counts: Repo.Counts = Repo.Counts(0, 0, 0, 0, 0),
        val unparsed: List<Unparsed> = emptyList(),
        val access: Access = Access(false, false, false, false),
        val diag: Diag = Diag(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        val prefs: Prefs = Prefs(false, false, true, true, false),
        val history: History = History.Idle,
        /** Where read SMS history starts (0 = never read). */
        val historyFromMillis: Long = 0L,
        /** The month one "Add <month>" tap would read. */
        val earlierMonth: YearMonth = YearMonth.now(Insights.IST),
        /** Imported bank statements, newest period first. */
        val statements: List<StatementRecord> = emptyList(),
        val ranges: List<CoveredRange> = emptyList(),
        val import: Import = Import.Idle,
        val by: PulseBy = PulseBy.MONTH,
        /** The statement BY STATEMENT shows (an id from [statements]). */
        val statementId: Long? = null,
        /**
         * The simple report Pulse shows for a statement period or a past/part month. Null = today's live Pulse (no
         * statements yet, or the current month with no statement covering it).
         */
        val report: Reports.Report? = null,
        /** The statements behind [report], for the "FROM STATEMENT · KOTAK ••1234 · 202 ROWS ✓" strip. */
        val reportStatements: List<StatementRecord> = emptyList(),
        /** The oldest month with any payment or statement: BY MONTH's ‹ stops here. */
        val firstMonth: YearMonth = YearMonth.now(Insights.IST),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    @Volatile private var month: YearMonth = YearMonth.now(Insights.IST)
    @Volatile private var by: PulseBy = PulseBy.MONTH
    @Volatile private var statementId: Long? = null
    /** Set when an import finishes: the next recompute opens Pulse on the best period to look at. */
    @Volatile private var openBestPeriod = false
    @Volatile private var coldStart = true
    /** The files of the import in progress, kept only so a locked PDF can be retried with its password. */
    @Volatile private var picked: List<Uri> = emptyList()
    /** One inbox read at a time from the UI: the permission callback and onResume both ask, the second must not race. */
    private val reads = Mutex()

    init {
        scope.launch {
            repo.version.collect {
                delay(120) // coalesce bursts (importing history writes hundreds of rows)
                recompute()
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ compute

    fun refresh() { scope.launch { recompute() } }

    /**
     * One pass at a time: the database collector and refresh() both call this on Dispatchers.Default, and a pass that
     * started before a view switch must not land after it with a report for the other view.
     */
    @Synchronized
    private fun recompute() {
        val now = System.currentTimeMillis()
        val txns = repo.all()
        val statements = repo.statements()
        val ranges = repo.coveredRanges()
        if (openBestPeriod && statements.isNotEmpty()) { openBestPeriod = false; openBest(statements, ranges) }
        if (coldStart) {
            coldStart = false
            // Opening the app with statements but nothing yet this month: show the statement, not an empty month.
            val thisMonth = YearMonth.now(Insights.IST)
            val anyThisMonth = txns.any { YearMonth.from(Instant.ofEpochMilli(it.occurredAtMillis).atZone(Insights.IST)) == thisMonth }
            if (statements.isNotEmpty() && !anyThisMonth && month == thisMonth) openBest(statements, ranges)
        }
        // Read the selection once, so the report, the toggle and the switcher in this state always agree.
        val by = this.by
        val month = this.month
        val view = pulseView(txns, statements, ranges, now, by, month)
        val s = Insights.monthSummary(txns, month, now)
        val split = Insights.groceriesSplit(s)
        val ticker = txns.take(9).map { t ->
            val sign = if (t.direction == Direction.DEBIT) "−" else "+"
            "${t.merchantName.uppercase()} $sign${Money.format(t.amountPaise)}"
        }
        fun n(k: String) = repo.metaLong(k)
        // The statement edition never touches SMS code paths (ADR-016).
        val historyStart = if (BuildConfig.SMS_CAPTURE) CatchUp.historyStart(app, now) else now
        val historyFrom = if (BuildConfig.SMS_CAPTURE && (n("history_from") > 0 || repo.earliestSmsAt() != null)) historyStart else 0L
        val next = UiState(
            ready = true, now = now, month = month, txns = txns, summary = s, split = split,
            headline = Insights.headline(s, split),
            movers = Insights.movers(txns, month, now),
            leaks = Insights.leaks(txns, month),
            recurring = Insights.recurring(txns, now),
            rhythm = Insights.rhythm(txns, now - 90L * 24 * 3600 * 1000),
            review = txns.filter { it.needsReview },
            reviewGroups = txns.filter { it.needsReview }.groupBy { it.merchantKey }
                .map { (k, l) -> ReviewGroup(k, l.first().merchantName, l.size, l.sumOf { it.amountPaise }, l.first()) }
                .sortedByDescending { it.totalPaise },
            ticker = ticker,
            counts = repo.counts(),
            unparsed = repo.unparsed(),
            access = access(),
            diag = Diag(n("last_sms_at"), n("last_notif_at"), n("last_scan_at"), n("last_scan_read"), n("listener_connected_at"), n("listener_disconnected_at"),
                n("n_sms"), n("n_notif"), n("n_accepted"), n("n_ignored"), n("n_duplicate"), n("n_unreadable")),
            prefs = Prefs(settings.privacyMode, settings.secureScreen, settings.notifyOnCapture, settings.keepRaw, settings.onboarded),
            historyFromMillis = historyFrom,
            earlierMonth = HistoryWindow.monthOf(HistoryWindow.monthEarlier(historyStart)),
            statements = statements, ranges = ranges, by = by, statementId = view.statementId,
            report = view.report, reportStatements = view.behind, firstMonth = view.firstMonth,
        )
        // History and import progress are written by worker threads; never overwrite them.
        _state.update { next.copy(history = it.history, import = it.import) }
    }

    private class PulseView(val statementId: Long?, val report: Reports.Report?, val behind: List<StatementRecord>, val firstMonth: YearMonth)

    /**
     * What Pulse shows. No statements: today's Pulse, untouched. BY STATEMENT: the chosen statement's period.
     * BY MONTH: a report for any past or part-covered month; the current month stays live until a statement covers it.
     */
    private fun pulseView(txns: List<Txn>, statements: List<StatementRecord>, ranges: List<CoveredRange>, now: Long, by: PulseBy, month: YearMonth): PulseView {
        val today = Instant.ofEpochMilli(now).atZone(Insights.IST).toLocalDate()
        val current = YearMonth.from(today)
        val oldest = listOfNotNull(
            txns.lastOrNull()?.let { YearMonth.from(Instant.ofEpochMilli(it.occurredAtMillis).atZone(Insights.IST)) },
            statements.minOfOrNull { it.from }?.let(YearMonth::from),
        ).minOrNull() ?: current
        if (statements.isEmpty()) return PulseView(null, null, emptyList(), oldest)
        val st = statements.firstOrNull { it.id == statementId } ?: statements.first()
        if (by == PulseBy.STATEMENT) {
            return PulseView(st.id, Reports.report(txns, st.from, st.to, ranges, isCalendarMonth = false), listOf(st), oldest)
        }
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val covered = Reports.coverage(ranges, first, minOf(last, today)).coveredDays
        // Full app: the current month stays live (ring, pace, ticker) until a statement covers it. The statement edition
        // has nothing live, so its current month is a report like any other.
        if (BuildConfig.SMS_CAPTURE && month == current && covered == 0) return PulseView(st.id, null, emptyList(), oldest)
        // A month no statement touches is read like SMS-only data (treated as complete), so it still compares.
        val report = Reports.month(txns, month, if (covered > 0) ranges else null)
        val behind = statements.filter { !it.to.isBefore(first) && !it.from.isAfter(last) }
        return PulseView(st.id, report, behind, oldest)
    }

    /** After an import: the latest fully covered month, else the newest statement's own period. */
    private fun openBest(statements: List<StatementRecord>, ranges: List<CoveredRange>) {
        val newest = YearMonth.from(statements.maxOf { it.to })
        val oldest = YearMonth.from(statements.minOf { it.from })
        var m = newest
        while (!m.isBefore(oldest)) {
            if (Reports.coverage(ranges, m.atDay(1), m.atEndOfMonth()).full) { by = PulseBy.MONTH; month = m; return }
            m = m.minusMonths(1)
        }
        by = PulseBy.STATEMENT
        statementId = statements.first().id
    }

    private fun access(): Access {
        val pm = app.getSystemService(PowerManager::class.java)
        if (!BuildConfig.SMS_CAPTURE) return Access(sms = false, notifListener = false, postNotif = false, battery = pm?.isIgnoringBatteryOptimizations(app.packageName) == true)
        return Access(
            sms = app.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                app.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED,
            notifListener = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName),
            postNotif = Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            battery = pm?.isIgnoringBatteryOptimizations(app.packageName) == true,
        )
    }

    // ------------------------------------------------------------------------------------------------ actions

    fun setMonth(m: YearMonth) { month = m; refresh() }
    /**
     * Switch BY STATEMENT / BY MONTH and land on the matching period: a statement opens on the month it covers most
     * (the later one on a tie); a month opens on the newest statement that touches it.
     */
    fun setBy(b: PulseBy) {
        val s = _state.value
        if (b == by || s.statements.isEmpty()) { by = b; refresh(); return }
        if (b == PulseBy.MONTH) {
            val st = s.statements.firstOrNull { it.id == statementId } ?: s.statements.first()
            var m = YearMonth.from(st.from)
            var best = m; var bestDays = -1
            while (!m.isAfter(YearMonth.from(st.to))) {
                val days = Reports.coverage(listOf(CoveredRange(st.from, st.to)), m.atDay(1), m.atEndOfMonth()).coveredDays
                if (days >= bestDays) { best = m; bestDays = days }
                m = m.plusMonths(1)
            }
            month = best
        } else {
            val first = month.atDay(1); val last = month.atEndOfMonth()
            s.statements.firstOrNull { !it.to.isBefore(first) && !it.from.isAfter(last) }?.let { statementId = it.id }
        }
        by = b
        refresh()
    }
    fun showStatement(id: Long) { statementId = id; by = PulseBy.STATEMENT; refresh() }

    fun setCategory(id: Long, category: Category, remember: Boolean) { scope.launch(Dispatchers.IO) { repo.setCategory(id, category, remember) } }

    /** Review's bulk action: file each payee (one payment stands for all of them) as a person, and remember it. */
    fun markAsPeople(txnIds: List<Long>) {
        scope.launch(Dispatchers.IO) { txnIds.forEach { repo.setCategory(it, Category.SENT_TO_PEOPLE, remember = true) } }
    }

    // ------------------------------------------------------------------------------------------------ statements

    /** The owner picked one or more statement PDFs. Read them off the main thread, one import or inbox read at a time. */
    fun importStatements(uris: List<Uri>) {
        if (uris.isEmpty()) return
        picked = uris
        runImport(null)
    }

    /** Retry the picked files with the password the owner typed. The password is passed through, never kept. */
    fun unlockStatement(password: String) { if (picked.isNotEmpty()) runImport(password) }

    fun cancelImport() { picked = emptyList(); setImport(Import.Idle) }
    fun dismissImport() { setImport(Import.Idle) }

    private fun runImport(password: String?) {
        val uris = picked
        setImport(Import.Reading(null))
        scope.launch(Dispatchers.IO) {
            reads.withLock {
                val out = runCatching { StatementImport.run(app, uris, password) { setImport(Import.Reading(it)) } }
                    .getOrElse { uris.map { FileResult(null, null, null, null, 0, 0, 0, 0, false, 0, ImportProblem.UNREADABLE) }.let(ImportOutcome::Done) }
                when (out) {
                    is ImportOutcome.NeedsPassword -> setImport(Import.Locked(out.fileIndex, uris.size, out.wrongPassword))
                    is ImportOutcome.Done -> {
                        picked = emptyList()
                        if (out.files.any { it.problem == null }) openBestPeriod = true
                        setImport(Import.Done(out.files))
                    }
                }
                repo.bump()
            }
        }
    }

    private fun setImport(i: Import) { _state.update { it.copy(import = i) } }

    fun removeStatement(id: Long) { scope.launch(Dispatchers.IO) { reads.withLock { repo.deleteStatement(id) }; repo.bump() } }
    fun deleteTxn(id: Long) { scope.launch(Dispatchers.IO) { repo.deleteTxn(id) } }
    suspend fun detail(id: Long): TxnDetail? = withContext(Dispatchers.IO) { repo.detail(id) }

    fun loadSample() { scope.launch(Dispatchers.IO) { SampleData.signals(System.currentTimeMillis()).forEach { Ingest.handle(app, it, quiet = true) }; repo.bump() } }
    fun clearSample() { scope.launch(Dispatchers.IO) { repo.clearSamples() } }
    /**
     * Reads new SMS. The first time SMS access works, this is the first-link read: this month, plus all of last month
     * in the first week (HistoryWindow), with live progress, and Pulse opens where the numbers are.
     */
    fun catchUpNow() {
        if (!BuildConfig.SMS_CAPTURE) return
        scope.launch(Dispatchers.IO) {
            reads.withLock {
                if (!CatchUp.needsFirstLink(app) || !CatchUp.hasPermission(app)) { CatchUp.scan(app); repo.bump(); return@withLock }
                val w = HistoryWindow.firstLink(System.currentTimeMillis())
                setHistory(History.Reading(w.startMillis, 0, 0))
                val r = CatchUp.scan(app) { read, total -> setHistory(History.Reading(w.startMillis, read, total)) }
                setHistory(History.Done(r.fromMillis, r.read, repo.countSince(r.fromMillis)))
                r.window?.let { if (it.openOn != month) month = it.openOn }
                repo.bump()
            }
        }
    }

    /** "Add <month>": reads one more month of SMS history, back from where it starts now. */
    fun addEarlierMonth() {
        if (!BuildConfig.SMS_CAPTURE) return
        scope.launch(Dispatchers.IO) {
            reads.withLock {
                if (!CatchUp.hasPermission(app)) return@withLock
                val from = HistoryWindow.monthEarlier(CatchUp.historyStart(app)) // from storage, not the last frame: a double tap reads the next month
                setHistory(History.Reading(from, 0, 0))
                val r = CatchUp.scanFrom(app, from) { read, total -> setHistory(History.Reading(from, read, total)) }
                setHistory(History.Done(from, r.read, repo.countSince(from)))
                repo.bump()
            }
        }
    }

    private fun setHistory(h: History) { _state.update { it.copy(history = h) } }
    fun deleteAll() {
        scope.launch(Dispatchers.IO) {
            reads.withLock { if (BuildConfig.SMS_CAPTURE) CatchUp.whileIdle { repo.deleteEverything() } else repo.deleteEverything() }
            setHistory(History.Idle)
        }
    }
    fun clearUnparsed() { scope.launch(Dispatchers.IO) { repo.clearUnparsed() } }

    fun setPrivacy(v: Boolean) { settings.privacyMode = v; refresh() }
    fun setSecure(v: Boolean) { settings.secureScreen = v; refresh() }
    fun setNotify(v: Boolean) { settings.notifyOnCapture = v; refresh() }
    fun setKeepRaw(v: Boolean) { settings.keepRaw = v; refresh() }
    fun setOnboarded(v: Boolean) { settings.onboarded = v; refresh() }

    fun exportCsv(out: java.io.OutputStream) {
        out.bufferedWriter().use { repo.exportCsv(it) }
    }

    /** Ask Android to re-bind the notification listener (some phone makers unbind it silently). */
    fun rebindListener() {
        if (!BuildConfig.SMS_CAPTURE) return
        runCatching { android.service.notification.NotificationListenerService.requestRebind(ComponentName(app, NotificationCaptureService::class.java)) }
    }

    /** Checks GitHub for a newer release. Sends nothing about the owner or their data; runs only when the owner taps. */
    suspend fun checkUpdate(): String = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL("https://api.github.com/repos/Chiragjjjks/Credu1ar/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000; setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (c.responseCode == 404) return@runCatching "No release published yet."
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(c.inputStream.bufferedReader().readText())?.groupValues?.get(1)
            when {
                tag == null -> "Could not read the latest version."
                tag.removePrefix("v") == BuildConfig.VERSION_NAME -> "You're on the latest version ($tag)."
                else -> "Newer version available: $tag (you have v${BuildConfig.VERSION_NAME}). Download it from the project's Releases page."
            }
        }.getOrElse { "Couldn't reach GitHub (${it.javaClass.simpleName}). Everything else works offline." }
    }

    /** A paste-able report for debugging on a real phone. Digits are masked; read it before sharing. */
    fun report(): String {
        val s = _state.value
        fun t(ms: Long) = if (ms == 0L) "never" else "${(s.now - ms) / 60000} min ago"
        val d = s.diag
        return buildString {
            appendLine("Credu1ar ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("SMS permission: ${s.access.sms} · Notification access: ${s.access.notifListener} · Alerts: ${s.access.postNotif} · Battery unrestricted: ${s.access.battery}")
            appendLine("Last SMS: ${t(d.lastSmsAt)} · Last notification: ${t(d.lastNotifAt)} · Last catch-up: ${t(d.lastScanAt)} (read ${d.lastScanRead})")
            appendLine("Listener connected: ${t(d.listenerUpAt)} · disconnected: ${t(d.listenerDownAt)}")
            appendLine("Received sms=${d.nSms} notif=${d.nNotif} · accepted=${d.nAccepted} ignored=${d.nIgnored} duplicate=${d.nDuplicate} unreadable=${d.nUnreadable}")
            appendLine("Stored: ${s.counts.total} (sms ${s.counts.sms}, notification ${s.counts.notif}, sample ${s.counts.sample}) · review ${s.counts.review} · statements ${s.statements.size} (${s.statements.count { it.complete }} complete)")
            if (!BuildConfig.SMS_CAPTURE) appendLine("Edition: statement only (no SMS or notification access)")
            if (s.unparsed.isNotEmpty()) {
                appendLine(); appendLine("Messages that looked like payments but could not be read (digits masked):")
                s.unparsed.take(10).forEach { appendLine("- [${it.sender.replace(Regex("\\d"), "9")}] ${it.reason}: ${it.body.replace(Regex("\\d"), "9").take(300)}") }
            }
        }
    }

    fun instant(ms: Long): Instant = Instant.ofEpochMilli(ms)
}
