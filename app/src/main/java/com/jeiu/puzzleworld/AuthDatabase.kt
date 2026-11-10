package com.jeiu.puzzleworld

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

/**
 * Local SQLite store for player credentials (offline-first authentication).
 * Passwords are stored as salted SHA-256 hashes, never in plaintext.
 */
class AuthDatabase(context: Context) :
    SQLiteOpenHelper(context, "puzzleworld_auth.db", null, 1) {

    companion object {
        private const val TABLE = "users"
        private const val SALT = "puzzleworld.salt.v1"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "username TEXT PRIMARY KEY, " +
                "password_hash TEXT NOT NULL, " +
                "created_at TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun exists(username: String): Boolean {
        readableDatabase.query(
            TABLE, arrayOf("username"), "username = ?", arrayOf(username),
            null, null, null
        ).use { return it.moveToFirst() }
    }

    /** Returns true if the account was created; false if the username is taken. */
    fun register(username: String, password: String): Boolean {
        if (exists(username)) return false
        val values = ContentValues().apply {
            put("username", username)
            put("password_hash", hash(password))
            put("created_at", System.currentTimeMillis().toString())
        }
        return writableDatabase.insert(TABLE, null, values) != -1L
    }

    fun verify(username: String, password: String): Boolean {
        readableDatabase.query(
            TABLE, arrayOf("password_hash"), "username = ?", arrayOf(username),
            null, null, null
        ).use { c ->
            return c.moveToFirst() && c.getString(0) == hash(password)
        }
    }

    private fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$SALT:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
