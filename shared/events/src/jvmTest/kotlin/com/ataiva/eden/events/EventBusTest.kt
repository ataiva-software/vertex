package com.ataiva.eden.events

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

class EventBusTest {
    
    private lateinit var eventBus: InMemoryEventBus
    private val testEvents = mutableListOf<DomainEvent>()
    
    @BeforeTest
    fun setup() = runTest {
        eventBus = InMemoryEventBus()
        testEvents.clear()
        eventBus.start()
    }
    
    @AfterTest
    fun cleanup() = runTest {
        eventBus.stop()
    }
    
    @Test
    fun `event bus should start and stop correctly`() = runTest {
        val newEventBus = InMemoryEventBus()
        
        newEventBus.start()
        // Should not throw exception when started
        
        newEventBus.stop()
        // Should not throw exception when stopped
    }
    
    @Test
    fun `publish and subscribe should work correctly`() = runTest {
        val handler = TestEventHandler("test-handler")
        eventBus.subscribe("user.created", handler)
        
        val event = createUserCreatedEvent()
        eventBus.publish(event)
        
        // Give some time for async processing
        delay(100)
        
        assertEquals(1, handler.handledEvents.size)
        assertEquals(event.eventId, handler.handledEvents[0].eventId)
    }
    
    @Test
    fun `publish multiple events should work correctly`() = runTest {
        val handler = TestEventHandler("test-handler")
        eventBus.subscribe("user.created", handler)
        
        val events = listOf(
            createUserCreatedEvent("user-1"),
            createUserCreatedEvent("user-2"),
            createUserCreatedEvent("user-3")
        )
        
        eventBus.publishAll(events)
        
        // Give some time for async processing
        delay(100)
        
        assertEquals(3, handler.handledEvents.size)
        assertEquals(events.map { it.eventId }.toSet(), handler.handledEvents.map { it.eventId }.toSet())
    }
    
    @Test
    fun `multiple handlers for same event should all receive event`() = runTest {
        val handler1 = TestEventHandler("handler-1")
        val handler2 = TestEventHandler("handler-2")
        
        eventBus.subscribe("user.created", handler1)
        eventBus.subscribe("user.created", handler2)
        
        val event = createUserCreatedEvent()
        eventBus.publish(event)
        
        // Give some time for async processing
        delay(100)
        
        assertEquals(1, handler1.handledEvents.size)
        assertEquals(1, handler2.handledEvents.size)
        assertEquals(event.eventId, handler1.handledEvents[0].eventId)
        assertEquals(event.eventId, handler2.handledEvents[0].eventId)
    }
    
    @Test
    fun `unsubscribe should stop receiving events`() = runTest {
        val handler = TestEventHandler("test-handler")
        eventBus.subscribe("user.created", handler)
        
        val event1 = createUserCreatedEvent("user-1")
        eventBus.publish(event1)
        
        delay(100)
        assertEquals(1, handler.handledEvents.size)
        
        eventBus.unsubscribe("user.created", handler)
        
        val event2 = createUserCreatedEvent("user-2")
        eventBus.publish(event2)
        
        delay(100)
        assertEquals(1, handler.handledEvents.size) // Should still be 1
    }
    
    @Test
    fun `pattern subscription should work correctly`() = runTest {
        val handler = TestEventHandler("pattern-handler", setOf("user.*"))
        eventBus.subscribePattern("user.*", handler)
        
        val userCreatedEvent = createUserCreatedEvent()
        val userUpdatedEvent = createUserUpdatedEvent()
        val orgCreatedEvent = createOrganizationCreatedEvent()
        
        eventBus.publish(userCreatedEvent)
        eventBus.publish(userUpdatedEvent)
        eventBus.publish(orgCreatedEvent)
        
        delay(100)
        
        // Should receive user events but not organization event
        assertEquals(2, handler.handledEvents.size)
        assertTrue(handler.handledEvents.any { it.eventType == "user.created" })
        assertTrue(handler.handledEvents.any { it.eventType == "user.updated" })
        assertFalse(handler.handledEvents.any { it.eventType == "organization.created" })
    }
    
    @Test
    fun `handler exception should not stop event processing`() = runTest {
        val goodHandler = TestEventHandler("good-handler")
        val badHandler = object : EventHandler {
            override suspend fun handle(event: DomainEvent) {
                throw RuntimeException("Handler error")
            }
            
            override fun getSupportedEventTypes(): Set<String> = setOf("user.created")
            override val handlerName: String = "bad-handler"
        }
        
        eventBus.subscribe("user.created", goodHandler)
        eventBus.subscribe("user.created", badHandler)
        
        val event = createUserCreatedEvent()
        eventBus.publish(event)
        
        delay(100)
        
        // Good handler should still receive the event despite bad handler throwing
        assertEquals(1, goodHandler.handledEvents.size)
    }
    
