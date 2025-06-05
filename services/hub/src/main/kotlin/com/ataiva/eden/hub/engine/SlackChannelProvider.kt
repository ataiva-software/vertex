package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Slack channel provider using Slack API
 */
class SlackChannelProvider(
    private val config: SlackProviderConfig = loadSlackConfig()
) : NotificationChannelProvider {
    
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
        return try {
            val slackRecipients = recipients.filter { 
                it.type == RecipientType.SLACK_CHANNEL || it.type == RecipientType.SLACK_USER 
            }
            
            if (slackRecipients.isEmpty()) {
                return ChannelDeliveryResult(false, "No Slack recipients found")
            }
            
            // Send messages to all recipients
            val results = withContext(Dispatchers.IO) {
                slackRecipients.map { recipient ->
                    sendSlackMessage(recipient, subject, body, priority)
                }
            }
            
            // Check if any messages failed
            val failedMessages = results.filter { !it.first }
            if (failedMessages.isNotEmpty()) {
                return ChannelDeliveryResult(
                    success = false,
                    error = "Failed to send messages to some recipients: ${failedMessages.map { it.second }.joinToString(", ")}",
                    details = mapOf(
                        "successCount" to (results.size - failedMessages.size),
                        "failureCount" to failedMessages.size,
                        "errors" to failedMessages.map { it.second }
                    )
                )
            }
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "recipients" to slackRecipients.map { it.address },
                    "messageCount" to results.size,
                    "provider" to "slack-api"
                )
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(false, "Slack delivery failed: ${e.message}")
        }
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
            
            // Create message payload
            val message = mapOf(
                "channel" to target,
                "blocks" to blocks,
                "text" to (subject ?: body) // Fallback text
            )
            
            // Send message to Slack API
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://slack.com/api/chat.postMessage"))
                .header("Authorization", "Bearer ${config.botToken}")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(message)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            // Parse response
            val responseBody = response.body()
            val responseJson = json.decodeFromString<Map<String, Any>>(responseBody)
            val success = responseJson["ok"] as? Boolean ?: false
            
            return if (success) {
                Pair(true, "Message sent successfully")
            } else {
                val error = responseJson["error"] as? String ?: "Unknown error"
                Pair(false, "Failed to send message: $error")
            }
        } catch (e: Exception) {
            return Pair(false, "Error sending message: ${e.message}")
        }
    }
    
    companion object {
        /**
         * Load Slack configuration from environment or configuration file
         */
        fun loadSlackConfig(): SlackProviderConfig {
            // In a real implementation, this would load from environment variables or a configuration file
            val botToken = System.getenv("SLACK_BOT_TOKEN") ?: "xoxb-your-token"
            val signingSecret = System.getenv("SLACK_SIGNING_SECRET") ?: "your-signing-secret"
            val appToken = System.getenv("SLACK_APP_TOKEN") ?: "xapp-your-token"
            
            return SlackProviderConfig(
                botToken = botToken,
                signingSecret = signingSecret,
                appToken = appToken
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
    val appToken: String
)