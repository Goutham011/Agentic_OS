package com.agenticos.agent.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsManager
import com.agenticos.agent.models.ToolCall
import com.agenticos.agent.models.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface Tool {
    val name: String
    val description: String
    val parametersSchema: String // JSON Schema
    val requiresConfirmation: Boolean
    val requiredPermissions: List<String>
    
    suspend fun execute(params: Map<String, Any>): ToolResult
}

abstract class BaseTool(
    override val name: String,
    override val description: String,
    override val parametersSchema: String,
    override val requiresConfirmation: Boolean = false,
    override val requiredPermissions: List<String> = emptyList()
) : Tool {
    override suspend fun execute(params: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) { doExecute(params) }
    
    protected abstract suspend fun doExecute(params: Map<String, Any>): ToolResult
}

// ===== TOOL IMPLEMENTATIONS =====

class OpenAppTool(private val context: Context) : BaseTool(
    name = "open_app",
    description = "Launch an installed application",
    parametersSchema = """{"type":"object","properties":{"package_name":{"type":"string"}}}""",
    requiresConfirmation = false
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val packageName = params["package_name"] as? String ?: return ToolResult(name, false, error = "package_name required")
        val pm: PackageManager = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return ToolResult(name, false, error = "App not found: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult(name, true, summary = "Opened $packageName")
    }
}

class ListAppsTool(private val context: Context) : BaseTool(
    name = "list_apps",
    description = "List all installed applications",
    parametersSchema = "{}",
    requiresConfirmation = false
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { ai -> mapOf("package" to ai.packageName, "label" to pm.getApplicationLabel(ai).toString()) }
        return ToolResult(name, true, data = mapOf("apps" to apps))
    }
}

class SendSmsTool(private val context: Context) : BaseTool(
    name = "send_sms",
    description = "Send an SMS message",
    parametersSchema = """{"type":"object","properties":{"phone_number":{"type":"string"},"message":{"type":"string"}},"required":["phone_number","message"]}""",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.SEND_SMS")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val phone = params["phone_number"] as? String ?: return ToolResult(name, false, error = "phone_number required")
        val message = params["message"] as? String ?: return ToolResult(name, false, error = "message required")
        try {
            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            return ToolResult(name, true, summary = "SMS sent to $phone")
        } catch (e: Exception) {
            return ToolResult(name, false, error = e.message)
        }
    }
}

class ReadSmsTool(private val context: Context) : BaseTool(
    name = "read_sms",
    description = "Read recent SMS messages",
    parametersSchema = """{"type":"object","properties":{"limit":{"type":"integer","default":10}}}""",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.READ_SMS")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Int) ?: 10
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date", "read"),
            null, null, "date DESC"
        ) ?: return ToolResult(name, false, error = "No SMS access")
        
        val messages = mutableListOf<Map<String, Any>>()
        while (cursor.moveToNext() && messages.size < limit) {
            messages.add(mapOf(
                "id" to cursor.getLong(0),
                "from" to cursor.getString(1),
                "body" to cursor.getString(2),
                "date" to cursor.getLong(3),
                "read" to cursor.getInt(4) == 1
            ))
        }
        cursor.close()
        return ToolResult(name, true, data = mapOf("messages" to messages))
    }
}

class MakeCallTool(private val context: Context) : BaseTool(
    name = "make_call",
    description = "Initiate a phone call",
    parametersSchema = """{"type":"object","properties":{"phone_number":{"type":"string"}},"required":["phone_number"]}""",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.CALL_PHONE")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val phone = params["phone_number"] as? String ?: return ToolResult(name, false, error = "phone_number required")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult(name, true, summary = "Calling $phone")
    }
}

