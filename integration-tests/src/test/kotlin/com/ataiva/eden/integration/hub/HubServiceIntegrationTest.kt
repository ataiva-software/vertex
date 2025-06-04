package com.ataiva.eden.integration.hub

import com.ataiva.eden.hub.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import kotlin.test.*

/**
 * Integration tests for Hub Service API endpoints
 * Tests the complete Hub Service functionality including REST API, business logic, and integrations
 */
class HubServiceIntegrationTest {
    
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Assuming Hub Service runs on port 8080 during integration tests
    private val baseUrl = "http://localhost:8080"
    
    @Test
    fun `should get service info successfully`() = runTest {
        // When
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/"))
            .GET()
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        // Then
        assertEquals(200, response.statusCode())
        val serviceInfo = json.decodeFromString<Map<String, Any>>(response.body())
        assertEquals("Eden Hub Service", serviceInfo["name"])
        assertEquals("1.0.0", serviceInfo["version"])
        assertTrue(serviceInfo.containsKey("features"))
    }
    
    @Test
    fun `should get health status successfully`() = runTest {
        // When
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/health"))
            .GET()
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        // Then
        assertEquals(200, response.statusCode())
        val healthResponse = json.decodeFromString<ApiResponse<HubHealthResponse>>(response.body())
        assertTrue(healthResponse.success)
        assertNotNull(healthResponse.data)
        assertEquals("healthy", healthResponse.data!!.status)
        assertEquals("hub", healthResponse.data!!.service)
    }
    
    @Test
    fun `should get service statistics successfully`() = runTest {
        // When
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/stats"))
            .GET()
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        // Then
        assertEquals(200, response.statusCode())
        val statsResponse = json.decodeFromString<ApiResponse<Map<String, Any>>>(response.body())
        assertTrue(statsResponse.success)
        assertNotNull(statsResponse.data)
        assertTrue(statsResponse.data!!.containsKey("integrations"))
        assertTrue(statsResponse.data!!.containsKey("webhooks"))
        assertTrue(statsResponse.data!!.containsKey("notifications"))
        assertTrue(statsResponse.data!!.containsKey("events"))
    }
    
    // ================================
    // Integration Management Tests
    // ================================
    
    @Test
    fun `should create and manage GitHub integration end-to-end`() = runTest {
        val userId = "test-user-${System.currentTimeMillis()}"
        
        // 1. Create GitHub integration
        val createRequest = CreateIntegrationRequest(
            name = "Test GitHub Integration",
            type = IntegrationType.GITHUB,
            description = "Integration test GitHub connector",
            configuration = mapOf(
                "baseUrl" to "https://api.github.com",
                "owner" to "testorg"
            ),
            credentials = IntegrationCredentials(
                type = CredentialType.TOKEN,
                encryptedData = "test-github-token",
                encryptionKeyId = ""
            ),
            userId = userId,
            organizationId = "test-org"
        )
        
        val createResponse = createIntegration(createRequest)
        assertEquals(201, createResponse.statusCode())
        
        val createResult = json.decodeFromString<ApiResponse<IntegrationResponse>>(createResponse.body())
        assertTrue(createResult.success)
        val integrationId = createResult.data!!.id
        
        // 2. Test the integration
        val testResponse = testIntegration(integrationId, userId)
        assertEquals(200, testResponse.statusCode())
        
        val testResult = json.decodeFromString<ApiResponse<IntegrationTestResult>>(testResponse.body())
        assertTrue(testResult.success)
        assertTrue(testResult.data!!.success)
        
        // 3. List integrations
        val listResponse = listIntegrations(userId)
        assertEquals(200, listResponse.statusCode())
        
        val listResult = json.decodeFromString<ApiResponse<List<IntegrationResponse>>>(listResponse.body())
        assertTrue(listResult.success)
        assertTrue(listResult.data!!.any { it.id == integrationId })
        
        // 4. Execute an operation
        val executeResponse = executeIntegrationOperation(
            integrationId, 
            userId,
            "listRepositories",
            mapOf("type" to "all", "sort" to "updated")
        )
        assertEquals(200, executeResponse.statusCode())
        
        val executeResult = json.decodeFromString<ApiResponse<Map<String, Any>>>(executeResponse.body())
        assertTrue(executeResult.success)
        assertTrue(executeResult.data!!.containsKey("repositories"))
        
        // 5. Update integration
        val updateRequest = UpdateIntegrationRequest(
            id = integrationId,
            name = "Updated GitHub Integration",
            description = "Updated description",
            userId = userId
        )
        
        val updateResponse = updateIntegration(integrationId, updateRequest)
        assertEquals(200, updateResponse.statusCode())
        
        val updateResult = json.decodeFromString<ApiResponse<IntegrationResponse>>(updateResponse.body())
        assertTrue(updateResult.success)
        assertEquals("Updated GitHub Integration", updateResult.data!!.name)
        
        // 6. Delete integration
        val deleteResponse = deleteIntegration(integrationId, userId)
        assertEquals(204, deleteResponse.statusCode())
    }
    
