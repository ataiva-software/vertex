package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.Repository
import kotlinx.datetime.Instant

/**
 * Task entity for database operations
 */
data class Task(
    val id: String,
    val name: String,
    val description: String?,
    val taskType: String,
    val configuration: Map<String, Any>,
    val scheduleCron: String?,
    val userId: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Task execution entity
 */
data class TaskExecution(
    val id: String,
    val taskId: String,
    val status: String,
    val priority: Int,
    val inputData: Map<String, Any>?,
    val outputData: Map<String, Any>?,
    val errorMessage: String?,
    val progressPercentage: Int,
    val queuedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationMs: Int?
)

/**
 * Repository interface for task management operations
 */
interface TaskRepository : Repository<Task, String> {
    
    /**
     * Find task by name and user ID
     */
    suspend fun findByNameAndUser(name: String, userId: String): Task?
    
    /**
     * Find all tasks for a user
     */
    suspend fun findByUserId(userId: String): List<Task>
    
    /**
     * Find active tasks for a user
     */
    suspend fun findActiveByUserId(userId: String): List<Task>
    
    /**
     * Find tasks by type
     */
    suspend fun findByType(taskType: String): List<Task>
    
    /**
     * Find tasks by type and user
     */
    suspend fun findByTypeAndUser(taskType: String, userId: String): List<Task>
    
    /**
     * Find scheduled tasks (with cron expressions)
     */
    suspend fun findScheduledTasks(): List<Task>
    
    /**
     * Find scheduled tasks for user
     */
    suspend fun findScheduledTasksByUser(userId: String): List<Task>
    
    /**
     * Search tasks by name pattern
     */
    suspend fun searchByName(userId: String, namePattern: String): List<Task>
    
    /**
     * Update task status (activate/deactivate)
     */
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    
    /**
     * Update task configuration
     */
    suspend fun updateConfiguration(id: String, configuration: Map<String, Any>): Boolean
    
    /**
     * Update task schedule
     */
    suspend fun updateSchedule(id: String, scheduleCron: String?): Boolean
    
    /**
     * Get task statistics for user
     */
    suspend fun getTaskStats(userId: String): TaskStats
}

/**
 * Repository interface for task execution operations
 */
interface TaskExecutionRepository : Repository<TaskExecution, String> {
    
    /**
     * Find executions by task ID
     */
    suspend fun findByTaskId(taskId: String): List<TaskExecution>
    
    /**
     * Find executions by status
     */
    suspend fun findByStatus(status: String): List<TaskExecution>
    
    /**
     * Find queued executions ordered by priority
     */
    suspend fun findQueuedByPriority(): List<TaskExecution>
    
    /**
     * Find running executions
     */
    suspend fun findRunning(): List<TaskExecution>
    
    /**
     * Find recent executions
     */
    suspend fun findRecent(limit: Int = 50): List<TaskExecution>
    
    /**
     * Find executions by priority
     */
    suspend fun findByPriority(priority: Int): List<TaskExecution>
    
    /**
     * Find high priority executions (priority > threshold)
     */
    suspend fun findHighPriority(threshold: Int = 5): List<TaskExecution>
    
    /**
     * Find next queued execution for processing
     */
    suspend fun findNextQueued(): TaskExecution?
    
    /**
     * Update execution status
     */
    suspend fun updateStatus(id: String, status: String, startedAt: Instant? = null, completedAt: Instant? = null): Boolean
    
    /**
     * Update execution progress
     */
    suspend fun updateProgress(id: String, progressPercentage: Int): Boolean
    
    /**
     * Update execution output
     */
    suspend fun updateOutput(id: String, outputData: Map<String, Any>): Boolean
    
    /**
     * Update execution error
     */
    suspend fun updateError(id: String, errorMessage: String, completedAt: Instant): Boolean
    
    /**
     * Mark execution as started
     */
    suspend fun markStarted(id: String, startedAt: Instant): Boolean
    
    /**
     * Mark execution as completed
     */
    suspend fun markCompleted(id: String, outputData: Map<String, Any>?, completedAt: Instant, durationMs: Int): Boolean
    
    /**
     * Mark execution as failed
     */
    suspend fun markFailed(id: String, errorMessage: String, completedAt: Instant, durationMs: Int): Boolean
    
    /**
     * Cancel execution
     */
    suspend fun cancel(id: String, completedAt: Instant): Boolean
    
    /**
     * Get execution statistics
     */
    suspend fun getExecutionStats(taskId: String? = null): TaskExecutionStats
    
    /**
     * Get queue statistics
     */
    suspend fun getQueueStats(): QueueStats
}

/**
 * Task statistics data class
 */
data class TaskStats(
    val totalTasks: Long,
    val activeTasks: Long,
    val inactiveTasks: Long,
    val scheduledTasks: Long,
    val tasksByType: Map<String, Long>,
    val recentlyCreated: Long,
    val recentlyUpdated: Long
)

/**
 * Task execution statistics data class
 */
data class TaskExecutionStats(
    val totalExecutions: Long,
    val completedExecutions: Long,
    val failedExecutions: Long,
    val runningExecutions: Long,
    val queuedExecutions: Long,
    val cancelledExecutions: Long,
    val averageDurationMs: Double?,
    val successRate: Double,
    val averageQueueTime: Double?
)

/**
 * Queue statistics data class
 */
data class QueueStats(
    val queuedCount: Long,
    val runningCount: Long,
    val highPriorityCount: Long,
    val averagePriority: Double?,
    val oldestQueuedAt: Instant?,
    val estimatedProcessingTime: Long?
)