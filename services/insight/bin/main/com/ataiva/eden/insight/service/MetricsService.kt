package com.ataiva.eden.insight.service

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import io.prometheus.client.hotspot.DefaultExports
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Service for collecting and exposing performance metrics.
 * Uses Prometheus for metrics collection and provides methods for tracking
 * various performance indicators.
 */
class MetricsService(
    private val registry: CollectorRegistry = CollectorRegistry.defaultRegistry
) {
    private val logger = LoggerFactory.getLogger(MetricsService::class.java)
    
    // Request metrics
    private val requestCounter = Counter.build()
        .name("insight_requests_total")
        .help("Total number of requests")
        .labelNames("endpoint", "method", "status")
        .register(registry)
    
    private val requestLatency = Histogram.build()
        .name("insight_request_duration_seconds")
        .help("Request duration in seconds")
        .labelNames("endpoint", "method")
        .buckets(0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0)
        .register(registry)
    
    // Database metrics
    private val queryCounter = Counter.build()
        .name("insight_database_queries_total")
        .help("Total number of database queries")
        .labelNames("type", "status")
        .register(registry)
    
    private val queryLatency = Histogram.build()
        .name("insight_database_query_duration_seconds")
        .help("Database query duration in seconds")
        .labelNames("type")
        .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0)
        .register(registry)
    
    private val connectionPoolSize = Gauge.build()
        .name("insight_database_connection_pool_size")
        .help("Database connection pool size")
        .labelNames("state")
        .register(registry)
    
    // Cache metrics
    private val cacheHits = Counter.build()
        .name("insight_cache_hits_total")
        .help("Total number of cache hits")
        .labelNames("cache", "type")
        .register(registry)
    
    private val cacheMisses = Counter.build()
        .name("insight_cache_misses_total")
        .help("Total number of cache misses")
        .labelNames("cache", "type")
        .register(registry)
    
    private val cacheSize = Gauge.build()
        .name("insight_cache_size")
        .help("Current cache size")
        .labelNames("cache")
        .register(registry)
    
    // JVM metrics
    private val jvmMemoryUsage = Gauge.build()
        .name("insight_jvm_memory_bytes_used")
        .help("JVM memory usage in bytes")
        .labelNames("area")
        .register(registry)
    
    private val jvmThreadCount = Gauge.build()
        .name("insight_jvm_threads")
        .help("JVM thread count")
        .labelNames("state")
        .register(registry)
    
    // Business metrics
    private val activeUsers = Gauge.build()
        .name("insight_active_users")
        .help("Number of active users")
        .register(registry)
    
    private val reportGenerationTime = Summary.build()
        .name("insight_report_generation_seconds")
        .help("Report generation time in seconds")
        .labelNames("report_type")
        .quantile(0.5, 0.05)   // Add 50th percentile with 5% error
        .quantile(0.9, 0.01)   // Add 90th percentile with 1% error
        .quantile(0.99, 0.001) // Add 99th percentile with 0.1% error
        .register(registry)
    
    // Timers for measuring operation durations
    private val timers = ConcurrentHashMap<String, Long>()
    
    init {
        // Register JVM metrics
        DefaultExports.initialize()
        logger.info("Metrics service initialized")
    }
    
    /**
     * Record a request
     */
    fun recordRequest(endpoint: String, method: String, status: Int) {
        requestCounter.labels(endpoint, method, status.toString()).inc()
    }
    
    /**
     * Record request latency
     */
    fun recordRequestLatency(endpoint: String, method: String, durationMs: Long) {
        requestLatency.labels(endpoint, method).observe(durationMs / 1000.0)
    }
    
    /**
     * Start timing an operation
     */
    fun startTimer(operationId: String) {
        timers[operationId] = System.nanoTime()
    }
    
    /**
     * Stop timing an operation and record the duration
     */
    fun stopTimer(operationId: String, endpoint: String, method: String): Long {
        val startTime = timers.remove(operationId) ?: return 0
        val durationNs = System.nanoTime() - startTime
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        recordRequestLatency(endpoint, method, durationMs)
        return durationMs
    }
    
    /**
     * Record a database query
     */
    fun recordDatabaseQuery(type: String, status: String) {
        queryCounter.labels(type, status).inc()
    }
    
    /**
     * Record database query latency
     */
    fun recordDatabaseQueryLatency(type: String, durationMs: Long) {
        queryLatency.labels(type).observe(durationMs / 1000.0)
    }
    
    /**
     * Update connection pool metrics
     */
    fun updateConnectionPoolMetrics(total: Int, active: Int, idle: Int, waiting: Int) {
        connectionPoolSize.labels("total").set(total.toDouble())
        connectionPoolSize.labels("active").set(active.toDouble())
        connectionPoolSize.labels("idle").set(idle.toDouble())
        connectionPoolSize.labels("waiting").set(waiting.toDouble())
    }
    
    /**
     * Record a cache hit
     */
    fun recordCacheHit(cache: String, type: String) {
        cacheHits.labels(cache, type).inc()
    }
    
    /**
     * Record a cache miss
     */
    fun recordCacheMiss(cache: String, type: String) {
        cacheMisses.labels(cache, type).inc()
    }
    
    /**
     * Update cache size
     */
    fun updateCacheSize(cache: String, size: Long) {
        cacheSize.labels(cache).set(size.toDouble())
    }
    
    /**
     * Update JVM memory metrics
     */
    fun updateJvmMemoryMetrics() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        
        jvmMemoryUsage.labels("used").set(usedMemory.toDouble())
        jvmMemoryUsage.labels("free").set(runtime.freeMemory().toDouble())
        jvmMemoryUsage.labels("total").set(runtime.totalMemory().toDouble())
        jvmMemoryUsage.labels("max").set(runtime.maxMemory().toDouble())
    }
    
    /**
     * Update JVM thread metrics
     */
    fun updateJvmThreadMetrics() {
        val threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean()
        jvmThreadCount.labels("total").set(threadMXBean.threadCount.toDouble())
        jvmThreadCount.labels("daemon").set(threadMXBean.daemonThreadCount.toDouble())
        jvmThreadCount.labels("peak").set(threadMXBean.peakThreadCount.toDouble())
    }
    
    /**
     * Update active users count
     */
    fun updateActiveUsers(count: Int) {
        activeUsers.set(count.toDouble())
    }
    
    /**
     * Record report generation time
     */
    fun recordReportGenerationTime(reportType: String, durationMs: Long) {
        reportGenerationTime.labels(reportType).observe(durationMs / 1000.0)
    }
    
    /**
     * Get cache hit ratio
     */
    fun getCacheHitRatio(cache: String, type: String): Double {
        val hits = cacheHits.labels(cache, type).get()
        val misses = cacheMisses.labels(cache, type).get()
        val total = hits + misses
        
        return if (total > 0) hits / total else 0.0
    }
}