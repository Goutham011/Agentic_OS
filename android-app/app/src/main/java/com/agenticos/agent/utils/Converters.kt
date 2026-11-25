package com.agenticos.agent.utils

import androidx.room.TypeConverter
import com.agenticos.agent.models.ActionSpec
import com.agenticos.agent.models.TriggerSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class Converters {
    @TypeConverter
    fun fromTriggerSpec(value: String): TriggerSpec = decodeFromString(value)

    @TypeConverter
    fun toTriggerSpec(spec: TriggerSpec): String = encodeToString(spec)

    @TypeConverter
    fun fromActionSpecList(value: String): List<ActionSpec> = decodeFromString(value)

    @TypeConverter
    fun toActionSpecList(list: List<ActionSpec>): String = encodeToString(list)
}