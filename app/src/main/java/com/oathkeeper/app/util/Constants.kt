package com.oathkeeper.app.util

object Constants {
    const val TAG = "Oathkeeper"
    
    // Notification
    const val NOTIFICATION_CHANNEL_ID = "oathkeeper_capture"
    const val NOTIFICATION_CHANNEL_NAME = "Screen Capture Service"
    const val NOTIFICATION_ID = 1001
    
    // Service
    const val CAPTURE_INTERVAL_MS = 2000L
    
    // Permissions
    const val REQUEST_MEDIA_PROJECTION = 1001
    const val REQUEST_OVERLAY_PERMISSION = 1002
    const val REQUEST_USAGE_STATS = 1003
    const val REQUEST_BATTERY_OPTIMIZATION = 1004
    
    // Preferences
    const val PREFS_NAME = "oathkeeper_prefs"
    const val PREF_FIRST_RUN = "first_run"
    const val PREF_SERVICE_ENABLED = "service_enabled"
    const val PREF_CAPTURE_INTERVAL = "capture_interval"
    const val PREF_THRESHOLD_PORN = "threshold_porn"
    const val PREF_THRESHOLD_SEXY = "threshold_sexy"
    const val PREF_ENABLE_NOTIFICATIONS = "enable_notifications"
    const val PREF_MEDIA_PROJECTION_RESULT_CODE = "media_projection_result_code"
    const val PREF_MEDIA_PROJECTION_DATA = "media_projection_data"
}
