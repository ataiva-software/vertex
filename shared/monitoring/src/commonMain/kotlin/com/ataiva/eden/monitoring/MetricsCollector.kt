package com.ataiva.eden.monitoring

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive metrics collection system for Eden DevOps Suite
 * Provides real-time monitoring, alerting, and performance analytics
 */
interface MetricsCollector {
    suspend fun recordMetric(metric: Metric)
    suspend fun recordCounter(name: String, value: Double = 1.0, tags: Map<String, String> = emptyMap())
    suspend fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap())
    suspend fun recordTimer(name: String, duration: Duration, tags: Map<String, String> = emptyMap())
    suspend fun recordHistogram(name: String, value: Double, tags: Map<String, String> = emptyMap())
    
    fun getMetrics(filter: MetricFilter = MetricFilter.ALL): Flow<Metric>
    fun getAggregatedMetrics(timeWindow: Duration): Flow<AggregatedMetric>
    
    suspend fun createAlert(alert: AlertRule)
    suspend fun removeAlert(alertId: String)
    fun getActiveAlerts(): Flow<Alert>
}

class DefaultMetricsCollector : MetricsCollector {
    private val metrics = MutableSharedFlow<Metric>(replay = 1000)
    private val alerts = mutableMapOf<String, AlertRule>()
    private val activeAlerts = MutableSharedFlow<Alert>(replay = 100)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // Start alert monitoring
        scope.launch {
            monitorAlerts()
        }
        