    @Test
    fun `event serializer should work correctly`() {
        val serializer = JsonEventSerializer()
        val event = createUserCreatedEvent()
        
        val serialized = serializer.serialize(event)
        assertTrue(serialized.isNotEmpty())
        assertTrue(serialized.contains(event.eventId))
        assertTrue(serialized.contains(event.email))
        
        val deserialized = serializer.deserialize(serialized, event.eventType)
        assertNotNull(deserialized)
        assertTrue(deserialized is UserCreatedEvent)
        assertEquals(event.eventId, deserialized.eventId)
        assertEquals(event.email, (deserialized as UserCreatedEvent).email)
        
        val supportedTypes = serializer.getSupportedTypes()
        assertTrue(supportedTypes.contains("user.created"))
        assertTrue(supportedTypes.contains("organization.created"))
        assertTrue(supportedTypes.contains("vault.secret_created"))
    }
    
    @Test
    fun `event serializer should handle unknown event types`() {
        val serializer = JsonEventSerializer()
        val result = serializer.deserialize("{}", "unknown.event")
        assertNull(result)
    }
    
    @Test
    fun `event bus factory should create instances correctly`() {
        val inMemoryBus = EventBusFactory.createInMemoryEventBus()
        assertNotNull(inMemoryBus)
        assertTrue(inMemoryBus is InMemoryEventBus)
        
        val configBus = EventBusFactory.createEventBusFromConfig()
        assertNotNull(configBus)
        // Should create in-memory bus when no Redis config is present
        assertTrue(configBus is InMemoryEventBus)
    }
    
    @Test
    fun `different event types should be handled correctly`() = runTest {
        val allEventsHandler = TestEventHandler("all-events", setOf(
            "user.created", "user.updated", "organization.created",
            "vault.secret_created", "flow.workflow_created", "task.created"
        ))
        
        eventBus.subscribe("user.created", allEventsHandler)
        eventBus.subscribe("user.updated", allEventsHandler)
        eventBus.subscribe("organization.created", allEventsHandler)
        eventBus.subscribe("vault.secret_created", allEventsHandler)
        eventBus.subscribe("flow.workflow_created", allEventsHandler)
        eventBus.subscribe("task.created", allEventsHandler)
        
        val events = listOf(
            createUserCreatedEvent(),
            createUserUpdatedEvent(),
            createOrganizationCreatedEvent(),
            createSecretCreatedEvent(),
            createWorkflowCreatedEvent(),
            createTaskCreatedEvent()
        )
        
        eventBus.publishAll(events)
        delay(100)
        
        assertEquals(6, allEventsHandler.handledEvents.size)
        
        val eventTypes = allEventsHandler.handledEvents.map { it.eventType }.toSet()
        assertEquals(6, eventTypes.size)
        assertTrue(eventTypes.contains("user.created"))
        assertTrue(eventTypes.contains("user.updated"))
        assertTrue(eventTypes.contains("organization.created"))
        assertTrue(eventTypes.contains("vault.secret_created"))
        assertTrue(eventTypes.contains("flow.workflow_created"))
        assertTrue(eventTypes.contains("task.created"))
    }
    
    private fun createUserCreatedEvent(userId: String = "user-123"): UserCreatedEvent {
        return UserCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = userId,
            organizationId = "org-123",
            userId = userId,
            timestamp = Clock.System.now(),
            email = "test@example.com",
            profile = mapOf("firstName" to "Test", "lastName" to "User")
        )
    }
    
    private fun createUserUpdatedEvent(userId: String = "user-123"): UserUpdatedEvent {
        return UserUpdatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = userId,
            organizationId = "org-123",
            userId = userId,
            timestamp = Clock.System.now(),
            changes = mapOf("firstName" to "Updated Name")
        )
    }
    
    private fun createOrganizationCreatedEvent(): OrganizationCreatedEvent {
        return OrganizationCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = "org-123",
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            name = "Test Organization",
            slug = "test-org"
        )
    }
    
    private fun createSecretCreatedEvent(): SecretCreatedEvent {
        return SecretCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = "secret-123",
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            secretName = "test-secret",
            secretType = "api-key"
        )
    }
    
    private fun createWorkflowCreatedEvent(): WorkflowCreatedEvent {
        return WorkflowCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = "workflow-123",
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            workflowName = "test-workflow",
            workflowType = "ci-cd"
        )
    }
    
    private fun createTaskCreatedEvent(): TaskCreatedEvent {
        return TaskCreatedEvent(
            eventId = "event-${System.currentTimeMillis()}-${(0..999).random()}",
            aggregateId = "task-123",
            organizationId = "org-123",
            userId = "user-123",
            timestamp = Clock.System.now(),
            taskName = "test-task",
            taskType = "build"
        )
    }
}

class TestEventHandler(
    override val handlerName: String,
    private val supportedTypes: Set<String> = setOf("user.created")
) : EventHandler {
    
    val handledEvents = mutableListOf<DomainEvent>()
    
    override suspend fun handle(event: DomainEvent) {
        handledEvents.add(event)
    }
    
    override fun getSupportedEventTypes(): Set<String> = supportedTypes
}