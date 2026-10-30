package io.github.chiragjjjks.credu1ar.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import io.github.chiragjjjks.credu1ar.BuildConfig
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.HistoryWindow
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Money
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.core.sample.SampleData
import io.github.chiragjjjks.credu1ar.data.Repo
import io.github.chiragjjjks.credu1ar.data.TxnDetail
import io.github.chiragjjjks.credu1ar.data.Unparsed
import io.github.chiragjjjks.credu1ar.ingest.CatchUp
import io.github.chiragjjjks.credu1ar.ingest.Ingest
import io.github.chiragjjjks.credu1ar.ingest.NotificationCaptureService
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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    @Volatile private var month: YearMonth = YearMonth.now(Insights.IST)
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

    private fun recompute() {
        val now = System.currentTimeMillis()
        val txns = repo.all()
        val s = Insights.monthSummary(txns, month, now)
        val split = Insights.groceriesSplit(s)
        val ticker = txns.take(9).map { t ->
            val sign = if (t.direction == Direction.DEBIT) "−" else "+"
            "${t.merchantName.uppercase()} $sign${Money.format(t.amountPaise)}"
        }
        fun n(k: String) = repo.metaLong(k)
        val historyStart = CatchUp.historyStart(app, now)
        val historyFrom = if (n("history_from") > 0 || repo.earliestSmsAt() != null) historyStart else 0L
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
        )
        _state.update { next.copy(history = it.history) } // progress is written by the reader thread; never overwrite it
    }

    private fun access(): Access {
        val pm = app.getSystemService(PowerManager::class.java)
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

    fun setCategory(id: Long, category: Category, remember: Boolean) { scope.launch(Dispatchers.IO) { repo.setCategory(id, category, remember) } }
    fun deleteTxn(id: Long) { scope.launch(Dispatchers.IO) { repo.deleteTxn(id) } }
    suspend fun detail(id: Long): TxnDetail? = withContext(Dispatchers.IO) { repo.detail(id) }

    fun loadSample() { scope.launch(Dispatchers.IO) { SampleData.signals(System.currentTimeMillis()).forEach { Ingest.handle(app, it, quiet = true) }; repo.bump() } }
    fun clearSample() { scope.launch(Dispatchers.IO) { repo.clearSamples() } }
    /**
     * Reads new SMS. The first time SMS access works, this is the first-link read: this month, plus all of last month
     * in the first week (HistoryWindow), with live progress, and Pulse opens where the numbers are.
     */
    fun catchUpNow() {
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
            reads.withLock { CatchUp.whileIdle { repo.deleteEverything() } }
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
            appendLine("Stored: ${s.counts.total} (sms ${s.counts.sms}, notification ${s.counts.notif}, sample ${s.counts.sample}) · review ${s.counts.review}")
            if (s.unparsed.isNotEmpty()) {
                appendLine(); appendLine("Messages that looked like payments but could not be read (digits masked):")
                s.unparsed.take(10).forEach { appendLine("- [${it.sender.replace(Regex("\\d"), "9")}] ${it.reason}: ${it.body.replace(Regex("\\d"), "9").take(300)}") }
            }
        }
    }

    fun instant(ms: Long): Instant = Instant.ofEpochMilli(ms)
}