        // Start metrics aggregation
        scope.launch {
            aggregateMetrics()
        }
    }
    
    override suspend fun recordMetric(metric: Metric) {
        metrics.emit(metric.copy(timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
    }
    
    override suspend fun recordCounter(name: String, value: Double, tags: Map<String, String>) {
        recordMetric(Metric(
            name = name,
            type = MetricType.COUNTER,
            value = value,
            tags = tags
        ))
    }
    
    override suspend fun recordGauge(name: String, value: Double, tags: Map<String, String>) {
        recordMetric(Metric(
            name = name,
            type = MetricType.GAUGE,
            value = value,
            tags = tags
        ))
    }
    
    override suspend fun recordTimer(name: String, duration: Duration, tags: Map<String, String>) {
        recordMetric(Metric(
            name = name,
            type = MetricType.TIMER,
            value = duration.inWholeMilliseconds.toDouble(),
            tags = tags
        ))
    }
    
    override suspend fun recordHistogram(name: String, value: Double, tags: Map<String, String>) {
        recordMetric(Metric(
            name = name,
            type = MetricType.HISTOGRAM,
            value = value,
            tags = tags
        ))
    }
    
    override fun getMetrics(filter: MetricFilter): Flow<Metric> {
        return metrics.asSharedFlow().filter { metric ->
            when (filter) {
                MetricFilter.ALL -> true
                is MetricFilter.ByName -> metric.name == filter.name
                is MetricFilter.ByType -> metric.type == filter.type
                is MetricFilter.ByTag -> filter.tags.all { (key, value) ->
                    metric.tags[key] == value
                }
                is MetricFilter.ByTimeRange -> metric.timestamp in filter.startTime..filter.endTime
                is MetricFilter.Composite -> filter.filters.all { subFilter ->
                    getMetrics(subFilter).first { it.name == metric.name } == metric
                }
            }
        }
    }
    
    override fun getAggregatedMetrics(timeWindow: Duration): Flow<AggregatedMetric> {
        return metrics.asSharedFlow()
            .windowedBy(timeWindow)
            .map { windowMetrics ->
                aggregateMetricsWindow(windowMetrics, timeWindow)
            }
    }
    
    override suspend fun createAlert(alert: AlertRule) {
        alerts[alert.id] = alert
    }
    
    override suspend fun removeAlert(alertId: String) {
        alerts.remove(alertId)
    }
    
    override fun getActiveAlerts(): Flow<Alert> {
        return activeAlerts.asSharedFlow()
    }
    
    private suspend fun monitorAlerts() {
        metrics.asSharedFlow()
            .collect { metric ->
                alerts.values.forEach { alertRule ->
                    if (shouldTriggerAlert(metric, alertRule)) {
                        val alert = Alert(
                            id = "${alertRule.id}-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
                            ruleId = alertRule.id,
                            severity = alertRule.severity,
                            message = generateAlertMessage(metric, alertRule),
                            metric = metric,
                            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        )
                        activeAlerts.emit(alert)
                    }
                }
            }
    }
    
    private suspend fun aggregateMetrics() {
        // Aggregate metrics every 30 seconds
        while (scope.isActive) {
            delay(30.seconds)
            
            val recentMetrics = metrics.replayCache.filter { 
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - it.timestamp < 30_000 
            }
            
            val aggregated = aggregateMetricsWindow(recentMetrics, 30.seconds)
            // Store or emit aggregated metrics as needed
        }
    }
    
    private fun shouldTriggerAlert(metric: Metric, alertRule: AlertRule): Boolean {
        if (metric.name != alertRule.metricName) return false
        
        return when (alertRule.condition) {
            is AlertCondition.Threshold -> {
                when (alertRule.condition.operator) {
                    ComparisonOperator.GREATER_THAN -> metric.value > alertRule.condition.value
                    ComparisonOperator.LESS_THAN -> metric.value < alertRule.condition.value
                    ComparisonOperator.EQUALS -> metric.value == alertRule.condition.value
                    ComparisonOperator.GREATER_THAN_OR_EQUAL -> metric.value >= alertRule.condition.value
                    ComparisonOperator.LESS_THAN_OR_EQUAL -> metric.value <= alertRule.condition.value
                }
            }
            is AlertCondition.RateOfChange -> {
                // Implementation for rate of change detection
                false // Placeholder
            }
            is AlertCondition.Anomaly -> {
                // Implementation for anomaly detection
                false // Placeholder
            }
        }
    }
    
    private fun generateAlertMessage(metric: Metric, alertRule: AlertRule): String {
        return when (alertRule.condition) {
            is AlertCondition.Threshold -> {
                "${alertRule.name}: ${metric.name} is ${metric.value}, which is ${alertRule.condition.operator.description} ${alertRule.condition.value}"
            }
            is AlertCondition.RateOfChange -> {
                "${alertRule.name}: ${metric.name} rate of change exceeded threshold"
            }
            is AlertCondition.Anomaly -> {
                "${alertRule.name}: Anomaly detected in ${metric.name}"
            }
        }
    }
    
    private fun aggregateMetricsWindow(metrics: List<Metric>, window: Duration): AggregatedMetric {
        val groupedMetrics = metrics.groupBy { "${it.name}-${it.type}" }
        
        return AggregatedMetric(
            windowStart = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - window.inWholeMilliseconds,
            windowEnd = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            windowDuration = window,
            metrics = groupedMetrics.map { (key, metricList) ->
                val sample = metricList.first()
                AggregatedMetricData(
                    name = sample.name,
                    type = sample.type,
                    count = metricList.size.toLong(),
                    sum = metricList.sumOf { it.value },
                    min = metricList.minOf { it.value },
                    max = metricList.maxOf { it.value },
                    avg = metricList.map { it.value }.average(),
                    p50 = metricList.map { it.value }.percentile(50.0),
                    p95 = metricList.map { it.value }.percentile(95.0),
                    p99 = metricList.map { it.value }.percentile(99.0),
                    tags = sample.tags
                )
            }
        )
    }
}

// Extension function for windowing metrics by time
private fun Flow<Metric>.windowedBy(duration: Duration): Flow<List<Metric>> = flow {
    val window = mutableListOf<Metric>()
    var windowStart = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    
    collect { metric ->
        if (metric.timestamp - windowStart >= duration.inWholeMilliseconds) {
            if (window.isNotEmpty()) {
                emit(window.toList())
                window.clear()
            }
            windowStart = metric.timestamp
        }
        window.add(metric)
    }
    
    if (window.isNotEmpty()) {
        emit(window.toList())
    }
}

// Extension function for calculating percentiles
private fun List<Double>.percentile(percentile: Double): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val index = (percentile / 100.0 * (size - 1)).toInt()
    return sorted[index.coerceIn(0, size - 1)]
}

