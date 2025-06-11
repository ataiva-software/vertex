package com.ataiva.eden.hub.service

import com.ataiva.eden.hub.model.*
import kotlinx.datetime.Clock
import java.util.UUID
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
import kotlin.time.Duration.Companion.seconds
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min
import kotlin.math.pow

/**
 * Webhook service for managing webhook delivery with retry mechanisms
 */
class WebhookService() {
    private val webhooks = ConcurrentHashMap<String, WebhookInstance>()
    private val deliveries = ConcurrentHashMap<String, WebhookDeliveryInstance>()
    private val deliveryQueue = mutableListOf<WebhookDeliveryInstance>()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Background coroutine for processing deliveries
    private val deliveryProcessor = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Start delivery processor
        deliveryProcessor.launch {
            processDeliveryQueue()
        }
    }
    
    /**
     * Create a new webhook
     */
    suspend fun createWebhook(request: CreateWebhookRequest): HubResult<WebhookResponse> {
        return try {
            // Validate webhook URL
            if (!isValidUrl(request.url)) {
                return HubResult.Error("Invalid webhook URL")
            }
            
            // Validate events
            if (request.events.isEmpty()) {
                return HubResult.Error("At least one event must be specified")
            }
            
            val webhookId = UUID.randomUUID().toString()
            val webhook = WebhookInstance(
                id = webhookId,
                name = request.name,
                url = request.url,
                events = request.events,
                description = request.description,
                secret = request.secret,
                headers = request.headers,
                payloadTemplate = request.payloadTemplate,
                retryPolicy = request.retryPolicy,
                isActive = request.isActive,
                status = if (request.isActive) WebhookStatus.ACTIVE else WebhookStatus.INACTIVE,
                userId = request.userId,
                organizationId = request.organizationId,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            webhooks[webhookId] = webhook
            HubResult.Success(webhook.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to create webhook: ${e.message}")
        }
    }
    
    /**
     * Update an existing webhook
     */
    suspend fun updateWebhook(request: UpdateWebhookRequest): HubResult<WebhookResponse> {
        return try {
            val webhook = webhooks[request.id]
                ?: return HubResult.Error("Webhook not found: ${request.id}")
            
            // Validate URL if provided
            if (request.url != null && !isValidUrl(request.url)) {
                return HubResult.Error("Invalid webhook URL")
            }
            
            // Validate events if provided
            if (request.events != null && request.events.isEmpty()) {
                return HubResult.Error("At least one event must be specified")
            }
            
            val updatedWebhook = webhook.copy(
                name = request.name ?: webhook.name,
                url = request.url ?: webhook.url,
                events = request.events ?: webhook.events,
                description = request.description ?: webhook.description,
                secret = request.secret ?: webhook.secret,
                headers = request.headers ?: webhook.headers,
                payloadTemplate = request.payloadTemplate ?: webhook.payloadTemplate,
                retryPolicy = request.retryPolicy ?: webhook.retryPolicy,
                isActive = request.isActive ?: webhook.isActive,
                status = if (request.isActive == false) WebhookStatus.INACTIVE else webhook.status,
                updatedAt = Clock.System.now()
            )
            
            webhooks[request.id] = updatedWebhook
            HubResult.Success(updatedWebhook.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to update webhook: ${e.message}")
        }
    }
    
    /**
     * Get webhook by ID
     */
    fun getWebhook(webhookId: String): HubResult<WebhookResponse> {
        val webhook = webhooks[webhookId]
            ?: return HubResult.Error("Webhook not found: $webhookId")
        
        return HubResult.Success(webhook.toResponse())
    }
    
    /**
     * List webhooks for a user
     */
    fun listWebhooks(userId: String, organizationId: String? = null): HubResult<List<WebhookResponse>> {
        val userWebhooks = webhooks.values.filter { webhook ->
            webhook.userId == userId && 
            (organizationId == null || webhook.organizationId == organizationId)
        }
        
        return HubResult.Success(userWebhooks.map { it.toResponse() })
    }
    
    /**
     * Delete a webhook
     */
    suspend fun deleteWebhook(webhookId: String): HubResult<Unit> {
        return try {
            val webhook = webhooks[webhookId]
                ?: return HubResult.Error("Webhook not found: $webhookId")
            
            // Cancel any pending deliveries for this webhook
            deliveryQueue.removeAll { it.webhookId == webhookId }
            
            webhooks.remove(webhookId)
            HubResult.Success(Unit)
            
        } catch (e: Exception) {
            HubResult.Error("Failed to delete webhook: ${e.message}")
        }
    }
    
    /**
     * Deliver a webhook payload
     */
    suspend fun deliverWebhook(request: WebhookDeliveryRequest): HubResult<WebhookDeliveryResponse> {
        return try {
            val webhook = webhooks[request.webhookId]
                ?: return HubResult.Error("Webhook not found: ${request.webhookId}")
            
            if (!webhook.isActive || webhook.status != WebhookStatus.ACTIVE) {
                return HubResult.Error("Webhook is not active")
            }
            
            // Check if webhook is configured for this event
            if (!webhook.events.contains(request.event) && !webhook.events.contains("*")) {
                return HubResult.Error("Webhook is not configured for event: ${request.event}")
            }
            
            val deliveryId = UUID.randomUUID().toString()
            val delivery = WebhookDeliveryInstance(
                id = deliveryId,
                webhookId = request.webhookId,
                event = request.event,
                payload = request.payload,
                status = DeliveryStatus.PENDING,
                attemptCount = 0,
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
            HubResult.Error("Failed to queue webhook delivery: ${e.message}")
        }
    }
    
    /**
     * Test webhook delivery
     */
    suspend fun testWebhook(webhookId: String): HubResult<WebhookDeliveryResponse> {
        return try {
            val webhook = webhooks[webhookId]
                ?: return HubResult.Error("Webhook not found: $webhookId")
            
            val testPayload = mapOf(
                "event" to "test",
                "timestamp" to Clock.System.now().toString(),
                "webhook_id" to webhookId,
                "test" to true
            )
            
            val deliveryRequest = WebhookDeliveryRequest(
                webhookId = webhookId,
                event = "test",
                payload = testPayload
            )
            
            deliverWebhook(deliveryRequest)
            
        } catch (e: Exception) {
            HubResult.Error("Failed to test webhook: ${e.message}")
        }
    }
    
    /**
     * Get delivery status
     */
    fun getDelivery(deliveryId: String): HubResult<WebhookDeliveryResponse> {
        val delivery = deliveries[deliveryId]
            ?: return HubResult.Error("Delivery not found: $deliveryId")
        
        return HubResult.Success(delivery.toResponse())
    }
    
    /**
     * List deliveries for a webhook
     */
    fun listDeliveries(webhookId: String, limit: Int = 50): HubResult<List<WebhookDeliveryResponse>> {
        val webhookDeliveries = deliveries.values
            .filter { it.webhookId == webhookId }
            .sortedByDescending { it.createdAt }
            .take(limit)
        
        return HubResult.Success(webhookDeliveries.map { it.toResponse() })
    }
    
    /**
     * Get webhook health status
     */
    fun getWebhooksHealth(): WebhooksHealth {
        val totalWebhooks = webhooks.size
        val activeWebhooks = webhooks.values.count { it.isActive && it.status == WebhookStatus.ACTIVE }
        val pendingDeliveries = deliveries.values.count { it.status == DeliveryStatus.PENDING || it.status == DeliveryStatus.RETRYING }
        
        val totalDeliveries = deliveries.size
        val successfulDeliveries = deliveries.values.count { it.status == DeliveryStatus.DELIVERED }
        val successRate = if (totalDeliveries > 0) successfulDeliveries.toDouble() / totalDeliveries else 1.0
        
        return WebhooksHealth(
            totalWebhooks = totalWebhooks,
            activeWebhooks = activeWebhooks,
            pendingDeliveries = pendingDeliveries,
            successRate = successRate
        )
    }
    
    // Private helper methods
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = URI.create(url)
            uri.scheme in listOf("http", "https") && uri.host != null
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun processDeliveryQueue() {
        while (true) {
            try {
                val pendingDeliveries = synchronized(deliveryQueue) {
                    deliveryQueue.filter { 
                        it.status == DeliveryStatus.PENDING || 
                        (it.status == DeliveryStatus.RETRYING && it.nextRetryAt != null && it.nextRetryAt <= Clock.System.now())
                    }
                }
                
                pendingDeliveries.forEach { delivery ->
                    deliveryProcessor.launch {
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
    
    private suspend fun processDelivery(delivery: WebhookDeliveryInstance) {
        try {
            val webhook = webhooks[delivery.webhookId] ?: return
            
            // Update delivery status to processing
            val processingDelivery = delivery.copy(
                status = DeliveryStatus.RETRYING,
                attemptCount = delivery.attemptCount + 1,
                updatedAt = Clock.System.now()
            )
            deliveries[delivery.id] = processingDelivery
            
            // Prepare payload
            val payload = if (webhook.payloadTemplate != null) {
                transformPayload(delivery.payload, webhook.payloadTemplate)
            } else {
                delivery.payload
            }
            
            val payloadJson = json.encodeToString(payload)
            
            // Build request
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webhook.url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub-Webhook/1.0")
                .timeout(Duration.ofSeconds(webhook.retryPolicy.timeoutSeconds.toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
            
            // Add custom headers
            webhook.headers.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            
            // Add signature if secret is provided
            webhook.secret?.let { secret ->
                val signature = generateSignature(payloadJson, secret)
                requestBuilder.header("X-Hub-Signature-256", "sha256=$signature")
            }
            
            val request = requestBuilder.build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            // Update delivery based on response
            val updatedDelivery = if (response.statusCode() in 200..299) {
                // Success
                processingDelivery.copy(
                    status = DeliveryStatus.DELIVERED,
                    httpStatusCode = response.statusCode(),
                    responseBody = response.body(),
                    deliveredAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            } else {
                // Failure - check if we should retry
                val shouldRetry = processingDelivery.attemptCount < webhook.retryPolicy.maxRetries
                if (shouldRetry) {
                    val nextRetryDelay = calculateRetryDelay(processingDelivery.attemptCount, webhook.retryPolicy)
                    processingDelivery.copy(
                        status = DeliveryStatus.RETRYING,
                        httpStatusCode = response.statusCode(),
                        responseBody = response.body(),
                        nextRetryAt = Clock.System.now().plus(nextRetryDelay.seconds),
                        updatedAt = Clock.System.now()
                    )
                } else {
                    processingDelivery.copy(
                        status = DeliveryStatus.ABANDONED,
                        httpStatusCode = response.statusCode(),
                        responseBody = response.body(),
                        updatedAt = Clock.System.now()
                    )
                }
            }
            
            deliveries[delivery.id] = updatedDelivery
            
            // Update webhook statistics
            val currentWebhook = webhooks[delivery.webhookId]!!
            val updatedWebhook = if (updatedDelivery.status == DeliveryStatus.DELIVERED) {
                currentWebhook.copy(
                    successCount = webhook.successCount + 1,
                    lastDeliveryAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            } else if (updatedDelivery.status == DeliveryStatus.ABANDONED) {
                currentWebhook.copy(
                    failureCount = webhook.failureCount + 1,
                    updatedAt = Clock.System.now()
                )
            } else {
                currentWebhook
            }
            webhooks[delivery.webhookId] = updatedWebhook
            
            // Remove from queue if completed
            if (updatedDelivery.status in listOf(DeliveryStatus.DELIVERED, DeliveryStatus.ABANDONED)) {
                synchronized(deliveryQueue) {
                    deliveryQueue.removeAll { it.id == delivery.id }
                }
            }
            
        } catch (e: Exception) {
            // Handle delivery failure
            val failedDelivery = delivery.copy(
                status = if (delivery.attemptCount < webhooks[delivery.webhookId]?.retryPolicy?.maxRetries ?: 0) {
                    DeliveryStatus.RETRYING
                } else {
                    DeliveryStatus.FAILED
                },
                attemptCount = delivery.attemptCount + 1,
                responseBody = "Error: ${e.message}",
                updatedAt = Clock.System.now()
            )
            
            deliveries[delivery.id] = failedDelivery
            
            if (failedDelivery.status == DeliveryStatus.FAILED) {
                synchronized(deliveryQueue) {
                    deliveryQueue.removeAll { it.id == delivery.id }
                }
            }
        }
    }
    
    private fun transformPayload(payload: Map<String, Any>, template: String): Map<String, Any> {
        // Simple template transformation - in production, use a proper template engine
        var transformedTemplate = template
        payload.forEach { (key, value) ->
            transformedTemplate = transformedTemplate.replace("{{$key}}", value.toString())
        }
        
        return try {
            json.decodeFromString<Map<String, Any>>(transformedTemplate)
        } catch (e: Exception) {
            payload // Return original payload if template transformation fails
        }
    }
    
    private fun generateSignature(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val signature = mac.doFinal(payload.toByteArray())
        return signature.joinToString("") { "%02x".format(it) }
    }
    
    private fun calculateRetryDelay(attemptCount: Int, retryPolicy: WebhookRetryPolicy): Int {
        val baseDelay = retryPolicy.initialDelaySeconds
        val multiplier = retryPolicy.backoffMultiplier
        val maxDelay = retryPolicy.maxDelaySeconds
        
        val delay = (baseDelay * multiplier.pow(attemptCount - 1)).toInt()
        return min(delay, maxDelay)
    }
}

/**
 * Internal webhook instance
 */
private data class WebhookInstance(
    val id: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val description: String?,
    val secret: String?,
    val headers: Map<String, String>,
    val payloadTemplate: String?,
    val retryPolicy: WebhookRetryPolicy,
    val isActive: Boolean,
    val status: WebhookStatus,
    val lastDeliveryAt: Instant? = null,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val userId: String,
    val organizationId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toResponse(): WebhookResponse {
        return WebhookResponse(
            id = id,
            name = name,
            url = url,
            events = events,
            description = description,
            headers = headers,
            payloadTemplate = payloadTemplate,
            retryPolicy = retryPolicy,
            isActive = isActive,
            status = status,
            lastDeliveryAt = lastDeliveryAt,
            successCount = successCount,
            failureCount = failureCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
            userId = userId,
            organizationId = organizationId
        )
    }
}

/**
 * Internal webhook delivery instance
 */
private data class WebhookDeliveryInstance(
    val id: String,
    val webhookId: String,
    val event: String,
    val payload: Map<String, Any>,
    val status: DeliveryStatus,
    val httpStatusCode: Int? = null,
    val responseBody: String? = null,
    val attemptCount: Int,
    val nextRetryAt: Instant? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toResponse(): WebhookDeliveryResponse {
        return WebhookDeliveryResponse(
            id = id,
            webhookId = webhookId,
            event = event,
            status = status,
            httpStatusCode = httpStatusCode,
            responseBody = responseBody,
            attemptCount = attemptCount,
            nextRetryAt = nextRetryAt,
            deliveredAt = deliveredAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}