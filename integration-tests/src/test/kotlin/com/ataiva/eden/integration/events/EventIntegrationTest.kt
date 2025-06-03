package com.ataiva.eden.integration.events

import com.ataiva.eden.events.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Integration tests for event system across different scenarios
 */
class EventIntegrationTest {
    
    private lateinit var eventBus: InMemoryEventBus
    private val eventSerializer = JsonEventSerializer()
    
    @BeforeTest
    fun setup() = runTest {
        eventBus = InMemoryEventBus()
        eventBus.start()
    }
    
    @AfterTest
    fun cleanup() = runTest {
        eventBus.stop()
    }
    
    @Test
    fun `complete event workflow should work end-to-end`() = runTest {
        val eventCollector = EventCollector()
        
        // Step 1: Subscribe to multiple event types
        eventBus.subscribe("user.created", eventCollector)
        eventBus.subscribe("user.updated", eventCollector)
        eventBus.subscribe("organization.created", eventCollector)
        
        // Step 2: Publish various events
        val userCreatedEvent = createUserCreatedEvent("user-123", "john@example.com")
        val userUpdatedEvent = createUserUpdatedEvent("user-123", mapOf("name" to "John Doe"))
        val orgCreatedEvent = createOrganizationCreatedEvent("org-456", "Test Org")
        
        eventBus.publish(userCreatedEvent)
        eventBus.publish(userUpdatedEvent)
        eventBus.publish(orgCreatedEvent)
        
        // Step 3: Wait for async processing
        delay(100)
        
        // Step 4: Verify all events were received
        assertEquals(3, eventCollector.receivedEvents.size)
        
        val receivedEventTypes = eventCollector.receivedEvents.map { it.eventType }.toSet()
        assertTrue(receivedEventTypes.contains("user.created"))
        assertTrue(receivedEventTypes.contains("user.updated"))
        assertTrue(receivedEventTypes.contains("organization.created"))
        
        // Step 5: Verify event data integrity
        val receivedUserCreated = eventCollector.receivedEvents
            .filterIsInstance<UserCreatedEvent>()
            .first()
        assertEquals("john@example.com", receivedUserCreated.email)
        assertEquals("user-123", receivedUserCreated.aggregateId)
    }
    
    @Test
    fun `event serialization and deserialization should work correctly`() = runTest {
        val originalEvent = createUserCreatedEvent("user-789", "jane@example.com")
        
        // Step 1: Serialize event
        val serializedEvent = eventSerializer.serialize(originalEvent)
        assertTrue(serializedEvent.isNotEmpty())
        assertTrue(serializedEvent.contains("user-789"))
        assertTrue(serializedEvent.contains("jane@example.com"))
        
        // Step 2: Deserialize event
        val deserializedEvent = eventSerializer.deserialize(serializedEvent, "user.created")
        assertNotNull(deserializedEvent)
        assertTrue(deserializedEvent is UserCreatedEvent)
        
        val userCreatedEvent = deserializedEvent as UserCreatedEvent
        assertEquals(originalEvent.eventId, userCreatedEvent.eventId)
        assertEquals(originalEvent.email, userCreatedEvent.email)
        assertEquals(originalEvent.aggregateId, userCreatedEvent.aggregateId)
        assertEquals(originalEvent.organizationId, userCreatedEvent.organizationId)
        
        // Step 3: Test unsupported event type
        val unknownEvent = eventSerializer.deserialize(serializedEvent, "unknown.event")
        assertNull(unknownEvent)
    }
    
