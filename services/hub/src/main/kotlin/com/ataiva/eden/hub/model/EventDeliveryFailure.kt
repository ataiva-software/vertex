package com.ataiva.eden.hub.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a failed event delivery that may be retried
 */
@Serializable
data class EventDeliveryFailure(
    val id: String,
    val eventId: String,
    val subscriptionId: String,
    val endpoint: String,
    val errorMessage: String,
    val timestamp: Instant,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant
)