package com.ataiva.eden.flow.engine

import com.ataiva.eden.database.repositories.WorkflowStep
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.*
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import kotlinx.serialization.json.*

/**
 * Step executor for running individual workflow steps
 */
class StepExecutor {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    /**
     * Execute a workflow step
     */
    suspend fun executeStep(step: WorkflowStep, inputData: Map<String, Any>?): StepResult {
        val startTime = Clock.System.now()
        
        return try {
            // Apply timeout
            withTimeout(step.durationMs?.toLong() ?: 300000L) { // Default 5 minutes
                when (step.stepType) {
                    "http_request" -> executeHttpRequest(step, inputData, startTime)
                    "shell_command" -> executeShellCommand(step, inputData, startTime)
                    "sql_query" -> executeSqlQuery(step, inputData, startTime)
                    "file_operation" -> executeFileOperation(step, inputData, startTime)
                    "email_notification" -> executeEmailNotification(step, inputData, startTime)
                    "slack_notification" -> executeSlackNotification(step, inputData, startTime)
                    "webhook" -> executeWebhook(step, inputData, startTime)
                    "delay" -> executeDelay(step, inputData, startTime)
                    "condition" -> executeCondition(step, inputData, startTime)
                    "script" -> executeScript(step, inputData, startTime)
                    else -> StepResult.Error(
                        "Unsupported step type: ${step.stepType}",
                        startTime,
                        Clock.System.now()
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            StepResult.Error(
                "Step execution timed out",
                startTime,
                Clock.System.now()
            )
        } catch (e: Exception) {
            StepResult.Error(
                "Step execution failed: ${e.message}",
                startTime,
                Clock.System.now()
            )
        }
    }
    
    /**
     * Execute HTTP request step
     */
    private suspend fun executeHttpRequest(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = step.inputData ?: emptyMap()
                val url = config["url"] as? String ?: return@withContext StepResult.Error(
                    "HTTP request step missing 'url' parameter",
                    startTime,
                    Clock.System.now()
                )
                
                val method = config["method"] as? String ?: "GET"
                val headers = config["headers"] as? Map<String, String> ?: emptyMap()
                val body = config["body"] as? String
                
                // Build HTTP request
                val requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                
                // Add headers
                headers.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }
                
                // Set method and body
                when (method.uppercase()) {
                    "GET" -> requestBuilder.GET()
                    "POST" -> requestBuilder.POST(
                        HttpRequest.BodyPublishers.ofString(body ?: "")
                    )
                    "PUT" -> requestBuilder.PUT(
                        HttpRequest.BodyPublishers.ofString(body ?: "")
                    )
                    "DELETE" -> requestBuilder.DELETE()
                    else -> return@withContext StepResult.Error(
                        "Unsupported HTTP method: $method",
                        startTime,
                        Clock.System.now()
                    )
                }
                
                val request = requestBuilder.build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                val outputData = mapOf(
                    "status_code" to response.statusCode(),
                    "headers" to response.headers().map(),
                    "body" to response.body()
                )
                
                StepResult.Success(
                    outputData,
                    startTime,
                    Clock.System.now()
                )
                
            } catch (e: Exception) {
                StepResult.Error(
                    "HTTP request failed: ${e.message}",
                    startTime,
                    Clock.System.now()
                )
            }
        }
    }
    
