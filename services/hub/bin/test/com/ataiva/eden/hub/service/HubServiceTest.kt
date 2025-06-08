package com.ataiva.eden.hub.service

import com.ataiva.eden.hub.model.*
import com.ataiva.eden.crypto.Encryption
import com.ataiva.eden.crypto.SecureRandom
import com.ataiva.eden.crypto.EncryptionResult
import com.ataiva.eden.crypto.DecryptionResult
import com.ataiva.eden.database.EdenDatabaseService
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Comprehensive unit tests for HubService
 */
class HubServiceTest {
    
    private lateinit var hubService: HubService
    private lateinit var mockDatabaseService: MockEdenDatabaseService
    private lateinit var mockEncryption: MockEncryption
    private lateinit var mockSecureRandom: MockSecureRandom
    
    @BeforeTest
    fun setup() {
        mockDatabaseService = MockEdenDatabaseService()
        mockEncryption = MockEncryption()
        mockSecureRandom = MockSecureRandom()
        
        hubService = HubService(
            databaseService = mockDatabaseService,
            encryption = mockEncryption,
            secureRandom = mockSecureRandom
        )
    }
    
    // ================================
    // Integration Management Tests
    // ================================
    
    @Test
    fun `should create GitHub integration successfully`() = runTest {
        // Given
        val request = CreateIntegrationRequest(
            name = "Test GitHub Integration",
            type = IntegrationType.GITHUB,
            description = "Test integration",
            configuration = mapOf(
                "baseUrl" to "https://api.github.com",
                "owner" to "testorg"
            ),
            credentials = IntegrationCredentials(
                type = CredentialType.TOKEN,
                encryptedData = "test-token",
                encryptionKeyId = ""
            ),
            userId = "user123",
            organizationId = "org123"
        )
        
        // When
        val result = hubService.createIntegration(request)
        
        // Then
        assertTrue(result is HubResult.Success)
        val integration = result.data
        assertEquals("Test GitHub Integration", integration.name)
        assertEquals(IntegrationType.GITHUB, integration.type)
        assertEquals(IntegrationStatus.ACTIVE, integration.status)
        assertEquals("user123", integration.userId)
    }
    
    @Test
    fun `should fail to create integration with invalid type`() = runTest {
        // Given
        val request = CreateIntegrationRequest(
            name = "Test Invalid Integration",
            type = IntegrationType.DOCKER, // Not registered
            description = "Test integration",
            configuration = mapOf("baseUrl" to "https://api.docker.com"),
            credentials = IntegrationCredentials(
                type = CredentialType.TOKEN,
                encryptedData = "test-token",
                encryptionKeyId = ""
            ),
            userId = "user123"
        )
        
        // When
        val result = hubService.createIntegration(request)
        
        // Then
        assertTrue(result is HubResult.Error)
        assertTrue(result.message.contains("Unsupported integration type"))
    }
    
    @Test
    fun `should test integration connection successfully`() = runTest {
        // Given - First create an integration
        val createRequest = CreateIntegrationRequest(
            name = "Test Integration",
            type = IntegrationType.GITHUB,
            description = "Test integration",
            configuration = mapOf(
                "baseUrl" to "https://api.github.com",
                "owner" to "testorg"
            ),
            credentials = IntegrationCredentials(
                type = CredentialType.TOKEN,
                encryptedData = "test-token",
                encryptionKeyId = ""
            ),
            userId = "user123"
        )
        
        val createResult = hubService.createIntegration(createRequest)
        assertTrue(createResult is HubResult.Success)
        val integrationId = createResult.data.id
        
        // When
        val testResult = hubService.testIntegration(integrationId, "user123")
        
        // Then
        assertTrue(testResult is HubResult.Success)
        assertTrue(testResult.data.success)
        assertEquals("GitHub connection successful (simulated)", testResult.data.message)
    }
    
