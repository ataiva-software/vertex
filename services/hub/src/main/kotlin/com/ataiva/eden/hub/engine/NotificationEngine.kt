package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import com.ataiva.eden.crypto.SecureRandom
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Multi-channel notification engine with template support
 */
class NotificationEngine(
    private val secureRandom: SecureRandom
) {
    private val templates = ConcurrentHashMap<String, NotificationTemplateInstance>()
    private val deliveries = ConcurrentHashMap<String, NotificationDeliveryInstance>()
    private val deliveryQueue = mutableListOf<NotificationDeliveryInstance>()
    private val channelProviders = ConcurrentHashMap<NotificationType, NotificationChannelProvider>()
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Background coroutine for processing notifications
    private val deliveryProcessor = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Initialize default channel providers
        initializeChannelProviders()
        
        // Start delivery processor
        deliveryProcessor.launch {
            processDeliveryQueue()
        }
    }
    
    /**
     * Create a notification template
     */
    suspend fun createTemplate(request: CreateNotificationTemplateRequest): HubResult<NotificationTemplateResponse> {
        return try {
            // Validate template variables
            val variables = extractVariables(request.body)
            if (request.subject != null) {
                variables.addAll(extractVariables(request.subject))
            }
            
            val templateId = SecureRandom.generateUuid()
            val template = NotificationTemplateInstance(
                id = templateId,
                name = request.name,
                type = request.type,
                subject = request.subject,
                body = request.body,
                variables = variables.distinct(),
                metadata = request.metadata,
                userId = request.userId,
                organizationId = request.organizationId,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            templates[templateId] = template
            HubResult.Success(template.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to create notification template: ${e.message}")
        }
    }
    
    /**
     * Update a notification template
     */
    suspend fun updateTemplate(request: UpdateNotificationTemplateRequest): HubResult<NotificationTemplateResponse> {
        return try {
            val template = templates[request.id]
                ?: return HubResult.Error("Template not found: ${request.id}")
            
            // Extract variables from updated content
            val body = request.body ?: template.body
            val subject = request.subject ?: template.subject
            val variables = extractVariables(body).toMutableList()
            if (subject != null) {
                variables.addAll(extractVariables(subject))
            }
            
            val updatedTemplate = template.copy(
                name = request.name ?: template.name,
                subject = subject,
                body = body,
                variables = request.variables ?: variables.distinct(),
                metadata = request.metadata ?: template.metadata,
                updatedAt = Clock.System.now()
            )
            
            templates[request.id] = updatedTemplate
            HubResult.Success(updatedTemplate.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to update notification template: ${e.message}")
        }
    }
    
    /**
     * Get template by ID
     */
    fun getTemplate(templateId: String): HubResult<NotificationTemplateResponse> {
        val template = templates[templateId]
            ?: return HubResult.Error("Template not found: $templateId")
        
        return HubResult.Success(template.toResponse())
    }
    
    /**
     * List templates for a user
     */
    fun listTemplates(userId: String, organizationId: String? = null): HubResult<List<NotificationTemplateResponse>> {
        val userTemplates = templates.values.filter { template ->
            template.userId == userId && 
            (organizationId == null || template.organizationId == organizationId)
        }
        
        return HubResult.Success(userTemplates.map { it.toResponse() })
    }
    
    /**
     * Delete a template
     */
    suspend fun deleteTemplate(templateId: String): HubResult<Unit> {
        return try {
            templates.remove(templateId)
                ?: return HubResult.Error("Template not found: $templateId")
            
            HubResult.Success(Unit)
            
        } catch (e: Exception) {
            HubResult.Error("Failed to delete template: ${e.message}")
        }
    }
    
    /**
     * Send a notification
     */
    suspend fun sendNotification(request: SendNotificationRequest): HubResult<NotificationDeliveryResponse> {
        return try {
            // Prepare notification content
            val (subject, body) = if (request.templateId != null) {
                val template = templates[request.templateId]
                    ?: return HubResult.Error("Template not found: ${request.templateId}")
                
                val renderedSubject = template.subject?.let { renderTemplate(it, request.variables) }
                val renderedBody = renderTemplate(template.body, request.variables)
                
                Pair(renderedSubject, renderedBody)
            } else {
                Pair(request.subject, request.body ?: "")
            }
            
            if (body.isBlank()) {
                return HubResult.Error("Notification body cannot be empty")
            }
            
            val deliveryId = SecureRandom.generateUuid()
            val delivery = NotificationDeliveryInstance(
                id = deliveryId,
                type = request.type,
                recipients = request.recipients,
                subject = subject,
                body = body,
                variables = request.variables,
                priority = request.priority,
                status = NotificationStatus.PENDING,
                scheduledAt = request.scheduledAt,
                userId = request.userId,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            deliveries[deliveryId] = delivery
            
            // Add to delivery queue for processing
            synchronized(deliveryQueue) {
                deliveryQueue.add(delivery)
            }
            
            HubResult.Success(delivery.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to send notification: ${e.message}")
        }
    }
    
    /**
     * Get delivery status
     */
    fun getDelivery(deliveryId: String): HubResult<NotificationDeliveryResponse> {
        val delivery = deliveries[deliveryId]
            ?: return HubResult.Error("Delivery not found: $deliveryId")
        
        return HubResult.Success(delivery.toResponse())
    }
    
    /**
     * List deliveries for a user
     */
    fun listDeliveries(userId: String, limit: Int = 50): HubResult<List<NotificationDeliveryResponse>> {
        val userDeliveries = deliveries.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
            .take(limit)
        
        return HubResult.Success(userDeliveries.map { it.toResponse() })
    }
    
    /**
     * Cancel a scheduled notification
     */
    suspend fun cancelNotification(deliveryId: String): HubResult<Unit> {
        return try {
            val delivery = deliveries[deliveryId]
                ?: return HubResult.Error("Delivery not found: $deliveryId")
            
            if (delivery.status != NotificationStatus.PENDING) {
                return HubResult.Error("Cannot cancel notification with status: ${delivery.status}")
            }
            
            val cancelledDelivery = delivery.copy(
                status = NotificationStatus.CANCELLED,
                updatedAt = Clock.System.now()
            )
            
            deliveries[deliveryId] = cancelledDelivery
            
            // Remove from queue
            synchronized(deliveryQueue) {
                deliveryQueue.removeAll { it.id == deliveryId }
            }
            
            HubResult.Success(Unit)
            
        } catch (e: Exception) {
            HubResult.Error("Failed to cancel notification: ${e.message}")
        }
    }
    
    /**
     * Get notifications health status
     */
    fun getNotificationsHealth(): NotificationsHealth {
        val totalNotifications = deliveries.size.toLong()
        val pendingNotifications = deliveries.values.count { it.status == NotificationStatus.PENDING }
        val failedNotifications = deliveries.values.count { it.status == NotificationStatus.FAILED }
        
        val sentNotifications = deliveries.values.count { it.status == NotificationStatus.SENT || it.status == NotificationStatus.DELIVERED }
        val deliveryRate = if (totalNotifications > 0) sentNotifications.toDouble() / totalNotifications else 1.0
        
        return NotificationsHealth(
            totalNotifications = totalNotifications,
            pendingNotifications = pendingNotifications,
            failedNotifications = failedNotifications,
            deliveryRate = deliveryRate
        )
    }
    
    // Private helper methods
    
    private fun initializeChannelProviders() {
        // Initialize email provider with configuration
        val emailConfig = loadEmailConfig()
        channelProviders[NotificationType.EMAIL] = if (emailConfig.provider == "sendgrid") {
            SendGridEmailProvider(
                apiKey = emailConfig.password,
                fromEmail = emailConfig.fromEmail,
                fromName = emailConfig.fromName
            )
        } else {
            EmailChannelProvider(emailConfig)
        }
        
        // Initialize Slack provider with configuration
        channelProviders[NotificationType.SLACK] = SlackChannelProvider()
        
        channelProviders[NotificationType.SMS] = SmsChannelProvider()
        channelProviders[NotificationType.WEBHOOK] = WebhookChannelProvider()
        channelProviders[NotificationType.PUSH] = PushChannelProvider()
    }
    
    /**
     * Load email configuration from environment or configuration file
     */
    private fun loadEmailConfig(): EmailProviderConfig {
        // In a real implementation, this would load from environment variables or a configuration file
        // For now, we'll use default values that can be overridden in production
        val provider = System.getenv("EMAIL_PROVIDER") ?: "smtp"
        val host = System.getenv("EMAIL_HOST") ?: "smtp.gmail.com"
        val port = System.getenv("EMAIL_PORT") ?: "587"
        val username = System.getenv("EMAIL_USERNAME") ?: "notifications@example.com"
        val password = System.getenv("EMAIL_PASSWORD") ?: "password"
        val fromEmail = System.getenv("EMAIL_FROM_ADDRESS") ?: "notifications@example.com"
        val fromName = System.getenv("EMAIL_FROM_NAME") ?: "Eden Notifications"
        
        return EmailProviderConfig(
            provider = provider,
            host = host,
            port = port,
            username = username,
            password = password,
            fromEmail = fromEmail,
            fromName = fromName
        )
    }
    
    private fun extractVariables(content: String): List<String> {
        val pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}")
        val matcher = pattern.matcher(content)
        val variables = mutableListOf<String>()
        
        while (matcher.find()) {
            variables.add(matcher.group(1).trim())
        }
        
        return variables
    }
    
    private fun renderTemplate(template: String, variables: Map<String, String>): String {
        var rendered = template
        variables.forEach { (key, value) ->
            rendered = rendered.replace("{{$key}}", value)
        }
        return rendered
    }
    
    private suspend fun processDeliveryQueue() {
        while (true) {
            try {
                val pendingDeliveries = synchronized(deliveryQueue) {
                    deliveryQueue.filter { delivery ->
                        delivery.status == NotificationStatus.PENDING &&
                        (delivery.scheduledAt == null || delivery.scheduledAt <= Clock.System.now())
                    }
                }
                
                pendingDeliveries.forEach { delivery ->
                    launch {
                        processDelivery(delivery)
                    }
                }
                
                delay(1000) // Check every second
            } catch (e: Exception) {
                // Log error and continue
                delay(5000) // Wait longer on error
            }
        }
    }
    
    private suspend fun processDelivery(delivery: NotificationDeliveryInstance) {
        try {
            val provider = channelProviders[delivery.type]
                ?: throw IllegalStateException("No provider for notification type: ${delivery.type}")
            
            // Update status to sending
            val sendingDelivery = delivery.copy(
                status = NotificationStatus.SENT,
                updatedAt = Clock.System.now()
            )
            deliveries[delivery.id] = sendingDelivery
            
            // Send notification through provider
            val result = provider.sendNotification(
                recipients = delivery.recipients,
                subject = delivery.subject,
                body = delivery.body,
                priority = delivery.priority
            )
            
            // Update delivery based on result
            val updatedDelivery = if (result.success) {
                sendingDelivery.copy(
                    status = NotificationStatus.DELIVERED,
                    deliveredAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            } else {
                sendingDelivery.copy(
                    status = NotificationStatus.FAILED,
                    failureReason = result.error,
                    updatedAt = Clock.System.now()
                )
            }
            
            deliveries[delivery.id] = updatedDelivery
            
            // Remove from queue
            synchronized(deliveryQueue) {
                deliveryQueue.removeAll { it.id == delivery.id }
            }
            
        } catch (e: Exception) {
            // Handle delivery failure
            val failedDelivery = delivery.copy(
                status = NotificationStatus.FAILED,
                failureReason = e.message,
                updatedAt = Clock.System.now()
            )
            
            deliveries[delivery.id] = failedDelivery
            
            synchronized(deliveryQueue) {
                deliveryQueue.removeAll { it.id == delivery.id }
            }
        }
    }
}

/**
 * Base interface for notification channel providers
 */
interface NotificationChannelProvider {
    suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult
}

/**
 * Channel delivery result
 */
data class ChannelDeliveryResult(
    val success: Boolean,
    val error: String? = null,
    val details: Map<String, Any> = emptyMap()
)

// Email channel provider implementation moved to EmailChannelProvider.kt

/**
 * SMS channel provider
 */
class SmsChannelProvider : NotificationChannelProvider {
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        return try {
            val smsRecipients = recipients.filter { it.type == RecipientType.PHONE }
            if (smsRecipients.isEmpty()) {
                return ChannelDeliveryResult(false, "No SMS recipients found")
            }
            
            // TODO: Integrate with actual SMS service (Twilio, etc.)
            // For now, simulate SMS sending
            delay(200) // Simulate network delay
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "recipients" to smsRecipients.map { it.address },
                    "message" to body,
                    "provider" to "mock-sms"
                )
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(false, "SMS delivery failed: ${e.message}")
        }
    }
}

// Slack channel provider implementation moved to SlackChannelProvider.kt

/**
 * Webhook channel provider
 */
class WebhookChannelProvider : NotificationChannelProvider {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        return try {
            val webhookRecipients = recipients.filter { it.type == RecipientType.WEBHOOK_URL }
            if (webhookRecipients.isEmpty()) {
                return ChannelDeliveryResult(false, "No webhook recipients found")
            }
            
            val payload = mapOf(
                "subject" to (subject ?: ""),
                "body" to body,
                "priority" to priority.name,
                "timestamp" to Clock.System.now().toString()
            )
            
            val json = Json.encodeToString(payload)
            val results = mutableListOf<String>()
            
            webhookRecipients.forEach { recipient ->
                try {
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create(recipient.address))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Eden-Hub-Notification/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build()
                    
                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    results.add("${recipient.address}: HTTP ${response.statusCode()}")
                } catch (e: Exception) {
                    results.add("${recipient.address}: Error - ${e.message}")
                }
            }
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "results" to results,
                    "provider" to "webhook"
                )
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(false, "Webhook delivery failed: ${e.message}")
        }
    }
}

