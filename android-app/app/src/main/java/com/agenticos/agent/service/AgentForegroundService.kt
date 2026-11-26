package com.agenticos.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.agenticos.agent.MainActivity
import com.agenticos.agent.models.ToolCall
import com.agenticos.agent.models.ToolResult
import com.agenticos.agent.data.AgenticDatabase
import com.agenticos.agent.utils.JsonProvider
import com.agenticos.agent.tools.ToolRegistry
import com.agenticos.agent.llm.LLMManager
import com.agenticos.agent.llm.RuleBasedLLMManager
import com.agenticos.agent.voice.VoiceManager
import com.agenticos.agent.automation.AutomationEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AgentForegroundService : Service() {
    companion object {
        private const val TAG = "AgentForegroundService"
        const val CHANNEL_ID = "agentic_os_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_LISTENING = "com.agenticos.agent.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.agenticos.agent.STOP_LISTENING"
        const val ACTION_TOGGLE_HOTWORD = "com.agenticos.agent.TOGGLE_HOTWORD"
        
        // Broadcast actions
        const val BROADCAST_AGENT_RESPONSE = "com.agenticos.agent.AGENT_RESPONSE"
        const val BROADCAST_TOOL_CALL = "com.agenticos.agent.TOOL_CALL"
        const val BROADCAST_TOOL_RESULT = "com.agenticos.agent.TOOL_RESULT"
        const val BROADCAST_STATUS = "com.agenticos.agent.STATUS"
        const val EXTRA_SPEAK_TEXT = "speak_text"
        const val EXTRA_TOOL_CALL = "tool_call"
        const val EXTRA_TOOL_RESULT = "tool_result"
        const val EXTRA_STATUS = "status"
        
        // Foreground service type flags for API 29+
        @Suppress("DEPRECATION")
        private const val FOREGROUND_SERVICE_TYPE_AGENT = 0x00000020
        
        @Suppress("DEPRECATION")
        private const val FOREGROUND_SERVICE_TYPE_MICROPHONE = 0x00000010
        @Suppress("DEPRECATION")
        private const val FOREGROUND_SERVICE_TYPE_LOCATION = 0x00000008
        @Suppress("DEPRECATION")
        private const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 0x00000002
    }
    
    // Dependencies
    private lateinit var llmManager: LLMManager
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var voiceManager: VoiceManager
    private lateinit var automationEngine: AutomationEngine
    private lateinit var database: AgenticDatabase
    
    // State - using AtomicBoolean for thread safety across coroutines
    private val isListening = AtomicBoolean(false)
    private val hotwordEnabled = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val commandChannel = kotlinx.coroutines.channels.Channel<String>(capacity = 10)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AgentForegroundService created")
        
        // Initialize dependencies
        llmManager = RuleBasedLLMManager() // Replace with MediaPipeLLMManager for production
        toolRegistry = ToolRegistry(this)
        voiceManager = VoiceManager(this)
        automationEngine = AutomationEngine(this)
        database = AgenticDatabase.getInstance(this)
        
        // Create notification channel
        createNotificationChannel()
        
        // Start agent loop
        startAgentLoop()
        
        // Start automation engine
        automationEngine.start()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_TOGGLE_HOTWORD -> toggleHotword()
        }
        
        // Start foreground with notification
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 
                FOREGROUND_SERVICE_TYPE_MICROPHONE or
                FOREGROUND_SERVICE_TYPE_LOCATION or
                FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // FIX #2: Changed from IMPORTANCE_LOW to IMPORTANCE_DEFAULT for better reliability
            // on Android 12+ devices with Doze/OEM restrictions
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agentic OS Assistant",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Keeps the assistant running in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agentic OS")
            .setContentText(if (isListening.get()) "Listening..." else "Ready")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Changed from LOW to HIGH for better reliability
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Stop Listening",
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP_LISTENING),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            .build()
    }
    
    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startAgentLoop() {
        scope.launch {
            while (isActive) {
                try {
                    // Wait for command (voice or manual)
                    val command = commandChannel.receive()
                    
                    if (command.isNotBlank()) {
                        processCommand(command)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Agent loop error", e)
                    delay(1000)
                }
            }
        }
    }
    
    private fun processCommand(userText: String) {
        scope.launch {
            // Broadcast status: thinking
            broadcastStatus("thinking")
            
            // Get context
            val screenContext = getScreenContext()
            val notifications = getRecentNotifications()
            
            // FIX #17: Use Locale.US for consistent machine-readable time format
            val context = mapOf(
                "screen_content" to screenContext,
                "active_notifications" to notifications,
                "current_time" to SimpleDateFormat("HH:mm", Locale.US).format(Date()),
                "battery_level" to getBatteryLevel()
            )
            
            // LLM inference
            val llmResponse = llmManager.inferWithContext(userText, context)
            
            // Handle response
            if (llmResponse.tool != null) {
                // Execute tool
                val toolCall = ToolCall(
                    tool = llmResponse.tool!!,
                    params = llmResponse.params ?: JsonObject(mutableMapOf()),
                    speak = llmResponse.speak
                )
                
                broadcastToolCall(toolCall)
                
                // FIX #13: Use actor pattern to serialize database operations and avoid race conditions
                saveConversation(userText, toolCall.speak ?: "", toolCall)
                
                val result = toolRegistry.execute(toolCall)
                
                broadcastToolResult(result)
                
                // Speak result
                val speakText = llmResponse.speak ?: result.summary ?: result.error ?: "Done"
                
                // Queue speech to avoid concurrent TTS calls
                voiceManager.speak(speakText)
            } else {
                // Direct response
                val speakText = llmResponse.speak ?: "I'm not sure how to help with that."
                voiceManager.speak(speakText)
                saveConversation(userText, speakText, null)
            }
            
            broadcastStatus("ready")
        }
    }
    
    private fun getScreenContext(): String {
        return "Screen content unavailable in background"
    }
    
    private fun getRecentNotifications(): String {
        // In production, query from automation engine's notification cache
        return "No recent notifications"
    }
    
    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
    
    // FIX #13: Use channel actor to serialize database operations
    private val conversationChannel = Channel<ConversationEntry>(capacity = 5)
    
    init {
        scope.launch {
            for (entry in conversationChannel) {
                try {
                    val memory = com.agenticos.agent.models.ConversationMemory(
                        userText = entry.userText,
                        agentResponse = entry.agentResponse,
                        toolCallsJson = entry.toolCall?.let { ToolCall.toJson(it) } ?: "[]"
                    )
                    database.conversationMemoryDao().insert(memory)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving conversation", e)
                }
            }
        }
    }
    
    private data class ConversationEntry(
        val userText: String,
        val agentResponse: String,
        val toolCall: ToolCall?
    )
    
    private fun saveConversation(userText: String, agentResponse: String, toolCall: ToolCall?) {
        conversationChannel.trySend(ConversationEntry(userText, agentResponse, toolCall))
    }
    
    // ===== PUBLIC API =====
    
    fun startListening() {
        if (isListening.getAndSet(true)) return
        updateNotification()
        broadcastStatus("listening")
        
        scope.launch {
            val result = voiceManager.listenForCommand()
            if (result.startsWith("ERROR:")) {
                voiceManager.speak("Sorry, I couldn't hear that. Try again.")
            } else {
                commandChannel.trySend(result)
            }
            isListening.set(false)
            updateNotification()
        }
    }
    
    fun stopListening() {
        isListening.set(false)
        voiceManager.stopSpeaking()
        updateNotification()
        broadcastStatus("ready")
    }
    
    fun toggleHotword() {
        val enabled = hotwordEnabled.getAndSet(!hotwordEnabled.get())
        if (enabled) {
            // Stop hotword detection
            broadcastStatus("hotword_disabled")
        } else {
            // Start Porcupine wake word detection
            voiceManager.startHotwordDetection { keywordName: String ->
                scope.launch(Dispatchers.Main) {
                    commandChannel.trySend("HOTWORD_DETECTED")
                }
            }
            broadcastStatus("hotword_enabled")
        }
    }
    
    fun submitTextCommand(text: String) {
        commandChannel.trySend(text)
    }
    
    // ===== BROADCASTS =====
    
    private fun broadcastStatus(status: String) {
        val intent = Intent(BROADCAST_STATUS).putExtra(EXTRA_STATUS, status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastToolCall(call: ToolCall) {
        val intent = Intent(BROADCAST_TOOL_CALL).putExtra(EXTRA_TOOL_CALL, ToolCall.toJson(call))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastToolResult(result: ToolResult) {
        val json = JsonProvider.json.encodeToString(ToolResult.serializer(), result)
        val intent = Intent(BROADCAST_TOOL_RESULT).putExtra(EXTRA_TOOL_RESULT, json)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        scope.cancel()
        voiceManager.shutdown()
        automationEngine.stop()
        super.onDestroy()
        Log.d(TAG, "AgentForegroundService destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}