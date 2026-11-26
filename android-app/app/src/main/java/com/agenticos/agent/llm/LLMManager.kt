package com.agenticos.agent.llm

import android.content.Context
import com.agenticos.agent.models.LLMResponse
import com.agenticos.agent.models.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface LLMManager {
    suspend fun infer(prompt: String): LLMResponse
    suspend fun inferWithContext(prompt: String, context: Map<String, Any>): LLMResponse
    fun getModelInfo(): String
}

// Helper to convert Map to JsonObject
private fun mapOfToJson(map: Map<String, Any>): JsonObject {
    val jsonMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    map.forEach { (k, v) ->
        jsonMap[k] = when (v) {
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Map<*, *> -> JsonObject(v.entries.filterIsInstance<Map.Entry<String, Any>>().associate {
                it.key to JsonPrimitive(it.value.toString())
            } as Map<String, kotlinx.serialization.json.JsonElement>)
            else -> JsonPrimitive(v.toString())
        }
    }
    return JsonObject(jsonMap)
}

private fun jsonObjectOf(vararg pairs: Pair<String, Any>): JsonObject {
    val map = pairs.map { (k, v) ->
        k to when (v) {
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is JsonObject -> v
            else -> JsonPrimitive(v.toString())
        }
    }.toMap()
    return JsonObject(map)
}

// ===== RULE-BASED FALLBACK (For Prototype) =====
class RuleBasedLLMManager : LLMManager {
    override suspend fun infer(prompt: String): LLMResponse = withContext(Dispatchers.IO) {
        inferWithContext(prompt, emptyMap())
    }
    
    override suspend fun inferWithContext(prompt: String, context: Map<String, Any>): LLMResponse = withContext(Dispatchers.IO) {
        val lower = prompt.lowercase()
        
        // FIX #6: Intent disambiguation with priority ordering
        // Check for more specific patterns first, then fall back to general patterns
        // This prevents collision on multi-intent phrases like "text me weather news"
        
        // Web search / news / weather (check FIRST for multi-intent disambiguation)
        if (containsAny(lower, listOf("weather", "news", "sports", "stock", "exchange rate", "exchange rate"))) {
            return@withContext LLMResponse(
                tool = "web_search",
                params = jsonObjectOf("query" to prompt, "num_results" to 5),
                speak = "Searching for that...",
                needsInternet = true
            )
        }
        
        // Open app - check early
        if (containsVerb(lower, "open", "launch", "start")) {
            val appName = extractAppName(lower)
            return@withContext LLMResponse(
                tool = "open_app",
                params = jsonObjectOf("package_name" to resolvePackage(appName)),
                speak = "Opening $appName",
                needsInternet = false
            )
        }
        
        // Send SMS - IMPORTANT: Check for "text" but NOT when followed by content keywords
        // This fixes the collision: "text me weather news" should search, not SMS
        if (containsWord(lower, "text") || containsWord(lower, "sms") || containsWord(lower, "message")) {
            // Disambiguation: If text is followed by news/weather/content keywords = search
            if (containsAny(lower, listOf("weather", "news", "sports", "sports news", "latest", "today", "tomorrow"))) {
                return@withContext LLMResponse(
                    tool = "web_search",
                    params = jsonObjectOf("query" to prompt, "num_results" to 5),
                    speak = "Searching for that...",
                    needsInternet = true
                )
            }
            // Ask clarification for SMS
            return@withContext LLMResponse(
                tool = "send_sms",
                params = jsonObjectOf("phone_number" to "", "message" to ""),
                speak = "Who should I text and what should I say?",
                needsInternet = false
            )
        }
        
        // Make call - check before generic "call" phrases
        if (containsWord(lower, "call") && !containsAny(lower, listOf("callback", "call back", "call me back"))) {
            return@withContext LLMResponse(
                tool = "make_call",
                params = jsonObjectOf("phone_number" to ""),
                speak = "Who should I call?",
                needsInternet = false
            )
        }
        
        // Set alarm - check for time-specific context
        if (containsAny(lower, listOf("alarm", "wake me", "reminder")) && containsTime(lower)) {
            val time = extractTime(lower)
            return@withContext LLMResponse(
                tool = "set_alarm",
                params = jsonObjectOf("time" to time),
                speak = "Alarm set for $time",
                needsInternet = false
            )
        }
        
        // Play media
        if (containsVerb(lower, "play", "music", "song")) {
            val query = extractMediaQuery(lower)
            return@withContext LLMResponse(
                tool = "play_media",
                params = jsonObjectOf("query" to query, "source" to "youtube"),
                speak = "Playing $query on YouTube",
                needsInternet = false
            )
        }
        
        // Toggle WiFi - check for explicit on/off context
        if (containsWord(lower, "wifi") || containsWord(lower, "wi-fi")) {
            val enabled = containsAny(lower, listOf("on", "enable", "turn on", "connect", "connected"))
            return@withContext LLMResponse(
                tool = "toggle_wifi",
                params = jsonObjectOf("enabled" to enabled.toString()),
                speak = "Turning WiFi ${if (enabled) "on" else "off"}",
                needsInternet = false
            )
        }
        
        // Toggle Bluetooth
        if (containsWord(lower, "bluetooth")) {
            val enabled = containsAny(lower, listOf("on", "enable", "turn on"))
            return@withContext LLMResponse(
                tool = "toggle_bluetooth",
                params = jsonObjectOf("enabled" to enabled.toString()),
                speak = "Turning Bluetooth ${if (enabled) "on" else "off"}",
                needsInternet = false
            )
        }
        
        // Set ringer mode - check for silent/vibrate first
        if (containsWord(lower, "silent") || containsWord(lower, "vibrate") || containsWord(lower, "ringer")) {
            val mode = when {
                containsWord(lower, "silent") -> "silent"
                containsWord(lower, "vibrate") -> "vibrate"
                else -> "normal"
            }
            return@withContext LLMResponse(
                tool = "set_ringer_mode",
                params = jsonObjectOf("mode" to mode),
                speak = "Setting ringer to $mode",
                needsInternet = false
            )
        }
        
        // Summarize URL
        if (containsWord(lower, "summarize") && prompt.contains("http")) {
            val url = extractUrl(prompt)
            return@withContext LLMResponse(
                tool = "summarize_url",
                params = jsonObjectOf("url" to url, "max_length" to 200),
                speak = "Summarizing that page...",
                needsInternet = true
            )
        }
        
        // Default fallback
        LLMResponse(
            tool = null,
            params = null,
            speak = "I can help with: opening apps, sending texts, making calls, setting alarms, playing music, toggling WiFi/Bluetooth, setting ringer mode, searching the web, or summarizing pages. What would you like?",
            needsInternet = false
        )
    }
    
