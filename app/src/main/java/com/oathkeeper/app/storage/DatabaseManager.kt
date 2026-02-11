package com.oathkeeper.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.oathkeeper.app.model.DetectionEvent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

class DatabaseManager private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    
    companion object {
        private const val TAG = "Oathkeeper"
        private const val DATABASE_NAME = "oathkeeper_events.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_EVENTS = "events"
        
        // SQLCipher password - in production, use a secure key derivation
        private const val DATABASE_PASSWORD = "OathkeeperSecureKey2026"
        
        @Volatile
        private var instance: DatabaseManager? = null
        
        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context).also { instance = it }
            }
        }
        
        fun initialize(context: Context) {
            SQLiteDatabase.loadLibs(context)
            getInstance(context)
        }
    }
    
    private val writableDb: SQLiteDatabase
        get() = writableDatabase
    
    override fun getWritableDatabase(): SQLiteDatabase {
        return super.getWritableDatabase(DATABASE_PASSWORD)
    }
    
    override fun getReadableDatabase(): SQLiteDatabase {
        return super.getReadableDatabase(DATABASE_PASSWORD)
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_EVENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                detected_class TEXT NOT NULL,
                severity TEXT NOT NULL,
                confidence REAL NOT NULL,
                screenshot_path TEXT,
                app_name TEXT,
                is_reviewed INTEGER DEFAULT 0,
                notes TEXT,
                created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
            )
        """.trimIndent()
        
        db.execSQL(createTableSQL)
        
        // Create indexes for better query performance
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON $TABLE_EVENTS(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_severity ON $TABLE_EVENTS(severity)")
        
        Log.d(TAG, "Database created successfully")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database migrations here
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        onCreate(db)
    }
    
    fun insertEvent(event: DetectionEvent): Long {
        val values = ContentValues().apply {
            put("timestamp", event.timestamp)
            put("detected_class", event.detectedClass)
            put("severity", event.severity)
            put("confidence", event.confidence)
            put("screenshot_path", event.screenshotPath)
            put("app_name", event.appName)
            put("is_reviewed", if (event.isReviewed) 1 else 0)
            put("notes", event.notes)
            put("created_at", event.createdAt)
        }
        
        return try {
            writableDb.insert(TABLE_EVENTS, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert event: ${e.message}")
            -1
        }
    }
    
    fun getAllEvents(): List<DetectionEvent> {
        val events = mutableListOf<DetectionEvent>()
        val cursor = readableDb.query(
            TABLE_EVENTS,
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                events.add(cursorToEvent(it))
            }
        }
        
        return events
    }
    
    fun getEventsByDateRange(start: Long, end: Long): List<DetectionEvent> {
        val events = mutableListOf<DetectionEvent>()
        val cursor = readableDb.query(
            TABLE_EVENTS,
            null,
            "timestamp >= ? AND timestamp <= ?",
            arrayOf(start.toString(), end.toString()),
            null,
            null,
            "timestamp DESC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                events.add(cursorToEvent(it))
            }
        }
        
        return events
    }
    
    fun getEventsBySeverity(severity: String): List<DetectionEvent> {
        val events = mutableListOf<DetectionEvent>()
        val cursor = readableDb.query(
            TABLE_EVENTS,
            null,
            "severity = ?",
            arrayOf(severity),
            null,
            null,
            "timestamp DESC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                events.add(cursorToEvent(it))
            }
        }
        
        return events
    }
    
    fun markEventAsReviewed(id: Long, notes: String? = null): Boolean {
        val values = ContentValues().apply {
            put("is_reviewed", 1)
            notes?.let { put("notes", it) }
        }
        
        return writableDb.update(
            TABLE_EVENTS,
            values,
            "id = ?",
            arrayOf(id.toString())
        ) > 0
    }
    
    fun deleteEvent(id: Long): Boolean {
        return writableDb.delete(
            TABLE_EVENTS,
            "id = ?",
            arrayOf(id.toString())
        ) > 0
    }
    
    fun getEventCount(): Int {
        val cursor = readableDb.rawQuery("SELECT COUNT(*) FROM $TABLE_EVENTS", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }
    
    private fun cursorToEvent(cursor: Cursor): DetectionEvent {
        return DetectionEvent(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            detectedClass = cursor.getString(cursor.getColumnIndexOrThrow("detected_class")),
            severity = cursor.getString(cursor.getColumnIndexOrThrow("severity")),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence")),
            screenshotPath = cursor.getString(cursor.getColumnIndexOrThrow("screenshot_path")),
            appName = cursor.getString(cursor.getColumnIndexOrThrow("app_name")),
            isReviewed = cursor.getInt(cursor.getColumnIndexOrThrow("is_reviewed")) == 1,
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }
    
    override fun close() {
        instance = null
        super.close()
    }
}
