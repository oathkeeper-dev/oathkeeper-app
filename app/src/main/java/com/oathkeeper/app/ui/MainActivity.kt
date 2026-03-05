package com.oathkeeper.app.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
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
import androidx.core.content.ContextCompat
import com.oathkeeper.app.R
import com.oathkeeper.app.service.OathkeeperAccessibilityService
import com.oathkeeper.app.storage.DatabaseManager
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.ModelUtils
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
    private var mediaProjectionManager: MediaProjectionManager? = null
    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == OathkeeperAccessibilityService.ACTION_SERVICE_STATE_CHANGED) {
                updateUI()
            }
        }
    }
    
    companion object {
        private const val REQUEST_ACCESSIBILITY_SERVICE = 1005
        var mediaProjectionResultCode: Int = -1
        var mediaProjectionData: Intent? = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Check for required ML model first
        if (!ModelUtils.isModelAvailable(assets)) {
            showFatalErrorDialog()
            return
        }
        
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        
        prefs = PreferenceManager(this)
        
        // Initialize database
        DatabaseManager.initialize(this)
        
        val filter = IntentFilter(OathkeeperAccessibilityService.ACTION_SERVICE_STATE_CHANGED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        initViews()
        checkFirstRun()
        updateUI()
        
        Log.d(Constants.TAG, "MainActivity created")
    }
    
    private fun showFatalErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(R.string.error_model_missing)
            .setPositiveButton(R.string.exit) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
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
            showPermissionFlow()
        }
    }
    
    private fun checkFirstRun() {
        if (prefs.isFirstRun) {
            prefs.isFirstRun = false
        }
    }
    
    private fun showPermissionFlow() {
        val missingPermissions = getMissingPermissions()
        
        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show welcome + permission list only once, then request individual permissions
        val introMessage = buildString {
            appendLine("Oathkeeper helps you maintain accountability by monitoring screen content locally on your device.")
            appendLine("No data is ever sent to external servers.")
            appendLine()
            appendLine("The following permissions are required:")
        }
        
        val message = introMessage + missingPermissions.joinToString("\n") { "\u2022 $it" }
        
        AlertDialog.Builder(this)
            .setTitle("Welcome to Oathkeeper")
            .setMessage(message)
            .setPositiveButton("Get Started") { _, _ ->
                requestNextPermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPermissionRationale() {
        // Delegate to showPermissionFlow for consistency
        showPermissionFlow()
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
        
        // Check MediaProjection - stored result code indicates permission was granted before
        if (prefs.mediaProjectionResultCode == -1 && prefs.mediaProjectionData == null) {
            missing.add("Screen Capture Permission")
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
            prefs.mediaProjectionResultCode == -1 && prefs.mediaProjectionData == null -> {
                requestMediaProjectionPermission()
            }
            else -> {
                // All permissions granted - update UI to show service is ready to start
                Toast.makeText(this, "All permissions granted! Tap 'Start Monitoring' to begin.", Toast.LENGTH_LONG).show()
                updateUI()
            }
        }
    }
    
    private fun requestMediaProjectionPermission() {
        AlertDialog.Builder(this)
            .setTitle("Screen Capture Permission")
            .setMessage("Oathkeeper needs screen capture permission to monitor content. " +
                    "This allows the app to take screenshots of your screen for analysis.\n\n" +
                    "No content is sent to any server - all analysis happens on-device.")
            .setPositiveButton("Allow") { _, _ ->
                mediaProjectionManager?.let { mgr ->
                    startActivityForResult(
                        mgr.createScreenCaptureIntent(),
                        Constants.REQUEST_MEDIA_PROJECTION
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }
    
    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(PermissionUtils.getAccessibilityServiceInstructions())
            .setPositiveButton("OK") { _, _ ->
                PermissionUtils.openAccessibilitySettings(this, Constants.REQUEST_ACCESSIBILITY_SERVICE)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startServiceWithPermissions() {
        if (!checkAllPermissions()) {
            showPermissionRationale()
            return
        }
        
        // Check if Accessibility Service is enabled
        if (PermissionUtils.isAccessibilityServiceEnabled(this)) {
            // Service already enabled - start monitoring service
            startMonitoringService()
        } else {
            // Guide user to enable it in system settings
            showAccessibilityServiceDialog()
        }
    }
    
    private fun startMonitoringService() {
        val intent = Intent(this, OathkeeperAccessibilityService::class.java)
        
        // Transfer MediaProjection data if available
        if (mediaProjectionResultCode != -1 && mediaProjectionData != null) {
            OathkeeperAccessibilityService.setPendingMediaProjection(
                mediaProjectionResultCode,
                mediaProjectionData
            )
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(Constants.TAG, "Failed to start service", e)
        }
    }
    
    private fun stopService() {
        // Send broadcast to stop the service
        val stopIntent = Intent("com.oathkeeper.app.STOP_SERVICE").setPackage(packageName)
        sendBroadcast(stopIntent)
        
        prefs.isServiceEnabled = false
        updateUI()
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun checkAllPermissions(): Boolean {
        return PermissionUtils.isAccessibilityServiceEnabled(this) &&
               PermissionUtils.hasOverlayPermission(this) &&
               PermissionUtils.hasUsageStatsPermission(this) &&
               PermissionUtils.isIgnoringBatteryOptimizations(this) &&
               (prefs.mediaProjectionResultCode != -1 || prefs.mediaProjectionData != null)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            Constants.REQUEST_ACCESSIBILITY_SERVICE -> {
                // Re-check accessibility and continue requesting remaining permissions
                requestNextPermission()
            }
            Constants.REQUEST_MEDIA_PROJECTION -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Store the result for persistence
                    prefs.mediaProjectionResultCode = resultCode
                    prefs.mediaProjectionData = "granted"
                    
                    // Store in companion object for accessibility service to use
                    mediaProjectionResultCode = resultCode
                    mediaProjectionData = data
                    
                    Log.d(Constants.TAG, "MediaProjection permission granted")
                } else {
                    Log.w(Constants.TAG, "MediaProjection permission denied")
                    Toast.makeText(this, "Screen capture permission is required", Toast.LENGTH_LONG).show()
                }
                // Continue with remaining permissions or start service
                requestNextPermission()
            }
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
        
        // Check if user enabled Accessibility Service after being prompted
        if (!OathkeeperAccessibilityService.isRunning && 
            PermissionUtils.isAccessibilityServiceEnabled(this)) {
            // User just enabled it - check permissions and auto-start
            if (checkAllPermissions()) {
                startMonitoringService()
            }
        }
        
        updateUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}
