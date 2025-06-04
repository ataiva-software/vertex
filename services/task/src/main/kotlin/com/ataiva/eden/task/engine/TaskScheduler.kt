package com.ataiva.eden.task.engine

import com.ataiva.eden.database.repositories.Task
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Task scheduler for handling cron-based task scheduling
 */
class TaskScheduler {
    
    private val scheduledTasks = ConcurrentHashMap<String, ScheduledTask>()
    
    /**
     * Validate cron expression
     */
    fun validateCronExpression(cronExpression: String): CronValidationResult {
        return try {
            val parts = cronExpression.trim().split("\\s+".toRegex())
            
            if (parts.size != 5 && parts.size != 6) {
                return CronValidationResult(false, "Cron expression must have 5 or 6 parts")
            }
            
            // Basic validation for each part
            val (minute, hour, dayOfMonth, month, dayOfWeek) = parts
            
            if (!isValidCronField(minute, 0, 59)) {
                return CronValidationResult(false, "Invalid minute field: $minute")
            }
            
            if (!isValidCronField(hour, 0, 23)) {
                return CronValidationResult(false, "Invalid hour field: $hour")
            }
            
            if (!isValidCronField(dayOfMonth, 1, 31)) {
                return CronValidationResult(false, "Invalid day of month field: $dayOfMonth")
            }
            
            if (!isValidCronField(month, 1, 12)) {
                return CronValidationResult(false, "Invalid month field: $month")
            }
            
            if (!isValidCronField(dayOfWeek, 0, 7)) {
                return CronValidationResult(false, "Invalid day of week field: $dayOfWeek")
            }
            
            CronValidationResult(true, null)
            
        } catch (e: Exception) {
            CronValidationResult(false, "Invalid cron expression format: ${e.message}")
        }
    }
    
    /**
     * Schedule a task
     */
    fun scheduleTask(task: Task) {
        if (task.scheduleCron != null) {
            val scheduledTask = ScheduledTask(
                task = task,
                cronExpression = task.scheduleCron,
                lastRun = null,
                nextRun = calculateNextRun(task.scheduleCron)
            )
            scheduledTasks[task.id] = scheduledTask
        }
    }
    
    /**
     * Unschedule a task
     */
    fun unscheduleTask(taskId: String) {
        scheduledTasks.remove(taskId)
    }
    
    /**
     * Get tasks that need to run now
     */
    fun getTasksToRun(): List<Task> {
        val now = Clock.System.now()
        val tasksToRun = mutableListOf<Task>()
        
        scheduledTasks.values.forEach { scheduledTask ->
            if (scheduledTask.nextRun != null && scheduledTask.nextRun <= now) {
                tasksToRun.add(scheduledTask.task)
                
                // Update next run time
                val updatedTask = scheduledTask.copy(
                    lastRun = now,
                    nextRun = calculateNextRun(scheduledTask.cronExpression)
                )
                scheduledTasks[scheduledTask.task.id] = updatedTask
            }
        }
        
        return tasksToRun
    }
    
    /**
     * Get scheduled task info
     */
    fun getScheduledTaskInfo(taskId: String): ScheduledTask? {
        return scheduledTasks[taskId]
    }
    
    /**
     * Get all scheduled tasks
     */
    fun getAllScheduledTasks(): List<ScheduledTask> {
        return scheduledTasks.values.toList()
    }
    
    /**
     * Calculate next run time based on cron expression
     */
    private fun calculateNextRun(cronExpression: String): Instant? {
        // Simplified cron calculation - in production, use a proper cron library
        return try {
            val parts = cronExpression.trim().split("\\s+".toRegex())
            if (parts.size < 5) return null
            
            val (minute, hour, _, _, _) = parts
            
            // For simplicity, handle only basic cases like "0 9 * * *" (daily at 9 AM)
            if (minute == "0" && hour.toIntOrNull() != null) {
                val targetHour = hour.toInt()
                val now = Clock.System.now()
                
                // Calculate next occurrence of this hour
                val nowMillis = now.toEpochMilliseconds()
                val nextDay = nowMillis + (24 * 60 * 60 * 1000) // Add 24 hours
                
                // This is a simplified calculation - real implementation would be more complex
                return Instant.fromEpochMilliseconds(nextDay)
            }
            
            // For other patterns, schedule for next hour as fallback
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            Instant.fromEpochMilliseconds(nowMillis + (60 * 60 * 1000)) // Next hour
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validate a cron field
     */
    private fun isValidCronField(field: String, min: Int, max: Int): Boolean {
        return try {
            when {
                field == "*" -> true
                field.contains("/") -> {
                    val parts = field.split("/")
                    if (parts.size != 2) return false
                    val step = parts[1].toInt()
                    step > 0 && (parts[0] == "*" || isValidCronField(parts[0], min, max))
                }
                field.contains("-") -> {
                    val parts = field.split("-")
                    if (parts.size != 2) return false
                    val start = parts[0].toInt()
                    val end = parts[1].toInt()
                    start in min..max && end in min..max && start <= end
                }
                field.contains(",") -> {
                    field.split(",").all { part ->
                        isValidCronField(part.trim(), min, max)
                    }
                }
                else -> {
                    val value = field.toInt()
                    value in min..max
                }
            }
        } catch (e: NumberFormatException) {
            false
        }
    }
}

/**
 * Cron validation result
 */
data class CronValidationResult(
    val isValid: Boolean,
    val error: String?
)

/**
 * Scheduled task information
 */
data class ScheduledTask(
    val task: Task,
    val cronExpression: String,
    val lastRun: Instant?,
    val nextRun: Instant?
)