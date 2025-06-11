package com.ataiva.eden.hub.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

// ================================
// Integration Configuration Models
// ================================

/**
 * Request to create a new integration configuration
 */
@Serializable
data class CreateIntegrationRequest(
    val name: String,
    val type: IntegrationType,
    val description: String? = null,
    val configuration: Map<String, String>,
    val credentials: IntegrationCredentials,
    val userId: String,
    val organizationId: String? = null,
    val isActive: Boolean = true
)

/**
 * Request to update an integration configuration
 */
@Serializable
data class UpdateIntegrationRequest(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val configuration: Map<String, String>? = null,
    val credentials: IntegrationCredentials? = null,
    val isActive: Boolean? = null,
    val userId: String
)

/**
 * Integration configuration response
 */
@Serializable
data class IntegrationResponse(
    val id: String,
    val name: String,
    val type: IntegrationType,
    val description: String?,
    val configuration: Map<String, String>,
    val isActive: Boolean,
    val status: IntegrationStatus,
    val lastTestAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val userId: String,
    val organizationId: String?
)

/**
 * Integration types supported by the Hub
 */
@Serializable
enum class IntegrationType {
    GITHUB,
    SLACK,
    JIRA,
    AWS,
    AZURE,
    GCP,
    DOCKER,
    KUBERNETES,
    JENKINS,
    GITLAB,
    BITBUCKET,
    TEAMS,
    DISCORD,
    TELEGRAM,
    EMAIL,
    SMS,
    WEBHOOK
}

/**
 * Integration status
 */
@Serializable
enum class IntegrationStatus {
    ACTIVE,
    INACTIVE,
    ERROR,
    TESTING,
    CONFIGURING
}

/**
 * Integration credentials (encrypted)
 */