    // Helper function to check word boundaries (avoids "text" matching "textbook")
    private fun containsWord(text: String, word: String): Boolean {
        val pattern = "\\b${Regex.escape(word)}\\b".toRegex(RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }
    
    // Helper function to check if text contains any of the keywords
    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            text.contains(keyword.lowercase())
        }
    }
    
    // Helper function to check for action verbs
    private fun containsVerb(text: String, vararg verbs: String): Boolean {
        return verbs.any { verb ->
            // Match "X" or "please X" or "can you X" patterns
            val pattern = "(?:please|can you|can\\'t you)\\s+(${verbs.joinToString("|")})".toRegex(RegexOption.IGNORE_CASE)
            pattern.containsMatchIn(text) || text.contains(verb.lowercase())
        }
    }
    
    // Check if text contains time references
    private fun containsTime(text: String): Boolean {
        val timeRegex = "\\b(\\d{1,2}):(\\d{2})\\b".toRegex()
        val ampmRegex = "\\b(\\d{1,2})\\s*(am|pm)\\b".toRegex()
        return timeRegex.containsMatchIn(text) || ampmRegex.containsMatchIn(text)
    }
    
    private fun extractAppName(input: String): String {
        val keywords = listOf("open ", "launch ", "start ", "play ", "music on ", "play music on ")
        for (kw in keywords) {
            val idx = input.indexOf(kw)
            if (idx >= 0) {
                val after = input.substring(idx + kw.length).trim()
                // Handle "youtube music" case
                val end = after.indexOf(" ")
                return if (end > 0) after.substring(0, end) else after
            }
        }
        return "app"
    }
    
    private fun resolvePackage(appName: String): String {
        return when (appName.lowercase()) {
            "youtube", "yt" -> "com.google.android.youtube"
            "youtube music", "yt music" -> "com.google.android.apps.youtube.music"
            "spotify" -> "com.spotify.music"
            "chrome" -> "com.android.chrome"
            "maps", "google maps" -> "com.google.android.apps.maps"
            "gmail" -> "com.google.android.gm"
            "messages", "sms" -> "com.google.android.apps.messaging"
            "phone", "dialer" -> "com.google.android.dialer"
            "settings" -> "com.android.settings"
            "camera" -> "com.android.camera"
            "photos", "gallery" -> "com.google.android.apps.photos"
            "drive" -> "com.google.android.apps.docs"
            "calendar" -> "com.google.android.calendar"
            "keep", "notes" -> "com.google.android.keep"
            "assistant" -> "com.google.android.googlequicksearchbox"
            else -> "com.google.android.youtube" // default
        }
    }
    
    private fun extractTime(input: String): String {
        // Simple extraction: look for HH:MM or "X AM/PM"
        val timeRegex = "\\b(\\d{1,2}):(\\d{2})\\b".toRegex()
        val match = timeRegex.find(input)
        if (match != null) {
            return "${match.groupValues[1].padStart(2, '0')}:${match.groupValues[2]}"
        }
        // Check for "X AM/PM"
        val ampmRegex = "\\b(\\d{1,2})\\s*(am|pm)\\b".toRegex()
        val ampmMatch = ampmRegex.find(input)
        if (ampmMatch != null) {
            val hour = ampmMatch.groupValues[1].toInt()
            val ampm = ampmMatch.groupValues[2]
            val hour24 = when {
                ampm == "pm" && hour != 12 -> hour + 12
                ampm == "am" && hour == 12 -> 0
                else -> hour
            }
            return "${hour24.toString().padStart(2, '0')}:00"
        }
        return "07:00" // default
    }
    
    private fun extractMediaQuery(input: String): String {
        val keywords = listOf("play ", "music ", "song ", "music on ", "play music on ")
        for (kw in keywords) {
            val idx = input.indexOf(kw)
            if (idx >= 0) {
                return input.substring(idx + kw.length).trim()
            }
        }
        return "music"
    }
    
    private fun extractUrl(input: String): String {
        val urlRegex = "https?://[^\\s]+".toRegex()
        return urlRegex.find(input)?.value ?: ""
    }

    override fun getModelInfo(): String = "Rule-Based LLM (Prototype with intent disambiguation)"
}

