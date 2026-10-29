package io.github.chiragjjjks.credu1ar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite (ADR-005): no Room, no annotation processing, smallest possible wake-up cost for the SMS receiver.
 * Everything lives in app-private storage; backups are disabled in the manifest.
 */
class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "credu1ar.db", null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
        db.enableWriteAheadLogging() // the receiver and the UI may touch the file at the same time
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txn (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                source_id TEXT,
                occurred_at INTEGER NOT NULL,
                received_at INTEGER NOT NULL,
                direction INTEGER NOT NULL,
                amount_paise INTEGER NOT NULL,
                merchant_key TEXT NOT NULL,
                merchant_name TEXT NOT NULL,
                category TEXT NOT NULL,
                confidence REAL NOT NULL,
                review INTEGER NOT NULL DEFAULT 0,
                user_set INTEGER NOT NULL DEFAULT 0,
                instrument TEXT NOT NULL,
                bank TEXT,
                account_tail TEXT,
                reference TEXT,
                vpa TEXT,
                payee_kind TEXT,
                hints TEXT,
                balance_paise INTEGER,
                sender TEXT,
                raw TEXT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX txn_source_id ON txn(source, source_id) WHERE source_id IS NOT NULL")
        db.execSQL("CREATE INDEX txn_time ON txn(occurred_at)")
        db.execSQL("CREATE INDEX txn_merchant ON txn(merchant_key)")
        db.execSQL("CREATE TABLE rule (merchant_key TEXT PRIMARY KEY, category TEXT NOT NULL, created_at INTEGER NOT NULL)")
        // Looked financial but could not be read. The only non-payment text we ever keep, so the owner can see it and we can add a rule.
        db.execSQL("CREATE TABLE unparsed (id INTEGER PRIMARY KEY AUTOINCREMENT, received_at INTEGER NOT NULL, sender TEXT, reason TEXT, body TEXT)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema. Add numbered migrations here, never edit onCreate for shipped versions.
    }

    companion object {
        const val VERSION = 1
    }
}
