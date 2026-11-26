package com.agenticos.agent.utils

import kotlinx.serialization.json.Json

object JsonProvider {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }
}