    @Test
    fun `should list integrations for user`() = runTest {
        // Given - Create multiple integrations
        val requests = listOf(
            CreateIntegrationRequest(
                name = "GitHub Integration",
                type = IntegrationType.GITHUB,
                description = "GitHub integration",
                configuration = mapOf("baseUrl" to "https://api.github.com", "owner" to "testorg"),
                credentials = IntegrationCredentials(CredentialType.TOKEN, "token1", ""),
                userId = "user123"
            ),
            CreateIntegrationRequest(
                name = "Slack Integration",
                type = IntegrationType.SLACK,
                description = "Slack integration",
                configuration = mapOf("baseUrl" to "https://slack.com/api"),
                credentials = IntegrationCredentials(CredentialType.TOKEN, "token2", ""),
                userId = "user123"
            )
        )
        
        requests.forEach { hubService.createIntegration(it) }
        
        // When
        val result = hubService.listIntegrations("user123")
        
        // Then
        assertTrue(result is HubResult.Success)
        assertEquals(2, result.data.size)
        assertTrue(result.data.any { it.name == "GitHub Integration" })
        assertTrue(result.data.any { it.name == "Slack Integration" })
    }
    
    // ================================
    // Webhook Management Tests
    // ================================
    
    @Test
    fun `should create webhook successfully`() = runTest {
        // Given
        val request = CreateWebhookRequest(
            name = "Test Webhook",
            url = "https://example.com/webhook",
            events = listOf("push", "pull_request"),
            description = "Test webhook",
            secret = "webhook-secret",
            userId = "user123"
        )
        
        // When
        val result = hubService.createWebhook(request)
        
        // Then
        assertTrue(result is HubResult.Success)
        val webhook = result.data
        assertEquals("Test Webhook", webhook.name)
        assertEquals("https://example.com/webhook", webhook.url)
        assertEquals(listOf("push", "pull_request"), webhook.events)
        assertEquals(WebhookStatus.ACTIVE, webhook.status)
        assertTrue(webhook.isActive)
    }
    
    @Test
    fun `should fail to create webhook with invalid URL`() = runTest {
        // Given
        val request = CreateWebhookRequest(
            name = "Test Webhook",
            url = "invalid-url",
            events = listOf("push"),
            userId = "user123"
        )
        
        // When
        val result = hubService.createWebhook(request)
        
        // Then
        assertTrue(result is HubResult.Error)
        assertTrue(result.message.contains("Invalid webhook URL"))
    }
    
    @Test
    fun `should deliver webhook successfully`() = runTest {
        // Given - First create a webhook
        val createRequest = CreateWebhookRequest(
            name = "Test Webhook",
            url = "https://example.com/webhook",
            events = listOf("push"),
            userId = "user123"
        )
        
        val createResult = hubService.createWebhook(createRequest)
        assertTrue(createResult is HubResult.Success)
        val webhookId = createResult.data.id
        
        val deliveryRequest = WebhookDeliveryRequest(
            webhookId = webhookId,
            event = "push",
            payload = mapOf("action" to "opened", "repository" to "test-repo")
        )
        
        // When
        val result = hubService.deliverWebhook(deliveryRequest)
        
        // Then
        assertTrue(result is HubResult.Success)
        assertEquals(webhookId, result.data.webhookId)
        assertEquals("push", result.data.event)
        assertEquals(DeliveryStatus.PENDING, result.data.status)
    }
    
    // ================================
    // Notification Management Tests
    // ================================
    
    @Test
    fun `should create notification template successfully`() = runTest {
        // Given
        val request = CreateNotificationTemplateRequest(
            name = "Welcome Template",
            type = NotificationType.EMAIL,
            subject = "Welcome {{name}}!",
            body = "Hello {{name}}, welcome to {{platform}}!",
            variables = listOf("name", "platform"),
            userId = "user123"
        )
        
        // When
        val result = hubService.createNotificationTemplate(request)
        
        // Then
        assertTrue(result is HubResult.Success)
        val template = result.data
        assertEquals("Welcome Template", template.name)
        assertEquals(NotificationType.EMAIL, template.type)
        assertEquals("Welcome {{name}}!", template.subject)
        assertEquals("Hello {{name}}, welcome to {{platform}}!", template.body)
        assertEquals(listOf("name", "platform"), template.variables)
    }
    