    @Test
    fun `should create and manage Slack integration end-to-end`() = runTest {
        val userId = "test-user-${System.currentTimeMillis()}"
        
        // Create Slack integration
        val createRequest = CreateIntegrationRequest(
            name = "Test Slack Integration",
            type = IntegrationType.SLACK,
            description = "Integration test Slack connector",
            configuration = mapOf("baseUrl" to "https://slack.com/api"),
            credentials = IntegrationCredentials(
                type = CredentialType.TOKEN,
                encryptedData = "test-slack-token",
                encryptionKeyId = ""
            ),
            userId = userId
        )
        
        val createResponse = createIntegration(createRequest)
        assertEquals(201, createResponse.statusCode())
        
        val createResult = json.decodeFromString<ApiResponse<IntegrationResponse>>(createResponse.body())
        assertTrue(createResult.success)
        val integrationId = createResult.data!!.id
        
        // Execute Slack operations
        val operations = listOf(
            "listChannels" to mapOf("types" to "public_channel", "limit" to 10),
            "sendMessage" to mapOf(
                "channel" to "#general",
                "text" to "Test message from Eden Hub",
                "username" to "Eden Bot"
            )
        )
        
        operations.forEach { (operation, params) ->
            val response = executeIntegrationOperation(integrationId, userId, operation, params)
            assertEquals(200, response.statusCode())
            
            val result = json.decodeFromString<ApiResponse<Map<String, Any>>>(response.body())
            assertTrue(result.success)
        }
        
        // Cleanup
        deleteIntegration(integrationId, userId)
    }
    
    // ================================
    // Webhook Management Tests
    // ================================
    
    @Test
    fun `should create and manage webhooks end-to-end`() = runTest {
        val userId = "test-user-${System.currentTimeMillis()}"
        
        // 1. Create webhook
        val createRequest = CreateWebhookRequest(
            name = "Test Webhook",
            url = "https://httpbin.org/post",
            events = listOf("integration.created", "integration.tested"),
            description = "Integration test webhook",
            secret = "webhook-secret-123",
            headers = mapOf("X-Custom-Header" to "test-value"),
            userId = userId
        )
        
        val createResponse = createWebhook(createRequest)
        assertEquals(201, createResponse.statusCode())
        
        val createResult = json.decodeFromString<ApiResponse<WebhookResponse>>(createResponse.body())
        assertTrue(createResult.success)
        val webhookId = createResult.data!!.id
        
        // 2. Test webhook
        val testResponse = testWebhook(webhookId, userId)
        assertEquals(200, testResponse.statusCode())
        
        val testResult = json.decodeFromString<ApiResponse<WebhookDeliveryResponse>>(testResponse.body())
        assertTrue(testResult.success)
        assertEquals(webhookId, testResult.data!!.webhookId)
        
        // 3. Deliver webhook manually
        val deliverResponse = deliverWebhook(
            webhookId,
            "test.event",
            mapOf("message" to "Test webhook delivery", "timestamp" to System.currentTimeMillis())
        )
        assertEquals(202, deliverResponse.statusCode())
        
        val deliverResult = json.decodeFromString<ApiResponse<WebhookDeliveryResponse>>(deliverResponse.body())
        assertTrue(deliverResult.success)
        assertEquals("test.event", deliverResult.data!!.event)
        
        // 4. List webhook deliveries
        val deliveriesResponse = listWebhookDeliveries(webhookId)
        assertEquals(200, deliveriesResponse.statusCode())
        
        val deliveriesResult = json.decodeFromString<ApiResponse<List<WebhookDeliveryResponse>>>(deliveriesResponse.body())
        assertTrue(deliveriesResult.success)
        assertTrue(deliveriesResult.data!!.isNotEmpty())
        
        // 5. Update webhook
        val updateRequest = UpdateWebhookRequest(
            id = webhookId,
            name = "Updated Test Webhook",
            events = listOf("integration.created", "integration.tested", "webhook.delivered"),
            userId = userId
        )
        
        val updateResponse = updateWebhook(webhookId, updateRequest)
        assertEquals(200, updateResponse.statusCode())
        
        val updateResult = json.decodeFromString<ApiResponse<WebhookResponse>>(updateResponse.body())
        assertTrue(updateResult.success)
        assertEquals("Updated Test Webhook", updateResult.data!!.name)
        assertEquals(3, updateResult.data!!.events.size)
        
        // 6. Delete webhook
        val deleteResponse = deleteWebhook(webhookId, userId)
        assertEquals(204, deleteResponse.statusCode())
    }
    
