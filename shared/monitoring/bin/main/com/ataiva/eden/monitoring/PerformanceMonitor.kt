package com.ataiva.eden.monitoring

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Performance data for a specific operation
 */
@Serializable
data class OperationPerformanceData(
    val operationName: String,
    val invocationCount: Long = 0,
    val totalTimeMs: Long = 0,
    val minTimeMs: Long = Long.MAX_VALUE,
    val maxTimeMs: Long = 0,
    val averageTimeMs: Double = 0.0,
    val errorCount: Long = 0,
    val lastExecutionTimeMs: Long = 0,
    val lastExecutedAt: Long = 0
)

/**
 * System performance data
 */
@Serializable
data class SystemPerformanceData(
    val timestamp: Long = Instant.now().toEpochMilli(),
    val cpuUsagePercent: Double,
    val systemLoadAverage: Double,
    val heapMemoryUsedMb: Long,
    val heapMemoryMaxMb: Long,
    val heapMemoryUsagePercent: Double,
    val nonHeapMemoryUsedMb: Long,
    val threadCount: Int,
    val daemonThreadCount: Int,
    val peakThreadCount: Int,
    val totalStartedThreadCount: Long,
    val gcCollectionCount: Long,
    val gcCollectionTimeMs: Long,
    @Contextual
    val uptime: Duration
)

/**
 * Performance monitor for tracking operation and system performance
 */