    @Test
    fun `should send notification successfully`() = runTest {
        // Given
        val request = SendNotificationRequest(
            type = NotificationType.EMAIL,
            recipients = listOf(
                NotificationRecipient(
                    type = RecipientType.EMAIL,
                    address = "test@example.com",
                    name = "Test User"
                )
            ),
            subject = "Test Notification",
            body = "This is a test notification",
            priority = NotificationPriority.NORMAL,
            userId = "user123"
        )
        
        // When
        val result = hubService.sendNotification(request)
        
        // Then
        assertTrue(result is HubResult.Success)
        val delivery = result.data
        assertEquals(NotificationType.EMAIL, delivery.type)
        assertEquals(NotificationStatus.PENDING, delivery.status)
        assertEquals(1, delivery.recipients.size)
        assertEquals("test@example.com", delivery.recipients[0].address)
    }
    
    @Test
    fun `should send notification with template successfully`() = runTest {
        // Given - First create a template
        val templateRequest = CreateNotificationTemplateRequest(
            name = "Test Template",
            type = NotificationType.EMAIL,
            subject = "Hello {{name}}",
            body = "Welcome to {{platform}}, {{name}}!",
            userId = "user123"
        )
        
        val templateResult = hubService.createNotificationTemplate(templateRequest)
        assertTrue(templateResult is HubResult.Success)
        val templateId = templateResult.data.id
        
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
            variables = mapOf("name" to "John", "platform" to "Eden"),
            userId = "user123"
        )
        
        // When
        val result = hubService.sendNotification(notificationRequest)
        