    @Test
    fun `pattern-based event subscription should work correctly`() = runTest {
        val userEventCollector = EventCollector()
        val vaultEventCollector = EventCollector()
        val allEventCollector = EventCollector()
        
        // Step 1: Subscribe to patterns
        eventBus.subscribePattern("user.*", userEventCollector)
        eventBus.subscribePattern("vault.*", vaultEventCollector)
        eventBus.subscribePattern("*", allEventCollector)
        
        // Step 2: Publish various events
        val events = listOf(
            createUserCreatedEvent("user-1", "user1@example.com"),
            createUserUpdatedEvent("user-1", mapOf("status" to "active")),
            createSecretCreatedEvent("secret-1", "api-key"),
            createSecretAccessedEvent("secret-1", "read"),
            createWorkflowCreatedEvent("workflow-1", "ci-cd")
        )
        
        events.forEach { event ->
            eventBus.publish(event)
        }
        
        delay(100)
        
        // Step 3: Verify pattern matching
        assertEquals(2, userEventCollector.receivedEvents.size) // user.created, user.updated
        assertEquals(2, vaultEventCollector.receivedEvents.size) // vault.secret_created, vault.secret_accessed
        assertEquals(5, allEventCollector.receivedEvents.size) // All events
        
        // Step 4: Verify correct event types
        val userEventTypes = userEventCollector.receivedEvents.map { it.eventType }.toSet()
        assertEquals(setOf("user.created", "user.updated"), userEventTypes)
        
        val vaultEventTypes = vaultEventCollector.receivedEvents.map { it.eventType }.toSet()
        assertEquals(setOf("vault.secret_created", "vault.secret_accessed"), vaultEventTypes)
    }
    
    @Test
    fun `event bus should handle multiple handlers gracefully`() = runTest {
        val handler1 = EventCollector("handler-1")
        val handler2 = EventCollector("handler-2")
        val handler3 = EventCollector("handler-3")
        val faultyHandler = FaultyEventHandler()
        
        // Step 1: Subscribe multiple handlers to same event
        eventBus.subscribe("user.created", handler1)
        eventBus.subscribe("user.created", handler2)
        eventBus.subscribe("user.created", handler3)
        eventBus.subscribe("user.created", faultyHandler)
        
        // Step 2: Publish event
        val event = createUserCreatedEvent("user-multi", "multi@example.com")
        eventBus.publish(event)
        
        delay(100)
        
        // Step 3: Verify all good handlers received the event
        assertEquals(1, handler1.receivedEvents.size)
        assertEquals(1, handler2.receivedEvents.size)
        assertEquals(1, handler3.receivedEvents.size)
        
        // Step 4: Verify faulty handler didn't break the system
        assertEquals(1, faultyHandler.errorCount)
        
        // Step 5: Verify event data is correct in all handlers
        listOf(handler1, handler2, handler3).forEach { handler ->
            val receivedEvent = handler.receivedEvents.first() as UserCreatedEvent
            assertEquals("multi@example.com", receivedEvent.email)
            assertEquals("user-multi", receivedEvent.aggregateId)
        }
    }
    
    @Test
    fun `event bus should handle high volume of events`() = runTest {
        val eventCollector = EventCollector()
        eventBus.subscribe("user.created", eventCollector)
        
        val eventCount = 1000
        val events = mutableListOf<DomainEvent>()
        
        // Step 1: Generate many events
        repeat(eventCount) { index ->
            events.add(createUserCreatedEvent("user-$index", "user$index@example.com"))
        }
        
        val startTime = System.currentTimeMillis()
        
        // Step 2: Publish all events
        eventBus.publishAll(events)
        
        // Step 3: Wait for processing
        delay(1000) // Give more time for high volume
        
        val endTime = System.currentTimeMillis()
        val processingTime = endTime - startTime
        
        // Step 4: Verify all events were processed
        assertEquals(eventCount, eventCollector.receivedEvents.size)
        
        // Step 5: Verify performance (should process 1000 events in reasonable time)
        assertTrue(processingTime < 5000, "High volume processing took too long: ${processingTime}ms")
        println("Processed $eventCount events in ${processingTime}ms")
        
        // Step 6: Verify event data integrity
        val firstEvent = eventCollector.receivedEvents.first() as UserCreatedEvent
        assertTrue(firstEvent.email.startsWith("user"))
        assertTrue(firstEvent.aggregateId.startsWith("user-"))
    }
    
    @Test
    fun `event unsubscription should work correctly`() = runTest {
        val eventCollector = EventCollector()
        
        // Step 1: Subscribe and publish event
        eventBus.subscribe("user.created", eventCollector)
        eventBus.publish(createUserCreatedEvent("user-1", "user1@example.com"))
        delay(50)
        
        assertEquals(1, eventCollector.receivedEvents.size)
        
        // Step 2: Unsubscribe and publish another event
        eventBus.unsubscribe("user.created", eventCollector)
        eventBus.publish(createUserCreatedEvent("user-2", "user2@example.com"))
        delay(50)
        
        // Step 3: Verify no new events received after unsubscription
        assertEquals(1, eventCollector.receivedEvents.size)
        
        // Step 4: Re-subscribe and verify it works again
        eventBus.subscribe("user.created", eventCollector)
        eventBus.publish(createUserCreatedEvent("user-3", "user3@example.com"))
        delay(50)
        
        assertEquals(2, eventCollector.receivedEvents.size)
    }
    
