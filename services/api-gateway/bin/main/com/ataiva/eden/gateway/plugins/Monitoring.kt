package com.ataiva.eden.gateway.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.util.*

/**
 * Configures monitoring features for the API Gateway
 * - Metrics collection with Prometheus
 * - Request/response logging
 * - Health checks
 * - Call ID tracking
 */
fun Application.configureMonitoring() {
    val logger = LoggerFactory.getLogger("MonitoringPlugin")
    val appEnvironment = environment
    
    // Create Prometheus registry
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    
    // Install Micrometer metrics
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        
        // Configure distribution statistics for response time metrics
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
            .serviceLevelObjectives(
                Duration.ofMillis(100).toNanos().toDouble(),
                Duration.ofMillis(500).toNanos().toDouble(),
                Duration.ofSeconds(1).toNanos().toDouble()
            )
            .build()
        
        // Add common tags
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ClassLoaderMetrics(),
            ProcessorMetrics()
        )
        
        // Tag metrics with additional dimensions
        // In Ktor 2.3.5, the timers function expects ApplicationCall and Throwable? parameters
        timers { call, _ ->
            if (call.request.path().isNotEmpty()) {
                listOf(Tag.of("route", call.request.path().substringBefore("(")))
            } else {
                emptyList()
            }
        }
    }
    
    // Install Call ID tracking
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId ->
            callId.isNotEmpty()
        }
        generate {
            UUID.randomUUID().toString()
        }
    }
    
    // Note: CallLogging plugin is not directly installed here due to compatibility issues
    // Logging is handled through interceptors instead
    
    // Track active requests
    val activeRequests = AtomicInteger(0)
    
    // Add request/response interceptors for detailed logging and metrics
    intercept(ApplicationCallPipeline.Monitoring) {
        // Increment active requests counter
        activeRequests.incrementAndGet()
        
        // Get client IP from headers or connection
        val clientIp = call.request.header("X-Forwarded-For") ?: call.request.local.remoteHost
        
        // Log request details at DEBUG level
        logger.debug("Received request: ${call.request.httpMethod.value} ${call.request.path()} from $clientIp")
        
        try {
            // Continue with the request
            proceed()
        } finally {
            // Decrement active requests counter
            activeRequests.decrementAndGet()
            
            // Log response details at DEBUG level
            val status = call.response.status() ?: HttpStatusCode.InternalServerError
            logger.debug("Completed request: ${call.request.httpMethod.value} ${call.request.path()} with status $status in ${call.processingTimeMillis()}ms")
        }
    }
    
    // Configure metrics and health check endpoints
    routing {
        // Prometheus metrics endpoint
        get("/metrics") {
            call.respondText(appMicrometerRegistry.scrape(), ContentType.parse(TextFormat.CONTENT_TYPE_004))
        }
        
        // Detailed health check endpoint
        get("/health/details") {
            val healthStatus = mapOf(
                "service" to "api-gateway",
                "status" to "healthy",
                "version" to (appEnvironment.config.propertyOrNull("application.version")?.getString() ?: "1.0.0"),
                "timestamp" to System.currentTimeMillis(),
                "metrics" to mapOf(
                    "activeRequests" to activeRequests.get(),
                    "uptime" to (System.currentTimeMillis() - ApplicationStartTime.startTime)
                )
            )
            call.respond(healthStatus)
        }
    }
    
    logger.info("Monitoring configured with metrics collection, request logging, and health checks")
}

/**
 * Extension function to get the processing time of a call in milliseconds
 */
private fun ApplicationCall.processingTimeMillis(): Long {
    val startTime = attributes.getOrNull(ProcessingTimeKey)
    return if (startTime != null) {
        System.currentTimeMillis() - startTime
    } else {
        0L
    }
}

/**
 * Key for storing the start time of a request
 */
private val ProcessingTimeKey = AttributeKey<Long>("ProcessingTime")

/**
 * Singleton to track application start time
 */
object ApplicationStartTime {
    val startTime: Long = System.currentTimeMillis()
}