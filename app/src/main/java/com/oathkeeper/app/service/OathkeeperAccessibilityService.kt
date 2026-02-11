package com.oathkeeper.app.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.oathkeeper.app.R
import com.oathkeeper.app.ml.NsfwClassifier
import com.oathkeeper.app.model.DetectionEvent
import com.oathkeeper.app.storage.DatabaseManager
import com.oathkeeper.app.ui.MainActivity
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OathkeeperAccessibilityService : AccessibilityService() {
    
    private lateinit var nsfwClassifier: NsfwClassifier
    private lateinit var prefs: PreferenceManager
    private lateinit var databaseManager: DatabaseManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var isServiceRunning = false
    private var lastCaptureTime = 0L
    private var captureHandler: Handler? = null
    private var captureRunnable: Runnable? = null
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var windowManager: WindowManager? = null
    
    companion object {
        private const val TAG = "Oathkeeper"
        private const val NOTIFICATION_CHANNEL_ID = "oathkeeper_accessibility"
        private const val NOTIFICATION_CHANNEL_NAME = "Oathkeeper Accessibility Service"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP_SERVICE = "com.oathkeeper.app.STOP_SERVICE"
        
        var isRunning = false
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
        DatabaseManager.initialize(this)
        databaseManager = DatabaseManager.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Register stop receiver
        val filter = IntentFilter(ACTION_STOP_SERVICE)
        ContextCompat.registerReceiver(this, stopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        Log.d(TAG, "OathkeeperAccessibilityService created")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        try {
            nsfwClassifier = NsfwClassifier(assets)
            isServiceRunning = true
            isRunning = true
            prefs.isServiceEnabled = true
            
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Start periodic capture
            startPeriodicCapture()
            
            Log.d(TAG, "Accessibility service connected and started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service: ${e.message}")
            stopSelf()
        }
    }
    
    private fun startPeriodicCapture() {
        captureHandler = Handler(Looper.getMainLooper())
        captureRunnable = object : Runnable {
            override fun run() {
                if (!isServiceRunning) return
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCaptureTime >= prefs.captureInterval) {
                    captureAndAnalyze()
                    lastCaptureTime = currentTime
                }
                
                captureHandler?.postDelayed(this, 1000) // Check every second
            }
        }
        captureHandler?.post(captureRunnable!!)
    }
    
    private fun captureAndAnalyze() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use the new takeScreenshot API on Android 11+
            val executor = mainExecutor
            takeScreenshot(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        
                        if (hardwareBuffer != null) {
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()
                            
                            bitmap?.let { processScreenshot(it) }
                        }
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                    }
                }
            )
        } else {
            // For older devices, use MediaProjection-based approach
            captureWithMediaProjection()
        }
    }
    
    private fun captureWithMediaProjection() {
        // For Android 10 and below, we need to use MediaProjection
        // This would require setting up MediaProjection and capturing
        // Since we're focusing on Android 11+, this is a fallback
        Log.d(TAG, "MediaProjection capture not implemented for Android < 11")
    }
    
    private fun processScreenshot(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                val result = nsfwClassifier.classify(bitmap)
                
                if (shouldLogDetection(result)) {
                    logDetection(result, bitmap)
                }
                
                // Recycle the bitmap to free memory
                if (bitmap.isRecycled.not()) {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshot: ${e.message}")
                bitmap.recycle()
            }
        }
    }
    
    private fun shouldLogDetection(result: NsfwClassifier.ClassificationResult): Boolean {
        return when (result.detectedClass) {
            "porn" -> result.confidence > prefs.thresholdPorn
            "sexy" -> result.confidence > prefs.thresholdSexy
            "hentai" -> result.confidence > 0.6f
            "drawings" -> false // Don't log drawings as they're often benign
            else -> false
        }
    }
    
    private suspend fun logDetection(result: NsfwClassifier.ClassificationResult, bitmap: Bitmap) {
        val event = DetectionEvent(
            timestamp = System.currentTimeMillis(),
            detectedClass = result.detectedClass,
            severity = result.severity,
            confidence = result.confidence,
            appName = getCurrentAppName(),
            screenshotPath = null // Will be implemented in Phase 3 with pixelation
        )
        
        val id = databaseManager.insertEvent(event)
        
        if (id > 0) {
            Log.d(TAG, "Detection logged: ${result.detectedClass} (${result.confidence})")
            
            if (prefs.enableNotifications) {
                showDetectionNotification(result)
            }
        }
    }
    
    private fun getCurrentAppName(): String? {
        // Get the current foreground app name
        return try {
            val packageName = rootInActiveWindow?.packageName?.toString()
            packageName?.let {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(it, 0)
                pm.getApplicationLabel(appInfo).toString()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun showDetectionNotification(result: NsfwClassifier.ClassificationResult) {
        val title = when (result.severity) {
            "CRITICAL" -> "Critical Content Detected"
            "WARNING" -> "Warning: Potentially Inappropriate Content"
            else -> "Content Detected"
        }
        
        val message = "Detected: ${result.detectedClass} (${String.format("%.1f%%", result.confidence * 100)})"
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Oathkeeper monitoring service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Oathkeeper")
            .setContentText("Monitoring active - protecting your digital wellbeing")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Monitoring", stopIntent)
            .build()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Handle accessibility events if needed
        // For now, we're using periodic capture, but we could trigger on window changes
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Window changed - could trigger immediate capture
                // captureAndAnalyze()
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_STOP_SERVICE) {
                stopSelf()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        isServiceRunning = false
        isRunning = false
        prefs.isServiceEnabled = false
        
        captureHandler?.removeCallbacks(captureRunnable!!)
        serviceScope.cancel()
        
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        
        nsfwClassifier.close()
        databaseManager.close()
        
        Log.d(TAG, "OathkeeperAccessibilityService destroyed")
    }
}