    // ================================
    // Notification Management Tests
    // ================================
    
    @Test
    fun `should create and manage notifications end-to-end`() = runTest {
        val userId = "test-user-${System.currentTimeMillis()}"
        
        // 1. Create notification template
        val templateRequest = CreateNotificationTemplateRequest(
            name = "Welcome Template",
            type = NotificationType.EMAIL,
            subject = "Welcome to {{platform}}, {{name}}!",
            body = "Hello {{name}},\n\nWelcome to {{platform}}! We're excited to have you on board.\n\nBest regards,\nThe {{platform}} Team",
            variables = listOf("name", "platform"),
            metadata = mapOf("category" to "welcome", "priority" to "high"),
            userId = userId
        )
        
        val templateResponse = createNotificationTemplate(templateRequest)
        assertEquals(201, templateResponse.statusCode())
        
        val templateResult = json.decodeFromString<ApiResponse<NotificationTemplateResponse>>(templateResponse.body())
        assertTrue(templateResult.success)
        val templateId = templateResult.data!!.id
        
        // 2. Send notification using template
        val notificationRequest = SendNotificationRequest(
            templateId = templateId,
            type = NotificationType.EMAIL,
            recipients = listOf(
                NotificationRecipient(
                    type = RecipientType.EMAIL,
                    address = "test@example.com",
                    name = "Test User"
                )
            ),
            variables = mapOf(
                "name" to "John Doe",
                "platform" to "Eden DevOps Suite"
            ),
            priority = NotificationPriority.HIGH,
            userId = userId
        )
        
        val notificationResponse = sendNotification(notificationRequest)
        assertEquals(202, notificationResponse.statusCode())
        
        val notificationResult = json.decodeFromString<ApiResponse<NotificationDeliveryResponse>>(notificationResponse.body())
        assertTrue(notificationResult.success)
        val deliveryId = notificationResult.data!!.id
        
        // 3. Check notification delivery status
        val deliveryResponse = getNotificationDelivery(deliveryId)
        assertEquals(200, deliveryResponse.statusCode())
        
        val deliveryResult = json.decodeFromString<ApiResponse<NotificationDeliveryResponse>>(deliveryResponse.body())
        assertTrue(deliveryResult.success)
        assertEquals(deliveryId, deliveryResult.data!!.id)
        
        // 4. Send direct notification (without template)
        val directRequest = SendNotificationRequest(
            type = NotificationType.SLACK,
            recipients = listOf(
                NotificationRecipient(
                    type = RecipientType.SLACK_CHANNEL,
                    address = "#general",
                    name = "General Channel"
                )
            ),
            subject = "Direct Notification",
            body = "This is a direct notification without using a template",
            priority = NotificationPriority.NORMAL,
            userId = userId
        )
        
        val directResponse = sendNotification(directRequest)
        assertEquals(202, directResponse.statusCode())
        
        // 5. List notification deliveries
        val deliveriesResponse = listNotificationDeliveries(userId)
        assertEquals(200, deliveriesResponse.statusCode())
        
        val deliveriesResult = json.decodeFromString<ApiResponse<List<NotificationDeliveryResponse>>>(deliveriesResponse.body())
        assertTrue(deliveriesResult.success)
        assertEquals(2, deliveriesResult.data!!.size)
        
        // 6. Update template
        val updateTemplateRequest = UpdateNotificationTemplateRequest(
            id = templateId,
            name = "Updated Welcome Template",
            body = "Updated welcome message for {{name}} on {{platform}}!",
            userId = userId
        )
        
        val updateTemplateResponse = updateNotificationTemplate(templateId, updateTemplateRequest)
        assertEquals(200, updateTemplateResponse.statusCode())
        
        // 7. Delete template
        val deleteTemplateResponse = deleteNotificationTemplate(templateId, userId)
        assertEquals(204, deleteTemplateResponse.statusCode())
    }
    
