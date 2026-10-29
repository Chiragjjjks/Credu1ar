package io.github.chiragjjjks.credu1ar

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.github.chiragjjjks.credu1ar.data.Db
import io.github.chiragjjjks.credu1ar.data.Repo

/** Deliberately empty: this runs on every SMS wake-up, so it must cost nothing. Everything else is lazy in [Graph]. */
class App : Application()

/** Process-wide singletons, created on first use (so an SMS wake-up that is ignored never opens the database). */
object Graph {
    @Volatile private var repo: Repo? = null
    @Volatile private var prefs: Settings? = null

    fun repo(context: Context): Repo = repo ?: synchronized(this) {
        repo ?: Repo(Db(context.applicationContext)).also { repo = it }
    }

    fun settings(context: Context): Settings = prefs ?: synchronized(this) {
        prefs ?: Settings(context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)).also { prefs = it }
    }
}

/** Owner-visible switches. Defaults favour privacy and low noise. */
class Settings(private val p: SharedPreferences) {
    var onboarded: Boolean get() = p.getBoolean("onboarded", false); set(v) = p.edit().putBoolean("onboarded", v).apply()
    /** Blur amounts on screen until tapped. */
    var privacyMode: Boolean get() = p.getBoolean("privacy_mode", false); set(v) = p.edit().putBoolean("privacy_mode", v).apply()
    /** FLAG_SECURE: hide the app from screenshots and the recents screen. */
    var secureScreen: Boolean get() = p.getBoolean("secure_screen", false); set(v) = p.edit().putBoolean("secure_screen", v).apply()
    /** Quiet confirmation when a payment is captured. */
    var notifyOnCapture: Boolean get() = p.getBoolean("notify_capture", true); set(v) = p.edit().putBoolean("notify_capture", v).apply()
    /** Keep the original message text beside each payment (lets us re-read it after a parser update). */
    var keepRaw: Boolean get() = p.getBoolean("keep_raw", true); set(v) = p.edit().putBoolean("keep_raw", v).apply()
}
