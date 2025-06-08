package com.ataiva.eden.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.datetime.Clock

/**
 * Simple JavaScript test for the events module.
 * This test only verifies basic functionality that can run in JS environment.
 */
class EventsJsTest {
    @Test
    fun testUserCreatedEvent() {
        val timestamp = Clock.System.now()
        val eventId = "test-event-id"
        val userId = "test-user-id"
        val email = "test@example.com"
        
        val event = UserCreatedEvent(
            eventId = eventId,
            aggregateId = userId,
            organizationId = "org-1",
            userId = userId,
            timestamp = timestamp,
            email = email
        )
        
        assertEquals(eventId, event.eventId)
        assertEquals(userId, event.aggregateId)
        assertEquals("user.created", event.eventType)
        assertEquals("user", event.aggregateType)
        assertEquals(email, event.email)
        assertEquals(1, event.version)
        assertNotNull(event.timestamp)
    }
    
    @Test
    fun testTaskExecutedEvent() {
        val timestamp = Clock.System.now()
        val eventId = "test-event-id"
        val taskId = "test-task-id"
        
        val event = TaskExecutedEvent(
            eventId = eventId,
            aggregateId = taskId,
            organizationId = "org-1",
            userId = "user-1",
            timestamp = timestamp,
            taskName = "Test Task",
            executionId = "exec-1",
            status = "completed",
            exitCode = 0,
            duration = 1000L
        )
        
        assertEquals(eventId, event.eventId)
        assertEquals(taskId, event.aggregateId)
        assertEquals("task.executed", event.eventType)
        assertEquals("task", event.aggregateType)
        assertEquals("Test Task", event.taskName)
        assertEquals("completed", event.status)
        assertEquals(0, event.exitCode)
        assertEquals(1000L, event.duration)
    }
}