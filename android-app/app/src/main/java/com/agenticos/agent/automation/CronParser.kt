package com.agenticos.agent.automation

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Simple cron expression parser for 5-field cron expressions.
 * Supports: minute hour day-of-month month day-of-week
 * Wildcards (*), ranges (1-5), lists (1,3,5), steps (*'til 5 or 0/10)
 */
object CronParser {
    
    /**
     * Check if a cron expression matches the current time
     */
    fun cronMatches(cronExpr: String, checkTime: LocalDateTime = LocalDateTime.now()): Boolean {
        val parts = cronExpr.trim().split("\\s+".toRegex())
        if (parts.size != 5) {
            return false
        }
        
        val current = getCurrentTimeParts(checkTime)
        
        return matchesField(parts[0], current.minute) &&  // minute
               matchesField(parts[1], current.hour) &&    // hour
               matchesField(parts[2], current.dayOfMonth) && // day of month
               matchesField(parts[3], current.monthValue) && // month
               matchesField(parts[4], current.dayOfWeekValue) // day of week
    }
    
    /**
     * Parse a cron expression and return the next scheduled time
     */
    fun nextExecution(cronExpr: String, from: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        var candidate = from.plusMinutes(1)
        
        // Search up to 1 year forward
        val maxSearch = from.plusYears(1)
        
        while (candidate.isBefore(maxSearch)) {
            if (cronMatches(cronExpr, candidate)) {
                return candidate
            }
            candidate = candidate.plusMinutes(1)
        }
        
        return null
    }
    
    private fun getCurrentTimeParts(time: LocalDateTime): CurrentTimeParts {
        return CurrentTimeParts(
            minute = time.minute,
            hour = time.hour,
            dayOfMonth = time.dayOfMonth,
            monthValue = time.monthValue,
            // Convert: Java LocalDateTime uses 1=Mon..7=Sun, cron uses 0=Sun..6=Sat
            // We need to map: 1=Mon..7=Sun -> 1=Mon..6=Sat, 0=Sun
            dayOfWeekValue = time.dayOfWeek.value // 1=Monday, 7=Sunday - keep as-is
        )
    }
    
    private data class CurrentTimeParts(
        val minute: Int,
        val hour: Int,
        val dayOfMonth: Int,
        val monthValue: Int,
        val dayOfWeekValue: Int
    )
    
    private fun matchesField(field: String, value: Int): Boolean {
        // Wildcard matches any value
        if (field == "*") return true
        
        // Handle ranges (e.g., "1-5", "9-17")
        if (field.contains("-")) {
            val range = field.split("-")
            if (range.size == 2) {
                // Handle "0-1" range for day-of-week (covers Sunday-Monday in some cron variants)
                // But standard cron doesn't allow ranges like 6-1, so we skip this
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
}

/**
 * Extension function to check if a cron expression matches
 */
fun String.cronMatches(time: LocalDateTime = LocalDateTime.now()): Boolean {
    return CronParser.cronMatches(this, time)
}

/**
 * Convert timestamp in milliseconds to LocalDateTime
 */
fun Long.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(this),
        ZoneId.systemDefault()
    )
}