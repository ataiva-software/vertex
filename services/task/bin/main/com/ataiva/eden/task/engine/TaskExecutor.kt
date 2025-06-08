package com.ataiva.eden.task.engine

import com.ataiva.eden.database.repositories.Task
import com.ataiva.eden.task.service.TaskExecutionResult
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

/**
 * Task executor for running different types of tasks
 */
class TaskExecutor {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    /**
     * Validate task configuration
     */
    fun validateConfiguration(taskType: String, configuration: Map<String, Any>): ValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            when (taskType) {
                "http_request" -> validateHttpRequestConfig(configuration, errors)
                "shell_command" -> validateShellCommandConfig(configuration, errors)
                "file_operation" -> validateFileOperationConfig(configuration, errors)
                "database_query" -> validateDatabaseQueryConfig(configuration, errors)
                "email_notification" -> validateEmailNotificationConfig(configuration, errors)
                "webhook" -> validateWebhookConfig(configuration, errors)
                "data_processing" -> validateDataProcessingConfig(configuration, errors)
                "backup_task" -> validateBackupTaskConfig(configuration, errors)
                "monitoring_check" -> validateMonitoringCheckConfig(configuration, errors)
                "cleanup_task" -> validateCleanupTaskConfig(configuration, errors)
                else -> errors.add("Unsupported task type: $taskType")
            }
        } catch (e: Exception) {
            errors.add("Configuration validation error: ${e.message}")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Execute a task
     */
    suspend fun executeTask(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        return try {
            when (task.taskType) {
                "http_request" -> executeHttpRequest(task, inputData)
                "shell_command" -> executeShellCommand(task, inputData)
                "file_operation" -> executeFileOperation(task, inputData)
                "database_query" -> executeDatabaseQuery(task, inputData)
                "email_notification" -> executeEmailNotification(task, inputData)
                "webhook" -> executeWebhook(task, inputData)
                "data_processing" -> executeDataProcessing(task, inputData)
                "backup_task" -> executeBackupTask(task, inputData)
                "monitoring_check" -> executeMonitoringCheck(task, inputData)
                "cleanup_task" -> executeCleanupTask(task, inputData)
                else -> TaskExecutionResult.Error("Unsupported task type: ${task.taskType}")
            }
        } catch (e: Exception) {
            TaskExecutionResult.Error("Task execution failed: ${e.message}")
        }
    }
    
    /**
     * Execute HTTP request task
     */
    private suspend fun executeHttpRequest(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = task.configuration
                val url = config["url"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'url' parameter")
                val method = config["method"] as? String ?: "GET"
                val headers = config["headers"] as? Map<String, String> ?: emptyMap()
                val body = config["body"] as? String
                val timeout = config["timeout"] as? Int ?: 30
                
                // Build HTTP request
                val requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout.toLong()))
                
                // Add headers
                headers.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }
                
                // Set method and body
                when (method.uppercase()) {
                    "GET" -> requestBuilder.GET()
                    "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
                    "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body ?: ""))
                    "DELETE" -> requestBuilder.DELETE()
                    else -> return@withContext TaskExecutionResult.Error("Unsupported HTTP method: $method")
                }
                
                val request = requestBuilder.build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                val outputData = mapOf(
                    "status_code" to response.statusCode(),
                    "headers" to response.headers().map(),
                    "body" to response.body(),
                    "success" to (response.statusCode() in 200..299)
                )
                
                TaskExecutionResult.Success(outputData)
                
            } catch (e: Exception) {
                TaskExecutionResult.Error("HTTP request failed: ${e.message}")
            }
        }
    }
    
    /**
     * Execute shell command task
     */
    private suspend fun executeShellCommand(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = task.configuration
                val command = config["command"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'command' parameter")
                val workingDir = config["working_dir"] as? String
                val environment = config["environment"] as? Map<String, String> ?: emptyMap()
                val timeout = config["timeout"] as? Int ?: 300
                
                val processBuilder = ProcessBuilder()
                    .command("sh", "-c", command)
                    .redirectErrorStream(true)
                
                // Set working directory
                if (workingDir != null) {
                    processBuilder.directory(File(workingDir))
                }
                
                // Set environment variables
                val env = processBuilder.environment()
                environment.forEach { (key, value) ->
                    env[key] = value
                }
                
                val process = processBuilder.start()
                
                // Wait for completion with timeout
                val completed = process.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                
                if (!completed) {
                    process.destroyForcibly()
                    return@withContext TaskExecutionResult.Error("Command timed out after $timeout seconds")
                }
                
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.exitValue()
                
                val outputData = mapOf(
                    "exit_code" to exitCode,
                    "output" to output,
                    "success" to (exitCode == 0)
                )
                
                if (exitCode == 0) {
                    TaskExecutionResult.Success(outputData)
                } else {
                    TaskExecutionResult.Error("Command failed with exit code $exitCode: $output")
                }
                
            } catch (e: Exception) {
                TaskExecutionResult.Error("Shell command execution failed: ${e.message}")
            }
        }
    }
    
    /**
     * Execute file operation task
     */
    private suspend fun executeFileOperation(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = task.configuration
                val operation = config["operation"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'operation' parameter")
                
                when (operation) {
                    "read" -> {
                        val filePath = config["path"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'path' parameter")
                        val content = File(filePath).readText()
                        TaskExecutionResult.Success(mapOf("content" to content, "size" to content.length))
                    }
                    "write" -> {
                        val filePath = config["path"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'path' parameter")
                        val content = config["content"] as? String ?: ""
                        File(filePath).writeText(content)
                        TaskExecutionResult.Success(mapOf("message" to "File written successfully", "size" to content.length))
                    }
                    "copy" -> {
                        val sourcePath = config["source"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'source' parameter")
                        val destPath = config["destination"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'destination' parameter")
                        val sourceFile = File(sourcePath)
                        sourceFile.copyTo(File(destPath), overwrite = true)
                        TaskExecutionResult.Success(mapOf("message" to "File copied successfully", "size" to sourceFile.length()))
                    }
                    "delete" -> {
                        val filePath = config["path"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'path' parameter")
                        val deleted = File(filePath).delete()
                        if (deleted) {
                            TaskExecutionResult.Success(mapOf("message" to "File deleted successfully"))
                        } else {
                            TaskExecutionResult.Error("Failed to delete file")
                        }
                    }
                    "list" -> {
                        val dirPath = config["path"] as? String ?: return@withContext TaskExecutionResult.Error("Missing 'path' parameter")
                        val files = File(dirPath).listFiles()?.map { it.name } ?: emptyList()
                        TaskExecutionResult.Success(mapOf("files" to files, "count" to files.size))
                    }
                    else -> TaskExecutionResult.Error("Unsupported file operation: $operation")
                }
                
            } catch (e: Exception) {
                TaskExecutionResult.Error("File operation failed: ${e.message}")
            }
        }
    }
    
    /**
     * Execute database query task (placeholder)
     */
    private suspend fun executeDatabaseQuery(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        // TODO: Implement actual database query execution
        val config = task.configuration
        val query = config["query"] as? String ?: return TaskExecutionResult.Error("Missing 'query' parameter")
        
        return TaskExecutionResult.Success(mapOf(
            "message" to "Database query executed (placeholder)",
            "query" to query,
            "rows_affected" to 0
        ))
    }
    
    /**
     * Execute email notification task (placeholder)
     */
    private suspend fun executeEmailNotification(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        // TODO: Implement actual email sending
        val config = task.configuration
        val to = config["to"] as? String ?: return TaskExecutionResult.Error("Missing 'to' parameter")
        val subject = config["subject"] as? String ?: "Task Notification"
        val body = config["body"] as? String ?: "Task completed successfully"
        
        return TaskExecutionResult.Success(mapOf(
            "message" to "Email sent (placeholder)",
            "to" to to,
            "subject" to subject
        ))
    }
    
    /**
     * Execute webhook task
     */
    private suspend fun executeWebhook(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        // Similar to HTTP request but with webhook-specific handling
        return executeHttpRequest(task, inputData)
    }
    
    /**
     * Execute data processing task
     */
    private suspend fun executeDataProcessing(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        val config = task.configuration
        val operation = config["operation"] as? String ?: return TaskExecutionResult.Error("Missing 'operation' parameter")
        
        return when (operation) {
            "transform" -> {
                val data = inputData ?: emptyMap()
                val transformedData = data.mapValues { (_, value) -> 
                    if (value is String) value.uppercase() else value
                }
                TaskExecutionResult.Success(mapOf("transformed_data" to transformedData))
            }
            "aggregate" -> {
                val numbers = inputData?.values?.filterIsInstance<Number>() ?: emptyList()
                val sum = numbers.sumOf { it.toDouble() }
                val avg = if (numbers.isNotEmpty()) sum / numbers.size else 0.0
                TaskExecutionResult.Success(mapOf("sum" to sum, "average" to avg, "count" to numbers.size))
            }
            "filter" -> {
                val data = inputData ?: emptyMap()
                val filteredData = data.filterValues { it != null }
                TaskExecutionResult.Success(mapOf("filtered_data" to filteredData))
            }
            else -> TaskExecutionResult.Error("Unsupported data processing operation: $operation")
        }
    }
    
    /**
     * Execute backup task
     */
    private suspend fun executeBackupTask(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        val config = task.configuration
        val source = config["source"] as? String ?: return TaskExecutionResult.Error("Missing 'source' parameter")
        val destination = config["destination"] as? String ?: return TaskExecutionResult.Error("Missing 'destination' parameter")
        
        return try {
            // Simple file/directory backup
            val sourceFile = File(source)
            val destFile = File(destination)
            
            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(destFile, overwrite = true)
            } else {
                sourceFile.copyTo(destFile, overwrite = true)
            }
            
            TaskExecutionResult.Success(mapOf(
                "message" to "Backup completed successfully",
                "source" to source,
                "destination" to destination,
                "size" to sourceFile.length()
            ))
        } catch (e: Exception) {
            TaskExecutionResult.Error("Backup failed: ${e.message}")
        }
    }
    
    /**
     * Execute monitoring check task
     */
    private suspend fun executeMonitoringCheck(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        val config = task.configuration
        val checkType = config["check_type"] as? String ?: return TaskExecutionResult.Error("Missing 'check_type' parameter")
        
        return when (checkType) {
            "disk_space" -> {
                val path = config["path"] as? String ?: "/"
                val file = File(path)
                val freeSpace = file.freeSpace
                val totalSpace = file.totalSpace
                val usedPercentage = ((totalSpace - freeSpace).toDouble() / totalSpace * 100).toInt()
                
                TaskExecutionResult.Success(mapOf(
                    "free_space" to freeSpace,
                    "total_space" to totalSpace,
                    "used_percentage" to usedPercentage,
                    "status" to if (usedPercentage > 90) "critical" else if (usedPercentage > 80) "warning" else "ok"
                ))
            }
            "memory_usage" -> {
                val runtime = Runtime.getRuntime()
                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val usedMemory = totalMemory - freeMemory
                val usedPercentage = (usedMemory.toDouble() / totalMemory * 100).toInt()
                
                TaskExecutionResult.Success(mapOf(
                    "total_memory" to totalMemory,
                    "used_memory" to usedMemory,
                    "free_memory" to freeMemory,
                    "used_percentage" to usedPercentage,
                    "status" to if (usedPercentage > 90) "critical" else if (usedPercentage > 80) "warning" else "ok"
                ))
            }
            "service_health" -> {
                val url = config["url"] as? String ?: return TaskExecutionResult.Error("Missing 'url' parameter for service health check")
                // Perform HTTP health check
                executeHttpRequest(task.copy(configuration = mapOf("url" to url, "method" to "GET")), inputData)
            }
            else -> TaskExecutionResult.Error("Unsupported monitoring check type: $checkType")
        }
    }
    
    /**
     * Execute cleanup task
     */
    private suspend fun executeCleanupTask(task: Task, inputData: Map<String, Any>?): TaskExecutionResult {
        val config = task.configuration
        val cleanupType = config["cleanup_type"] as? String ?: return TaskExecutionResult.Error("Missing 'cleanup_type' parameter")
        
        return when (cleanupType) {
            "old_files" -> {
                val directory = config["directory"] as? String ?: return TaskExecutionResult.Error("Missing 'directory' parameter")
                val maxAge = config["max_age_days"] as? Int ?: 30
                val pattern = config["pattern"] as? String ?: "*"
                
                val dir = File(directory)
                val cutoffTime = System.currentTimeMillis() - (maxAge * 24 * 60 * 60 * 1000L)
                val deletedFiles = mutableListOf<String>()
                
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime && file.name.matches(Regex(pattern.replace("*", ".*")))) {
                        if (file.delete()) {
                            deletedFiles.add(file.name)
                        }
                    }
                }
                
                TaskExecutionResult.Success(mapOf(
                    "deleted_files" to deletedFiles,
                    "count" to deletedFiles.size,
                    "directory" to directory
                ))
            }
            "temp_files" -> {
                val tempDir = System.getProperty("java.io.tmpdir")
                val dir = File(tempDir)
                val deletedFiles = mutableListOf<String>()
                
                dir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("tmp") || file.name.endsWith(".tmp")) {
                        if (file.delete()) {
                            deletedFiles.add(file.name)
                        }
                    }
                }
                
                TaskExecutionResult.Success(mapOf(
                    "deleted_files" to deletedFiles,
                    "count" to deletedFiles.size,
                    "directory" to tempDir
                ))
            }
            else -> TaskExecutionResult.Error("Unsupported cleanup type: $cleanupType")
        }
    }
    
    // Validation methods for different task types
    private fun validateHttpRequestConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("url")) {
            errors.add("HTTP request task requires 'url' parameter")
        }
        val method = config["method"] as? String
        if (method != null && method.uppercase() !in listOf("GET", "POST", "PUT", "DELETE", "PATCH")) {
            errors.add("Invalid HTTP method: $method")
        }
    }
    
    private fun validateShellCommandConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("command")) {
            errors.add("Shell command task requires 'command' parameter")
        }
    }
    
    private fun validateFileOperationConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("operation")) {
            errors.add("File operation task requires 'operation' parameter")
        }
        val operation = config["operation"] as? String
        if (operation !in listOf("read", "write", "copy", "delete", "list")) {
            errors.add("Invalid file operation: $operation")
        }
    }
    
    private fun validateDatabaseQueryConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("query")) {
            errors.add("Database query task requires 'query' parameter")
        }
    }
    
    private fun validateEmailNotificationConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("to")) {
            errors.add("Email notification task requires 'to' parameter")
        }
    }
    
    private fun validateWebhookConfig(config: Map<String, Any>, errors: MutableList<String>) {
        validateHttpRequestConfig(config, errors)
    }
    
    private fun validateDataProcessingConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("operation")) {
            errors.add("Data processing task requires 'operation' parameter")
        }
    }
    
    private fun validateBackupTaskConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("source")) {
            errors.add("Backup task requires 'source' parameter")
        }
        if (!config.containsKey("destination")) {
            errors.add("Backup task requires 'destination' parameter")
        }
    }
    
    private fun validateMonitoringCheckConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("check_type")) {
            errors.add("Monitoring check task requires 'check_type' parameter")
        }
    }
    
    private fun validateCleanupTaskConfig(config: Map<String, Any>, errors: MutableList<String>) {
        if (!config.containsKey("cleanup_type")) {
            errors.add("Cleanup task requires 'cleanup_type' parameter")
        }
    }
    
    companion object {
        val SUPPORTED_TASK_TYPES = setOf(
            "http_request",
            "shell_command",
            "file_operation",
            "database_query",
            "email_notification",
            "webhook",
            "data_processing",
            "backup_task",
            "monitoring_check",
            "cleanup_task"
        )
    }
}

/**
 * Task validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)