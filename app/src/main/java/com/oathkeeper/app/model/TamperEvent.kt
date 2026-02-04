package com.oathkeeper.app.model

data class TamperEvent(
    val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val details: String? = null,
    val recovered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
