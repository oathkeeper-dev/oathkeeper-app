package com.oathkeeper.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.oathkeeper.app.service.OathkeeperAccessibilityService
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.PreferenceManager
import com.oathkeeper.app.util.PermissionUtils

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = Constants.TAG
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed received")
            
            val prefs = PreferenceManager(context)
            
            // Only auto-start if the service was previously enabled
            // Note: AccessibilityService auto-starts when enabled in system settings
            // We just need to log that we're ready
            if (prefs.isServiceEnabled) {
                Log.d(TAG, "Service was enabled before reboot")
                
                // Check if AccessibilityService is still enabled
                if (PermissionUtils.isAccessibilityServiceEnabled(context)) {
                    Log.d(TAG, "AccessibilityService is enabled, it will auto-start")
                    
                    // The service will automatically start when the system
                    // initializes accessibility services
                } else {
                    Log.w(TAG, "AccessibilityService was disabled, cannot auto-start")
                    prefs.isServiceEnabled = false
                }
            } else {
                Log.d(TAG, "Service not enabled, skipping auto-start")
            }
        }
    }
}
