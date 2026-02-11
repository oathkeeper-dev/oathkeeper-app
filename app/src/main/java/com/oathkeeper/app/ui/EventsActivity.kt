package com.oathkeeper.app.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oathkeeper.app.R
import com.oathkeeper.app.model.DetectionEvent
import com.oathkeeper.app.storage.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventsActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: EventsAdapter
    private lateinit var databaseManager: DatabaseManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        databaseManager = DatabaseManager.getInstance(this)
        
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        
        adapter = EventsAdapter(
            onItemClick = { event -> showEventDetails(event) },
            onMarkReviewed = { event -> markAsReviewed(event) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        loadEvents()
    }
    
    private fun loadEvents() {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                databaseManager.getAllEvents()
            }
            
            if (events.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                adapter.submitList(events)
            }
        }
    }
    
    private fun showEventDetails(event: DetectionEvent) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date(event.timestamp))
        
        val message = buildString {
            appendLine("Detected: ${event.detectedClass}")
            appendLine("Severity: ${event.severity}")
            appendLine("Confidence: ${String.format("%.1f%%", event.confidence * 100)}")
            appendLine("Time: $timestamp")
            event.appName?.let { appendLine("App: $it") }
            if (event.isReviewed) {
                appendLine("Status: Reviewed")
                event.notes?.let { appendLine("Notes: $it") }
            } else {
                appendLine("Status: Unreviewed")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Event Details")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .apply {
                if (!event.isReviewed) {
                    setNeutralButton("Mark as Reviewed") { _, _ ->
                        markAsReviewed(event)
                    }
                }
                setNegativeButton("Delete") { _, _ ->
                    confirmDelete(event)
                }
            }
            .show()
    }
    
    private fun markAsReviewed(event: DetectionEvent) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                databaseManager.markEventAsReviewed(event.id, "Reviewed by user")
            }
            loadEvents()
        }
    }
    
    private fun confirmDelete(event: DetectionEvent) {
        AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete this event?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEvent(event)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteEvent(event: DetectionEvent) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                databaseManager.deleteEvent(event.id)
            }
            loadEvents()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_events, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_filter_critical -> {
                filterBySeverity("CRITICAL")
                true
            }
            R.id.action_filter_warning -> {
                filterBySeverity("WARNING")
                true
            }
            R.id.action_show_all -> {
                loadEvents()
                true
            }
            R.id.action_clear_all -> {
                confirmClearAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun filterBySeverity(severity: String) {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                databaseManager.getEventsBySeverity(severity)
            }
            
            if (events.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.text = "No $severity events found"
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                adapter.submitList(events)
            }
        }
    }
    
    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Events")
            .setMessage("Are you sure you want to delete all events? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllEvents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearAllEvents() {
        lifecycleScope.launch {
            // Note: This would need a clearAll method in DatabaseManager
            // For now, we'll just reload
            loadEvents()
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadEvents()
    }
}
