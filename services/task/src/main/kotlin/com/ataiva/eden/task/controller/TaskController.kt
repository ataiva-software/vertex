package com.ataiva.eden.task.controller

import com.ataiva.eden.task.service.TaskService
import com.ataiva.eden.task.service.TaskResult
import com.ataiva.eden.task.model.*
import com.ataiva.eden.task.engine.TaskExecutor
import com.ataiva.eden.task.engine.TaskScheduler
import com.ataiva.eden.task.queue.TaskQueue
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.datetime.Clock

/**
 * REST API controller for Eden Task Service
 */
class TaskController(
    private val taskService: TaskService,
    private val taskExecutor: TaskExecutor,
    private val taskScheduler: TaskScheduler,
    private val taskQueue: TaskQueue
) {
    
    fun Route.taskRoutes() {
        route("/api/v1") {
            tasksRoutes()
            executionsRoutes()
            jobsRoutes()
            queuesRoutes()
            templatesRoutes()
            validationRoutes()
            bulkRoutes()
            searchRoutes()
        }
    }
    
    private fun Route.tasksRoutes() {
        route("/tasks") {
            // Create task
            post {
                try {
                    val request = call.receive<CreateTaskRequest>()
                    
                    when (val result = taskService.createTask(request)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<TaskResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<TaskResponse>("Internal server error"))
                }
            }
            
            // List tasks
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<TaskResponse>>("userId is required"))
                    
                    val request = ListTasksRequest(
                        userId = userId,
                        taskType = call.request.queryParameters["taskType"],
                        namePattern = call.request.queryParameters["namePattern"],
                        includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false,
                        scheduledOnly = call.request.queryParameters["scheduledOnly"]?.toBoolean() ?: false
                    )
                    
                    when (val result = taskService.listTasks(request)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<TaskResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<TaskResponse>>("Internal server error"))
                }
            }
            
            // Get specific task
            get("/{id}") {
                try {
                    val taskId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<TaskResponse>("Task ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<TaskResponse>("userId is required"))
                    
                    when (val result = taskService.getTask(taskId, userId)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<TaskResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<TaskResponse>("Internal server error"))
                }
            }
            
            // Update task
            put("/{id}") {
                try {
                    val taskId = call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse.error<TaskResponse>("Task ID is required"))
                    
                    val updateData = call.receive<UpdateTaskRequest>()
                    val request = updateData.copy(taskId = taskId)
                    
                    when (val result = taskService.updateTask(request)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<TaskResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<TaskResponse>("Internal server error"))
                }
            }
            
            // Delete task
            delete("/{id}") {
                try {
                    val taskId = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Task ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = taskService.deleteTask(taskId, userId)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(Unit))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Execute task
            post("/{id}/execute") {
                try {
                    val taskId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>("Task ID is required"))
                    
                    val executeData = call.receive<ExecuteTaskRequest>()
                    val request = executeData.copy(taskId = taskId)
                    
                    when (val result = taskService.executeTask(request)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.Accepted, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExecutionResponse>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.executionsRoutes() {
        route("/executions") {
            // List executions
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExecutionResponse>>("userId is required"))
                    
                    val request = ListExecutionsRequest(
                        userId = userId,
                        taskId = call.request.queryParameters["taskId"],
                        status = call.request.queryParameters["status"],
                        limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    )
                    
                    when (val result = taskService.listExecutions(request)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExecutionResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<ExecutionResponse>>("Internal server error"))
                }
            }
            
            // Get specific execution
            get("/{id}") {
                try {
                    val executionId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>("Execution ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>("userId is required"))
                    
                    when (val result = taskService.getExecution(executionId, userId)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<ExecutionResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExecutionResponse>("Internal server error"))
                }
            }
            
            // Cancel execution
            post("/{id}/cancel") {
                try {
                    val executionId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Execution ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = taskService.cancelExecution(executionId, userId)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(Unit))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Get execution logs
            get("/{id}/logs") {
                try {
                    val executionId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionLogsResponse>("Execution ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionLogsResponse>("userId is required"))
                    
                    // Mock logs for now - in production, would retrieve from logging system
                    val logs = listOf(
                        LogEntry(Clock.System.now(), "INFO", "Task execution started"),
                        LogEntry(Clock.System.now(), "INFO", "Processing task configuration"),
                        LogEntry(Clock.System.now(), "INFO", "Task execution in progress...")
                    )
                    
                    val response = ExecutionLogsResponse(
                        logs = logs,
                        totalCount = logs.size,
                        hasMore = false
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExecutionLogsResponse>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.jobsRoutes() {
        route("/jobs") {
            // List scheduled jobs
            get {
                try {
                    val scheduledTasks = taskScheduler.getAllScheduledTasks()
                    val jobs = scheduledTasks.map { scheduledTask ->
                        mapOf(
                            "id" to scheduledTask.task.id,
                            "name" to scheduledTask.task.name,
                            "cronExpression" to scheduledTask.cronExpression,
                            "lastRun" to scheduledTask.lastRun,
                            "nextRun" to scheduledTask.nextRun,
                            "status" to if (scheduledTask.task.isActive) "active" else "inactive"
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(jobs))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<Any>>("Internal server error"))
                }
            }
            
            // Get specific job
            get("/{id}") {
                try {
                    val jobId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>("Job ID is required"))
                    
                    val scheduledTask = taskScheduler.getScheduledTaskInfo(jobId)
                    if (scheduledTask != null) {
                        val job = mapOf(
                            "id" to scheduledTask.task.id,
                            "name" to scheduledTask.task.name,
                            "description" to scheduledTask.task.description,
                            "cronExpression" to scheduledTask.cronExpression,
                            "lastRun" to scheduledTask.lastRun,
                            "nextRun" to scheduledTask.nextRun,
                            "status" to if (scheduledTask.task.isActive) "active" else "inactive"
                        )
                        call.respond(HttpStatusCode.OK, ApiResponse.success(job))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<Any>("Job not found"))
                    }
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.queuesRoutes() {
        route("/queues") {
            // Get queue statistics
            get {
                try {
                    val queueStats = taskQueue.getStats()
                    val response = QueueStatsResponse(
                        totalQueued = queueStats.totalQueued,
                        highPriority = queueStats.highPriority,
                        mediumPriority = queueStats.mediumPriority,
                        lowPriority = queueStats.lowPriority,
                        averagePriority = queueStats.averagePriority,
                        oldestQueuedAt = queueStats.oldestQueuedAt,
                        priorityDistribution = queueStats.priorityDistribution
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<QueueStatsResponse>("Internal server error"))
                }
            }
            
            // Get queued tasks by priority
            get("/priority/{level}") {
                try {
                    val priorityLevel = call.parameters["level"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExecutionResponse>>("Invalid priority level"))
                    
                    val queuedExecutions = taskQueue.getQueuedByPriority(priorityLevel)
                    val response = queuedExecutions.map { ExecutionResponse.fromExecution(it) }
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<ExecutionResponse>>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.templatesRoutes() {
        route("/templates") {
            get {
                // Return predefined task templates
                val templates = listOf(
                    TaskTemplateResponse(
                        id = "http-health-check",
                        name = "HTTP Health Check",
                        description = "Monitor service health via HTTP endpoint",
                        taskType = "monitoring_check",
                        category = "Monitoring",
                        configuration = mapOf(
                            "check_type" to "service_health",
                            "url" to "{{service_url}}/health",
                            "method" to "GET",
                            "timeout" to 30
                        ),
                        parameters = listOf(
                            TemplateParameter("service_url", "string", "Service base URL")
                        ),
                        tags = listOf("monitoring", "health-check", "http")
                    ),
                    TaskTemplateResponse(
                        id = "file-backup",
                        name = "File Backup",
                        description = "Backup files or directories",
                        taskType = "backup_task",
                        category = "Maintenance",
                        configuration = mapOf(
                            "source" to "{{source_path}}",
                            "destination" to "{{backup_path}}",
                            "compress" to true
                        ),
                        parameters = listOf(
                            TemplateParameter("source_path", "string", "Source file or directory path"),
                            TemplateParameter("backup_path", "string", "Backup destination path")
                        ),
                        tags = listOf("backup", "files", "maintenance")
                    ),
                    TaskTemplateResponse(
                        id = "cleanup-temp-files",
                        name = "Cleanup Temporary Files",
                        description = "Clean up old temporary files",
                        taskType = "cleanup_task",
                        category = "Maintenance",
                        configuration = mapOf(
                            "cleanup_type" to "temp_files",
                            "max_age_days" to 7
                        ),
                        parameters = listOf(
                            TemplateParameter("max_age_days", "integer", "Maximum age in days", false, "7")
                        ),
                        tags = listOf("cleanup", "maintenance", "temp-files")
                    ),
                    TaskTemplateResponse(
                        id = "disk-space-monitor",
                        name = "Disk Space Monitor",
                        description = "Monitor disk space usage",
                        taskType = "monitoring_check",
                        category = "Monitoring",
                        configuration = mapOf(
                            "check_type" to "disk_space",
                            "path" to "/",
                            "warning_threshold" to 80,
                            "critical_threshold" to 90
                        ),
                        parameters = listOf(
                            TemplateParameter("path", "string", "Path to monitor", false, "/"),
                            TemplateParameter("warning_threshold", "integer", "Warning threshold percentage", false, "80"),
                            TemplateParameter("critical_threshold", "integer", "Critical threshold percentage", false, "90")
                        ),
                        tags = listOf("monitoring", "disk-space", "system")
                    )
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse.success(templates))
            }
        }
    }
    
    private fun Route.validationRoutes() {
        route("/validate") {
            post("/task") {
                try {
                    val request = call.receive<ValidateTaskRequest>()
                    
                    val validationResult = taskExecutor.validateConfiguration(request.taskType, request.configuration)
                    
                    val response = ValidateTaskResponse(
                        isValid = validationResult.isValid,
                        errors = validationResult.errors,
                        warnings = emptyList(), // TODO: Add warning detection
                        supportedTaskType = request.taskType in TaskExecutor.SUPPORTED_TASK_TYPES
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ValidateTaskResponse>("Validation failed: ${e.message}"))
                }
            }
        }
    }
    
    private fun Route.bulkRoutes() {
        route("/bulk") {
            post("/tasks") {
                try {
                    val request = call.receive<BulkTaskRequest>()
                    
                    val successful = mutableListOf<TaskResponse>()
                    val failed = mutableListOf<BulkOperationError>()
                    
                    for (taskRequest in request.tasks) {
                        val createRequest = taskRequest.copy(userId = request.userId)
                        
                        when (val result = taskService.createTask(createRequest)) {
                            is TaskResult.Success -> successful.add(result.data)
                            is TaskResult.Error -> failed.add(BulkOperationError(taskRequest.name, result.message))
                        }
                    }
                    
                    val response = BulkTaskResponse(
                        successful = successful,
                        failed = failed,
                        totalProcessed = request.tasks.size,
                        successCount = successful.size,
                        failureCount = failed.size
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<BulkTaskResponse>("Bulk operation failed"))
                }
            }
        }
    }
    
    private fun Route.searchRoutes() {
        route("/search") {
            post("/tasks") {
                try {
                    val request = call.receive<SearchTasksRequest>()
                    
                    val listRequest = ListTasksRequest(
                        userId = request.userId,
                        taskType = request.taskType,
                        namePattern = request.query
                    )
                    
                    when (val result = taskService.listTasks(listRequest)) {
                        is TaskResult.Success -> {
                            val tasks = result.data.drop(request.offset).take(request.limit)
                            val response = SearchTasksResponse(
                                tasks = tasks,
                                totalCount = result.data.size,
                                hasMore = result.data.size > request.offset + request.limit
                            )
                            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SearchTasksResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SearchTasksResponse>("Search failed"))
                }
            }
        }
    }
    
    // Statistics and monitoring endpoints
    fun Route.statsRoutes() {
        route("/stats") {
            get("/tasks") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>("userId is required"))
                    
                    when (val result = taskService.getTaskStats(userId)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Failed to get statistics"))
                }
            }
            
            get("/executions") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>("userId is required"))
                    
                    val taskId = call.request.queryParameters["taskId"]
                    
                    when (val result = taskService.getExecutionStats(taskId, userId)) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Failed to get execution statistics"))
                }
            }
            
            get("/queue") {
                try {
                    when (val result = taskService.getQueueStats()) {
                        is TaskResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is TaskResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Failed to get queue statistics"))
                }
            }
        }
    }
}