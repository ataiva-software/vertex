package com.ataiva.eden.gateway.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
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

/**
 * Configures monitoring features for the API Gateway
 * - Metrics collection with Prometheus
 * - Request/response logging
 * - Health checks
 * - Call ID tracking
 */
fun Application.configureMonitoring() {
    val logger = LoggerFactory.getLogger("MonitoringPlugin")
    
    // Create Prometheus registry
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)
    
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
        timers { _, route ->
            if (route.toString().isNotEmpty()) {
                tag("route", route.toString().substringBefore("("))
            }
        }
        
        // Add custom tags to all metrics
        tags { call ->
            val tags = mutableListOf<Tag>()
            
            // Add service name
            tags.add(Tag.of("service", "api-gateway"))
            
            // Add environment
            val env = environment.config.propertyOrNull("ktor.environment")?.getString() ?: "development"
            tags.add(Tag.of("environment", env))
            
            // Add HTTP method
            call.request.httpMethod.value.let {
                tags.add(Tag.of("method", it))
            }
            
            // Add status code if response is already set
            call.response.status()?.value?.let {
                tags.add(Tag.of("status", it.toString()))
            }
            
            // Add endpoint path pattern if available
            call.attributes.getOrNull(AttributeKey("ktor.route"))?.toString()?.let {
                val routePattern = it.substringBefore("(")
                if (routePattern.isNotEmpty()) {
                    tags.add(Tag.of("endpoint", routePattern))
                }
            }
            
            tags
        }
    }
    
    // Install Call ID tracking
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
        generate {
            UUID.randomUUID().toString()
        }
    }
    
    // Install Call Logging
    install(CallLogging) {
        level = Level.INFO
        
        // Add call ID to MDC for correlation in logs
        callIdMdc("call-id")
        
        // Log all requests except for metrics and health endpoints
        filter { call ->
            val path = call.request.path()
            !path.startsWith("/metrics") && !path.startsWith("/health")
        }
        
        // Format log message with useful information
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val userAgent = call.request.headers["User-Agent"]
            val clientIp = call.request.origin.remoteHost
            val callId = call.callId ?: "unknown"
            val duration = call.processingTimeMillis()
            
            "$clientIp [$httpMethod] $path - $status - ${duration}ms - $callId - $userAgent"
        }
        
        // Add MDC parameters for structured logging
        mdc("client-ip") { call -> call.request.origin.remoteHost }
        mdc("http-method") { call -> call.request.httpMethod.value }
        mdc("path") { call -> call.request.path() }
        mdc("user-agent") { call -> call.request.headers["User-Agent"] ?: "unknown" }
    }
    
    // Track active requests
    val activeRequests = AtomicInteger(0)
    
    // Add request/response interceptors for detailed logging and metrics
    intercept(ApplicationCallPipeline.Monitoring) {
        // Increment active requests counter
        activeRequests.incrementAndGet()
        
        // Log request details at DEBUG level
        logger.debug("Received request: ${call.request.httpMethod.value} ${call.request.path()} from ${call.request.origin.remoteHost}")
        
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
                "version" to (environment.config.propertyOrNull("application.version")?.getString() ?: "1.0.0"),
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