package com.agenticos.agent.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.agenticos.agent.utils.Converters
import com.agenticos.agent.utils.JsonProvider
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ===========================================================
// ROOM ENTITIES
// ===========================================================

@Serializable
@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String,
    val triggerData: String = "", // JSON string for trigger params
    val actionsJson: String = "[]", // JSON string of action list
    val enabled: Boolean = true,
    val cronExpression: String? = null, // For TIME-based triggers
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(json: String): AutomationRule = JsonProvider.json.decodeFromString(AutomationRule.serializer(), json)
        fun toJson(rule: AutomationRule): String = JsonProvider.json.encodeToString(AutomationRule.serializer(), rule)
    }
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

// ===========================================================
// DOMAIN MODELS (Not Room entities)
// ===========================================================

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
) {
    companion object {
        fun fromJson(json: String): TriggerSpec = JsonProvider.json.decodeFromString(TriggerSpec.serializer(), json)
        fun toJson(spec: TriggerSpec): String = JsonProvider.json.encodeToString(TriggerSpec.serializer(), spec)
    }
}

@Serializable
data class ActionSpec(
    val tool: String,
    val params: JsonObject
) {
    companion object {
        fun fromJson(json: String): ActionSpec = JsonProvider.json.decodeFromString(ActionSpec.serializer(), json)
        fun toJson(spec: ActionSpec): String = JsonProvider.json.encodeToString(ActionSpec.serializer(), spec)
    }
}

@Serializable
data class ToolCall(
    val tool: String,
    val params: JsonObject,
    val speak: String?
) {
    companion object {
        fun fromJson(json: String): ToolCall = JsonProvider.json.decodeFromString(ToolCall.serializer(), json)
        fun toJson(call: ToolCall): String = JsonProvider.json.encodeToString(ToolCall.serializer(), call)
    }
}

@Serializable
data class ToolResult(
    val tool: String,
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null,
    val needsFollowUp: Boolean = false,
    val summary: String? = null
)

@Serializable
data class LLMResponse(
    val tool: String? = null,
    val params: JsonObject? = null,
    val speak: String? = null,
    val needsInternet: Boolean = false
)

@Serializable
data class RecognitionResult(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean,
    val alternatives: List<String> = emptyList(),
    val needsRepeat: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class VoiceProfile(
    val userId: String,
    val customVocabulary: List<String>,
    val aliases: Map<String, String>
)

enum class TriggerType {
    TIME, LOCATION_ENTER, LOCATION_EXIT, MISSED_CALL,
    NOTIFICATION, BATTERY_LOW, WIFI_CONNECTED, WIFI_DISCONNECTED
}

@Serializable
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: String? = null
)

@Serializable
data class NotificationEvent(
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val summaryText: String? = null,
    val isCall: Boolean = false,
    val isSms: Boolean = false,
    val sender: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

typealias ToolParams = Map<String, String>