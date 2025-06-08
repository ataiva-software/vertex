package com.ataiva.eden.flow.service

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import org.slf4j.LoggerFactory
import java.util.Properties
import java.io.FileInputStream

/**
 * Service for sending Slack notifications via webhooks
 */
class SlackService {
    private val logger = LoggerFactory.getLogger(SlackService::class.java)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val properties = Properties()
    
    init {
        try {
            // Load default properties
            properties.setProperty("webhook.default-channel", "#general")
            properties.setProperty("webhook.username", "Eden Flow")
            properties.setProperty("webhook.icon-emoji", ":gear:")
            properties.setProperty("webhook.retry-count", "3")
            properties.setProperty("webhook.connection-timeout", "10000")
            properties.setProperty("webhook.read-timeout", "10000")
            
            // Try to load from environment or config file
            val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.properties"
            try {
                val configFile = java.io.File(configPath)
                if (configFile.exists()) {
                    val fileProps = Properties()
                    fileProps.load(FileInputStream(configFile))
                    
                    // Override with values from config file
                    properties.setProperty("webhook.default-channel", fileProps.getProperty("slack.webhook.default-channel", properties.getProperty("webhook.default-channel")))
                    properties.setProperty("webhook.username", fileProps.getProperty("slack.webhook.username", properties.getProperty("webhook.username")))
                    properties.setProperty("webhook.icon-emoji", fileProps.getProperty("slack.webhook.icon-emoji", properties.getProperty("webhook.icon-emoji")))
                    properties.setProperty("webhook.retry-count", fileProps.getProperty("slack.webhook.retry-count", properties.getProperty("webhook.retry-count")))
                    
                    logger.info("Slack configuration loaded from $configPath")
                }
            } catch (e: Exception) {
                logger.warn("Failed to load Slack configuration from file: ${e.message}")
            }
            
            // Override with environment variables if present
            System.getenv("SLACK_DEFAULT_CHANNEL")?.let { properties.setProperty("webhook.default-channel", it) }
            System.getenv("SLACK_USERNAME")?.let { properties.setProperty("webhook.username", it) }
            System.getenv("SLACK_ICON_EMOJI")?.let { properties.setProperty("webhook.icon-emoji", it) }
            
        } catch (e: Exception) {
            logger.error("Failed to initialize Slack service: ${e.message}")
        }
    }
    
    /**
     * Send a message to Slack via webhook
     */
    fun sendMessage(
        webhookUrl: String,
        message: String,
        channel: String? = null,
        username: String? = null,
        iconEmoji: String? = null
    ): Result<Int> {
        return try {
            val actualChannel = channel ?: properties.getProperty("webhook.default-channel")
            val actualUsername = username ?: properties.getProperty("webhook.username")
            val actualIconEmoji = iconEmoji ?: properties.getProperty("webhook.icon-emoji")
            
            // Create payload
            val payload = """
                {
                    "channel": "$actualChannel",
                    "username": "$actualUsername",
                    "icon_emoji": "$actualIconEmoji",
                    "text": ${escapeJsonString(message)}
                }
            """.trimIndent()
            
            // Build request
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(properties.getProperty("webhook.connection-timeout", "10000").toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            
            // Send request
            val maxRetries = properties.getProperty("webhook.retry-count", "3").toInt()
            var lastException: Exception? = null
            var statusCode = -1
            
            for (attempt in 1..maxRetries) {
                try {
                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    statusCode = response.statusCode()
                    
                    if (statusCode in 200..299) {
                        logger.info("Slack message sent successfully to $actualChannel")
                        return Result.success(statusCode)
                    } else {
                        logger.warn("Failed to send Slack message, status code: $statusCode, attempt: $attempt/$maxRetries")
                        if (attempt == maxRetries) {
                            return Result.failure(RuntimeException("Failed to send Slack message, status code: $statusCode"))
                        }
                        Thread.sleep(1000L * attempt) // Exponential backoff
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Error sending Slack message, attempt: $attempt/$maxRetries, error: ${e.message}")
                    if (attempt == maxRetries) {
                        return Result.failure(e)
                    }
                    Thread.sleep(1000L * attempt) // Exponential backoff
                }
            }
            
            // Should never reach here, but just in case
            if (statusCode in 200..299) {
                Result.success(statusCode)
            } else {
                Result.failure(lastException ?: RuntimeException("Failed to send Slack message"))
            }
            
        } catch (e: Exception) {
            logger.error("Failed to send Slack message: ${e.message}", e)
            Result.failure(e)
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
}