class SetAlarmTool(private val context: Context) : BaseTool(
    name = "set_alarm",
    description = "Create an alarm",
    parametersSchema = """{"type":"object","properties":{"time":{"type":"string","format":"HH:mm"},"label":{"type":"string"},"repeat":{"type":"string","enum":["none","daily","weekdays","weekends"]}}}""",
    requiresConfirmation = false
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val timeStr = params["time"] as? String ?: return ToolResult(name, false, error = "time required (HH:mm)")
        val parts = timeStr.split(":")
        if (parts.size != 2) return ToolResult(name, false, error = "Invalid time format")
        val hour = parts[0].toIntOrNull() ?: return ToolResult(name, false, error = "Invalid hour")
        val minute = parts[1].toIntOrNull() ?: return ToolResult(name, false, error = "Invalid minute")
        
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            params["label"]?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it as String) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolResult(name, true, summary = "Alarm set for ${String.format("%02d:%02d", hour, minute)}")
    }
}

class PlayMediaTool(private val context: Context) : BaseTool(
    name = "play_media",
    description = "Play media on YouTube/YouTube Music",
    parametersSchema = """{"type":"object","properties":{"query":{"type":"string"},"source":{"type":"string","enum":["youtube","youtube_music"]}}}""",
    requiresConfirmation = false
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String ?: return ToolResult(name, false, error = "query required")
        val source = (params["source"] as? String) ?: "youtube"
        val packageName = when (source) {
            "youtube_music" -> "com.google.android.apps.youtube.music"
            else -> "com.google.android.youtube"
        }
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return ToolResult(name, false, error = "$source not installed")
        intent.putExtra("query", query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult(name, true, summary = "Playing '$query' on $source")
    }
}

class ToggleWifiTool(private val context: Context) : BaseTool(
    name = "toggle_wifi",
    description = "Toggle WiFi on/off",
    parametersSchema = """{"type":"object","properties":{"enabled":{"type":"boolean"}}}""",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.CHANGE_WIFI_STATE", "android.permission.ACCESS_WIFI_STATE")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val enabled = params["enabled"] as? Boolean ?: return ToolResult(name, false, error = "enabled required")
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiManager.isWifiEnabled = enabled
        return ToolResult(name, true, summary = "WiFi ${if (enabled) "enabled" else "disabled"}")
    }
}

class ToggleBluetoothTool(private val context: Context) : BaseTool(
    name = "toggle_bluetooth",
    description = "Toggle Bluetooth on/off",
    parametersSchema = """{"type":"object","properties":{"enabled":{"type":"boolean"}}}""",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_ADMIN")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val enabled = params["enabled"] as? Boolean ?: return ToolResult(name, false, error = "enabled required")
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return ToolResult(name, false, error = "No Bluetooth adapter")
        if (enabled) adapter.enable() else adapter.disable()
        return ToolResult(name, true, summary = "Bluetooth ${if (enabled) "enabled" else "disabled"}")
    }
}

class SetRingerModeTool(private val context: Context) : BaseTool(
    name = "set_ringer_mode",
    description = "Set ringer mode (silent, vibrate, normal)",
    parametersSchema = """{"type":"object","properties":{"mode":{"type":"string","enum":["silent","vibrate","normal"]}}}""",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.ACCESS_NOTIFICATION_POLICY")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val modeStr = params["mode"] as? String ?: return ToolResult(name, false, error = "mode required")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val mode = when (modeStr) {
            "silent" -> android.media.AudioManager.RINGER_MODE_SILENT
            "vibrate" -> android.media.AudioManager.RINGER_MODE_VIBRATE
            else -> android.media.AudioManager.RINGER_MODE_NORMAL
        }
        audioManager.ringerMode = mode
        return ToolResult(name, true, summary = "Ringer set to $modeStr")
    }
}

