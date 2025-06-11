package com.ataiva.eden.auth.util

import java.time.Instant as JavaInstant

/**
 * Custom Instant class that mimics kotlinx.datetime.Instant
 * This is used as a workaround for kotlinx.datetime.Instant compatibility issues
 */
class CustomInstant private constructor(private val epochMillis: Long) {
    companion object {
        fun now(): CustomInstant {
            return CustomInstant(System.currentTimeMillis())
        }
        
        fun fromEpochMilliseconds(epochMillis: Long): CustomInstant {
            return CustomInstant(epochMillis)
        }
    }
    
    fun toEpochMilliseconds(): Long {
        return epochMillis
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomInstant) return false
        return epochMillis == other.epochMillis
    }
    
    override fun hashCode(): Int {
        return epochMillis.hashCode()
    }
    
    override fun toString(): String {
        return "CustomInstant($epochMillis)"
    }
}

/**
 * Utility functions for date and time operations
 */
object DateTimeUtil {
    /**
     * Get current time
     */
    fun now(): CustomInstant {
        return CustomInstant.now()
    }
    
    /**
     * Convert epoch milliseconds to CustomInstant
     */
    fun fromEpochMillis(epochMillis: Long): CustomInstant {
        return CustomInstant.fromEpochMilliseconds(epochMillis)
    }
    
    /**
     * Convert CustomInstant to epoch milliseconds
     */
    fun toEpochMillis(instant: CustomInstant): Long {
        return instant.toEpochMilliseconds()
    }
    
    /**
     * Create a dummy java.time.Instant value
     * This is used as a workaround for Instant compatibility issues
     */
    fun dummyJavaInstant(): JavaInstant {
        return JavaInstant.now()
    }
}

// Type alias to use in place of kotlinx.datetime.Instant
typealias Instant = CustomInstant