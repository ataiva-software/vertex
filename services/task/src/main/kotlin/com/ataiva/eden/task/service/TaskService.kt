package com.ataiva.eden.task.service

import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.repositories.*
import com.ataiva.eden.task.engine.TaskExecutor
import com.ataiva.eden.task.engine.TaskScheduler
import com.ataiva.eden.task.queue.TaskQueue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Core business logic for Eden Task Service - Task execution and job scheduling
 */
class TaskService(
    private val databaseService: EdenDatabaseService,
    private val taskExecutor: TaskExecutor,
    private val taskScheduler: TaskScheduler,
    private val taskQueue: TaskQueue
) {
    
    private val runningExecutions = ConcurrentHashMap<String, Job>()
    private val executionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Start background task processing
        startTaskProcessor()
        startScheduler()
    }
    
    /**
     * Create a new task
     */
    suspend fun createTask(request: CreateTaskRequest): TaskResult<TaskResponse> {
        return try {
            // Validate task configuration
            val validationResult = taskExecutor.validateConfiguration(request.taskType, request.configuration)
            if (!validationResult.isValid) {
                return TaskResult.Error("Invalid task configuration: ${validationResult.errors.joinToString(", ")}")
            }
            
            // Check if task with same name exists
            val existing = databaseService.taskRepository.findByNameAndUser(request.name, request.userId)
            if (existing != null) {
                return TaskResult.Error("Task with name '${request.name}' already exists")
            }
            
            // Validate cron expression if provided
            if (request.scheduleCron != null) {
                val cronValidation = taskScheduler.validateCronExpression(request.scheduleCron)
                if (!cronValidation.isValid) {
                    return TaskResult.Error("Invalid cron expression: ${cronValidation.error}")
                }
            }
            
            // Create task entity
            val task = Task(
                id = generateId(),
                name = request.name,
                description = request.description,
                taskType = request.taskType,
                configuration = request.configuration,
                scheduleCron = request.scheduleCron,
                userId = request.userId,
                isActive = true,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            // Save to database
            val savedTask = databaseService.taskRepository.create(task)
            
            // Register with scheduler if it's a scheduled task
            if (savedTask.scheduleCron != null) {
                taskScheduler.scheduleTask(savedTask)
            }
            
            TaskResult.Success(TaskResponse.fromTask(savedTask))
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to create task: ${e.message}")
        }
    }
    
    /**
     * Get task by ID
     */
    suspend fun getTask(taskId: String, userId: String): TaskResult<TaskResponse> {
        return try {
            val task = databaseService.taskRepository.findById(taskId)
                ?: return TaskResult.Error("Task not found")
            
            // Check user access
            if (task.userId != userId) {
                return TaskResult.Error("Access denied")
            }
            
            TaskResult.Success(TaskResponse.fromTask(task))
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to get task: ${e.message}")
        }
    }
    
    /**
     * Update task
     */
    suspend fun updateTask(request: UpdateTaskRequest): TaskResult<TaskResponse> {
        return try {
            val task = databaseService.taskRepository.findById(request.taskId)
                ?: return TaskResult.Error("Task not found")
            
            // Check user access
            if (task.userId != request.userId) {
                return TaskResult.Error("Access denied")
            }
            
            // Validate new configuration if provided
            if (request.configuration != null) {
                val validationResult = taskExecutor.validateConfiguration(task.taskType, request.configuration)
                if (!validationResult.isValid) {
                    return TaskResult.Error("Invalid task configuration: ${validationResult.errors.joinToString(", ")}")
                }
            }
            
            // Validate new cron expression if provided
            if (request.scheduleCron != null) {
                val cronValidation = taskScheduler.validateCronExpression(request.scheduleCron)
                if (!cronValidation.isValid) {
                    return TaskResult.Error("Invalid cron expression: ${cronValidation.error}")
                }
            }
            
            // Update task
            val updatedTask = task.copy(
                description = request.description ?: task.description,
                configuration = request.configuration ?: task.configuration,
                scheduleCron = request.scheduleCron ?: task.scheduleCron,
                updatedAt = Clock.System.now()
            )
            
            val saved = databaseService.taskRepository.update(updatedTask)
            if (!saved) {
                return TaskResult.Error("Failed to update task")
            }
            
            // Update scheduler if schedule changed
            if (request.scheduleCron != null) {
                taskScheduler.unscheduleTask(task.id)
                if (request.scheduleCron.isNotBlank()) {
                    taskScheduler.scheduleTask(updatedTask)
                }
            }
            
            TaskResult.Success(TaskResponse.fromTask(updatedTask))
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to update task: ${e.message}")
        }
    }
    
    /**
     * Delete task (deactivate)
     */
    suspend fun deleteTask(taskId: String, userId: String): TaskResult<Unit> {
        return try {
            val task = databaseService.taskRepository.findById(taskId)
                ?: return TaskResult.Error("Task not found")
            
            // Check user access
            if (task.userId != userId) {
                return TaskResult.Error("Access denied")
            }
            
            // Check if there are running executions
            val runningExecutions = databaseService.taskExecutionRepository.findByTaskId(taskId)
                .filter { it.status in listOf("queued", "running") }
            
            if (runningExecutions.isNotEmpty()) {
                return TaskResult.Error("Cannot delete task with running executions")
            }
            
            // Deactivate the task
            val success = databaseService.taskRepository.updateStatus(taskId, false)
            if (!success) {
                return TaskResult.Error("Failed to delete task")
            }
            
            // Unschedule from scheduler
            taskScheduler.unscheduleTask(taskId)
            
            TaskResult.Success(Unit)
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to delete task: ${e.message}")
        }
    }
    
    /**
     * List tasks for a user
     */
    suspend fun listTasks(request: ListTasksRequest): TaskResult<List<TaskResponse>> {
        return try {
            val tasks = when {
                request.taskType != null -> {
                    databaseService.taskRepository.findByTypeAndUser(request.taskType, request.userId)
                }
                request.namePattern != null -> {
                    databaseService.taskRepository.searchByName(request.userId, request.namePattern)
                }
                request.includeInactive -> {
                    databaseService.taskRepository.findByUserId(request.userId)
                }
                request.scheduledOnly -> {
                    databaseService.taskRepository.findScheduledTasksByUser(request.userId)
                }
                else -> {
                    databaseService.taskRepository.findActiveByUserId(request.userId)
                }
            }
            
            TaskResult.Success(tasks.map { TaskResponse.fromTask(it) })
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to list tasks: ${e.message}")
        }
    }
    
    /**
     * Execute a task immediately
     */
    suspend fun executeTask(request: ExecuteTaskRequest): TaskResult<ExecutionResponse> {
        return try {
            val task = databaseService.taskRepository.findById(request.taskId)
                ?: return TaskResult.Error("Task not found")
            
            // Check user access
            if (task.userId != request.userId) {
                return TaskResult.Error("Access denied")
            }
            
            // Check task status
            if (!task.isActive) {
                return TaskResult.Error("Task is not active")
            }
            
            // Create execution record
            val execution = TaskExecution(
                id = generateId(),
                taskId = task.id,
                status = "queued",
                priority = request.priority ?: 5,
                inputData = request.inputData,
                outputData = null,
                errorMessage = null,
                progressPercentage = 0,
                queuedAt = Clock.System.now(),
                startedAt = null,
                completedAt = null,
                durationMs = null
            )
            
            val savedExecution = databaseService.taskExecutionRepository.create(execution)
            
            // Add to task queue
            taskQueue.enqueue(savedExecution)
            
            TaskResult.Success(ExecutionResponse.fromExecution(savedExecution))
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to execute task: ${e.message}")
        }
    }
    
    /**
     * Get execution status
     */
    suspend fun getExecution(executionId: String, userId: String): TaskResult<ExecutionResponse> {
        return try {
            val execution = databaseService.taskExecutionRepository.findById(executionId)
                ?: return TaskResult.Error("Execution not found")
            
            // Check user access through task
            val task = databaseService.taskRepository.findById(execution.taskId)
            if (task?.userId != userId) {
                return TaskResult.Error("Access denied")
            }
            
            TaskResult.Success(ExecutionResponse.fromExecution(execution))
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to get execution: ${e.message}")
        }
    }
    
    /**
     * Cancel task execution
     */
    suspend fun cancelExecution(executionId: String, userId: String): TaskResult<Unit> {
        return try {
            val execution = databaseService.taskExecutionRepository.findById(executionId)
                ?: return TaskResult.Error("Execution not found")
            
            // Check user access through task
            val task = databaseService.taskRepository.findById(execution.taskId)
            if (task?.userId != userId) {
                return TaskResult.Error("Access denied")
            }
            
            // Check if execution can be cancelled
            if (execution.status !in listOf("queued", "running")) {
                return TaskResult.Error("Execution cannot be cancelled (status: ${execution.status})")
            }
            
            // Cancel the running job
            runningExecutions[executionId]?.cancel()
            runningExecutions.remove(executionId)
            
            // Remove from queue if queued
            taskQueue.remove(executionId)
            
            // Update execution status
            databaseService.taskExecutionRepository.cancel(executionId, Clock.System.now())
            
            TaskResult.Success(Unit)
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to cancel execution: ${e.message}")
        }
    }
    
    /**
     * List executions for a task or user
     */
    suspend fun listExecutions(request: ListExecutionsRequest): TaskResult<List<ExecutionResponse>> {
        return try {
            val executions = when {
                request.taskId != null -> {
                    // Verify user access to task
                    val task = databaseService.taskRepository.findById(request.taskId)
                    if (task?.userId != request.userId) {
                        return TaskResult.Error("Access denied")
                    }
                    databaseService.taskExecutionRepository.findByTaskId(request.taskId)
                }
                request.status != null -> {
                    // Get all user's tasks and filter executions by status
                    val userTasks = databaseService.taskRepository.findByUserId(request.userId)
                    val taskIds = userTasks.map { it.id }.toSet()
                    databaseService.taskExecutionRepository.findByStatus(request.status)
                        .filter { execution ->
                            taskIds.contains(execution.taskId)
                        }
                }
                else -> {
                    // Get all executions for user's tasks
                    val userTasks = databaseService.taskRepository.findByUserId(request.userId)
                    val taskIds = userTasks.map { it.id }
                    taskIds.flatMap { taskId ->
                        databaseService.taskExecutionRepository.findByTaskId(taskId)
                    }
                }
            }
            
            val limitedExecutions = if (request.limit != null) {
                executions.take(request.limit)
            } else {
                executions
            }
            
            TaskResult.Success(limitedExecutions.map { ExecutionResponse.fromExecution(it) })
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to list executions: ${e.message}")
        }
    }
    
    /**
     * Get task statistics
     */
    suspend fun getTaskStats(userId: String): TaskResult<TaskStats> {
        return try {
            val stats = databaseService.taskRepository.getTaskStats(userId)
            TaskResult.Success(stats)
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to get task statistics: ${e.message}")
        }
    }
    
    /**
     * Get execution statistics
     */
    suspend fun getExecutionStats(taskId: String?, userId: String): TaskResult<TaskExecutionStats> {
        return try {
            // Verify user access if taskId is provided
            if (taskId != null) {
                val task = databaseService.taskRepository.findById(taskId)
                if (task?.userId != userId) {
                    return TaskResult.Error("Access denied")
                }
            }
            
            val stats = databaseService.taskExecutionRepository.getExecutionStats(taskId)
            TaskResult.Success(stats)
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to get execution statistics: ${e.message}")
        }
    }
    
    /**
     * Get queue statistics
     */
    suspend fun getQueueStats(): TaskResult<QueueStats> {
        return try {
            val stats = databaseService.taskExecutionRepository.getQueueStats()
            TaskResult.Success(stats)
            
        } catch (e: Exception) {
            TaskResult.Error("Failed to get queue statistics: ${e.message}")
        }
    }
    
    /**
     * Start background task processor
     */
    private fun startTaskProcessor() {
        executionScope.launch {
            while (isActive) {
                try {
                    val execution = taskQueue.dequeue()
                    if (execution != null) {
                        // Process execution in separate coroutine
                        val job = launch {
                            processExecution(execution)
                        }
                        runningExecutions[execution.id] = job
                    } else {
                        // No tasks in queue, wait a bit
                        delay(1000)
                    }
                } catch (e: Exception) {
                    // Log error and continue processing
                    println("Error in task processor: ${e.message}")
                    delay(5000)
                }
            }
        }
    }
    
    /**
     * Start task scheduler for cron jobs
     */
    private fun startScheduler() {
        executionScope.launch {
            while (isActive) {
                try {
                    // Check for scheduled tasks that need to run
                    val tasksToRun = taskScheduler.getTasksToRun()
                    
                    for (task in tasksToRun) {
                        // Create execution for scheduled task
                        val execution = TaskExecution(
                            id = generateId(),
                            taskId = task.id,
                            status = "queued",
                            priority = 3, // Medium priority for scheduled tasks
                            inputData = null,
                            outputData = null,
                            errorMessage = null,
                            progressPercentage = 0,
                            queuedAt = Clock.System.now(),
                            startedAt = null,
                            completedAt = null,
                            durationMs = null
                        )
                        
                        val savedExecution = databaseService.taskExecutionRepository.create(execution)
                        taskQueue.enqueue(savedExecution)
                    }
                    
                    // Check every minute for scheduled tasks
                    delay(60000)
                    
                } catch (e: Exception) {
                    println("Error in task scheduler: ${e.message}")
                    delay(60000)
                }
            }
        }
    }
    
    /**
     * Process a single task execution
     */
    private suspend fun processExecution(execution: TaskExecution) {
        try {
            // Get task details
            val task = databaseService.taskRepository.findById(execution.taskId)
                ?: return
            
            // Mark execution as started
            val startTime = Clock.System.now()
            databaseService.taskExecutionRepository.markStarted(execution.id, startTime)
            
            // Execute the task
            val result = taskExecutor.executeTask(task, execution.inputData)
            
            val endTime = Clock.System.now()
            val duration = (endTime.toEpochMilliseconds() - startTime.toEpochMilliseconds()).toInt()
            
            // Update execution with result
            when (result) {
                is TaskExecutionResult.Success -> {
                    databaseService.taskExecutionRepository.markCompleted(
                        execution.id,
                        result.outputData,
                        endTime,
                        duration
                    )
                }
                is TaskExecutionResult.Error -> {
                    databaseService.taskExecutionRepository.markFailed(
                        execution.id,
                        result.errorMessage,
                        endTime,
                        duration
                    )
                }
            }
            
        } catch (e: Exception) {
            // Handle unexpected errors
            val endTime = Clock.System.now()
            databaseService.taskExecutionRepository.markFailed(
                execution.id,
                "Unexpected error: ${e.message}",
                endTime,
                0
            )
        } finally {
            // Remove from running executions
            runningExecutions.remove(execution.id)
        }
    }
    
    private fun generateId(): String {
        return java.util.UUID.randomUUID().toString()
    }
}

/**
 * Task operation result
 */
sealed class TaskResult<out T> {
    data class Success<T>(val data: T) : TaskResult<T>()
    data class Error(val message: String) : TaskResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun getErrorOrNull(): String? = when (this) {
        is Success -> null
        is Error -> message
    }
}

/**
 * Task execution result
 */
sealed class TaskExecutionResult {
    data class Success(val outputData: Map<String, Any>?) : TaskExecutionResult()
    data class Error(val errorMessage: String) : TaskExecutionResult()
}