class WebSearchTool(private val context: Context) : BaseTool(
    name = "web_search",
    description = "Search Google and return results",
    parametersSchema = """{"type":"object","properties":{"query":{"type":"string"},"num_results":{"type":"integer","default":5}}}""",
    requiresConfirmation = false
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String ?: return ToolResult(name, false, error = "query required")
        val numResults = (params["num_results"] as? Int) ?: 5
        
        // Scrape Google (MVP - replace with Custom Search API for production)
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://www.google.com/search?q=${Uri.encode(query)}&num=$numResults"
                val doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .get()
                
                val results = mutableListOf<Map<String, String>>()
                doc.select("div.g").forEach { result ->
                    val title = result.selectFirst("h3")?.text() ?: ""
                    val snippet = result.selectFirst("div.VwiC3b")?.text() 
                        ?: result.selectFirst("div.s")?.text() 
                        ?: ""
                    val link = result.selectFirst("a")?.attr("href") ?: ""
                    if (title.isNotBlank()) {
                        results.add(mapOf("title" to title, "snippet" to snippet, "url" to link))
                    }
                }
                ToolResult(name, true, data = mapOf("results" to results))
            } catch (e: Exception) {
                ToolResult(name, false, error = "Search failed: ${e.message}")
            }
        }
    }
}

class SummarizeUrlTool(private val context: Context) : BaseTool(
    name = "summarize_url",
    description = "Fetch and summarize a web page",
    parametersSchema = """{"type":"object","properties":{"url":{"type":"string"},"max_length":{"type":"integer","default":200}}}""",
    requiresConfirmation = false
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        val url = params["url"] as? String ?: return ToolResult(name, false, error = "url required")
        val maxLength = (params["max_length"] as? Int) ?: 200
        
        return withContext(Dispatchers.IO) {
            try {
                val doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .timeout(10000)
                    .get()
                
                // Extract main content
                val text = doc.body()?.text() ?: doc.text()
                val summary = if (text.length > maxLength) text.substring(0, maxLength) + "..." else text
                
                ToolResult(name, true, data = mapOf("summary" to summary, "title" to doc.title()), summary = summary)
            } catch (e: Exception) {
                ToolResult(name, false, error = "Failed to fetch: ${e.message}")
            }
        }
    }
}

class GetLocationTool(private val context: Context) : BaseTool(
    name = "get_location",
    description = "Get current device location",
    parametersSchema = "{}",
    requiresConfirmation = true,
    requiredPermissions = listOf("android.permission.ACCESS_FINE_LOCATION")
) {
    override suspend fun doExecute(params: Map<String, Any>): ToolResult {
        // In production, use FusedLocationProviderClient with coroutines
        // This is a placeholder
        return ToolResult(name, false, error = "Location tool not fully implemented in prototype")
    }
}

// ===== TOOL REGISTRY =====

class ToolRegistry(private val context: Context) {
    private val tools = mutableMapOf<String, Tool>()
    
    init {
        register(OpenAppTool(context))
        register(ListAppsTool(context))
        register(SendSmsTool(context))
        register(ReadSmsTool(context))
        register(MakeCallTool(context))
        register(SetAlarmTool(context))
        register(PlayMediaTool(context))
        register(ToggleWifiTool(context))
        register(ToggleBluetoothTool(context))
        register(SetRingerModeTool(context))
        register(WebSearchTool(context))
        register(SummarizeUrlTool(context))
        register(GetLocationTool(context))
    }
    
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    
    fun get(name: String): Tool? = tools[name]
    
    fun getAll(): List<Tool> = tools.values.toList()
    
    fun getSchemaJson(): String {
        val schemas = tools.values.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parametersSchema,
                "requiresConfirmation" to tool.requiresConfirmation,
                "requiredPermissions" to tool.requiredPermissions
            )
        }
        return kotlinx.serialization.encodeToString(schemas)
    }
    
    suspend fun execute(call: ToolCall): ToolResult {
        val tool = get(call.tool) ?: return ToolResult(call.tool, false, error = "Unknown tool: ${call.tool}")
        
        // Check permissions
        val missingPerms = tool.requiredPermissions.filter { perm ->
            context.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            return ToolResult(call.tool, false, error = "Missing permissions: ${missingPerms.joinToString()}")
        }
        
        return tool.execute(call.params)
    }
}