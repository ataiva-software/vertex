package com.ataiva.eden.hub.stubs

import java.util.concurrent.TimeUnit

/**
 * Stub implementation of MeterRegistry for development/testing
 */
class MeterRegistry {
    fun counter(name: String, tags: List<Tag>): Counter {
        return Counter()
    }

    fun timer(name: String, tags: List<Tag>): Timer {
        return Timer()
    }

    fun gauge(name: String, tags: List<Tag>, value: Double): Double {
        return value
    }

    class Counter {
        fun increment() {
            // Stub implementation - does nothing
        }
    }

    class Timer {
        fun record(amount: Long, unit: TimeUnit) {
            // Stub implementation - does nothing
        }
    }
}

/**
 * Stub implementation of Tag for development/testing
 */
class Tag private constructor(val key: String, val value: String) {
    companion object {
        fun of(key: String, value: String): Tag {
            return Tag(key, value)
        }
    }
}