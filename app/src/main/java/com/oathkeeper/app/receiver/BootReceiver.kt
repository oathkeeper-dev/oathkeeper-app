package com.oathkeeper.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.oathkeeper.app.service.ScreenCaptureService
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = Constants.TAG
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed received")
            
            val prefs = PreferenceManager(context)
            
            // Only auto-start if the service was previously enabled
            if (prefs.isServiceEnabled && prefs.mediaProjectionResultCode != -1) {
                val mediaProjectionData = prefs.mediaProjectionData
                
                if (mediaProjectionData != null) {
                    Log.d(TAG, "Auto-starting ScreenCaptureService")
                    
                    val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                        putExtra("resultCode", prefs.mediaProjectionResultCode)
                        putExtra("data", mediaProjectionData)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.d(TAG, "Service auto-started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-start service: ${e.message}")
                        // Clear the stored data so user has to re-grant permission
                        prefs.clearMediaProjectionData()
                        prefs.isServiceEnabled = false
                    }
                } else {
                    Log.w(TAG, "MediaProjection data not available, cannot auto-start")
                    prefs.isServiceEnabled = false
                }
            } else {
                Log.d(TAG, "Service not enabled, skipping auto-start")
            }
        }
    }
}
