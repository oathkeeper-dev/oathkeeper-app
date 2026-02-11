package com.oathkeeper.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.oathkeeper.app.R
import com.oathkeeper.app.service.OathkeeperAccessibilityService
import com.oathkeeper.app.storage.DatabaseManager
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.PreferenceManager
import com.oathkeeper.app.util.PermissionUtils

class MainActivity : AppCompatActivity() {
    
    private lateinit var prefs: PreferenceManager
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private lateinit var viewEventsButton: Button
    private lateinit var permissionButton: Button
    private lateinit var progressBar: ProgressBar
    
    companion object {
        private const val REQUEST_ACCESSIBILITY_SERVICE = 1005
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = PreferenceManager(this)
        
        // Initialize database
        DatabaseManager.initialize(this)
        
        initViews()
        checkFirstRun()
        updateUI()
        
        Log.d(Constants.TAG, "MainActivity created")
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        settingsButton = findViewById(R.id.settingsButton)
        viewEventsButton = findViewById(R.id.viewEventsButton)
        permissionButton = findViewById(R.id.permissionButton)
        progressBar = findViewById(R.id.progressBar)
        
        startButton.setOnClickListener {
            if (OathkeeperAccessibilityService.isRunning) {
                stopService()
            } else {
                startServiceWithPermissions()
            }
        }
        
        viewEventsButton.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
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
                requestNextPermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            missing.add("Accessibility Service (to monitor screen content)")
        }
        
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
    
    private fun requestNextPermission() {
        when {
            !PermissionUtils.isAccessibilityServiceEnabled(this) -> {
                showAccessibilityServiceDialog()
            }
            !PermissionUtils.hasOverlayPermission(this) -> {
                PermissionUtils.requestOverlayPermission(this, Constants.REQUEST_OVERLAY_PERMISSION)
            }
            !PermissionUtils.hasUsageStatsPermission(this) -> {
                PermissionUtils.requestUsageStatsPermission(this, Constants.REQUEST_USAGE_STATS)
            }
            !PermissionUtils.isIgnoringBatteryOptimizations(this) -> {
                PermissionUtils.requestBatteryOptimizationExemption(
                    this, 
                    Constants.REQUEST_BATTERY_OPTIMIZATION
                )
            }
            else -> {
                updateUI()
            }
        }
    }
    
    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(PermissionUtils.getAccessibilityServiceInstructions())
            .setPositiveButton("OK") { _, _ ->
                PermissionUtils.openAccessibilitySettings(this)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startServiceWithPermissions() {
        if (!checkAllPermissions()) {
            showPermissionRationale()
            return
        }
        
        // Accessibility Service will auto-start when enabled
        if (PermissionUtils.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, "Service is starting...", Toast.LENGTH_SHORT).show()
            updateUI()
        } else {
            showAccessibilityServiceDialog()
        }
    }
    
    private fun stopService() {
        // Send broadcast to stop the service
        val stopIntent = Intent("com.oathkeeper.app.STOP_SERVICE")
        sendBroadcast(stopIntent)
        
        prefs.isServiceEnabled = false
        updateUI()
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun checkAllPermissions(): Boolean {
        return PermissionUtils.isAccessibilityServiceEnabled(this) &&
               PermissionUtils.hasOverlayPermission(this) &&
               PermissionUtils.hasUsageStatsPermission(this) &&
               PermissionUtils.isIgnoringBatteryOptimizations(this)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            Constants.REQUEST_OVERLAY_PERMISSION,
            Constants.REQUEST_USAGE_STATS,
            Constants.REQUEST_BATTERY_OPTIMIZATION -> {
                // Re-check and continue requesting remaining permissions
                requestNextPermission()
            }
        }
    }
    
    private fun updateUI() {
        val isRunning = OathkeeperAccessibilityService.isRunning
        val hasPermissions = checkAllPermissions()
        
        if (isRunning) {
            statusText.text = getString(R.string.status_running)
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            startButton.text = getString(R.string.stop_service)
        } else {
            statusText.text = if (hasPermissions) getString(R.string.status_ready) else getString(R.string.status_stopped)
            statusText.setTextColor(
                if (hasPermissions) getColor(android.R.color.holo_blue_dark)
                else getColor(android.R.color.holo_red_dark)
            )
            startButton.text = getString(R.string.start_service)
        }
        
        permissionButton.visibility = if (hasPermissions) View.GONE else View.VISIBLE
        progressBar.visibility = View.GONE
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
