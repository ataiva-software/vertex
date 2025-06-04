package com.ataiva.eden.monitor

import com.ataiva.eden.monitor.controller.configureMonitorRouting
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
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
            call.respond(HttpStatusCode.OK, HealthCheck(
                status = "healthy",
                timestamp = System.currentTimeMillis(),
                uptime = System.currentTimeMillis() - startTime,
                service = "monitor",
                version = "1.0.0",
                features_enabled = true
            ))
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