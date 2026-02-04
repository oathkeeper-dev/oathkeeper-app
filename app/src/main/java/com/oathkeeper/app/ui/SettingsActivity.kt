package com.oathkeeper.app.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oathkeeper.app.R
import com.oathkeeper.app.util.PreferenceManager
import com.oathkeeper.app.util.Constants
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var prefs: PreferenceManager
    
    private lateinit var captureIntervalSeekBar: SeekBar
    private lateinit var captureIntervalValue: TextView
    private lateinit var thresholdPornSeekBar: SeekBar
    private lateinit var thresholdPornValue: TextView
    private lateinit var thresholdSexySeekBar: SeekBar
    private lateinit var thresholdSexyValue: TextView
    private lateinit var notificationsSwitch: Switch
    private lateinit var resetButton: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefs = PreferenceManager(this)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        
        initViews()
        loadSettings()
    }
    
    private fun initViews() {
        captureIntervalSeekBar = findViewById(R.id.captureIntervalSeekBar)
        captureIntervalValue = findViewById(R.id.captureIntervalValue)
        thresholdPornSeekBar = findViewById(R.id.thresholdPornSeekBar)
        thresholdPornValue = findViewById(R.id.thresholdPornValue)
        thresholdSexySeekBar = findViewById(R.id.thresholdSexySeekBar)
        thresholdSexyValue = findViewById(R.id.thresholdSexyValue)
        notificationsSwitch = findViewById(R.id.notificationsSwitch)
        resetButton = findViewById(R.id.resetButton)
        
        // Capture interval: 1-10 seconds
        captureIntervalSeekBar.max = 9 // 0-9 representing 1-10 seconds
        captureIntervalSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress + 1
                captureIntervalValue.text = "$seconds seconds"
                if (fromUser) {
                    prefs.captureInterval = seconds * 1000L
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Porn threshold: 50-95%
        thresholdPornSeekBar.max = 45 // 0-45 representing 50-95%
        thresholdPornSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percentage = 50 + progress
                thresholdPornValue.text = "$percentage%"
                if (fromUser) {
                    prefs.thresholdPorn = percentage / 100f
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Sexy threshold: 60-95%
        thresholdSexySeekBar.max = 35 // 0-35 representing 60-95%
        thresholdSexySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percentage = 60 + progress
                thresholdSexyValue.text = "$percentage%"
                if (fromUser) {
                    prefs.thresholdSexy = percentage / 100f
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.enableNotifications = isChecked
        }
        
        resetButton.setOnClickListener {
            resetToDefaults()
        }
    }
    
    private fun loadSettings() {
        // Capture interval
        val intervalSeconds = (prefs.captureInterval / 1000).toInt()
        captureIntervalSeekBar.progress = intervalSeconds - 1
        captureIntervalValue.text = "$intervalSeconds seconds"
        
        // Porn threshold
        val pornPercentage = (prefs.thresholdPorn * 100).toInt()
        thresholdPornSeekBar.progress = pornPercentage - 50
        thresholdPornValue.text = "$pornPercentage%"
        
        // Sexy threshold
        val sexyPercentage = (prefs.thresholdSexy * 100).toInt()
        thresholdSexySeekBar.progress = sexyPercentage - 60
        thresholdSexyValue.text = "$sexyPercentage%"
        
        // Notifications
        notificationsSwitch.isChecked = prefs.enableNotifications
    }
    
    private fun resetToDefaults() {
        prefs.captureInterval = Constants.CAPTURE_INTERVAL_MS
        prefs.thresholdPorn = 0.7f
        prefs.thresholdSexy = 0.8f
        prefs.enableNotifications = true
        
        loadSettings()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