class PerformanceMonitor(
    openTelemetry: OpenTelemetry,
    private val serviceName: String
) {
    private val meter: Meter = openTelemetry.getMeter("com.ataiva.eden.monitoring.performance")
    private val tracer: Tracer = openTelemetry.getTracer("com.ataiva.eden.monitoring.performance")
    
    private val operationDurations: DoubleHistogram
    private val operationCounter: LongCounter
    private val operationErrorCounter: LongCounter
    
    private val operationData = ConcurrentHashMap<String, OperationPerformanceData>()
    private val invocationCounts = ConcurrentHashMap<String, AtomicLong>()
    private val totalTimes = ConcurrentHashMap<String, AtomicLong>()
    private val minTimes = ConcurrentHashMap<String, AtomicLong>()
    private val maxTimes = ConcurrentHashMap<String, AtomicLong>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val lastExecutionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val lastExecutedAts = ConcurrentHashMap<String, AtomicLong>()
    
    init {
        operationDurations = meter.histogramBuilder("operation.duration")
            .setDescription("Duration of operations")
            .setUnit("ms")
            .build()
        
        operationCounter = meter.counterBuilder("operation.count")
            .setDescription("Number of operations executed")
            .setUnit("1")
            .build()
        
        operationErrorCounter = meter.counterBuilder("operation.errors")
            .setDescription("Number of operation errors")
            .setUnit("1")
            .build()
    }
    
    /**
     * Track the execution time of a synchronous operation
     */
    fun <T> trackOperation(operationName: String, operation: () -> T): T {
        val startTime = System.currentTimeMillis()
        val span = tracer.spanBuilder("$serviceName.$operationName").startSpan()
        
        return try {
            span.makeCurrent().use {
                val result = operation()
                val executionTime = System.currentTimeMillis() - startTime
                recordOperationMetrics(operationName, executionTime, false)
                result
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            recordOperationMetrics(operationName, executionTime, true)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "Operation failed")
            throw e
        } finally {
            span.end()
        }
    }
    
    /**
     * Track the execution time of a suspending operation
     */
    suspend fun <T> trackSuspendOperation(operationName: String, operation: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val span = tracer.spanBuilder("$serviceName.$operationName").startSpan()
        
        return try {
            span.makeCurrent().use {
                val result = operation()
                val executionTime = System.currentTimeMillis() - startTime
                recordOperationMetrics(operationName, executionTime, false)
                result
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            recordOperationMetrics(operationName, executionTime, true)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "Operation failed")
            throw e
        } finally {
            span.end()
        }
    }
    
    /**
     * Record operation metrics
     */
    private fun recordOperationMetrics(operationName: String, executionTimeMs: Long, isError: Boolean) {
        // Update OpenTelemetry metrics
        val attributes = Attributes.builder()
            .put("service", serviceName)
            .put("operation", operationName)
            .build()
        
        operationDurations.record(executionTimeMs.toDouble(), attributes)
        operationCounter.add(1, attributes)
        
        if (isError) {
            operationErrorCounter.add(1, attributes)
        }
        
        // Update internal metrics
        invocationCounts.computeIfAbsent(operationName) { AtomicLong(0) }.incrementAndGet()
        totalTimes.computeIfAbsent(operationName) { AtomicLong(0) }.addAndGet(executionTimeMs)
        
        // Update min time
        val currentMin = minTimes.computeIfAbsent(operationName) { AtomicLong(Long.MAX_VALUE) }.get()
        if (executionTimeMs < currentMin) {
            minTimes[operationName]?.set(executionTimeMs)
        }
        
        // Update max time
        val currentMax = maxTimes.computeIfAbsent(operationName) { AtomicLong(0) }.get()
        if (executionTimeMs > currentMax) {
            maxTimes[operationName]?.set(executionTimeMs)
        }
        
        // Update error count
        if (isError) {
            errorCounts.computeIfAbsent(operationName) { AtomicLong(0) }.incrementAndGet()
        }
        
        // Update last execution time and timestamp
        lastExecutionTimes[operationName] = AtomicLong(executionTimeMs)
        lastExecutedAts[operationName] = AtomicLong(System.currentTimeMillis())
        
        // Update operation data
        updateOperationData(operationName)
    }
    
    /**
     * Update operation data
     */
    private fun updateOperationData(operationName: String) {
        val invocationCount = invocationCounts[operationName]?.get() ?: 0
        val totalTime = totalTimes[operationName]?.get() ?: 0
        val minTime = minTimes[operationName]?.get() ?: 0
        val maxTime = maxTimes[operationName]?.get() ?: 0
        val errorCount = errorCounts[operationName]?.get() ?: 0
        val lastExecutionTime = lastExecutionTimes[operationName]?.get() ?: 0
        val lastExecutedAt = lastExecutedAts[operationName]?.get() ?: 0
        
        val averageTime = if (invocationCount > 0) totalTime.toDouble() / invocationCount.toDouble() else 0.0
        
        val data = OperationPerformanceData(
            operationName = operationName,
            invocationCount = invocationCount,
            totalTimeMs = totalTime,
            minTimeMs = if (minTime == Long.MAX_VALUE) 0 else minTime,
            maxTimeMs = maxTime,
            averageTimeMs = averageTime,
            errorCount = errorCount,
            lastExecutionTimeMs = lastExecutionTime,
            lastExecutedAt = lastExecutedAt
        )
        
        operationData[operationName] = data
    }
    
    /**
     * Get performance data for a specific operation
     */
    fun getOperationPerformanceData(operationName: String): OperationPerformanceData? {
        return operationData[operationName]
    }
    
    /**
     * Get performance data for all operations
     */
    fun getAllOperationPerformanceData(): List<OperationPerformanceData> {
        return operationData.values.toList()
    }
    
    /**
     * Get system performance data
     */
    suspend fun getSystemPerformanceData(): SystemPerformanceData = withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        val threadMXBean = ManagementFactory.getThreadMXBean()
        val osMXBean = ManagementFactory.getOperatingSystemMXBean()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        
        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage
        
        val heapMemoryUsedMb = heapMemoryUsage.used / (1024 * 1024)
        val heapMemoryMaxMb = heapMemoryUsage.max / (1024 * 1024)
        val heapMemoryUsagePercent = (heapMemoryUsage.used.toDouble() / heapMemoryUsage.max.toDouble()) * 100.0
        val nonHeapMemoryUsedMb = nonHeapMemoryUsage.used / (1024 * 1024)
        
        val cpuUsagePercent = osMXBean.systemLoadAverage * 100.0 / runtime.availableProcessors()
        val systemLoadAverage = osMXBean.systemLoadAverage
        
        val threadCount = threadMXBean.threadCount
        val daemonThreadCount = threadMXBean.daemonThreadCount
        val peakThreadCount = threadMXBean.peakThreadCount
        val totalStartedThreadCount = threadMXBean.totalStartedThreadCount
        
        // Get GC information
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        var gcCollectionCount = 0L
        var gcCollectionTimeMs = 0L
        
        for (gcBean in gcBeans) {
            val count = gcBean.collectionCount
            val time = gcBean.collectionTime
            
            if (count >= 0) {
                gcCollectionCount += count
            }
            
            if (time >= 0) {
                gcCollectionTimeMs += time
            }
        }
        
        val uptime = Duration.ofMillis(runtimeMXBean.uptime)
        
        SystemPerformanceData(
            timestamp = Instant.now().toEpochMilli(),
            cpuUsagePercent = cpuUsagePercent,
            systemLoadAverage = systemLoadAverage,
            heapMemoryUsedMb = heapMemoryUsedMb,
            heapMemoryMaxMb = heapMemoryMaxMb,
            heapMemoryUsagePercent = heapMemoryUsagePercent,
            nonHeapMemoryUsedMb = nonHeapMemoryUsedMb,
            threadCount = threadCount,
            daemonThreadCount = daemonThreadCount,
            peakThreadCount = peakThreadCount,
            totalStartedThreadCount = totalStartedThreadCount,
            gcCollectionCount = gcCollectionCount,
            gcCollectionTimeMs = gcCollectionTimeMs,
            uptime = uptime
        )
    }
    
    /**
     * Create a span for manual performance tracking
     */
    fun createPerformanceSpan(operationName: String): Span {
        return tracer.spanBuilder("$serviceName.$operationName").startSpan()
    }
}

/**
 * Extension function to measure and record the execution time of a block of code
 */
inline fun <T> PerformanceMonitor.measure(operationName: String, noinline block: () -> T): T {
    return trackOperation(operationName, block)
}

/**
 * Extension function to measure and record the execution time of a suspending block of code
 */
suspend inline fun <T> PerformanceMonitor.measureSuspend(operationName: String, noinline block: suspend () -> T): T {
    return trackSuspendOperation(operationName, block)
}