// ===== MEDIAPIPE LLM MANAGER (Production) =====
/*
class MediaPipeLLMManager(private val context: Context) : LLMManager {
    private var llmInference: com.google.mediapipe.tasks.genai.LlmInference? = null
    private val modelPath = "gemma_2b_it_gpu.tflite" // or phi-3-mini, etc.
    
    override suspend fun infer(prompt: String): LLMResponse = withContext(Dispatchers.IO) {
        // Initialize if needed
        if (llmInference == null) {
            val options = com.google.mediapipe.tasks.genai.LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.7f)
                .setRandomSeed(42)
                .build()
            llmInference = com.google.mediapipe.tasks.genai.LlmInference.createFromOptions(context, options)
        }
        
        val response = llmInference?.generateResponse(prompt) ?: return@withContext LLMResponse(
            tool = null, params = null, speak = "Model not loaded", needsInternet = false
        )
        
        // Parse JSON from response
        return@withContext parseResponse(response)
    }
    
    override suspend fun inferWithContext(prompt: String, context: Map<String, Any>): LLMResponse {
        val fullPrompt = buildPrompt(prompt, context)
        return infer(fullPrompt)
    }
    
    private fun buildPrompt(userText: String, ctx: Map<String, Any>): String {
        // Use the system prompt from Agent.md
        return """
            SYSTEM: You are Agentic_OS...
            USER: $userText
            CONTEXT: ${kotlinx.serialization.encodeToString(ctx)}
        """.trimIndent()
    }
    
    private fun parseResponse(text: String): LLMResponse {
        // Extract JSON from LLM response
        try {
            val jsonStart = text.indexOf("{")
            val jsonEnd = text.lastIndexOf("}") + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val json = text.substring(jsonStart, jsonEnd)
                return kotlinx.serialization.decodeFromString(json)
            }
        } catch (e: Exception) {
            // Fall through
        }
        return LLMResponse(tool = null, params = null, speak = text, needsInternet = false)
    }
    
    override fun getModelInfo(): String = "MediaPipe LLM (On-device)"
}*/