/**
 * Push notification channel provider
 */
class PushChannelProvider : NotificationChannelProvider {
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        return try {
            // TODO: Integrate with actual push notification service (FCM, APNS, etc.)
            // For now, simulate push notification sending
            delay(100) // Simulate network delay
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "recipients" to recipients.size,
                    "title" to (subject ?: ""),
                    "body" to body,
                    "provider" to "mock-push"
                )
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(false, "Push notification delivery failed: ${e.message}")
        }
    }
}

/**
 * Internal notification template instance
 */
private data class NotificationTemplateInstance(
    val id: String,
    val name: String,
    val type: NotificationType,
    val subject: String?,
    val body: String,
    val variables: List<String>,
    val metadata: Map<String, String>,
    val userId: String,
    val organizationId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toResponse(): NotificationTemplateResponse {
        return NotificationTemplateResponse(
            id = id,
            name = name,
            type = type,
            subject = subject,
            body = body,
            variables = variables,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = updatedAt,
            userId = userId,
            organizationId = organizationId
        )
    }
}

/**
 * Internal notification delivery instance
 */
private data class NotificationDeliveryInstance(
    val id: String,
    val type: NotificationType,
    val recipients: List<NotificationRecipient>,
    val subject: String?,
    val body: String,
    val variables: Map<String, String>,
    val priority: NotificationPriority,
    val status: NotificationStatus,
    val scheduledAt: Instant?,
    val deliveredAt: Instant? = null,
    val failureReason: String? = null,
    val userId: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toResponse(): NotificationDeliveryResponse {
        return NotificationDeliveryResponse(
            id = id,
            type = type,
            status = status,
            recipients = recipients,
            subject = subject,
            deliveredAt = deliveredAt,
            failureReason = failureReason,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}