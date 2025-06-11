package com.ataiva.eden.monitoring

/**
 * Interface for collecting metrics
 */
interface MetricsCollector {
    /**
     * Increment a counter metric
     */
    fun incrementCounter(name: String, tags: Map<String, String> = emptyMap(), value: Long = 1)
    
    /**
     * Record a gauge metric
     */
    fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap())
    
    /**
     * Record a histogram metric
     */
    fun recordHistogram(name: String, value: Double, tags: Map<String, String> = emptyMap())
    
    /**
     * Start a timer
     */
    fun startTimer(name: String, tags: Map<String, String> = emptyMap()): Timer
    
    /**
     * Timer interface
     */
    interface Timer {
        /**
         * Stop the timer and record the duration
         */
        fun stop()
    }
}