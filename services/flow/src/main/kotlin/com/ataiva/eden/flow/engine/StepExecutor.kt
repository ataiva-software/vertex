package com.ataiva.eden.flow.engine

import com.ataiva.eden.database.repositories.WorkflowStep
import com.ataiva.eden.flow.service.ExpressionEvaluator
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
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory
import java.io.FileInputStream

/**
 * Step executor for running individual workflow steps
 */
class StepExecutor {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val logger = LoggerFactory.getLogger(StepExecutor::class.java)
    
    // Email configuration properties
    private val emailProperties = Properties()
    
    // Slack configuration properties
    private val slackProperties = Properties()
    
    init {
        try {
            // Load email configuration
            val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.properties"
            val props = Properties()
            FileInputStream(configPath).use { props.load(it) }
            
            // Extract email properties
            emailProperties.setProperty("mail.smtp.host", props.getProperty("email.smtp.host", "smtp.example.com"))
            emailProperties.setProperty("mail.smtp.port", props.getProperty("email.smtp.port", "587"))
            emailProperties.setProperty("mail.smtp.auth", props.getProperty("email.smtp.auth", "true"))
            emailProperties.setProperty("mail.smtp.starttls.enable", props.getProperty("email.smtp.starttls.enable", "true"))
            
            // Extract Slack properties
            slackProperties.setProperty("webhook.default-channel", props.getProperty("slack.webhook.default-channel", "#general"))
            slackProperties.setProperty("webhook.username", props.getProperty("slack.webhook.username", "Eden Flow"))
            slackProperties.setProperty("webhook.icon-emoji", props.getProperty("slack.webhook.icon-emoji", ":gear:"))
            slackProperties.setProperty("webhook.retry-count", props.getProperty("slack.webhook.retry-count", "3"))
            
            logger.info("Configuration loaded successfully")
        } catch (e: Exception) {
            logger.error("Failed to load configuration: ${e.message}")
        }
    }
    
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
     * Execute email notification step with actual email sending
     */
    private suspend fun executeEmailNotification(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = step.inputData ?: emptyMap()
                val to = config["to"] as? String ?: return@withContext StepResult.Error(
                    "Email notification step missing 'to' parameter",
                    startTime,
                    Clock.System.now()
                )
                val subject = config["subject"] as? String ?: "Workflow Notification"
                val body = config["body"] as? String ?: "This is an automated notification from Eden Flow."
                val isHtml = config["html"] as? Boolean ?: false
                
                // Get email configuration
                val host = emailProperties.getProperty("mail.smtp.host", "smtp.example.com")
                val port = emailProperties.getProperty("mail.smtp.port", "587").toInt()
                val username = System.getenv("SMTP_USERNAME") ?: emailProperties.getProperty("mail.smtp.username", "")
                val password = System.getenv("SMTP_PASSWORD") ?: emailProperties.getProperty("mail.smtp.password", "")
                val fromAddress = emailProperties.getProperty("email.from.address", "notifications@eden.example.com")
                val fromName = emailProperties.getProperty("email.from.name", "Eden Notifications")
                
                // Create mail session
                val session = Session.getInstance(emailProperties, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(username, password)
                    }
                })
                
                // Create message
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(fromAddress, fromName))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                message.subject = subject
                
                if (isHtml) {
                    // HTML email
                    val multipart = MimeMultipart("alternative")
                    
                    // Plain text part
                    val textPart = MimeBodyPart()
                    textPart.setText(body.replace("<[^>]*>".toRegex(), ""), "utf-8")
                    
                    // HTML part
                    val htmlPart = MimeBodyPart()
                    htmlPart.setContent(body, "text/html; charset=utf-8")
                    
                    multipart.addBodyPart(textPart)
                    multipart.addBodyPart(htmlPart)
                    message.setContent(multipart)
                } else {
                    // Plain text email
                    message.setText(body, "utf-8")
                }
                
                // Send message
                Transport.send(message)
                
                logger.info("Email sent successfully to $to with subject: $subject")
                
                StepResult.Success(
                    mapOf(
                        "message" to "Email sent successfully",
                        "to" to to,
                        "subject" to subject,
                        "timestamp" to Clock.System.now().toString()
                    ),
                    startTime,
                    Clock.System.now()
                )
                
            } catch (e: Exception) {
                logger.error("Failed to send email: ${e.message}", e)
                StepResult.Error(
                    "Failed to send email: ${e.message}",
                    startTime,
                    Clock.System.now()
                )
            }
        }
    }
    
    /**
     * Execute Slack notification step with actual webhook implementation
     */
    private suspend fun executeSlackNotification(
        step: WorkflowStep,
        inputData: Map<String, Any>?,
        startTime: Instant
    ): StepResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = step.inputData ?: emptyMap()
                val channel = config["channel"] as? String ?: "#general"
                val message = config["message"] as? String ?: "Workflow notification"
                val webhookUrl = config["webhook_url"] as? String ?: System.getenv("SLACK_WEBHOOK_URL") ?:
                    return@withContext StepResult.Error(
                        "Slack notification step missing 'webhook_url' parameter and no SLACK_WEBHOOK_URL environment variable found",
                        startTime,
                        Clock.System.now()
                    )
                
                val username = config["username"] as? String ?: slackProperties.getProperty("webhook.username", "Eden Flow")
                val iconEmoji = config["icon_emoji"] as? String ?: slackProperties.getProperty("webhook.icon-emoji", ":gear:")
                
                // Create payload
                val payload = """
                    {
                        "channel": "$channel",
                        "username": "$username",
                        "icon_emoji": "$iconEmoji",
                        "text": ${escapeJsonString(message)}
                    }
                """.trimIndent()
                
                // Build request
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()
                
                // Send request
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() in 200..299) {
                    logger.info("Slack notification sent successfully to $channel")
                    
                    StepResult.Success(
                        mapOf(
                            "message" to "Slack notification sent successfully",
                            "channel" to channel,
                            "status_code" to response.statusCode()
                        ),
                        startTime,
                        Clock.System.now()
                    )
                } else {
                    logger.error("Failed to send Slack notification: HTTP ${response.statusCode()}, ${response.body()}")
                    
                    StepResult.Error(
                        "Failed to send Slack notification: HTTP ${response.statusCode()}, ${response.body()}",
                        startTime,
                        Clock.System.now()
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to send Slack notification: ${e.message}", e)
                
                StepResult.Error(
                    "Failed to send Slack notification: ${e.message}",
                    startTime,
                    Clock.System.now()
                )
            }
        }
    }
    
    /**
     * Escape a string for JSON
     */
    private fun escapeJsonString(input: String): String {
        val escaped = input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
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
     * Evaluate condition expression using the ExpressionEvaluator service
     */
    private fun evaluateCondition(condition: String, inputData: Map<String, Any>?): Boolean {
        try {
            // Create bindings from input data
            val bindings = mutableMapOf<String, Any>()
            
            // Add input data to bindings
            inputData?.forEach { (key, value) ->
                bindings[key] = value
            }
            
            // Add system variables
            bindings["now"] = Clock.System.now().toString()
            bindings["timestamp"] = System.currentTimeMillis()
            
            // Create expression with variable substitution
            val processedExpression = substituteVariables(condition, bindings)
            
            // Use the ExpressionEvaluator service
            val expressionEvaluator = ExpressionEvaluator()
            val result = expressionEvaluator.evaluate(processedExpression, bindings)
            
            return result.getOrElse { false }
        } catch (e: Exception) {
            logger.error("Error evaluating condition: $condition", e)
            return false
        }
    }
    
    /**
     * Substitute variables in expression
     */
    private fun substituteVariables(expression: String, variables: Map<String, Any>): String {
        var result = expression
        
        // Replace ${variable} with actual values
        val pattern = Regex("\\$\\{([^}]+)}")
        val matches = pattern.findAll(expression)
        
        for (match in matches) {
            val variableName = match.groupValues[1]
            val value = variables[variableName]
            
            if (value != null) {
                val replacement = when (value) {
                    is String -> "\"$value\""
                    else -> value.toString()
                }
                
                result = result.replace("\${$variableName}", replacement)
            }
        }
        
        return result
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