@Serializable
data class Metric(
    val name: String,
    val type: MetricType,
    val value: Double,
    val tags: Map<String, String> = emptyMap(),
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

@Serializable
data class AggregatedMetric(
    val windowStart: Long,
    val windowEnd: Long,
    val windowDuration: Duration,
    val metrics: List<AggregatedMetricData>
)

@Serializable
data class AggregatedMetricData(
    val name: String,
    val type: MetricType,
    val count: Long,
    val sum: Double,
    val min: Double,
    val max: Double,
    val avg: Double,
    val p50: Double,
    val p95: Double,
    val p99: Double,
    val tags: Map<String, String>
)

@Serializable
data class AlertRule(
    val id: String,
    val name: String,
    val description: String,
    val metricName: String,
    val condition: AlertCondition,
    val severity: AlertSeverity,
    val enabled: Boolean = true,
    val tags: Map<String, String> = emptyMap()
)

@Serializable
data class Alert(
    val id: String,
    val ruleId: String,
    val severity: AlertSeverity,
    val message: String,
    val metric: Metric,
    val timestamp: Long,
    val acknowledged: Boolean = false,
    val resolvedAt: Long? = null
)

@Serializable
sealed class AlertCondition {
    @Serializable
    data class Threshold(
        val operator: ComparisonOperator,
        val value: Double
    ) : AlertCondition()
    
    @Serializable
    data class RateOfChange(
        val threshold: Double,
        val timeWindow: Duration
    ) : AlertCondition()
    
    @Serializable
    data class Anomaly(
        val sensitivity: Double = 0.95,
        val algorithm: AnomalyDetectionAlgorithm = AnomalyDetectionAlgorithm.STATISTICAL
    ) : AlertCondition()
}

@Serializable
enum class ComparisonOperator(val description: String) {
    GREATER_THAN("greater than"),
    LESS_THAN("less than"),
    EQUALS("equal to"),
    GREATER_THAN_OR_EQUAL("greater than or equal to"),
    LESS_THAN_OR_EQUAL("less than or equal to")
}

@Serializable
enum class AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
enum class AnomalyDetectionAlgorithm {
    STATISTICAL, MACHINE_LEARNING, SEASONAL
}

@Serializable
enum class MetricType {
    COUNTER, GAUGE, TIMER, HISTOGRAM
}

sealed class MetricFilter {
    object ALL : MetricFilter()
    data class ByName(val name: String) : MetricFilter()
    data class ByType(val type: MetricType) : MetricFilter()
    data class ByTag(val tags: Map<String, String>) : MetricFilter()
    data class ByTimeRange(val startTime: Long, val endTime: Long) : MetricFilter()
    data class Composite(val filters: List<MetricFilter>) : MetricFilter()
}

/**
 * Utility functions for common metrics patterns
 */
object MetricsUtils {
    suspend inline fun <T> MetricsCollector.timed(
        name: String,
        tags: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val start = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return try {
            block()
        } finally {
            val duration = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - start).seconds
            recordTimer(name, duration, tags)
        }
    }
    
    suspend fun MetricsCollector.incrementCounter(
        name: String,
        tags: Map<String, String> = emptyMap()
    ) {
        recordCounter(name, 1.0, tags)
    }
    
    suspend fun MetricsCollector.recordSuccess(
        operation: String,
        tags: Map<String, String> = emptyMap()
    ) {
        recordCounter("${operation}.success", 1.0, tags)
    }
    
    suspend fun MetricsCollector.recordError(
        operation: String,
        error: Throwable,
        tags: Map<String, String> = emptyMap()
    ) {
        val errorTags = tags + ("error_type" to error::class.simpleName.orEmpty())
        recordCounter("${operation}.error", 1.0, errorTags)
    }
    
    suspend fun MetricsCollector.recordLatency(
        operation: String,
        latencyMs: Long,
        tags: Map<String, String> = emptyMap()
    ) {
        recordTimer("${operation}.latency", latencyMs.seconds, tags)
        recordHistogram("${operation}.latency_histogram", latencyMs.toDouble(), tags)
    }
}