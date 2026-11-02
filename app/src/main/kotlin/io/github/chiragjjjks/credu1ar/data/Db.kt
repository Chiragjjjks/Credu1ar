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

    /** A fresh install builds the frozen v1 schema, then runs every migration, so new and upgraded phones end up identical. */
    override fun onCreate(db: SQLiteDatabase) {
        createV1(db)
        migrateTo2(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Numbered, additive migrations. Never edit createV1 or a shipped migrateToN: phones already ran them.
        if (oldVersion < 2) migrateTo2(db)
    }

    /** Schema v1 exactly as shipped in v0.1.0–v0.1.2. Frozen. */
    private fun createV1(db: SQLiteDatabase) {
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

    /**
     * v2 (v0.1.3): bank statements (ADR-015). Additive only, so every v1 row survives untouched.
     *  - `statement`: one row per imported statement. No holder name, file name or URI is ever stored.
     *    `account_tail` is '' when the statement shows none, so the UNIQUE period key also holds then.
     *  - `txn.stmt_key`: the row's identity across statement files (`StatementTxns.key`), also kept on SMS rows a statement matched.
     *  - `txn.statement_id`: the import that brought or matched the row (for Remove).
     *  - `txn.time_known`: 0 for date-only statement rows, stamped at noon IST.
     * Runs inside the helper's upgrade transaction: it all applies or none of it does.
     */
    private fun migrateTo2(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE statement (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bank TEXT NOT NULL,
                account_tail TEXT NOT NULL DEFAULT '',
                period_from TEXT NOT NULL,
                period_to TEXT NOT NULL,
                opening_paise INTEGER,
                closing_paise INTEGER,
                row_count INTEGER NOT NULL,
                added INTEGER NOT NULL DEFAULT 0,
                matched INTEGER NOT NULL DEFAULT 0,
                complete INTEGER NOT NULL,
                content_hash TEXT NOT NULL UNIQUE,
                imported_at INTEGER NOT NULL,
                UNIQUE(bank, account_tail, period_from, period_to)
            )""",
        )
        db.execSQL("ALTER TABLE txn ADD COLUMN stmt_key TEXT")
        db.execSQL("ALTER TABLE txn ADD COLUMN statement_id INTEGER")
        db.execSQL("ALTER TABLE txn ADD COLUMN time_known INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE UNIQUE INDEX txn_stmt_key ON txn(stmt_key) WHERE stmt_key IS NOT NULL")
        db.execSQL("CREATE INDEX txn_statement ON txn(statement_id) WHERE statement_id IS NOT NULL")
    }

    companion object {
        const val VERSION = 2
    }
}
