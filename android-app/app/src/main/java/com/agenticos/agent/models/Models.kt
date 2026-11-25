package com.agenticos.agent.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.agenticos.agent.utils.Converters
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @TypeConverters(Converters::class) val trigger: TriggerSpec,
    @TypeConverters(Converters::class) val actions: List<ActionSpec>,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class TriggerSpec(
    val type: TriggerType,
    val cronExpression: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
    val notificationPackage: String? = null,
    val notificationTextContains: String? = null,
    val batteryThreshold: Int? = null,
    val wifiSsid: String? = null
)

@Serializable
data class ActionSpec(
    val tool: String,
    val paramsJson: String
)

enum class TriggerType {
    TIME, LOCATION_ENTER, LOCATION_EXIT, MISSED_CALL,
    NOTIFICATION, BATTERY_LOW, WIFI_CONNECTED, WIFI_DISCONNECTED
}

@Serializable
@Entity(tableName = "conversation_memory")
data class ConversationMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userText: String,
    val agentResponse: String,
    val toolCallsJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ToolCall(
    val tool: String,
    val params: Map<String, Any>,
    val speak: String?
) {
    companion object {
        fun fromJson(json: String): ToolCall = kotlinx.serialization.decodeFromString(json)
        fun toJson(call: ToolCall): String = kotlinx.serialization.encodeToString(call)
    }
}

@Serializable
data class ToolResult(
    val tool: String,
    val success: Boolean,
    val data: Map<String, Any>? = null,
    val error: String? = null,
    val needsFollowUp: Boolean = false,
    val summary: String? = null
)

@Serializable
data class LLMResponse(
    val tool: String?,
    val params: Map<String, Any>?,
    val speak: String?,
    val needsInternet: Boolean = false
)

@Serializable
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: String? = null
)