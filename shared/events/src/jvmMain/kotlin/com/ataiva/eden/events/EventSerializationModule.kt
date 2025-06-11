package com.ataiva.eden.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Serialization module for domain events
 */
val eventSerializationModule = SerializersModule {
    polymorphic(DomainEvent::class) {
        subclass(UserCreatedEvent::class)
        subclass(UserUpdatedEvent::class)
        subclass(UserDeletedEvent::class)
        subclass(OrganizationCreatedEvent::class)
        subclass(OrganizationMemberAddedEvent::class)
        subclass(SecretCreatedEvent::class)
        subclass(SecretAccessedEvent::class)
        subclass(WorkflowCreatedEvent::class)
        subclass(WorkflowExecutedEvent::class)
        subclass(TaskCreatedEvent::class)
        subclass(TaskExecutedEvent::class)
    }
}