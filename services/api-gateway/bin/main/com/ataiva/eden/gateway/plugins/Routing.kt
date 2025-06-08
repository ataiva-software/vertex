package com.ataiva.eden.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

fun Application.configureRouting() {
    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    // Service configuration
    val serviceConfig = ServiceConfig()
    
    routing {
        // Health check for the gateway itself
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf(
                "service" to "api-gateway",
                "status" to "healthy",
                "timestamp" to System.currentTimeMillis(),
                "version" to "1.0.0"
            ))
        }
        
        // Gateway info endpoint
        get("/") {
            call.respond(mapOf(
                "name" to "Eden API Gateway",
                "version" to "1.0.0",
                "description" to "Central API gateway for Eden DevOps Suite",
                "services" to serviceConfig.getAllServices().map { it.name },
                "status" to "running"
            ))
        }
        
        // Service discovery endpoint
        get("/services") {
            val services = serviceConfig.getAllServices().map { service ->
                mapOf(
                    "name" to service.name,
                    "url" to service.url,
                    "health_endpoint" to "${service.url}/health",
                    "api_prefix" to "/api/v1/${service.name}"
                )
            }
            call.respond(mapOf(
                "services" to services,
                "total" to services.size
            ))
        }
        
        // Health check aggregation for all services
        get("/services/health") {
            val healthChecks = mutableListOf<Map<String, Any>>()
            
            serviceConfig.getAllServices().forEach { service ->
                try {
                    val response = withTimeout(5000) {
                        httpClient.get("${service.url}/health")
                    }
                    
                    healthChecks.add(mapOf(
                        "service" to service.name,
                        "status" to if (response.status.isSuccess()) "healthy" else "unhealthy",
                        "http_status" to response.status.value,
                        "url" to service.url,
                        "response_time" to System.currentTimeMillis() // Simplified
                    ))
                } catch (e: TimeoutCancellationException) {
                    healthChecks.add(mapOf(
                        "service" to service.name,
                        "status" to "timeout",
                        "error" to "Health check timeout",
                        "url" to service.url
                    ))
                } catch (e: Exception) {
                    healthChecks.add(mapOf(
                        "service" to service.name,
                        "status" to "error",
                        "error" to (e.message ?: "Unknown error"),
                        "url" to service.url
                    ))
                }
            }
            
            val healthyCount = healthChecks.count { (it["status"] as String) == "healthy" }
            val overallStatus = if (healthyCount == healthChecks.size) "healthy" else "degraded"
            
            call.respond(mapOf(
                "overall_status" to overallStatus,
                "healthy_services" to healthyCount,
                "total_services" to healthChecks.size,
                "services" to healthChecks,
                "timestamp" to System.currentTimeMillis()
            ))
        }
        
        // Vault service proxy
        route("/api/v1/vault") {
            proxyToService(httpClient, serviceConfig.getService("vault"))
        }
        
        // Flow service proxy
        route("/api/v1/flow") {
            proxyToService(httpClient, serviceConfig.getService("flow"))
        }
        
        // Task service proxy
        route("/api/v1/task") {
            proxyToService(httpClient, serviceConfig.getService("task"))
        }
        
        // Monitor service proxy
        route("/api/v1/monitor") {
            proxyToService(httpClient, serviceConfig.getService("monitor"))
        }
        
        // Sync service proxy
        route("/api/v1/sync") {
            proxyToService(httpClient, serviceConfig.getService("sync"))
        }
        
        // Insight service proxy
        route("/api/v1/insight") {
            proxyToService(httpClient, serviceConfig.getService("insight"))
        }
        
        // Hub service proxy
        route("/api/v1/hub") {
            proxyToService(httpClient, serviceConfig.getService("hub"))
        }
        
        // Legacy test endpoints for backward compatibility
        get("/test") {
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Test endpoint",
                "gateway_status" to "operational",
                "timestamp" to System.currentTimeMillis()
            ))
        }
        
        post("/test") {
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Test POST endpoint",
                "gateway_status" to "operational",
                "timestamp" to System.currentTimeMillis()
            ))
        }
    }
}

