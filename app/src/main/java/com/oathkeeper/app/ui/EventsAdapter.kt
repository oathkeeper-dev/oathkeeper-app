package com.oathkeeper.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oathkeeper.app.R
import com.oathkeeper.app.model.DetectionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventsAdapter(
    private val onItemClick: (DetectionEvent) -> Unit,
    private val onMarkReviewed: (DetectionEvent) -> Unit
) : ListAdapter<DetectionEvent, EventsAdapter.EventViewHolder>(EventDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val classText: TextView = itemView.findViewById(R.id.classText)
        private val severityText: TextView = itemView.findViewById(R.id.severityText)
        private val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val reviewedIndicator: View = itemView.findViewById(R.id.reviewedIndicator)
        
        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        
        fun bind(event: DetectionEvent) {
            classText.text = event.detectedClass.capitalize(Locale.getDefault())
            severityText.text = event.severity
            confidenceText.text = String.format("%.1f%%", event.confidence * 100)
            timestampText.text = dateFormat.format(Date(event.timestamp))
            
            // Set severity color
            val severityColor = when (event.severity) {
                "CRITICAL" -> android.R.color.holo_red_dark
                "WARNING" -> android.R.color.holo_orange_dark
                "INFO" -> android.R.color.holo_blue_dark
                else -> android.R.color.darker_gray
            }
            severityText.setTextColor(itemView.context.getColor(severityColor))
            
            // Show/hide reviewed indicator
            reviewedIndicator.visibility = if (event.isReviewed) View.GONE else View.VISIBLE
            
            itemView.setOnClickListener { onItemClick(event) }
        }
    }
    
    class EventDiffCallback : DiffUtil.ItemCallback<DetectionEvent>() {
        override fun areItemsTheSame(oldItem: DetectionEvent, newItem: DetectionEvent): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: DetectionEvent, newItem: DetectionEvent): Boolean {
            return oldItem == newItem
        }
    }
}
