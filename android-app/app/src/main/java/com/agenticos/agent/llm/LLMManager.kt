package com.agenticos.agent.llm

import android.content.Context
import com.agenticos.agent.models.LLMResponse
import com.agenticos.agent.models.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LLMManager {
    suspend fun infer(prompt: String): LLMResponse
    suspend fun inferWithContext(prompt: String, context: Map<String, Any>): LLMResponse
    fun getModelInfo(): String
}

// ===== RULE-BASED FALLBACK (For Prototype) =====
class RuleBasedLLMManager : LLMManager {
    override suspend fun infer(prompt: String): LLMResponse = withContext(Dispatchers.IO) {
        inferWithContext(prompt, emptyMap())
    }

    override suspend fun inferWithContext(prompt: String, context: Map<String, Any>): LLMResponse = withContext(Dispatchers.IO) {
        val lower = prompt.lowercase()
        
        // Open app
        if (lower.contains("open") || lower.contains("launch")) {
            val appName = extractAppName(lower)
            return@withContext LLMResponse(
                tool = "open_app",
                params = mapOf("package_name" to resolvePackage(appName)),
                speak = "Opening $appName",
                needsInternet = false
            )
        }
        
        // Send SMS
        if (lower.contains("text") || lower.contains("sms") || lower.contains("message")) {
            return@withContext LLMResponse(
                tool = "send_sms",
                params = mapOf("phone_number" to "", "message" to ""),
                speak = "Who should I text and what should I say?",
                needsInternet = false
            )
        }
        
        // Make call
        if (lower.contains("call") && !lower.contains("callback")) {
            return@withContext LLMResponse(
                tool = "make_call",
                params = mapOf("phone_number" to ""),
                speak = "Who should I call?",
                needsInternet = false
            )
        }
        
        // Set alarm
        if (lower.contains("alarm") || lower.contains("wake me")) {
            val time = extractTime(lower)
            return@withContext LLMResponse(
                tool = "set_alarm",
                params = mapOf("time" to time),
                speak = "Alarm set for $time",
                needsInternet = false
            )
        }
        
        // Play media
        if (lower.contains("play") || lower.contains("music") || lower.contains("song")) {
            val query = extractMediaQuery(lower)
            return@withContext LLMResponse(
                tool = "play_media",
                params = mapOf("query" to query, "source" to "youtube"),
                speak = "Playing $query on YouTube",
                needsInternet = false
            )
        }
        
        // Toggle WiFi
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val enabled = lower.contains("on") || lower.contains("enable") || lower.contains("turn on")
            return@withContext LLMResponse(
                tool = "toggle_wifi",
                params = mapOf("enabled" to enabled),
                speak = "Turning WiFi ${if (enabled) "on" else "off"}",
                needsInternet = false
            )
        }
        
        // Toggle Bluetooth
        if (lower.contains("bluetooth")) {
            val enabled = lower.contains("on") || lower.contains("enable") || lower.contains("turn on")
            return@withContext LLMResponse(
                tool = "toggle_bluetooth",
                params = mapOf("enabled" to enabled),
                speak = "Turning Bluetooth ${if (enabled) "on" else "off"}",
                needsInternet = false
            )
        }
        
        // Set ringer mode
        if (lower.contains("silent") || lower.contains("vibrate") || lower.contains("ringer")) {
            val mode = when {
                lower.contains("silent") -> "silent"
                lower.contains("vibrate") -> "vibrate"
                else -> "normal"
            }
            return@withContext LLMResponse(
                tool = "set_ringer_mode",
                params = mapOf("mode" to mode),
                speak = "Setting ringer to $mode",
                needsInternet = false
            )
        }
        
        // Web search / news / weather
        if (lower.contains("news") || lower.contains("weather") || lower.contains("search") 
            || lower.contains("who is") || lower.contains("what is") || lower.contains("how to")) {
            return@withContext LLMResponse(
                tool = "web_search",
                params = mapOf("query" to prompt, "num_results" to 5),
                speak = "Searching for that...",
                needsInternet = true
            )
        }
        
        // Summarize URL
        if (lower.contains("summarize") && prompt.contains("http")) {
            val url = extractUrl(prompt)
            return@withContext LLMResponse(
                tool = "summarize_url",
                params = mapOf("url" to url, "max_length" to 200),
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
    
    private fun extractAppName(input: String): String {
        val keywords = listOf("open ", "launch ", "start ")
        for (kw in keywords) {
            val idx = input.indexOf(kw)
            if (idx >= 0) {
                val after = input.substring(idx + kw.length).trim()
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
        val keywords = listOf("play ", "music ", "song ")
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
        return urlRegex.find(input)?.value ?: "https://google.com"
    }

    override fun getModelInfo(): String = "Rule-Based LLM (Prototype)"
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
}
*/