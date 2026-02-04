package com.oathkeeper.app.util

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
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
    
    // Capture interval
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
    
    // MediaProjection data
    var mediaProjectionResultCode: Int
        get() = prefs.getInt(Constants.PREF_MEDIA_PROJECTION_RESULT_CODE, -1)
        set(value) = prefs.edit { putInt(Constants.PREF_MEDIA_PROJECTION_RESULT_CODE, value) }
    
    var mediaProjectionData: Intent?
        get() {
            val uriString = prefs.getString(Constants.PREF_MEDIA_PROJECTION_DATA, null)
            return uriString?.let {
                try {
                    Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
                } catch (e: Exception) {
                    null
                }
            }
        }
        set(value) {
            prefs.edit {
                if (value != null) {
                    putString(Constants.PREF_MEDIA_PROJECTION_DATA, value.toUri(Intent.URI_INTENT_SCHEME))
                } else {
                    remove(Constants.PREF_MEDIA_PROJECTION_DATA)
                }
            }
        }
    
    fun clearMediaProjectionData() {
        prefs.edit {
            remove(Constants.PREF_MEDIA_PROJECTION_RESULT_CODE)
            remove(Constants.PREF_MEDIA_PROJECTION_DATA)
        }
    }
}
