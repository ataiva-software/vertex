package com.ataiva.eden.ai

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * JavaScript-specific time utilities to ensure cross-platform compatibility
 */
actual object TimeUtils {
    /**
     * Get current timestamp as a string
     */
    actual fun getCurrentTimestampId(): String {
        return Clock.System.now().toString()
    }
}