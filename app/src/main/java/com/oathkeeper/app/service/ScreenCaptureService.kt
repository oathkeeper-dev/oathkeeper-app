package com.oathkeeper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oathkeeper.app.R
import com.oathkeeper.app.ui.MainActivity
import com.oathkeeper.app.util.Constants
import com.oathkeeper.app.util.PreferenceManager

class ScreenCaptureService : Service() {
    
    private var mediaProjection: MediaProjection? = null
    private lateinit var prefs: PreferenceManager
    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null
    private var isRunning = false
    
    companion object {
        private const val TAG = Constants.TAG
        private const val NOTIFICATION_CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID
        private const val NOTIFICATION_CHANNEL_NAME = Constants.NOTIFICATION_CHANNEL_NAME
        private const val NOTIFICATION_ID = Constants.NOTIFICATION_ID
        
        var isServiceRunning = false
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
        createNotificationChannel()
        Log.d(TAG, "ScreenCaptureService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ScreenCaptureService started")
        
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        
        if (resultCode == -1 || data == null) {
            Log.e(TAG, "Invalid MediaProjection data")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        try {
            setupMediaProjection(resultCode, data)
            startCaptureLoop()
            isRunning = true
            isServiceRunning = true
            prefs.isServiceEnabled = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        
        return START_STICKY
    }
    
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                stopSelf()
            }
        }, handler)
        
        Log.d(TAG, "MediaProjection setup complete")
    }
    
    private fun startCaptureLoop() {
        captureRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                
                // TODO: Phase 2 - Implement frame capture for ML inference
                // For Phase 1, we just keep the service running with notification
                Log.d(TAG, "Capture tick - Service is running")
                
                handler.postDelayed(this, prefs.captureInterval)
            }
        }
        
        handler.post(captureRunnable!!)
        Log.d(TAG, "Capture loop started")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Oathkeeper screen capture service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).apply {
                action = "STOP_SERVICE"
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Oathkeeper")
            .setContentText("Monitoring active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isServiceRunning = false
        prefs.isServiceEnabled = false
        
        captureRunnable?.let { handler.removeCallbacks(it) }
        mediaProjection?.stop()
        
        Log.d(TAG, "ScreenCaptureService destroyed")
    }
}
