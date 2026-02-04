package com.oathkeeper.app.model

data class DetectionEvent(
    val id: Long = 0,
    val timestamp: Long,
    val detectedClass: String,
    val severity: String,
    val confidence: Float,
    val screenshotPath: String? = null,
    val appName: String? = null,
    val isReviewed: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