    // ================================
    // Event Management Tests
    // ================================
    
    @Test
    fun `should manage event subscriptions end-to-end`() = runTest {
        val userId = "test-user-${System.currentTimeMillis()}"
        
        // 1. Subscribe to events
        val subscribeResponse = subscribeToEvents(
            eventTypes = listOf("integration.created", "webhook.delivered", "notification.sent"),
            endpoint = "https://httpbin.org/post",
            secret = "event-secret-123",
            userId = userId
        )
        assertEquals(201, subscribeResponse.statusCode())
        
        val subscribeResult = json.decodeFromString<ApiResponse<EventSubscription>>(subscribeResponse.body())
        assertTrue(subscribeResult.success)
        val subscriptionId = subscribeResult.data!!.id
        
        // 2. List event subscriptions
        val listResponse = listEventSubscriptions(userId)
        assertEquals(200, listResponse.statusCode())
        
        val listResult = json.decodeFromString<ApiResponse<List<EventSubscription>>>(listResponse.body())
        assertTrue(listResult.success)
        assertTrue(listResult.data!!.any { it.id == subscriptionId })
        
        // 3. Publish a test event
        val event = HubEvent(
            id = "test-event-${System.currentTimeMillis()}",
            type = "integration.created",
            source = "hub",
            data = mapOf(
                "integrationId" to "test-integration-123",
                "integrationType" to "GITHUB",
                "message" to "Integration created successfully"
            ),
            userId = userId
        )
        
        val publishResponse = publishEvent(event)
        assertEquals(202, publishResponse.statusCode())
        
        // 4. Unsubscribe from events
        val unsubscribeResponse = unsubscribeFromEvents(subscriptionId, userId)
        assertEquals(204, unsubscribeResponse.statusCode())
    }
    
    // ================================
    // Cross-Service Integration Tests
    // ================================
    
    @Test
    fun `should handle complete DevOps workflow integration`() = runTest {
        val userId = "workflow-user-${System.currentTimeMillis()}"
        
        // 1. Create GitHub integration
        val githubIntegration = createIntegration(CreateIntegrationRequest(
            name = "Workflow GitHub",
            type = IntegrationType.GITHUB,
            description = "GitHub for workflow",
            configuration = mapOf("baseUrl" to "https://api.github.com", "owner" to "testorg"),
            credentials = IntegrationCredentials(CredentialType.TOKEN, "github-token", ""),
            userId = userId
        ))
        
        val githubResult = json.decodeFromString<ApiResponse<IntegrationResponse>>(githubIntegration.body())
        val githubId = githubResult.data!!.id
        
        // 2. Create Slack integration
        val slackIntegration = createIntegration(CreateIntegrationRequest(
            name = "Workflow Slack",
            type = IntegrationType.SLACK,
            description = "Slack for notifications",
            configuration = mapOf("baseUrl" to "https://slack.com/api"),
            credentials = IntegrationCredentials(CredentialType.TOKEN, "slack-token", ""),
            userId = userId
        ))
        
        val slackResult = json.decodeFromString<ApiResponse<IntegrationResponse>>(slackIntegration.body())
        val slackId = slackResult.data!!.id
        
        // 3. Create webhook for GitHub events
        val webhook = createWebhook(CreateWebhookRequest(
            name = "GitHub Webhook",
            url = "https://httpbin.org/post",
            events = listOf("push", "pull_request"),
            userId = userId
        ))
        
        val webhookResult = json.decodeFromString<ApiResponse<WebhookResponse>>(webhook.body())
        val webhookId = webhookResult.data!!.id
        
        // 4. Create notification template
        val template = createNotificationTemplate(CreateNotificationTemplateRequest(
            name = "Deployment Notification",
            type = NotificationType.SLACK,
            subject = "Deployment Status",
            body = "Deployment {{status}} for {{repository}} by {{author}}",
            userId = userId
        ))
        
        val templateResult = json.decodeFromString<ApiResponse<NotificationTemplateResponse>>(template.body())
        val templateId = templateResult.data!!.id
        
        // 5. Subscribe to integration events
        subscribeToEvents(
            eventTypes = listOf("*"),
            endpoint = "https://httpbin.org/post",
            userId = userId
        )
        
        // 6. Simulate workflow: GitHub push → Webhook delivery → Slack notification
        
        // Execute GitHub operation (simulate push event)
        executeIntegrationOperation(githubId, userId, "listRepositories", mapOf("type" to "all"))
        
        // Deliver webhook (simulate GitHub webhook)
        deliverWebhook(webhookId, "push", mapOf(
            "repository" to "test-repo",
            "author" to "developer",
            "commit" to "abc123"
        ))
        
        // Send Slack notification
        sendNotification(SendNotificationRequest(
            templateId = templateId,
            type = NotificationType.SLACK,
            recipients = listOf(NotificationRecipient(RecipientType.SLACK_CHANNEL, "#deployments")),
            variables = mapOf(
                "status" to "SUCCESS",
                "repository" to "test-repo",
                "author" to "developer"
            ),
            userId = userId
        ))
        
        // 7. Verify workflow completed successfully
        val stats = getServiceStatistics()
        val statsResult = json.decodeFromString<ApiResponse<Map<String, Any>>>(stats.body())
        assertTrue(statsResult.success)
        
        val integrations = statsResult.data!!["integrations"] as Map<String, Any>
        assertTrue((integrations["total"] as Int) >= 2)
        
        // Cleanup
        deleteIntegration(githubId, userId)
        deleteIntegration(slackId, userId)
        deleteWebhook(webhookId, userId)
        deleteNotificationTemplate(templateId, userId)
    }
    
