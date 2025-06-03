package com.ataiva.eden.flow

import com.ataiva.eden.flow.service.FlowService
import com.ataiva.eden.flow.controller.FlowController
import com.ataiva.eden.flow.model.*
import com.ataiva.eden.flow.engine.WorkflowEngine
import com.ataiva.eden.flow.engine.StepExecutor
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.PostgreSQLDatabaseService
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
import kotlinx.datetime.Clock

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
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
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-User-ID")
        anyHost() // For development - restrict in production
    }
    
    // Configure error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val errorResponse = ApiResponse.error<Unit>("Internal server error: ${cause.localizedMessage}")
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        }
        
        status(HttpStatusCode.NotFound) { call, status ->
            val errorResponse = ApiResponse.error<Unit>("Endpoint not found")
            call.respond(status, errorResponse)
        }
        
        status(HttpStatusCode.Unauthorized) { call, status ->
            val errorResponse = ApiResponse.error<Unit>("Authentication required")
            call.respond(status, errorResponse)
        }
    }
    
    // Initialize dependencies
    val databaseService = createDatabaseService()
    val workflowEngine = WorkflowEngine()
    val stepExecutor = StepExecutor()
    val flowService = FlowService(
        databaseService = databaseService,
        workflowEngine = workflowEngine,
        stepExecutor = stepExecutor
    )
    val flowController = FlowController(flowService, workflowEngine)
    
    routing {
        // Service info endpoint
        get("/") {
            call.respond(ServiceInfo(
                name = "Eden Flow Service",
                version = "1.0.0",
                description = "Workflow orchestration and automation service with real execution engine",
                status = "running"
            ))
        }
        
        // Enhanced health check
        get("/health") {
            val healthResponse = FlowHealthResponse(
                status = "healthy",
                timestamp = Clock.System.now(),
                uptime = System.currentTimeMillis() - startTime,
                service = "flow",
                database = DatabaseHealth(
                    connected = true, // TODO: Add real database health check
                    responseTime = null,
                    activeConnections = null
                ),
                workflowEngine = WorkflowEngineHealth(
                    available = true,
                    supportedStepTypes = WorkflowEngine.SUPPORTED_STEP_TYPES.toList(),
                    maxConcurrentExecutions = 10
                ),
                runningExecutions = 0 // TODO: Get actual count from FlowService
            )
            call.respond(HttpStatusCode.OK, ApiResponse.success(healthResponse))
        }
        
        // Flow API routes
        with(flowController) {
            flowRoutes()
            statsRoutes()
        }
    }
}

/**
 * Create database service with proper configuration
 */
private fun createDatabaseService(): EdenDatabaseService {
    // TODO: Load from configuration
    val config = mapOf(
        "url" to (System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/eden_dev"),
        "username" to (System.getenv("DATABASE_USERNAME") ?: "eden_user"),
        "password" to (System.getenv("DATABASE_PASSWORD") ?: "eden_password"),
        "driver" to "org.postgresql.Driver"
    )
    
    return PostgreSQLDatabaseService(config)
}

@Serializable
data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String
)

private val startTime = System.currentTimeMillis()