package com.oathkeeper.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    // First run
    var isFirstRun: Boolean
        get() = prefs.getBoolean(Constants.PREF_FIRST_RUN, true)
        set(value) = prefs.edit { putBoolean(Constants.PREF_FIRST_RUN, value) }
    
    // Service enabled
    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_SERVICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Constants.PREF_SERVICE_ENABLED, value) }
    
    // Capture interval (in milliseconds)
    var captureInterval: Long
        get() = prefs.getLong(Constants.PREF_CAPTURE_INTERVAL, Constants.CAPTURE_INTERVAL_MS)
        set(value) = prefs.edit { putLong(Constants.PREF_CAPTURE_INTERVAL, value) }
    
    // Thresholds
    var thresholdPorn: Float
        get() = prefs.getFloat(Constants.PREF_THRESHOLD_PORN, 0.7f)
        set(value) = prefs.edit { putFloat(Constants.PREF_THRESHOLD_PORN, value) }
    
    var thresholdSexy: Float
        get() = prefs.getFloat(Constants.PREF_THRESHOLD_SEXY, 0.8f)
        set(value) = prefs.edit { putFloat(Constants.PREF_THRESHOLD_SEXY, value) }
    
    // Notifications
    var enableNotifications: Boolean
        get() = prefs.getBoolean(Constants.PREF_ENABLE_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(Constants.PREF_ENABLE_NOTIFICATIONS, value) }
}
