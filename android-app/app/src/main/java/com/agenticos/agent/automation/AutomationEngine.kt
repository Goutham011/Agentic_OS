package com.agenticos.agent.automation

import android.app.AlarmManager
import android.app.PendingIntent
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
import com.agenticos.agent.models.TriggerType
import com.agenticos.agent.tools.ToolRegistry
import com.agenticos.agent.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    
    // Receivers
    private var bootReceiver: BroadcastReceiver? = null
    private var callReceiver: BroadcastReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var connectivityReceiver: BroadcastReceiver? = null
    
    fun start() {
        Log.d(TAG, "Starting Automation Engine")
        
        // Register broadcast receivers for instant triggers
        registerReceivers()
        
        // Schedule periodic work for time/location/battery triggers
        schedulePeriodicWork()
        
        // Start trigger processor
        processTriggers()
    }
    
    fun stop() {
        scope.cancel()
        unregisterReceivers()
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_AUTOMATION_CHECK)
        Log.d(TAG, "Automation Engine stopped")
    }
    
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
            addAction("android.intent.action.PHONE_STATE") // Call state
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
        }
        
        val receiver = BroadcastReceiver { ctx, intent ->
            val action = intent.action
            when (action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    schedulePeriodicWork()
                }
                "android.intent.action.PHONE_STATE" -> {
                    val state = intent.getStringExtra("state")
                    if (state == "RINGING") {
                        val number = intent.getStringExtra("incoming_number") ?: ""
                        triggerChannel.trySend(AutomationTrigger(TriggerType.MISSED_CALL, mapOf("number" to number, "state" to "ringing")))
                    } else if (state == "IDLE") {
                        // Call ended - could be missed
                        triggerChannel.trySend(AutomationTrigger(TriggerType.MISSED_CALL, mapOf("state" to "ended")))
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> {
                    // Could trigger automations
                }
                "android.net.conn.CONNECTIVITY_CHANGE" -> {
                    // WiFi state change
                    val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    val connected = wifiManager.isWifiEnabled
                    val ssid = wifiManager.connectionInfo.ssid.replace("\"", "")
                    triggerChannel.trySend(AutomationTrigger(
                        if (connected) TriggerType.WIFI_CONNECTED else TriggerType.WIFI_DISCONNECTED,
                        mapOf("ssid" to ssid)
                    ))
                }
            }
        }
        
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        bootReceiver = receiver
    }
    
    private fun unregisterReceivers() {
        bootReceiver?.let { context.unregisterReceiver(it) }
        callReceiver?.let { context.unregisterReceiver(it) }
        screenReceiver?.let { context.unregisterReceiver(it) }
        connectivityReceiver?.let { context.unregisterReceiver(it) }
    }
    
    private fun schedulePeriodicWork() {
        val workManager = WorkManager.getInstance(context)
        
        // Periodic automation check (time, battery, location)
        val automationWork = PeriodicWorkRequest.Builder(AutomationCheckWorker::class.java, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
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
            // Get enabled rules matching trigger type
            val rules = database.automationRuleDao().getEnabledRules().first()
                .filter { it.trigger.type == trigger.type }
            
            for (rule in rules) {
                if (matchesTrigger(rule, trigger)) {
                    executeRule(rule, trigger)
                }
            }
        }
    }
    
    private fun matchesTrigger(rule: AutomationRule, trigger: AutomationTrigger): Boolean {
        val t = rule.trigger
        return when (trigger.type) {
            TriggerType.MISSED_CALL -> true // Simple: any missed call
            TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED -> {
                t.wifiSsid?.isBlank() == false && t.wifiSsid == trigger.data["ssid"]
            }
            TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT -> {
                // Geofencing handled by separate worker
                false
            }
            TriggerType.BATTERY_LOW -> {
                val threshold = t.batteryThreshold ?: 20
                val level = trigger.data["battery_level"]?.toString()?.toIntOrNull() ?: 100
                level <= threshold
            }
            TriggerType.NOTIFICATION -> {
                t.notificationPackage?.isBlank() == false &&
                t.notificationPackage == trigger.data["package"] &&
                (t.notificationTextContains?.isBlank() == true || trigger.data["text"]?.contains(t.notificationTextContains!!) == true)
            }
            else -> false
        }
    }
    
    private fun executeRule(rule: AutomationRule, trigger: AutomationTrigger) {
        scope.launch {
            for (action in rule.actions) {
                try {
                    val params = kotlinx.serialization.decodeFromString<Map<String, Any>>(action.paramsJson)
                    // Inject trigger data
                    val mergedParams = params + trigger.data
                    
                    val call = com.agenticos.agent.models.ToolCall(
                        tool = action.tool,
                        params = mergedParams,
                        speak = null // Automations don't speak
                    )
                    
                    val result = toolRegistry.execute(call)
                    
                    if (!result.success) {
                        Log.w(TAG, "Automation action failed: ${action.tool} - ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing automation action: ${action.tool}", e)
                }
            }
        }
    }
    
    // Public API for manual trigger
    fun triggerManually(type: TriggerType, data: Map<String, Any>) {
        triggerChannel.trySend(AutomationTrigger(type, data))
    }
}

