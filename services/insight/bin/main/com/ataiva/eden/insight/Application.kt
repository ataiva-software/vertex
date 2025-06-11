package com.ataiva.eden.insight

import com.ataiva.eden.insight.controller.InsightController
import com.ataiva.eden.insight.model.*
import com.ataiva.eden.insight.service.InsightService
import com.ataiva.eden.insight.service.InsightConfiguration
import com.ataiva.eden.insight.config.InsightDatabaseConfig
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
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.util.*
import org.slf4j.LoggerFactory
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Configure JSON serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    
    // Configure CORS
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
        allowHeader("X-User-ID")
        allowCredentials = true
    }
    
    // Configure logging using interceptors instead of CallLogging plugin
    val logger = LoggerFactory.getLogger("InsightService")
    
    // Add request/response interceptors for logging
    intercept(ApplicationCallPipeline.Monitoring) {
        // Log request details
        logger.info("Received request: ${call.request.httpMethod.value} ${call.request.path()}")
        
        try {
            // Continue with the request
            proceed()
        } finally {
            // Log response details
            val status = call.response.status() ?: HttpStatusCode.InternalServerError
            logger.info("Completed request: ${call.request.httpMethod.value} ${call.request.path()} with status $status")
        }
    }
    
    // Configure default headers
    install(DefaultHeaders) {
        header("X-Service", "Eden Insight Service")
        header("X-Version", "1.0.0")
    }
    
    // Configure error handling
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "success" to false,
                    "error" to (cause.message ?: "Invalid request"),
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }
        
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf(
                    "success" to false,
                    "error" to (cause.message ?: "Invalid state"),
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }
        
        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf(
                    "success" to false,
                    "error" to (cause.message ?: "Resource not found"),
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }
        
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "success" to false,
                    "error" to "Internal server error",
                    "message" to (cause.message ?: "An unexpected error occurred"),
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }
    }
    
    // Initialize services
    val insightConfiguration = InsightConfiguration(
        reportOutputPath = System.getProperty("java.io.tmpdir") + "/eden-reports",
        cacheEnabled = true,
        cacheMaxSize = 10000,
        cacheTtlMinutes = 60,
        queryTimeoutSeconds = 300,
        maxResultRows = 100000,
        maxConcurrentQueries = 10
    )
    
    // Create service configuration
    val serviceConfig = InsightConfiguration(
        reportOutputPath = System.getProperty("java.io.tmpdir") + "/eden-reports",
        cacheEnabled = true,
        cacheMaxSize = 10000,
        cacheTtlMinutes = 60,
        queryTimeoutSeconds = 300,
        maxResultRows = 100000
    )
    
    // Create database config
    val databaseConfig = InsightDatabaseConfig()
    
    // Create insight service
    val insightService = InsightService(serviceConfig, databaseConfig)
    val insightController = InsightController(insightService)
    
    // Configure routing
    routing {
        // Root endpoint - Service information
        get("/") {
            call.respond(ServiceInfo(
                name = "Eden Insight Service",
                version = "1.0.0",
                description = "Advanced analytics and business intelligence service with real-time capabilities",
                status = "running"
            ))
        }
        
        // Health check endpoint
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthCheck(
                status = "healthy",
                timestamp = System.currentTimeMillis(),
                uptime = System.currentTimeMillis() - startTime,
                service = "insight"
            ))
        }
        
        // Readiness check endpoint
        get("/ready") {
            try {
                // Perform basic service checks
                val systemAnalytics = insightService.getSystemAnalytics()
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "ready",
                    "timestamp" to System.currentTimeMillis(),
                    "checks" to mapOf(
                        "analytics_engine" to "operational",
                        "query_cache" to "operational",
                        "report_generator" to "operational"
                    )
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                    "status" to "not_ready",
                    "error" to e.message,
                    "timestamp" to System.currentTimeMillis()
                ))
            }
        }
        
        // Metrics endpoint for monitoring
        get("/metrics") {
            try {
                val metrics = mapOf(
                    "service" to "insight",
                    "version" to "1.0.0",
                    "uptime_ms" to (System.currentTimeMillis() - startTime),
                    "memory_usage" to getMemoryUsage(),
                    "system_analytics" to insightService.getSystemAnalytics(),
                    "usage_statistics" to insightService.getUsageStatistics(),
                    "performance_analytics" to insightService.getPerformanceAnalytics()
                )
                call.respond(HttpStatusCode.OK, metrics)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Failed to collect metrics",
                    "message" to e.message
                ))
            }
        }
        
        // Configure all API routes through the controller
        insightController.configureRoutes(this)
        
        // Legacy compatibility endpoints (for backward compatibility)
        route("/api/v1") {
            // Legacy analytics endpoint
            get("/analytics") {
                call.respond(HttpStatusCode.MovedPermanently, mapOf(
                    "message" to "This endpoint has moved to /api/v1/analytics/overview",
                    "new_url" to "/api/v1/analytics/overview"
                ))
            }
            
            // Legacy reports endpoint
            get("/reports") {
                call.respond(HttpStatusCode.MovedPermanently, mapOf(
                    "message" to "This endpoint has moved to /api/v1/reports",
                    "new_url" to "/api/v1/reports"
                ))
            }
            
            // Legacy dashboards endpoint
            get("/dashboards") {
                call.respond(HttpStatusCode.MovedPermanently, mapOf(
                    "message" to "This endpoint has moved to /api/v1/dashboards",
                    "new_url" to "/api/v1/dashboards"
                ))
            }
            
            // Legacy queries endpoint
            get("/queries") {
                call.respond(HttpStatusCode.MovedPermanently, mapOf(
                    "message" to "This endpoint has moved to /api/v1/queries",
                    "new_url" to "/api/v1/queries"
                ))
            }
            
            // Legacy alerts endpoint (for compatibility with old placeholder)
            get("/alerts") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Analytics alerts are now managed through metrics and KPIs",
                    "active_alerts" to emptyList<Any>(),
                    "redirect_to" to listOf(
                        "/api/v1/metrics",
                        "/api/v1/kpis"
                    ),
                    "note" to "This endpoint is deprecated. Use metrics and KPIs for alerting."
                ))
            }
        }
        
        // API documentation endpoint
        get("/api/docs") {
            call.respond(HttpStatusCode.OK, getApiDocumentation())
        }
        
        // Service status endpoint with detailed information
        get("/status") {
            try {
                val status = mapOf(
                    "service" to "Eden Insight Service",
                    "version" to "1.0.0",
                    "status" to "operational",
                    "timestamp" to System.currentTimeMillis(),
                    "uptime_ms" to (System.currentTimeMillis() - startTime),
                    "features" to mapOf(
                        "analytics_engine" to "enabled",
                        "query_processing" to "enabled",
                        "report_generation" to "enabled",
                        "dashboard_management" to "enabled",
                        "metrics_and_kpis" to "enabled",
                        "real_time_analytics" to "enabled",
                        "caching" to "enabled"
                    ),
                    "statistics" to insightService.getUsageStatistics(),
                    "performance" to insightService.getPerformanceAnalytics()
                )
                call.respond(HttpStatusCode.OK, status)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "service" to "Eden Insight Service",
                    "status" to "error",
                    "error" to e.message,
                    "timestamp" to System.currentTimeMillis()
                ))
            }
        }
    }
}

