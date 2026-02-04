package com.oathkeeper.app.model

data class PermissionItem(
    val title: String,
    val description: String,
    val isRequired: Boolean,
    val checkFunction: () -> Boolean,
    val requestFunction: () -> Unit
)