    // ================================
    // Helper Methods for HTTP Requests
    // ================================
    
    private fun createIntegration(request: CreateIntegrationRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/integrations"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun updateIntegration(id: String, request: UpdateIntegrationRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/integrations/$id"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun testIntegration(id: String, userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/integrations/$id/test?userId=$userId"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun listIntegrations(userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/integrations?userId=$userId"))
            .GET()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun executeIntegrationOperation(
        id: String, 
        userId: String, 
        operation: String, 
        parameters: Map<String, Any>
    ): HttpResponse<String> {
        val requestBody = mapOf(
            "operation" to operation,
            "parameters" to parameters
        )
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/integrations/$id/execute?userId=$userId"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun deleteIntegration(id: String, userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/integrations/$id?userId=$userId"))
            .DELETE()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun createWebhook(request: CreateWebhookRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/webhooks"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun updateWebhook(id: String, request: UpdateWebhookRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/webhooks/$id"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun testWebhook(id: String, userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/webhooks/$id/test?userId=$userId"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun deliverWebhook(id: String, event: String, payload: Map<String, Any>): HttpResponse<String> {
        val requestBody = mapOf(
            "event" to event,
            "payload" to payload
        )
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/webhooks/$id/deliver"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun listWebhookDeliveries(id: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/webhooks/$id/deliveries"))
            .GET()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun deleteWebhook(id: String, userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/webhooks/$id?userId=$userId"))
            .DELETE()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun createNotificationTemplate(request: CreateNotificationTemplateRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/notifications/templates"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun updateNotificationTemplate(id: String, request: UpdateNotificationTemplateRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/notifications/templates/$id"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun sendNotification(request: SendNotificationRequest): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/notifications/send"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun getNotificationDelivery(id: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/notifications/deliveries/$id"))
            .GET()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun listNotificationDeliveries(userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/notifications/deliveries?userId=$userId"))
            .GET()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun deleteNotificationTemplate(id: String, userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/notifications/templates/$id?userId=$userId"))
            .DELETE()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun subscribeToEvents(eventTypes: List<String>, endpoint: String, secret: String? = null, userId: String): HttpResponse<String> {
        val requestBody = mapOf(
            "eventTypes" to eventTypes,
            "endpoint" to endpoint,
            "secret" to secret,
            "userId" to userId
        )
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/events/subscribe"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun listEventSubscriptions(userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/events/subscriptions?userId=$userId"))
            .GET()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun publishEvent(event: HubEvent): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/events/publish"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(event)))
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun unsubscribeFromEvents(subscriptionId: String, userId: String): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/events/subscriptions/$subscriptionId?userId=$userId"))
            .DELETE()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun getServiceStatistics(): HttpResponse<String> {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/stats"))
            .GET()
            .build()
        
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }
}
            .buil