    @Test
    fun `event factory should create correct instances`() {
        val inMemoryBus = EventBusFactory.createInMemoryEventBus()
        assertNotNull(inMemoryBus)
        assertTrue(inMemoryBus is InMemoryEventBus)
        
        val configBus = EventBusFactory.createEventBusFromConfig()
        assertNotNull(configBus)
        // Should create in-memory bus when no Redis config
        assertTrue(configBus is InMemoryEventBus)
    }
    
    @Test
    fun `event metadata should be preserved correctly`() = runTest {
        val eventCollector = EventCollector()
        eventBus.subscribe("user.created", eventCollector)
        
        val metadata = mapOf(
            "source" to "integration-test",
            "version" to "1.0",
            "correlation-id" to "test-correlation-123"
        )
        
        val event = UserCreatedEvent(
            eventId = "event-metadata-test",
            aggregateId = "user-metadata",
            organizationId = "org-metadata",
            userId = "user-metadata",
            timestamp = Clock.System.now(),
            metadata = metadata,
            email = "metadata@example.com",
            profile = mapOf("source" to "test")
        )
        
        eventBus.publish(event)
        delay(50)
        
        assertEquals(1, eventCollector.receivedEvents.size)
        val receivedEvent = eventCollector.receivedEvents.first()
        assertEquals(metadata, receivedEvent.metadata)
        assertEquals("integration-test", receivedEvent.metadata["source"])
        assertEquals("test-correlation-123", receivedEvent.metadata["correlation-id"])
    }
    
    // Helper methods for creating test events
    private fun createUserCreatedEvent(userId: String, email: String): UserCreatedEvent {
        return UserCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = userId,
            organizationId = "org-123",
            userId = userId,
            timestamp = Clock.System.now(),
            email = email,
            profile = mapOf("firstName" to "Test", "lastName" to "User")
        )
    }
    
    private fun createUserUpdatedEvent(userId: String, changes: Map<String, String>): UserUpdatedEvent {
        return UserUpdatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = userId,
            organizationId = "org-123",
            userId = userId,
            timestamp = Clock.System.now(),
            changes = changes
        )
    }
    
    private fun createOrganizationCreatedEvent(orgId: String, name: String): OrganizationCreatedEvent {
        return OrganizationCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = orgId,
            organizationId = orgId,
            userId = "admin-user",
            timestamp = Clock.System.now(),
            name = name,
            slug = name.lowercase().replace(" ", "-")
        )
    }
    
    private fun createSecretCreatedEvent(secretId: String, secretType: String): SecretCreatedEvent {
        return SecretCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = secretId,
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            secretName = "test-secret-$secretId",
            secretType = secretType
        )
    }
    
    private fun createSecretAccessedEvent(secretId: String, accessType: String): SecretAccessedEvent {
        return SecretAccessedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = secretId,
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            secretName = "test-secret-$secretId",
            accessType = accessType
        )
    }
    
    private fun createWorkflowCreatedEvent(workflowId: String, workflowType: String): WorkflowCreatedEvent {
        return WorkflowCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = workflowId,
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            workflowName = "test-workflow-$workflowId",
            workflowType = workflowType
        )
    }
}

class EventCollector(override val handlerName: String = "event-collector") : EventHandler {
    val receivedEvents = mutableListOf<DomainEvent>()
    
    override suspend fun handle(event: DomainEvent) {
        receivedEvents.add(event)
    }
    
    override fun getSupportedEventTypes(): Set<String> = setOf("*")
}

class FaultyEventHandler : EventHandler {
    var errorCount = 0
    
    override suspend fun handle(event: DomainEvent) {
        errorCount++
        throw RuntimeException("Simulated handler error")
    }
    
    override fun getSupportedEventTypes(): Set<String> = setOf("*")
    override val handlerName: String = "faulty-handler"
}