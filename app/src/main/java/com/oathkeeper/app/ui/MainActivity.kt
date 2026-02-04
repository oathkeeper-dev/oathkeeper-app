package com.oathkeeper.app.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.oathkeeper.app.R
import com.oathkeeper.app.service.ScreenCaptureService
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.PreferenceManager
import com.oathkeeper.app.util.PermissionUtils

class MainActivity : AppCompatActivity() {
    
    private lateinit var prefs: PreferenceManager
    private var mediaProjectionManager: MediaProjectionManager? = null
    
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private lateinit var permissionButton: Button
    private lateinit var progressBar: ProgressBar
    
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            prefs.mediaProjectionResultCode = result.resultCode
            prefs.mediaProjectionData = result.data
            prefs.isServiceEnabled = true
            startScreenCaptureService(result.resultCode, result.data!!)
            updateUI()
            Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = PreferenceManager(this)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        
        initViews()
        checkFirstRun()
        updateUI()
        
        Log.d(Constants.TAG, "MainActivity created")
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        settingsButton = findViewById(R.id.settingsButton)
        permissionButton = findViewById(R.id.permissionButton)
        progressBar = findViewById(R.id.progressBar)
        
        startButton.setOnClickListener {
            if (prefs.isServiceEnabled) {
                stopService()
            } else {
                startServiceWithPermissions()
            }
        }
        
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        permissionButton.setOnClickListener {
            showPermissionRationale()
        }
    }
    
    private fun checkFirstRun() {
        if (prefs.isFirstRun) {
            showWelcomeDialog()
            prefs.isFirstRun = false
        }
    }
    
    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to Oathkeeper")
            .setMessage("Oathkeeper helps you maintain accountability by monitoring screen content locally on your device. " +
                    "No data is ever sent to external servers.\n\n" +
                    "Please grant the required permissions to get started.")
            .setPositiveButton("Get Started") { _, _ ->
                showPermissionRationale()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showPermissionRationale() {
        val missingPermissions = getMissingPermissions()
        
        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val message = buildString {
            appendLine("The following permissions are required:")
            missingPermissions.forEach { permission ->
                appendLine("\u2022 $permission")
            }
            appendLine()
            appendLine("Tap OK to grant these permissions.")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        if (!PermissionUtils.hasUsageStatsPermission(this)) {
            missing.add("Usage Access (to monitor app activity)")
        }
        
        if (!PermissionUtils.hasOverlayPermission(this)) {
            missing.add("Overlay Permission (for tamper warnings)")
        }
        
        if (!PermissionUtils.isIgnoringBatteryOptimizations(this)) {
            missing.add("Battery Optimization Exemption")
        }
        
        return missing
    }
    
    private fun requestPermissions() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            PermissionUtils.requestOverlayPermission(this, Constants.REQUEST_OVERLAY_PERMISSION)
            return
        }
        
        if (!PermissionUtils.hasUsageStatsPermission(this)) {
            PermissionUtils.requestUsageStatsPermission(this, Constants.REQUEST_USAGE_STATS)
            return
        }
        
        if (!PermissionUtils.isIgnoringBatteryOptimizations(this)) {
            PermissionUtils.requestBatteryOptimizationExemption(
                this, 
                Constants.REQUEST_BATTERY_OPTIMIZATION
            )
            return
        }
        
        updateUI()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            Constants.REQUEST_OVERLAY_PERMISSION,
            Constants.REQUEST_USAGE_STATS,
            Constants.REQUEST_BATTERY_OPTIMIZATION -> {
                // Re-check and continue requesting remaining permissions
                requestPermissions()
            }
        }
    }
    
    private fun startServiceWithPermissions() {
        if (!checkAllPermissions()) {
            showPermissionRationale()
            return
        }
        
        // Request MediaProjection permission
        mediaProjectionManager?.let { manager ->
            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
        } ?: run {
            Toast.makeText(this, "MediaProjection not available", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkAllPermissions(): Boolean {
        return PermissionUtils.hasOverlayPermission(this) &&
               PermissionUtils.hasUsageStatsPermission(this) &&
               PermissionUtils.isIgnoringBatteryOptimizations(this)
    }
    
    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun stopService() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        stopService(serviceIntent)
        prefs.isServiceEnabled = false
        prefs.clearMediaProjectionData()
        updateUI()
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        if (prefs.isServiceEnabled) {
            statusText.text = "Status: Running"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            startButton.text = "Stop Service"
        } else {
            statusText.text = "Status: Stopped"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            startButton.text = "Start Service"
        }
        
        val hasPermissions = checkAllPermissions()
        permissionButton.visibility = if (hasPermissions) View.GONE else View.VISIBLE
        progressBar.visibility = View.GONE
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
