package com.ataiva.eden.task

import com.ataiva.eden.task.service.TaskService
import com.ataiva.eden.task.controller.TaskController
import com.ataiva.eden.task.model.*
import com.ataiva.eden.task.engine.TaskExecutor
import com.ataiva.eden.task.engine.TaskScheduler
import com.ataiva.eden.task.queue.TaskQueue
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
    embeddedServer(Netty, port = 8082, host = "0.0.0.0", module = Application::module)
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
    val taskExecutor = TaskExecutor()
    val taskScheduler = TaskScheduler()
    val taskQueue = TaskQueue()
    val taskService = TaskService(
        databaseService = databaseService,
        taskExecutor = taskExecutor,
        taskScheduler = taskScheduler,
        taskQueue = taskQueue
    )
    val taskController = TaskController(taskService, taskExecutor, taskScheduler, taskQueue)
    
    routing {
        // Service info endpoint
        get("/") {
            call.respond(ServiceInfo(
                name = "Eden Task Service",
                version = "1.0.0",
                description = "Task execution and job scheduling service with real execution engine",
                status = "running"
            ))
        }
        
        // Enhanced health check
        get("/health") {
            val queueStats = taskQueue.getStats()
            val scheduledTasks = taskScheduler.getAllScheduledTasks()
            
            val healthResponse = TaskHealthResponse(
                status = "healthy",
                timestamp = Clock.System.now(),
                uptime = System.currentTimeMillis() - startTime,
                service = "task",
                database = DatabaseHealth(
                    connected = true, // TODO: Add real database health check
                    responseTime = null,
                    activeConnections = null
                ),
                taskExecutor = TaskExecutorHealth(
                    available = true,
                    supportedTaskTypes = TaskExecutor.SUPPORTED_TASK_TYPES.toList(),
                    runningExecutions = 0, // TODO: Get actual count from TaskService
                    maxConcurrentExecutions = 10
                ),
                taskQueue = TaskQueueHealth(
                    available = true,
                    queuedTasks = queueStats.totalQueued,
                    processingRate = null
                ),
                scheduler = SchedulerHealth(
                    available = true,
                    scheduledTasks = scheduledTasks.size,
                    nextScheduledRun = scheduledTasks.minByOrNull { it.nextRun ?: Clock.System.now() }?.nextRun
                )
            )
            call.respond(HttpStatusCode.OK, ApiResponse.success(healthResponse))
        }
        
        // Task API routes
        with(taskController) {
            taskRoutes()
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