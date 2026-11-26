package com.agenticos.agent.automation

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agenticos.agent.data.AgenticDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

class AutomationCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "AutomationCheckWorker"
    }
    
    private val database = AgenticDatabase.getInstance(applicationContext)
    
    override suspend fun doWork(): Result {
        return try {
            val rules = database.automationRuleDao().getEnabledRules().first()
            Log.d(TAG, "Checking ${rules.size} automation rules")
            
            // Check TIME-based automations with cron expressions
            val currentTime = LocalDateTime.now()
            
            for (rule in rules) {
                when (rule.triggerType) {
                    "TIME" -> {
                        val cronExpr = rule.cronExpression
                        if (cronExpr != null) {
                            // Check if current time matches cron expression using CronParser
                            if (checkCronMatch(cronExpr, currentTime)) {
                                Log.d(TAG, "Cron matched for rule: ${rule.name}")
                                // Trigger the TIME automation
                                triggerTimeRule(rule)
                            }
                        }
                    }
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking automation rules", e)
            Result.retry()
        }
    }
    
    /**
     * Check if a cron expression matches the given time
     */
    private fun checkCronMatch(cronExpr: String, time: LocalDateTime): Boolean {
        val parts = cronExpr.trim().split("\\s+".toRegex())
        if (parts.size != 5) {
            Log.w(TAG, "Invalid cron expression: $cronExpr")
            return false
        }
        
        val current = getCurrentTimeParts(time)
        
        return matchesField(parts[0], current.minute) &&  // minute
               matchesField(parts[1], current.hour) &&    // hour
               matchesField(parts[2], current.dayOfMonth) && // day of month
               matchesField(parts[3], current.monthValue) && // month
               matchesField(parts[4], current.dayOfWeekValue) // day of week
    }
    
    private data class CurrentTimeParts(
        val minute: Int,
        val hour: Int,
        val dayOfMonth: Int,
        val monthValue: Int,
        val dayOfWeekValue: Int
    )
    
    private fun getCurrentTimeParts(time: LocalDateTime): CurrentTimeParts {
        return CurrentTimeParts(
            minute = time.minute,
            hour = time.hour,
            dayOfMonth = time.dayOfMonth,
            monthValue = time.monthValue,
            // dayOfWeekValue: Java LocalDateTime uses 1=Monday, 7=Sunday
            // Standard cron day-of-week uses 0-7 where both 0 and 7 are Sunday
            // For simplicity, we map: 1=Mon..7=Sun to 1=Mon..7=Sun (7=Sun in our mapping)
            dayOfWeekValue = time.dayOfWeek.value // 1=Monday, 7=Sunday
        )
    }
    
    private fun matchesField(field: String, value: Int): Boolean {
        // Wildcard matches any value
        if (field == "*") return true
        
        // Handle ranges (e.g., "1-5", "9-17")
        if (field.contains("-")) {
            val range = field.split("-")
            if (range.size == 2) {
                val start = range[0].toIntOrNull() ?: return false
                val end = range[1].toIntOrNull() ?: return false
                return value in start..end
            }
            return false
        }
        
        // Handle comma-separated lists (e.g., "1,3,5")
        if (field.contains(",")) {
            return field.split(",")
                .mapNotNull { it.toIntOrNull() }
                .contains(value)
        }
        
        // Handle step values (e.g., "*/5", "0/10")
        if (field.contains("/")) {
            val parts = field.split("/")
            if (parts.size == 2) {
                val step = parts[1].toIntOrNull() ?: return false
                if (step <= 0) return false
                
                val base = parts[0].toIntOrNull()
                // For wildcard base (e.g., "*/5"), base is null
                if (base == null) {
                    // "*/5" means every 5 units starting from 0
                    return value % step == 0
                }
                // "0/10" means every 10 starting from 0
                return value >= base && (value - base) % step == 0
            }
            return false
        }
        
        // Simple integer value
        val fieldValue = field.toIntOrNull() ?: return false
        return fieldValue == value
    }
    
    private suspend fun triggerTimeRule(rule: com.agenticos.agent.models.AutomationRule) {
        // Create a TIME trigger with current time data
        val trigger = AutomationTrigger(
            type = "TIME",
            data = mapOf<String, String>("cron" to (rule.cronExpression ?: ""))
        )
        
        // Execute the rule directly
        try {
            val actions = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(
                        com.agenticos.agent.models.ActionSpec.serializer()
                    ),
                    rule.actionsJson
                )
            
            val toolRegistry = com.agenticos.agent.tools.ToolRegistry(applicationContext)
            
            for (action in actions) {
                try {
                    val result = toolRegistry.executeWithAction(action, trigger)
                    if (!result.success) {
                        Log.w(TAG, "Automation action failed for rule '${rule.name}': ${action.tool} - ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing automation action for rule '${rule.name}': ${action.tool}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing automation rule '${rule.name}'", e)
        }
    }
}