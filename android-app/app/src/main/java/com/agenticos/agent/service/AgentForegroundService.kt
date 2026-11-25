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
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.agenticos.agent.MainActivity
import com.agenticos.agent.accessibility.NotificationListenerServiceImpl
import com.agenticos.agent.accessibility.ScreenReaderAccessibilityService
import com.agenticos.agent.automation.AutomationEngine
import com.agenticos.agent.data.AgenticDatabase
import com.agenticos.agent.llm.LLMManager
import com.agenticos.agent.llm.RuleBasedLLMManager
import com.agenticos.agent.models.ConversationMemory
import com.agenticos.agent.models.LLMResponse
import com.agenticos.agent.models.ToolCall
import com.agenticos.agent.tools.ToolRegistry
import com.agenticos.agent.tools.ToolResult
import com.agenticos.agent.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

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
        const val BROADCAST_TOOL_RESULT = "com.agenticos.agent.TOOL_RESULT"
        const val BROADCAST_STATUS = "com.agenticos.agent.STATUS"
        const val EXTRA_SPEAK_TEXT = "speak_text"
        const val EXTRA_TOOL_CALL = "tool_call"
        const val EXTRA_TOOL_RESULT = "tool_result"
        const val EXTRA_STATUS = "status"
    }
    
    // Dependencies
    private lateinit var llmManager: LLMManager
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var voiceManager: VoiceManager
    private lateinit var automationEngine: AutomationEngine
    private lateinit var database: AgenticDatabase
    
    // State
    private var isListening = false
    private var hotwordEnabled = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val commandChannel = Channel<String>(capacity = 10)
    private val screenContentReceiver: BroadcastReceiver? = null
    private val notificationReceiver: BroadcastReceiver? = null
    
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
        
        // Register broadcast receivers
        registerReceivers()
        
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
        startForeground(NOTIFICATION_ID, notification, Service.FOREGROUND_SERVICE_TYPE_MANIFEST)
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agentic OS Assistant",
                NotificationManager.IMPORTANCE_LOW
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
            .setContentText(if (isListening) "Listening..." else "Ready")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
    
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ScreenReaderAccessibilityService.ACTION_SCREEN_CHANGED)
            addAction(NotificationListenerServiceImpl.ACTION_NOTIFICATION_POSTED)
            addAction(NotificationListenerServiceImpl.ACTION_NOTIFICATION_REMOVED)
        }
        // Register in onStartCommand to avoid leaks
    }
    
    private fun startAgentLoop() {
        scope.launch {
            while (!scope.coroutineContext[Job]?.isCancelled == true) {
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
            
            val context = mapOf(
                "screen_content" to screenContext,
                "active_notifications" to notifications,
                "current_time" to java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                "battery_level" to getBatteryLevel()
            )
            
            // LLM inference
            val llmResponse = llmManager.inferWithContext(userText, context)
            
            // Handle response
            if (llmResponse.tool != null) {
                // Execute tool
                val toolCall = ToolCall(
                    tool = llmResponse.tool!!,
                    params = llmResponse.params ?: emptyMap(),
                    speak = llmResponse.speak
                )
                
                broadcastToolCall(toolCall)
                
                val result = toolRegistry.execute(toolCall)
                
                broadcastToolResult(result)
                
                // Speak result
                val speakText = llmResponse.speak ?: result.summary ?: result.error ?: "Done"
                voiceManager.speak(speakText)
                
                // Save to memory
                saveConversation(userText, speakText, toolCall)
            } else {
                // Direct response
                voiceManager.speak(llmResponse.speak ?: "I'm not sure how to help with that.")
                saveConversation(userText, llmResponse.speak ?: "", null)
            }
            
            broadcastStatus("ready")
        }
    }
    
    private fun getScreenContext(): String {
        val content = ScreenReaderAccessibilityService.getLatestContent()
        return content?.fullText?.take(2000) ?: "No screen content available"
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
    
    private fun saveConversation(userText: String, agentResponse: String, toolCall: ToolCall?) {
        scope.launch(Dispatchers.IO) {
            val memory = ConversationMemory(
                userText = userText,
                agentResponse = agentResponse,
                toolCallsJson = toolCall?.let { ToolCall.toJson(it) } ?: "[]"
            )
            database.conversationMemoryDao().insert(memory)
        }
    }
    
    // ===== PUBLIC API =====
    
    fun startListening() {
        if (isListening) return
        isListening = true
        updateNotification()
        broadcastStatus("listening")
        
        scope.launch {
            val result = voiceManager.listenForCommand()
            if (result.startsWith("ERROR:")) {
                voiceManager.speak("Sorry, I couldn't hear that. Try again.")
            } else {
                commandChannel.trySend(result)
            }
            isListening = false
            updateNotification()
        }
    }
    
    fun stopListening() {
        isListening = false
        voiceManager.stopSpeaking()
        updateNotification()
        broadcastStatus("ready")
    }
    
    fun toggleHotword() {
        hotwordEnabled = !hotwordEnabled
        if (hotwordEnabled) {
            // Start Porcupine wake word detection
            voiceManager.startHotwordDetection { keywordIndex ->
                commandChannel.trySend("HOTWORD_DETECTED")
            }
            broadcastStatus("hotword_enabled")
        } else {
            broadcastStatus("hotword_disabled")
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
        val intent = Intent(BROADCAST_TOOL_RESULT).putExtra(EXTRA_TOOL_RESULT, kotlinx.serialization.encodeToString(result))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        scope.cancel()
        voiceManager.shutdown()
        automationEngine.stop()
        unregisterReceivers()
        super.onDestroy()
        Log.d(TAG, "AgentForegroundService destroyed")
    }
    
    private fun unregisterReceivers() {
        try {
            unregisterReceiver(screenContentReceiver!!)
            unregisterReceiver(notificationReceiver!!)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}