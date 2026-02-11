package com.oathkeeper.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.oathkeeper.app.service.OathkeeperAccessibilityService

object PermissionUtils {
    
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    fun requestUsageStatsPermission(activity: Activity, requestCode: Int) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        activity.startActivityForResult(intent, requestCode)
    }
    
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    fun requestOverlayPermission(activity: Activity, requestCode: Int) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, requestCode)
    }
    
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    fun requestBatteryOptimizationExemption(activity: Activity, requestCode: Int) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivityForResult(intent, requestCode)
    }
    
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // Android 10+ doesn't require storage permission for app-specific directories
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun requestStoragePermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            activity.requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                requestCode
            )
        }
    }
    
    // Accessibility Service permissions
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        
        val serviceComponentName = "${context.packageName}/${OathkeeperAccessibilityService::class.java.canonicalName}"
        
        return enabledServices.any { it.id == serviceComponentName }
    }
    
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }
    
    fun getAccessibilityServiceInstructions(): String {
        return """
            To enable Oathkeeper monitoring:
            
            1. Tap "OK" to open Accessibility Settings
            2. Find "Oathkeeper" in the list
            3. Turn the toggle ON
            4. Tap "Allow" on the confirmation dialog
            5. Return to this app
            
            Accessibility service is required for:
            - Monitoring screen content
            - Taking screenshots for analysis
            - Protecting your digital wellbeing
            
            No data is sent to external servers.
        """.trimIndent()
    }
}