// ===== WORKERS =====

class AutomationCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        try {
            val engine = AutomationEngine(applicationContext)
            engine.start()
            
            // Check time-based triggers
            checkTimeTriggers(engine)
            
            // Check battery
            checkBatteryTrigger(engine)
            
            engine.stop()
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }
    
    private suspend fun checkTimeTriggers(engine: AutomationEngine) {
        val rules = AgenticDatabase.getInstance(applicationContext).automationRuleDao()
            .getEnabledRules().first()
            .filter { it.trigger.type == TriggerType.TIME }
        
        val now = java.util.Calendar.getInstance()
        val currentCron = "${now.get(java.util.Calendar.MINUTE)} ${now.get(java.util.Calendar.HOUR_OF_DAY)} * * *"
        
        for (rule in rules) {
            if (cronMatches(rule.trigger.cronExpression ?: "", currentCron)) {
                engine.triggerManually(TriggerType.TIME, mapOf("cron" to currentCron))
            }
        }
    }
    
    private suspend fun checkBatteryTrigger(engine: AutomationEngine) {
        val intent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", -1) ?: -1
        if (level >= 0 && scale > 0) {
            val percent = level * 100 / scale
            engine.triggerManually(TriggerType.BATTERY_LOW, mapOf("battery_level" to percent))
        }
    }
    
    private fun cronMatches(cronExpr: String, current: String): Boolean {
        // Simplified cron matching - in production use a proper cron library
        val parts = cronExpr.split(" ")
        val currentParts = current.split(" ")
        if (parts.size != 5 || currentParts.size != 5) return false
        
        // Check minute and hour (ignore day/month for now)
        return (parts[0] == "*" || parts[0] == currentParts[0]) &&
               (parts[1] == "*" || parts[1] == currentParts[1])
    }
}

// ===== DATA CLASSES =====

data class AutomationTrigger(
    val type: TriggerType,
    val data: Map<String, Any>
)

// ===== TIME TRIGGER WORKER (for exact alarms) =====

class TimeTriggerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong("rule_id", -1)
        if (ruleId != -1) {
            val engine = AutomationEngine(applicationContext)
            engine.triggerManually(TriggerType.TIME, mapOf("rule_id" to ruleId))
        }
        return Result.success()
    }
    
    companion object {
        fun scheduleExact(context: Context, rule: AutomationRule, triggerAtMillis: Long) {
            val workManager = WorkManager.getInstance(context)
            val work = androidx.work.OneTimeWorkRequest.Builder(TimeTriggerWorker::class.java)
                .setInputData(androidx.work.Data.Builder().putLong("rule_id", rule.id).build())
                .setInitialDelay(triggerAtMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addTag("time_trigger_${rule.id}")
                .build()
            workManager.enqueueUniqueWork("time_trigger_${rule.id}", androidx.work.ExistingWorkPolicy.REPLACE, work)
        }
    }
}