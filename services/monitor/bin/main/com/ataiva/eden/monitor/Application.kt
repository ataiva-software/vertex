package com.ataiva.eden.monitor

import com.ataiva.eden.monitor.controller.configureMonitorRouting
import com.ataiva.eden.monitoring.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

// OpenTelemetry configuration
private lateinit var openTelemetryConfig: OpenTelemetryConfig
private lateinit var metricsRegistry: MetricsRegistry
private lateinit var performanceMonitor: PerformanceMonitor
private lateinit var auditLogger: AuditLogger
private lateinit var healthCheckRegistry: HealthCheckRegistry
private lateinit var serviceDependencyMapper: ServiceDependencyMapper

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize OpenTelemetry
    openTelemetryConfig = OpenTelemetryConfig(
        serviceName = "eden-monitor",
        serviceVersion = "1.0.0",
        environment = System.getenv("ENVIRONMENT") ?: "development",
        otlpEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") ?: "http://otel-collector:4317",
        prometheusPort = System.getenv("PROMETHEUS_PORT")?.toIntOrNull() ?: 9464
    )
    
    val openTelemetry = openTelemetryConfig.initialize()
    
    // Initialize monitoring utilities
    metricsRegistry = MetricsRegistry(openTelemetry, "eden-monitor")
    performanceMonitor = PerformanceMonitor(openTelemetry, "eden-monitor")
    auditLogger = AuditLogger(openTelemetry, "eden-monitor")
    serviceDependencyMapper = ServiceDependencyMapper(openTelemetry, "eden-monitor")
    
    // Initialize health checks
    healthCheckRegistry = HealthCheckRegistry()
    
    // Add health checks
    healthCheckRegistry.register(MemoryHealthCheck())
    healthCheckRegistry.register(DiskSpaceHealthCheck("/"))
    
    // Install OpenTelemetry Ktor plugin
    KtorOpenTelemetry(openTelemetry).install(this)
    
    // Register shutdown hook
    environment.monitor.subscribe(ApplicationStopping) {
        log.info("Shutting down OpenTelemetry...")
        launch {
            openTelemetryConfig.shutdown()
        }
    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Requested-With")
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Internal Server Error",
                "message" to (cause.localizedMessage ?: "Unknown error occurred"),
                "timestamp" to System.currentTimeMillis()
            ))
        }
    }
    
    routing {
        get("/") {
            call.respond(ServiceInfo(
                name = "Eden Monitor Service",
                version = "1.0.0",
                description = "System monitoring and alerting service with real business logic",
                status = "running",
                features = listOf(
                    "Real-time system metrics",
                    "Service health monitoring",
                    "Alert management",
                    "Dashboard analytics",
                    "Log aggregation",
                    "Historical data tracking"
                )
            ))
        }
        
        get("/health") {
            val healthResult = performanceMonitor.trackSuspendOperation("health_check") {
                healthCheckRegistry.runHealthChecks()
            }
            
            val statusCode = when (healthResult.status) {
                HealthStatus.UP -> HttpStatusCode.OK
                HealthStatus.DEGRADED -> HttpStatusCode.OK
                HealthStatus.DOWN -> HttpStatusCode.ServiceUnavailable
                HealthStatus.UNKNOWN -> HttpStatusCode.InternalServerError
            }
            
            call.respond(statusCode, healthResult)
        }
        
        get("/metrics") {
            call.respond(HttpStatusCode.OK, performanceMonitor.getSystemPerformanceData())
        }
        
        get("/dependencies") {
            call.respond(HttpStatusCode.OK, serviceDependencyMapper.generateDependencyGraph())
        }
    }
    
    // Configure real monitoring endpoints
    configureMonitorRouting()
}

@Serializable
data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String,
    val features: List<String>
)

@Serializable
data class HealthCheck(
    val status: String,
    val timestamp: Long,
    val uptime: Long,
    val service: String,
    val version: String,
    val features_enabled: Boolean
)

private val startTime = System.currentTimeMillis()