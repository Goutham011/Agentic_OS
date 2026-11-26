package com.agenticos.agent.utils

import kotlinx.serialization.Serializable

@Serializable
data class NotificationEvent(
    val packageName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val summaryText: String,
    val isCall: Boolean,
    val isSms: Boolean,
    val sender: String,
    val timestamp: Long
)