        // Then
        assertTrue(result is HubResult.Success)
        val delivery = result.data
        assertEquals(NotificationType.EMAIL, delivery.type)
        assertEquals(NotificationStatus.PENDING, delivery.status)
    }
    
    // ================================
    // Event Management Tests
    // ================================
    
    @Test
    fun `should subscribe to events successfully`() = runTest {
        // Given
        val eventTypes = listOf("integration.created", "webhook.delivered")
        val endpoint = "https://example.com/events"
        val secret = "event-secret"
        val userId = "user123"
        
        // When
        val result = hubService.subscribeToEvents(eventTypes, endpoint, secret, userId)
        
        // Then
        assertTrue(result is HubResult.Success)
        val subscription = result.data
        assertEquals(eventTypes, subscription.eventTypes)
        assertEquals(endpoint, subscription.endpoint)
        assertEquals(secret, subscription.secret)
        assertTrue(subscription.isActive)
        assertEquals(userId, subscription.userId)
    }
    
    @Test
    fun `should publish event successfully`() = runTest {
        // Given
        val event = HubEvent(
            id = "event123",
            type = "test.event",
            source = "hub",
            data = mapOf("message" to "test event"),
            userId = "user123"
        )
        
        // When
        val result = hubService.publishEvent(event)
        
        // Then
        assertTrue(result is HubResult.Success)
    }
    
    @Test
    fun `should list event subscriptions for user`() = runTest {
        // Given - Create multiple subscriptions
        hubService.subscribeToEvents(listOf("event1"), "https://example.com/1", null, "user123")
        hubService.subscribeToEvents(listOf("event2"), "https://example.com/2", null, "user123")
        hubService.subscribeToEvents(listOf("event3"), "https://example.com/3", null, "user456")
        
        // When
        val result = hubService.listEventSubscriptions("user123")
        
        // Then
        assertTrue(result is HubResult.Success)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.userId == "user123" })
    }
    
    // ================================
    // Health and Statistics Tests
    // ================================
    
    @Test
    fun `should get health status successfully`() {
        // When
        val health = hubService.getHealthStatus()
        
        // Then
        assertEquals("healthy", health.status)
        assertEquals("hub", health.service)
        assertNotNull(health.timestamp)
        assertNotNull(health.database)
        assertNotNull(health.integrations)
        assertNotNull(health.webhooks)
        assertNotNull(health.notifications)
    }
    
    @Test
    fun `should get service statistics successfully`() = runTest {
        // Given - Create some test data
        hubService.createIntegration(CreateIntegrationRequest(
            name = "Test Integration",
            type = IntegrationType.GITHUB,
            description = "Test",
            configuration = mapOf("baseUrl" to "https://api.github.com", "owner" to "test"),
            credentials = IntegrationCredentials(CredentialType.TOKEN, "token", ""),
            userId = "user123"
        ))
        
        hubService.createWebhook(CreateWebhookRequest(
            name = "Test Webhook",
            url = "https://example.com/webhook",
            events = listOf("push"),
            userId = "user123"
        ))
        
        // When
        val stats = hubService.getServiceStatistics()
        
        // Then
        assertNotNull(stats["integrations"])
        assertNotNull(stats["webhooks"])
        assertNotNull(stats["notifications"])
        assertNotNull(stats["events"])
        
        val integrations = stats["integrations"] as Map<String, Any>
        assertEquals(1, integrations["total"])
        assertEquals(1, integrations["active"])
        assertEquals(0, integrations["errors"])
    }
    
    // ================================
    // Error Handling Tests
    // ================================
    
    @Test
    fun `should handle integration creation failure gracefully`() = runTest {
        // Given - Invalid configuration
        val request = CreateIntegrationRequest(
            name = "Test Integration",
            type = IntegrationType.GITHUB,
            description = "Test integration",
            configuration = emptyMap(), // Missing required configuration
            credentials = IntegrationCredentials(CredentialType.TOKEN, "token", ""),
            userId = "user123"
        )
        
        // When
        val result = hubService.createIntegration(request)
        
        // Then
        assertTrue(result is HubResult.Error)
        assertTrue(result.message.contains("GitHub owner/organization is required"))
    }
    
    @Test
    fun `should handle webhook creation with empty events list`() = runTest {
        // Given
        val request = CreateWebhookRequest(
            name = "Test Webhook",
            url = "https://example.com/webhook",
            events = emptyList(), // Empty events list
            userId = "user123"
        )
        
        // When
        val result = hubService.createWebhook(request)
        
        // Then
        assertTrue(result is HubResult.Error)
        assertTrue(result.message.contains("At least one event must be specified"))
    }
    
    @Test
    fun `should handle notification sending with empty body`() = runTest {
        // Given
        val request = SendNotificationRequest(
            type = NotificationType.EMAIL,
            recipients = listOf(
                NotificationRecipient(RecipientType.EMAIL, "test@example.com")
            ),
            body = "", // Empty body
            userId = "user123"
        )
        
        // When
        val result = hubService.sendNotification(request)
        
        // Then
        assertTrue(result is HubResult.Error)
        assertTrue(result.message.contains("Notification body cannot be empty"))
    }
}

// ================================
// Mock Implementations
// ================================

class MockEdenDatabaseService : EdenDatabaseService {
    // Minimal mock implementation for testing
    override suspend fun initialize() {}
    override suspend fun close() {}
    override fun isHealthy(): Boolean = true
}

class MockEncryption : Encryption {
    override fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult {
        return EncryptionResult(data, ByteArray(12), ByteArray(16))
    }
    
    override fun decrypt(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): DecryptionResult {
        return DecryptionResult.Success(encryptedData)
    }
    
    override fun encryptString(data: String, key: ByteArray): EncryptionResult {
        return encrypt(data.toByteArray(), key)
    }
    
    override fun decryptString(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): String? {
        return String(encryptedData)
    }
}

class MockSecureRandom : SecureRandom {
    private var counter = 0
    
    override fun nextBytes(size: Int): ByteArray {
        return ByteArray(size) { (counter++).toByte() }
    }
    
    override fun nextString(length: Int, charset: String): String {
        return (1..length).map { charset.random() }.joinToString("")
    }
    
    override fun nextUuid(): String {
        return "test-uuid-${counter++}"
    }
}