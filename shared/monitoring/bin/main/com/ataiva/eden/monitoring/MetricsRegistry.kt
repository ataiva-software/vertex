package com.ataiva.eden.monitoring

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleCounter
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for application metrics
 */
class MetricsRegistry(openTelemetry: OpenTelemetry, instrumentationName: String) {
    private val meter: Meter = openTelemetry.getMeter(instrumentationName)
    private val counters = ConcurrentHashMap<String, LongCounter>()
    private val doubleCounters = ConcurrentHashMap<String, DoubleCounter>()
    private val gauges = ConcurrentHashMap<String, LongUpDownCounter>()
    private val histograms = ConcurrentHashMap<String, DoubleHistogram>()

    /**
     * Create or get a counter metric
     */
    fun counter(name: String, description: String, unit: String = "1"): LongCounter {
        return counters.computeIfAbsent(name) {
            meter.counterBuilder(name)
                .setDescription(description)
                .setUnit(unit)
                .build()
        }
    }

    /**
     * Create or get a double counter metric
     */
    fun doubleCounter(name: String, description: String, unit: String = "1"): DoubleCounter {
        return doubleCounters.computeIfAbsent(name) {
            meter.counterBuilder(name)
                .setDescription(description)
                .setUnit(unit)
                .ofDoubles()
                .build()
        }
    }

    /**
     * Create or get a gauge metric
     */
    fun gauge(name: String, description: String, unit: String = "1"): LongUpDownCounter {
        return gauges.computeIfAbsent(name) {
            meter.upDownCounterBuilder(name)
                .setDescription(description)
                .setUnit(unit)
                .build()
        }
    }

    /**
     * Create or get a histogram metric
     */
    fun histogram(name: String, description: String, unit: String = "1"): DoubleHistogram {
        return histograms.computeIfAbsent(name) {
            meter.histogramBuilder(name)
                .setDescription(description)
                .setUnit(unit)
                .build()
        }
    }

    /**
     * Increment a counter by 1
     */
    fun incrementCounter(name: String, vararg labels: Pair<String, String>) {
        val counter = counters[name] ?: return
        val attributes = buildAttributes(*labels)
        counter.add(1, attributes)
    }

    /**
     * Increment a counter by a specific amount
     */
    fun incrementCounter(name: String, value: Long, vararg labels: Pair<String, String>) {
        val counter = counters[name] ?: return
        val attributes = buildAttributes(*labels)
        counter.add(value, attributes)
    }

    /**
     * Increment a double counter by a specific amount
     */
    fun incrementDoubleCounter(name: String, value: Double, vararg labels: Pair<String, String>) {
        val counter = doubleCounters[name] ?: return
        val attributes = buildAttributes(*labels)
        counter.add(value, attributes)
    }

    /**
     * Set a gauge value
     */
    fun setGauge(name: String, value: Long, vararg labels: Pair<String, String>) {
        val gauge = gauges[name] ?: return
        val attributes = buildAttributes(*labels)
        gauge.add(value, attributes)
    }

    /**
     * Record a histogram value
     */
    fun recordHistogram(name: String, value: Double, vararg labels: Pair<String, String>) {
        val histogram = histograms[name] ?: return
        val attributes = buildAttributes(*labels)
        histogram.record(value, attributes)
    }

    /**
     * Build attributes from label pairs
     */
    private fun buildAttributes(vararg labels: Pair<String, String>): Attributes {
        val attributesBuilder = Attributes.builder()
        labels.forEach { (key, value) ->
            attributesBuilder.put(key, value)
        }
        return attributesBuilder.build()
    }

    companion object {
        // Common metric names
        const val REQUEST_COUNTER = "http.server.requests.count"
        const val REQUEST_DURATION = "http.server.requests.duration"
        const val ACTIVE_REQUESTS = "http.server.requests.active"
        const val DATABASE_QUERIES = "database.queries.count"
        const val DATABASE_QUERY_DURATION = "database.queries.duration"
        const val CACHE_HITS = "cache.hits"
        const val CACHE_MISSES = "cache.misses"
        const val JVM_MEMORY_USED = "jvm.memory.used"
        const val JVM_MEMORY_MAX = "jvm.memory.max"
        const val JVM_GC_COLLECTIONS = "jvm.gc.collections"
        const val JVM_GC_PAUSE = "jvm.gc.pause"
        const val SYSTEM_CPU_USAGE = "system.cpu.usage"
        const val SYSTEM_LOAD_AVERAGE = "system.load.average"
    }
}