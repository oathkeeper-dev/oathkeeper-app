package com.oathkeeper.app

import android.app.Application
import android.util.Log
import com.oathkeeper.app.storage.DatabaseManager
import com.oathkeeper.app.util.Constants
import net.sqlcipher.database.SQLiteDatabase

class OathkeeperApplication : Application() {
    
    companion object {
        lateinit var instance: OathkeeperApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize SQLCipher
        SQLiteDatabase.loadLibs(this)
        
        // Initialize database
        DatabaseManager.initialize(this)
        
        Log.d(Constants.TAG, "OathkeeperApplication initialized")
    }
}