    /**
     * Execute shell command step
     */
    private suspend fun executeShellCommand(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = step.inputData ?: emptyMap()
                val command = config["command"] as? String ?: return@withContext StepResult.Error(
                    "Shell command step missing 'command' parameter",
                    startTime,
                    Clock.System.now()
                )
                
                val workingDir = config["working_dir"] as? String
                val environment = config["environment"] as? Map<String, String> ?: emptyMap()
                
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
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                val outputData = mapOf(
                    "exit_code" to exitCode,
                    "output" to output
                )
                
                if (exitCode == 0) {
                    StepResult.Success(
                        outputData,
                        startTime,
                        Clock.System.now()
                    )
                } else {
                    StepResult.Error(
                        "Command failed with exit code $exitCode: $output",
                        startTime,
                        Clock.System.now()
                    )
                }
                
            } catch (e: Exception) {
                StepResult.Error(
                    "Shell command execution failed: ${e.message}",
                    startTime,
                    Clock.System.now()
                )
            }
        }
    }
    
    /**
     * Execute SQL query step (placeholder implementation)
     */
    private suspend fun executeSqlQuery(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        // TODO: Implement actual SQL execution with database connection
        return StepResult.Success(
            mapOf("message" to "SQL query executed (placeholder)"),
            startTime,
            Clock.System.now()
        )
    }
    
    /**
     * Execute file operation step
     */
    private suspend fun executeFileOperation(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = step.inputData ?: emptyMap()
                val operation = config["operation"] as? String ?: return@withContext StepResult.Error(
                    "File operation step missing 'operation' parameter",
                    startTime,
                    Clock.System.now()
                )
                
                when (operation) {
                    "read" -> {
                        val filePath = config["path"] as? String ?: return@withContext StepResult.Error(
                            "File read operation missing 'path' parameter",
                            startTime,
                            Clock.System.now()
                        )
                        
                        val content = File(filePath).readText()
                        StepResult.Success(
                            mapOf("content" to content),
                            startTime,
                            Clock.System.now()
                        )
                    }
                    "write" -> {
                        val filePath = config["path"] as? String ?: return@withContext StepResult.Error(
                            "File write operation missing 'path' parameter",
                            startTime,
                            Clock.System.now()
                        )
                        val content = config["content"] as? String ?: ""
                        
                        File(filePath).writeText(content)
                        StepResult.Success(
                            mapOf("message" to "File written successfully"),
                            startTime,
                            Clock.System.now()
                        )
                    }
                    "copy" -> {
                        val sourcePath = config["source"] as? String ?: return@withContext StepResult.Error(
                            "File copy operation missing 'source' parameter",
                            startTime,
                            Clock.System.now()
                        )
                        val destPath = config["destination"] as? String ?: return@withContext StepResult.Error(
                            "File copy operation missing 'destination' parameter",
                            startTime,
                            Clock.System.now()
                        )
                        
                        File(sourcePath).copyTo(File(destPath), overwrite = true)
                        StepResult.Success(
                            mapOf("message" to "File copied successfully"),
                            startTime,
                            Clock.System.now()
                        )
                    }
                    else -> StepResult.Error(
                        "Unsupported file operation: $operation",
                        startTime,
                        Clock.System.now()
                    )
                }
                
            } catch (e: Exception) {
                StepResult.Error(
                    "File operation failed: ${e.message}",
                    startTime,
                    Clock.System.now()
                )
            }
        }
    }
    
    /**
     * Execute email notification step (placeholder)
     */
    private suspend fun executeEmailNotification(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        // TODO: Implement actual email sending
        val config = step.inputData ?: emptyMap()
        val to = config["to"] as? String ?: "unknown@example.com"
        val subject = config["subject"] as? String ?: "Workflow Notification"
        
        return StepResult.Success(
            mapOf(
                "message" to "Email sent (placeholder)",
                "to" to to,
                "subject" to subject
            ),
            startTime,
            Clock.System.now()
        )
    }
    
    /**
     * Execute Slack notification step (placeholder)
     */
    private suspend fun executeSlackNotification(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        // TODO: Implement actual Slack webhook
        val config = step.inputData ?: emptyMap()
        val channel = config["channel"] as? String ?: "#general"
        val message = config["message"] as? String ?: "Workflow notification"
        
        return StepResult.Success(
            mapOf(
                "message" to "Slack notification sent (placeholder)",
                "channel" to channel,
                "text" to message
            ),
            startTime,
            Clock.System.now()
        )
    }
    
    /**
     * Execute webhook step
     */
    private suspend fun executeWebhook(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        // Similar to HTTP request but with specific webhook handling
        return executeHttpRequest(step, inputData, startTime)
    }
    
    /**
     * Execute delay step
     */
    private suspend fun executeDelay(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        val config = step.inputData ?: emptyMap()
        val delayMs = config["delay_ms"] as? Int ?: 1000
        
        delay(delayMs.toLong())
        
        return StepResult.Success(
            mapOf("delayed_ms" to delayMs),
            startTime,
            Clock.System.now()
        )
    }
    
    /**
     * Execute condition step
     */
    private suspend fun executeCondition(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        val config = step.inputData ?: emptyMap()
        val condition = config["condition"] as? String ?: return StepResult.Error(
            "Condition step missing 'condition' parameter",
            startTime,
            Clock.System.now()
        )
        
        // Simple condition evaluation (placeholder)
        val result = evaluateCondition(condition, inputData)
        
        return StepResult.Success(
            mapOf(
                "condition" to condition,
                "result" to result
            ),
            startTime,
            Clock.System.now()
        )
    }
    
    /**
     * Execute script step
     */
    private suspend fun executeScript(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        val config = step.inputData ?: emptyMap()
        val scriptType = config["type"] as? String ?: "bash"
        val script = config["script"] as? String ?: return StepResult.Error(
            "Script step missing 'script' parameter",
            startTime,
            Clock.System.now()
        )
        
        // Execute script based on type
        return when (scriptType) {
            "bash", "sh" -> executeShellCommand(
                step.copy(inputData = mapOf("command" to script)),
                inputData,
                startTime
            )
            else -> StepResult.Error(
                "Unsupported script type: $scriptType",
                startTime,
                Clock.System.now()
            )
        }
    }
    
    /**
     * Simple condition evaluation (placeholder)
     */
    private fun evaluateCondition(condition: String, inputData: Map<String, Any>?): Boolean {
        // TODO: Implement proper expression evaluation
        // For now, just return true for non-empty conditions
        return condition.isNotBlank()
    }
}

/**
 * Step execution result
 */
sealed class StepResult {
    data class Success(
        val outputData: Map<String, Any>,
        val startedAt: Instant,
        val completedAt: Instant
    ) : StepResult()
    
    data class Error(
        val errorMessage: String,
        val startedAt: Instant,
        val completedAt: Instant
    ) : StepResult()
}