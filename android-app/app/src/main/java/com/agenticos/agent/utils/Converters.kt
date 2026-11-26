package com.agenticos.agent.utils

import androidx.room.TypeConverter
import com.agenticos.agent.models.AutomationRule
import com.agenticos.agent.models.TriggerSpec
import com.agenticos.agent.models.ActionSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.builtins.ListSerializer

class Converters {
    companion object {
        @TypeConverter
        @JvmStatic
        fun fromTriggerSpec(trigger: TriggerSpec): String {
            return JsonProvider.json.encodeToString(TriggerSpec.serializer(), trigger)
        }
        
        @TypeConverter
        @JvmStatic
        fun toTriggerSpec(value: String): TriggerSpec {
            return JsonProvider.json.decodeFromString(TriggerSpec.serializer(), value)
        }
        
        @TypeConverter
        @JvmStatic
        fun fromActionSpecs(actions: List<ActionSpec>): String {
            return JsonProvider.json.encodeToString(ListSerializer(ActionSpec.serializer()), actions)
        }
        
        @TypeConverter
        @JvmStatic
        fun toActionSpecs(value: String): List<ActionSpec> {
            return JsonProvider.json.decodeFromString(ListSerializer(ActionSpec.serializer()), value)
        }
        
        @TypeConverter
        @JvmStatic
        fun fromJsonObject(json: JsonObject?): String {
            return json?.let { JsonProvider.json.encodeToString(JsonObject.serializer(), it) } ?: "{}"
        }
        
        @TypeConverter
        @JvmStatic
        fun toJsonObject(value: String): JsonObject? {
            return try {
                JsonProvider.json.decodeFromString(JsonObject.serializer(), value)
            } catch (e: Exception) {
                JsonObject(mutableMapOf())
            }
        }
        
        @TypeConverter
        @JvmStatic
        fun fromAutomationRule(rule: AutomationRule): String {
            return JsonProvider.json.encodeToString(AutomationRule.serializer(), rule)
        }
        
        @TypeConverter
        @JvmStatic
        fun toAutomationRule(value: String): AutomationRule {
            return JsonProvider.json.decodeFromString(AutomationRule.serializer(), value)
        }
    }
}