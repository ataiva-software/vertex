package com.ataiva.eden.auth.util

import java.lang.reflect.Method

/**
 * Utility class for working with kotlinx.datetime.Instant
 * This is a workaround for kotlinx.datetime.Instant compatibility issues
 */
object InstantUtil {
    /**
     * Create a dummy kotlinx.datetime.Instant value using reflection
     * This is a hack to create a dummy Instant value for non-nullable fields
     */
    fun dummyInstant(): Any {
        try {
            // Try to load the Instant class using reflection
            val instantClass = Class.forName("kotlinx.datetime.Instant")
            
            // Try to find the fromEpochMilliseconds method
            val method = instantClass.getDeclaredMethod("fromEpochMilliseconds", Long::class.java)
            
            // Call the method to create a dummy Instant
            return method.invoke(null, 0L)
        } catch (e: Exception) {
            // If reflection fails, return a dummy object
            return object {
                override fun toString() = "DummyInstant"
            }
        }
    }
}