/**
 * Get current memory usage information
 */
private fun getMemoryUsage(): Map<String, Any> {
    val runtime = Runtime.getRuntime()
    val totalMemory = runtime.totalMemory()
    val freeMemory = runtime.freeMemory()
    val usedMemory = totalMemory - freeMemory
    val maxMemory = runtime.maxMemory()
    
    return mapOf(
        "used_bytes" to usedMemory,
        "free_bytes" to freeMemory,
        "total_bytes" to totalMemory,
        "max_bytes" to maxMemory,
        "usage_percentage" to ((usedMemory.toDouble() / totalMemory) * 100).toInt()
    )
}

/**
 * Generate API documentation
 */
private fun getApiDocumentation(): Map<String, Any> {
    return mapOf(
        "service" to "Eden Insight Service",
        "version" to "1.0.0",
        "description" to "Advanced analytics and business intelligence service",
        "base_url" to "/api/v1",
        "endpoints" to mapOf(
            "queries" to mapOf(
                "description" to "Manage analytics queries",
                "endpoints" to listOf(
                    "GET /api/v1/queries - List all queries",
                    "POST /api/v1/queries - Create new query",
                    "GET /api/v1/queries/{id} - Get specific query",
                    "PUT /api/v1/queries/{id} - Update query",
                    "DELETE /api/v1/queries/{id} - Delete query",
                    "POST /api/v1/queries/{id}/execute - Execute query",
                    "POST /api/v1/queries/execute - Execute raw query"
                )
            ),
            "reports" to mapOf(
                "description" to "Manage reports and report generation",
                "endpoints" to listOf(
                    "GET /api/v1/reports - List all reports",
                    "POST /api/v1/reports - Create new report",
                    "GET /api/v1/reports/{id} - Get specific report",
                    "POST /api/v1/reports/{id}/generate - Generate report",
                    "GET /api/v1/reports/executions/{id} - Get report execution status"
                )
            ),
            "report_templates" to mapOf(
                "description" to "Manage report templates",
                "endpoints" to listOf(
                    "GET /api/v1/report-templates - List all templates",
                    "POST /api/v1/report-templates - Create new template",
                    "GET /api/v1/report-templates/{id} - Get specific template"
                )
            ),
            "dashboards" to mapOf(
                "description" to "Manage dashboards and real-time data",
                "endpoints" to listOf(
                    "GET /api/v1/dashboards - List all dashboards",
                    "POST /api/v1/dashboards - Create new dashboard",
                    "GET /api/v1/dashboards/{id} - Get specific dashboard",
                    "GET /api/v1/dashboards/{id}/data - Get dashboard data",
                    "PUT /api/v1/dashboards/{id} - Update dashboard"
                )
            ),
            "analytics" to mapOf(
                "description" to "System analytics and insights",
                "endpoints" to listOf(
                    "GET /api/v1/analytics/overview - System analytics overview",
                    "GET /api/v1/analytics/usage - Usage statistics",
                    "GET /api/v1/analytics/performance - Performance analytics"
                )
            ),
            "metrics" to mapOf(
                "description" to "Metrics and KPI management",
                "endpoints" to listOf(
                    "GET /api/v1/metrics - List all metrics",
                    "POST /api/v1/metrics - Create new metric",
                    "GET /api/v1/kpis - List all KPIs",
                    "POST /api/v1/kpis - Create new KPI"
                )
            )
        ),
        "authentication" to mapOf(
            "type" to "Bearer Token",
            "header" to "Authorization: Bearer <token>",
            "user_header" to "X-User-ID: <user_id>"
        ),
        "response_format" to mapOf(
            "success" to mapOf(
                "success" to true,
                "data" to "<response_data>",
                "message" to "<optional_message>",
                "timestamp" to "<unix_timestamp>"
            ),
            "error" to mapOf(
                "success" to false,
                "error" to "<error_message>",
                "timestamp" to "<unix_timestamp>"
            )
        )
    )
}

private val startTime = System.currentTimeMillis()