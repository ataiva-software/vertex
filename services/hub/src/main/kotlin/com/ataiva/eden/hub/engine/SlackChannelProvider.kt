package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import com.ataiva.eden.hub.stubs.MeterRegistry
import com.ataiva.eden.hub.stubs.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Slack channel provider using Slack API
 */
class SlackChannelProvider(
    private val config: SlackProviderConfig = loadSlackConfig(),
    private val meterRegistry: MeterRegistry? = null
) : NotificationChannelProvider {
    
    private val logger = LoggerFactory.getLogger(SlackChannelProvider::class.java)
    
    // Maximum number of retry attempts for sending messages
    private val maxRetries = 3
    
    // Base delay for exponential backoff (in milliseconds)
    private val baseDelayMs = 1000L
    
    // Rate limiting - max messages per minute to avoid Slack API rate limits
    private val maxMessagesPerMinute = 50
    private val rateLimiter = RateLimiter(maxMessagesPerMinute, 60_000)
    
    /**
     * Create HTTP client for Slack API calls
     * This method can be overridden in tests
     */
    protected open fun createHttpClient(): HttpClient {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }
    
    private val httpClient by lazy { createHttpClient() }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        val startTime = System.currentTimeMillis()
        logger.info("Sending Slack notification with priority ${priority.name} to ${recipients.size} recipients")
        
        return try {
            val slackRecipients = recipients.filter {
                it.type == RecipientType.SLACK_CHANNEL || it.type == RecipientType.SLACK_USER
            }
            
            if (slackRecipients.isEmpty()) {
                logger.warn("No Slack recipients found in notification request")
                return ChannelDeliveryResult(false, "No Slack recipients found")
            }
            
            // Check rate limit
            if (!rateLimiter.tryAcquire(slackRecipients.size)) {
                logger.warn("Rate limit exceeded for Slack messages")
                return ChannelDeliveryResult(
                    success = false,
                    error = "Rate limit exceeded, please try again later",
                    details = mapOf("rateLimitPerMinute" to maxMessagesPerMinute)
                )
            }
            
            // Send messages to all recipients with retry
            val results = withContext(Dispatchers.IO) {
                slackRecipients.map { recipient ->
                    sendSlackMessageWithRetry(recipient, subject, body, priority)
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            // Check if any messages failed
            val failedMessages = results.filter { !it.first }
            if (failedMessages.isNotEmpty()) {
                logger.warn("Failed to send messages to ${failedMessages.size} of ${results.size} recipients")
                recordMetrics("partial_failure", duration, results.size, failedMessages.size, priority)
                
                return ChannelDeliveryResult(
                    success = false,
                    error = "Failed to send messages to some recipients: ${failedMessages.map { it.second }.joinToString(", ")}",
                    details = mapOf(
                        "successCount" to (results.size - failedMessages.size),
                        "failureCount" to failedMessages.size,
                        "errors" to failedMessages.map { it.second },
                        "durationMs" to duration
                    )
                )
            }
            
            logger.info("Successfully sent Slack messages to all ${results.size} recipients in ${duration}ms")
            recordMetrics("success", duration, results.size, 0, priority)
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "recipients" to slackRecipients.map { it.address },
                    "messageCount" to results.size,
                    "provider" to "slack-api",
                    "durationMs" to duration
                )
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Unexpected error during Slack delivery: ${e.message}", e)
            recordMetrics("error", duration, recipients.size, recipients.size, priority)
            
            ChannelDeliveryResult(
                false,
                "Slack delivery failed: ${e.message}",
                details = mapOf(
                    "errorType" to e.javaClass.simpleName,
                    "durationMs" to duration
                )
            )
        }
    }
    
    /**
     * Record metrics for Slack message sending
     */
    private fun recordMetrics(
        result: String,
        durationMs: Long,
        recipientCount: Int,
        failureCount: Int,
        priority: NotificationPriority
    ) {
        meterRegistry?.let { registry: MeterRegistry ->
            val tags = listOf(
                Tag.of("result", result),
                Tag.of("provider", "slack-api"),
                Tag.of("priority", priority.name)
            )
            
            // Count messages sent
            registry.counter("slack.send.count", tags).increment()
            
            // Record duration
            registry.timer("slack.send.duration", tags).record(durationMs, TimeUnit.MILLISECONDS)
            
            // Record recipient count
            registry.gauge("slack.recipients", tags, recipientCount.toDouble())
            
            // Record failure count
            registry.gauge("slack.failures", tags, failureCount.toDouble())
        }
    }
    
    /**
     * Send Slack message with retry logic
     */
    private suspend fun sendSlackMessageWithRetry(
        recipient: NotificationRecipient,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): Pair<Boolean, String> {
        var lastError = ""
        
        for (attempt in 0 until maxRetries) {
            try {
                if (attempt > 0) {
                    logger.info("Retry attempt ${attempt + 1}/$maxRetries for sending Slack message to ${recipient.address}")
                    // Exponential backoff with jitter
                    val delayMs = (baseDelayMs * Math.pow(2.0, attempt.toDouble())).toLong() +
                                 (Math.random() * baseDelayMs).toLong()
                    delay(delayMs)
                }
                
                val result = sendSlackMessage(recipient, subject, body, priority)
                if (result.first) {
                    return result
                } else {
                    lastError = result.second
                    
                    // Don't retry for certain types of errors
                    if (result.second.contains("channel_not_found") ||
                        result.second.contains("user_not_found") ||
                        result.second.contains("not_in_channel") ||
                        result.second.contains("invalid_auth")) {
                        logger.error("Not retrying due to permanent error: ${result.second}")
                        return result
                    }
                }
            } catch (e: Exception) {
                lastError = "Error sending message: ${e.message}"
                logger.warn("Slack message sending failed on attempt ${attempt + 1}/$maxRetries: ${e.message}")
            }
        }
        
        // All retries failed
        logger.error("Slack message sending failed after $maxRetries attempts to ${recipient.address}")
        return Pair(false, "Failed after $maxRetries attempts: $lastError")
    }
    
    private suspend fun sendSlackMessage(
        recipient: NotificationRecipient,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): Pair<Boolean, String> {
        try {
            // Determine channel or user ID
            val target = recipient.address
            logger.debug("Preparing Slack message for ${recipient.type}: $target")
            
            // Validate target format
            if (!isValidSlackTarget(target, recipient.type)) {
                logger.warn("Invalid Slack target format: ${recipient.type} - $target")
                return Pair(false, "Invalid Slack target format")
            }
            
            // Format message with blocks for better rendering
            val blocks = mutableListOf<Map<String, Any>>()
            
            // Add header if subject is provided
            if (!subject.isNullOrBlank()) {
                blocks.add(mapOf(
                    "type" to "header",
                    "text" to mapOf(
                        "type" to "plain_text",
                        "text" to subject
                    )
                ))
            }
            
            // Add main text
            blocks.add(mapOf(
                "type" to "section",
                "text" to mapOf(
                    "type" to "mrkdwn",
                    "text" to body
                )
            ))
            
            // Add priority context if not normal
            if (priority != NotificationPriority.NORMAL) {
                val priorityEmoji = when (priority) {
                    NotificationPriority.HIGH -> ":warning:"
                    NotificationPriority.URGENT -> ":rotating_light:"
                    else -> ":information_source:"
                }
                
                blocks.add(mapOf(
                    "type" to "context",
                    "elements" to listOf(
                        mapOf(
                            "type" to "mrkdwn",
                            "text" to "$priorityEmoji *Priority: ${priority.name}*"
                        )
                    )
                ))
            }
            
            // Add timestamp for tracking
            val timestamp = System.currentTimeMillis()
            blocks.add(mapOf(
                "type" to "context",
                "elements" to listOf(
                    mapOf(
                        "type" to "mrkdwn",
                        "text" to "_Message ID: ${timestamp}_"
                    )
                )
            ))
            
            // Create message payload
            val message = mapOf(
                "channel" to target,
                "blocks" to blocks,
                "text" to (subject ?: body), // Fallback text
                "unfurl_links" to false,     // Don't expand links by default
                "metadata" to mapOf(         // Add metadata for tracking
                    "event_type" to "eden_notification",
                    "event_payload" to mapOf(
                        "priority" to priority.name,
                        "message_id" to timestamp.toString()
                    )
                )
            )
            
            logger.debug("Sending message to Slack API")
            
            // Send message to Slack API
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://slack.com/api/chat.postMessage"))
                .header("Authorization", "Bearer ${config.botToken}")
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(message)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            // Parse response
            val responseBody = response.body()
            val responseJson = json.decodeFromString<Map<String, Any>>(responseBody)
            val success = responseJson["ok"] as? Boolean ?: false
            
            return if (success) {
                val ts = responseJson["ts"]?.toString() ?: ""
                val channel = responseJson["channel"]?.toString() ?: ""
                logger.debug("Message sent successfully to $target with ts: $ts")
                Pair(true, "Message sent successfully")
            } else {
                val error = responseJson["error"] as? String ?: "Unknown error"
                logger.warn("Failed to send Slack message: $error")
                Pair(false, "Failed to send message: $error")
            }
        } catch (e: Exception) {
            logger.error("Error sending Slack message: ${e.message}", e)
            return Pair(false, "Error sending message: ${e.message}")
        }
    }
    
    /**
     * Validate Slack target format
     */
    private fun isValidSlackTarget(target: String, type: RecipientType): Boolean {
        return when (type) {
            RecipientType.SLACK_CHANNEL -> {
                // Channel format: C01234567 or #channel-name
                target.startsWith("C") && target.length >= 9 || target.startsWith("#")
            }
            RecipientType.SLACK_USER -> {
                // User format: U01234567 or @username
                target.startsWith("U") && target.length >= 9 || target.startsWith("@")
            }
            else -> false
        }
    }
    
    companion object {
        /**
         * Load Slack configuration from environment or configuration file
         */
        fun loadSlackConfig(): SlackProviderConfig {
            val logger = LoggerFactory.getLogger(SlackChannelProvider::class.java)
            logger.debug("Loading Slack configuration")
            
            // Load from environment variables
            val botToken = System.getenv("SLACK_BOT_TOKEN")
            val signingSecret = System.getenv("SLACK_SIGNING_SECRET")
            val appToken = System.getenv("SLACK_APP_TOKEN")
            
            // Check if configuration is available
            if (botToken.isNullOrBlank()) {
                logger.warn("SLACK_BOT_TOKEN environment variable not set")
            }
            
            if (signingSecret.isNullOrBlank()) {
                logger.warn("SLACK_SIGNING_SECRET environment variable not set")
            }
            
            if (appToken.isNullOrBlank()) {
                logger.warn("SLACK_APP_TOKEN environment variable not set")
            }
            
            // In a production environment, we would load from a secure configuration store
            // and validate the configuration before returning
            return SlackProviderConfig(
                botToken = botToken ?: "",
                signingSecret = signingSecret ?: "",
                appToken = appToken ?: "",
                defaultChannel = System.getenv("SLACK_DEFAULT_CHANNEL") ?: "general"
            )
        }
    }
}

/**
 * Slack provider configuration
 */
data class SlackProviderConfig(
    val botToken: String,
    val signingSecret: String,
    val appToken: String,
    val defaultChannel: String
)

/**
 * Simple rate limiter for controlling Slack API request rate
 */
private class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val requestTimestamps = ArrayDeque<Long>()
    private val lock = Any()
    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)
    
    /**
     * Try to acquire permits for sending messages
     * @param permits Number of permits to acquire (typically number of recipients)
     * @return true if permits were acquired, false if rate limit was exceeded
     */
    fun tryAcquire(permits: Int = 1): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val windowStart = now - windowMs
            
            // Remove expired timestamps
            while (requestTimestamps.isNotEmpty() && requestTimestamps.first() <= windowStart) {
                requestTimestamps.removeFirst()
            }
            
            // Check if we can acquire permits
            if (requestTimestamps.size + permits <= maxRequests) {
                // Add timestamps for each permit
                repeat(permits) {
                    requestTimestamps.addLast(now)
                }
                return true
            }
            
            logger.warn("Rate limit exceeded: ${requestTimestamps.size} requests in window, limit is $maxRequests")
            return false
        }
    }
}