@Serializable
data class IntegrationCredentials(
    val type: CredentialType,
    val encryptedData: String,
    val encryptionKeyId: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Credential types
 */
@Serializable
enum class CredentialType {
    API_KEY,
    OAUTH2,
    BASIC_AUTH,
    TOKEN,
    CERTIFICATE,
    SSH_KEY,
    AWS_CREDENTIALS,
    SERVICE_ACCOUNT
}

// ================================
// Webhook Definition and Execution Models
// ================================

/**
 * Request to create a webhook
 */
@Serializable
data class CreateWebhookRequest(
    val name: String,
    val url: String,
    val events: List<String>,
    val description: String? = null,
    val secret: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val payloadTemplate: String? = null,
    val retryPolicy: WebhookRetryPolicy = WebhookRetryPolicy(),
    val isActive: Boolean = true,
    val userId: String,
    val organizationId: String? = null
)

/**
 * Request to update a webhook
 */
@Serializable
data class UpdateWebhookRequest(
    val id: String,
    val name: String? = null,
    val url: String? = null,
    val events: List<String>? = null,
    val description: String? = null,
    val secret: String? = null,
    val headers: Map<String, String>? = null,
    val payloadTemplate: String? = null,
    val retryPolicy: WebhookRetryPolicy? = null,
    val isActive: Boolean? = null,
    val userId: String
)

/**
 * Webhook response
 */
@Serializable
data class WebhookResponse(
    val id: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val description: String?,
    val headers: Map<String, String>,
    val payloadTemplate: String?,
    val retryPolicy: WebhookRetryPolicy,
    val isActive: Boolean,
    val status: WebhookStatus,
    val lastDeliveryAt: Instant?,
    val successCount: Long,
    val failureCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val userId: String,
    val organizationId: String?
)

/**
 * Webhook retry policy
 */
@Serializable
data class WebhookRetryPolicy(
    val maxRetries: Int = 3,
    val backoffMultiplier: Double = 2.0,
    val initialDelaySeconds: Int = 1,
    val maxDelaySeconds: Int = 300,
    val timeoutSeconds: Int = 30
)

/**
 * Webhook status
 */
@Serializable
enum class WebhookStatus {
    ACTIVE,
    INACTIVE,
    ERROR,
    RATE_LIMITED,
    SUSPENDED
}

/**
 * Webhook delivery request
 */
@Serializable
data class WebhookDeliveryRequest(
    val webhookId: String,
    val event: String,
    val payload: Map<String, @Contextual Any>,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * Webhook delivery response
 */
@Serializable
data class WebhookDeliveryResponse(
    val id: String,
    val webhookId: String,
    val event: String,
    val status: DeliveryStatus,
    val httpStatusCode: Int?,
    val responseBody: String?,
    val attemptCount: Int,
    val nextRetryAt: Instant?,
    val deliveredAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Delivery status
 */
@Serializable
enum class DeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
    RETRYING,
    ABANDONED
}

// ================================
// Notification Template and Delivery Models
// ================================

/**
 * Request to create a notification template
 */
@Serializable
data class CreateNotificationTemplateRequest(
    val name: String,
    val type: NotificationType,
    val subject: String? = null,
    val body: String,
    val variables: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val userId: String,
    val organizationId: String? = null
)

/**
 * Request to update a notification template
 */
@Serializable
data class UpdateNotificationTemplateRequest(
    val id: String,
    val name: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val variables: List<String>? = null,
    val metadata: Map<String, String>? = null,
    val userId: String
)

/**
 * Notification template response
 */
@Serializable
data class NotificationTemplateResponse(
    val id: String,
    val name: String,
    val type: NotificationType,
    val subject: String?,
    val body: String,
    val variables: List<String>,
    val metadata: Map<String, String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val userId: String,
    val organizationId: String?
)

/**
 * Notification types
 */
@Serializable
enum class NotificationType {
    EMAIL,
    SMS,
    SLACK,
    TEAMS,
    DISCORD,
    PUSH,
    WEBHOOK,
    IN_APP
}

/**
 * Request to send a notification
 */
@Serializable
data class SendNotificationRequest(
    val templateId: String? = null,
    val type: NotificationType,
    val recipients: List<NotificationRecipient>,
    val subject: String? = null,
    val body: String? = null,
    val variables: Map<String, String> = emptyMap(),
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val scheduledAt: Instant? = null,
    val userId: String
)

/**
 * Notification recipient
 */
@Serializable
data class NotificationRecipient(
    val type: RecipientType,
    val address: String,
    val name: String? = null,
    val preferences: Map<String, String> = emptyMap()
)

/**
 * Recipient types
 */
@Serializable
enum class RecipientType {
    EMAIL,
    PHONE,
    SLACK_CHANNEL,
    SLACK_USER,
    TEAMS_CHANNEL,
    TEAMS_USER,
    DISCORD_CHANNEL,
    DISCORD_USER,
    WEBHOOK_URL,
    USER_ID
}

/**
 * Notification priority
 */
@Serializable
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Notification delivery response
 */
@Serializable
data class NotificationDeliveryResponse(
    val id: String,
    val type: NotificationType,
    val status: NotificationStatus,
    val recipients: List<NotificationRecipient>,
    val subject: String?,
    val deliveredAt: Instant?,
    val failureReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Notification status
 */
@Serializable
enum class NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    CANCELLED
}

// ================================
// Authentication Credential Models
// ================================

/**
 * OAuth 2.0 configuration
 */
@Serializable
data class OAuth2Config(
    val clientId: String,
    val clientSecret: String,
    val authorizationUrl: String,
    val tokenUrl: String,
    val scopes: List<String> = emptyList(),
    val redirectUri: String? = null
)

/**
 * OAuth 2.0 token
 */
@Serializable
data class OAuth2Token(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Long? = null,
    val scope: String? = null,
    val expiresAt: Instant? = null
)

/**
 * API key configuration
 */
@Serializable
data class ApiKeyConfig(
    val key: String,
    val headerName: String = "Authorization",
    val prefix: String? = null
)

/**
 * Basic authentication configuration
 */
@Serializable
data class BasicAuthConfig(
    val username: String,
    val password: String
)

/**
 * AWS credentials configuration
 */
@Serializable
data class AwsCredentialsConfig(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null,
    val region: String? = null
)

// ================================
// Common Response Models
// ================================

/**
 * API error response
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val code: String? = null,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * API success response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(success = true, data = data)
        }
        
        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(success = false, error = message)
        }
    }
}

/**
 * Paginated response
 */
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean
)

/**
 * Health check response
 */
@Serializable
data class HubHealthResponse(
    val status: String,
    val timestamp: Instant,
    val uptime: Long,
    val service: String,
    val database: DatabaseHealth,
    val integrations: IntegrationsHealth,
    val webhooks: WebhooksHealth,
    val notifications: NotificationsHealth
)

/**
 * Database health status
 */
@Serializable
data class DatabaseHealth(
    val connected: Boolean,
    val responseTime: Long? = null,
    val activeConnections: Int? = null
)

/**
 * Integrations health status
 */
@Serializable
data class IntegrationsHealth(
    val totalIntegrations: Int,
    val activeIntegrations: Int,
    val errorIntegrations: Int,
    val lastTestAt: Instant?
)

/**
 * Webhooks health status
 */
@Serializable
data class WebhooksHealth(
    val totalWebhooks: Int,
    val activeWebhooks: Int,
    val pendingDeliveries: Int,
    val successRate: Double
)

/**
 * Notifications health status
 */
@Serializable
data class NotificationsHealth(
    val totalNotifications: Long,
    val pendingNotifications: Int,
    val failedNotifications: Int,
    val deliveryRate: Double
)

// ================================
// Event Models
// ================================

/**
 * Hub event
 */
@Serializable
data class HubEvent(
    val id: String,
    val type: String,
    val source: String,
    val data: Map<String, @Contextual Any>,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    val userId: String? = null,
    val organizationId: String? = null
)

/**
 * Event subscription
 */
@Serializable
data class EventSubscription(
    val id: String,
    val eventTypes: List<String>,
    val endpoint: String,
    val secret: String? = null,
    val isActive: Boolean = true,
    val userId: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

// ================================
// Result Types
// ================================

/**
 * Hub service result wrapper
 */
sealed class HubResult<out T> {
    data class Success<T>(val data: T) : HubResult<T>()
    data class Error(val message: String, val code: String? = null) : HubResult<Nothing>()
}

/**
 * Integration test result
 */
@Serializable
data class IntegrationTestResult(
    val success: Boolean,
    val message: String,
    val responseTime: Long,
    val details: Map<String, @Contextual Any> = emptyMap(),
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
)