private fun Route.proxyToService(httpClient: HttpClient, service: ServiceInfo?) {
    if (service == null) {
        handle {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "error" to "Service not available",
                "message" to "The requested service is not configured or unavailable"
            ))
        }
        return
    }
    
    // Handle all HTTP methods
    handle {
        try {
            val originalPath = call.request.path()
            val servicePath = originalPath.removePrefix("/api/v1/${service.name}")
            val targetUrl = "${service.url}/api/v1${servicePath}"
            
            // Forward query parameters
            val queryString = call.request.queryString()
            val fullUrl = if (queryString.isNotEmpty()) "$targetUrl?$queryString" else targetUrl
            
            val response = when (call.request.httpMethod) {
                HttpMethod.Get -> httpClient.get(fullUrl) {
                    // Forward headers
                    call.request.headers.forEach { name, values ->
                        if (shouldForwardHeader(name)) {
                            values.forEach { value ->
                                header(name, value)
                            }
                        }
                    }
                }
                HttpMethod.Post -> httpClient.post(fullUrl) {
                    // Forward headers
                    call.request.headers.forEach { name, values ->
                        if (shouldForwardHeader(name)) {
                            values.forEach { value ->
                                header(name, value)
                            }
                        }
                    }
                    // Forward body
                    setBody(call.receiveText())
                }
                HttpMethod.Put -> httpClient.put(fullUrl) {
                    call.request.headers.forEach { name, values ->
                        if (shouldForwardHeader(name)) {
                            values.forEach { value ->
                                header(name, value)
                            }
                        }
                    }
                    setBody(call.receiveText())
                }
                HttpMethod.Delete -> httpClient.delete(fullUrl) {
                    call.request.headers.forEach { name, values ->
                        if (shouldForwardHeader(name)) {
                            values.forEach { value ->
                                header(name, value)
                            }
                        }
                    }
                }
                HttpMethod.Patch -> httpClient.patch(fullUrl) {
                    call.request.headers.forEach { name, values ->
                        if (shouldForwardHeader(name)) {
                            values.forEach { value ->
                                header(name, value)
                            }
                        }
                    }
                    setBody(call.receiveText())
                }
                else -> {
                    call.respond(HttpStatusCode.MethodNotAllowed, mapOf(
                        "error" to "Method not allowed",
                        "method" to call.request.httpMethod.value
                    ))
                    return@handle
                }
            }
            
            // Forward response
            val responseBody = response.bodyAsText()
            val responseHeaders = response.headers
            
            // Forward response headers
            responseHeaders.forEach { name, values ->
                if (shouldForwardResponseHeader(name)) {
                    values.forEach { value ->
                        call.response.header(name, value)
                    }
                }
            }
            
            call.respond(response.status, responseBody)
            
        } catch (e: TimeoutCancellationException) {
            call.respond(HttpStatusCode.GatewayTimeout, mapOf(
                "error" to "Service timeout",
                "message" to "The service did not respond within the timeout period",
                "service" to service.name
            ))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, mapOf(
                "error" to "Service error",
                "message" to e.message,
                "service" to service.name
            ))
        }
    }
}

private fun shouldForwardHeader(headerName: String): Boolean {
    val skipHeaders = setOf(
        "host", "connection", "upgrade", "proxy-connection",
        "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding"
    )
    return !skipHeaders.contains(headerName.lowercase())
}

private fun shouldForwardResponseHeader(headerName: String): Boolean {
    val skipHeaders = setOf(
        "connection", "upgrade", "proxy-connection", "transfer-encoding"
    )
    return !skipHeaders.contains(headerName.lowercase())
}

data class ServiceInfo(
    val name: String,
    val url: String,
    val healthEndpoint: String
)

class ServiceConfig {
    private val services = mapOf(
        "vault" to ServiceInfo("vault", System.getenv("VAULT_SERVICE_URL") ?: "http://vault:8080", "${System.getenv("VAULT_SERVICE_URL") ?: "http://vault:8080"}/health"),
        "flow" to ServiceInfo("flow", System.getenv("FLOW_SERVICE_URL") ?: "http://flow:8080", "${System.getenv("FLOW_SERVICE_URL") ?: "http://flow:8080"}/health"),
        "task" to ServiceInfo("task", System.getenv("TASK_SERVICE_URL") ?: "http://task:8080", "${System.getenv("TASK_SERVICE_URL") ?: "http://task:8080"}/health"),
        "monitor" to ServiceInfo("monitor", System.getenv("MONITOR_SERVICE_URL") ?: "http://monitor:8080", "${System.getenv("MONITOR_SERVICE_URL") ?: "http://monitor:8080"}/health"),
        "sync" to ServiceInfo("sync", System.getenv("SYNC_SERVICE_URL") ?: "http://sync:8080", "${System.getenv("SYNC_SERVICE_URL") ?: "http://sync:8080"}/health"),
        "insight" to ServiceInfo("insight", System.getenv("INSIGHT_SERVICE_URL") ?: "http://insight:8080", "${System.getenv("INSIGHT_SERVICE_URL") ?: "http://insight:8080"}/health"),
        "hub" to ServiceInfo("hub", System.getenv("HUB_SERVICE_URL") ?: "http://hub:8080", "${System.getenv("HUB_SERVICE_URL") ?: "http://hub:8080"}/health")
    )
    
    fun getService(name: String): ServiceInfo? = services[name]
    
    fun getAllServices(): List<ServiceInfo> = services.values.toList()
}