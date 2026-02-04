package com.oathkeeper.app

import android.app.Application
import android.util.Log
import com.oathkeeper.app.util.Constants

class OathkeeperApplication : Application() {
    
    companion object {
        lateinit var instance: OathkeeperApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(Constants.TAG, "OathkeeperApplication initialized")
    }
}
