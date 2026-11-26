package com.agenticos.agent.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agenticos.agent.data.AgenticDatabase
import com.agenticos.agent.models.AutomationRule
import com.agenticos.agent.models.ActionSpec
import com.agenticos.agent.models.ToolCall
import com.agenticos.agent.models.ToolResult
import com.agenticos.agent.models.TriggerType
import com.agenticos.agent.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.TimeUnit

class AutomationEngine(private val context: Context) {
    companion object {
        private const val TAG = "AutomationEngine"
        const val WORK_AUTOMATION_CHECK = "automation_check_work"
        const val WORK_LOCATION_CHECK = "location_check_work"
        const val WORK_NOTIFICATION_CHECK = "notification_check_work"
        const val CHECK_INTERVAL_MINUTES = 15
    }
    
    private val toolRegistry = ToolRegistry(context)
    private val database = AgenticDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val triggerChannel = Channel<AutomationTrigger>(capacity = 50)
    private val json = Json { ignoreUnknownKeys = true }
    
    private var bootReceiver: BroadcastReceiver? = null
    
    fun start() {
        Log.d(TAG, "Starting Automation Engine")
        registerReceivers()
        schedulePeriodicWork()
        processTriggers()
    }
    
    fun stop() {
        scope.cancel()
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_AUTOMATION_CHECK)
        Log.d(TAG, "Automation Engine stopped")
    }
    
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
            addAction("android.intent.action.PHONE_STATE")
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
        }
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        schedulePeriodicWork()
                    }
                    "android.intent.action.PHONE_STATE" -> {
                        val state = intent.getStringExtra("state")
                        if (state == "RINGING") {
                            val number = intent.getStringExtra("incoming_number") ?: ""
                            triggerChannel.trySend(AutomationTrigger("MISSED_CALL", mapOf("number" to number, "state" to "ringing")))
                        } else if (state == "IDLE") {
                            triggerChannel.trySend(AutomationTrigger("MISSED_CALL", mapOf("state" to "ended")))
                        }
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> {
                        // Screen state changes
                    }
                    "android.net.conn.CONNECTIVITY_CHANGE" -> {
                        val wifiManager = ctx?.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                        val connected = wifiManager?.isWifiEnabled ?: false
                        val ssid = wifiManager?.connectionInfo?.ssid?.replace("\"", "") ?: ""
                        triggerChannel.trySend(AutomationTrigger(
                            if (connected) "WIFI_CONNECTED" else "WIFI_DISCONNECTED",
                            mapOf("ssid" to ssid)
                        ))
                    }
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATED_API_USAGE")
            context.registerReceiver(receiver, filter)
        }
        bootReceiver = receiver
    }
    
    private fun unregisterReceivers() {
        bootReceiver?.let { context.unregisterReceiver(it) }
    }
    
    private fun schedulePeriodicWork() {
        val workManager = WorkManager.getInstance(context)
        
        val automationWork = PeriodicWorkRequest.Builder(AutomationCheckWorker::class.java, CHECK_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(WORK_AUTOMATION_CHECK)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_AUTOMATION_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            automationWork
        )
    }
    
    private fun processTriggers() {
        scope.launch {
            for (trigger in triggerChannel) {
                evaluateTriggers(trigger)
            }
        }
    }
    
    private fun evaluateTriggers(trigger: AutomationTrigger) {
        scope.launch {
            val rules = database.automationRuleDao().getEnabledRules().first()
                .filter { 
                    when (trigger.type) {
                        "MISSED_CALL" -> it.triggerType == TriggerType.MISSED_CALL.name
                        "WIFI_CONNECTED", "WIFI_DISCONNECTED" -> it.triggerType == TriggerType.WIFI_CONNECTED.name
                        "BATTERY_LOW" -> it.triggerType == TriggerType.BATTERY_LOW.name
                        "NOTIFICATION" -> it.triggerType == TriggerType.NOTIFICATION.name
                        else -> false
                    }
                }
            
            for (rule in rules) {
                if (matchesTrigger(rule, trigger)) {
                    executeRule(rule, trigger)
                }
            }
        }
    }
    
    private fun matchesTrigger(rule: AutomationRule, trigger: AutomationTrigger): Boolean {
        val triggerInfo = try {
            json.decodeFromString<Map<String, Any>>(rule.triggerData)
        } catch (e: Exception) {
            emptyMap()
        }
        
        return when (trigger.type) {
            "MISSED_CALL" -> true
            "WIFI_CONNECTED", "WIFI_DISCONNECTED" -> {
                val expectedSsid = triggerInfo["wifiSsid"]?.toString()
                expectedSsid?.isBlank() != true && expectedSsid == trigger.data["ssid"]
            }
            "BATTERY_LOW" -> {
                val threshold = triggerInfo["batteryThreshold"]?.toString()?.toIntOrNull() ?: 20
                val level = trigger.data["battery_level"]?.toString()?.toIntOrNull() ?: 100
                level <= threshold
            }
            "NOTIFICATION" -> {
                val expectedPackage = triggerInfo["notificationPackage"]?.toString()
                val containsText = triggerInfo["notificationTextContains"]?.toString()
                expectedPackage?.isBlank() != true &&
                expectedPackage == trigger.data["package"] &&
                (containsText.isNullOrBlank() || trigger.data["text"]?.contains(containsText) == true)
            }
            else -> false
        }
    }
    
    private fun executeRule(rule: AutomationRule, trigger: AutomationTrigger) {
        scope.launch {
            try {
                val actions = json.decodeFromString(ListSerializer(ActionSpec.serializer()), rule.actionsJson)
                
                for (action in actions) {
                    try {
                        val result = toolRegistry.executeWithAction(action, trigger)
                        
                        if (!result.success) {
                            Log.w(TAG, "Automation action failed: ${action.tool} - ${result.error}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing automation action: ${action.tool}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing automation rule", e)
            }
        }
    }
    
    /**
     * Check and trigger TIME-based automations manually (called by AutomationCheckWorker)
     */
    suspend fun checkTimeTriggers() {
        val rules = database.automationRuleDao().getEnabledRules().first()
            .filter { it.triggerType == TriggerType.TIME.name }
        
        val currentTime = java.time.LocalDateTime.now()
        
        for (rule in rules) {
            val cronExpr = rule.cronExpression
            if (cronExpr != null) {
                // Check if current time matches cron expression
                if (cronExpr.cronMatches(currentTime)) {
                    Log.d(TAG, "Cron matched for TIME rule: ${rule.name}")
                    val trigger = AutomationTrigger("TIME", mapOf("cron" to cronExpr))
                    executeRule(rule, trigger)
                }
            }
        }
    }
    
    fun triggerManually(type: String, data: Map<String, String>) {
        triggerChannel.trySend(AutomationTrigger(type, data))
    }
}

data class AutomationTrigger(
    val type: String,
    val data: Map<String, String>
)