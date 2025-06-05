package com.ataiva.eden.insight

import com.ataiva.eden.insight.config.InsightDatabaseConfig
import com.ataiva.eden.insight.service.InsightConfiguration
import com.ataiva.eden.insight.service.InsightService
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Main application class for the Insight Service.
 * Initializes the database, service, and starts the HTTP server.
 */
class InsightApplication {
    private val logger = LoggerFactory.getLogger(InsightApplication::class.java)
    private val config = ConfigFactory.load()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    private lateinit var databaseConfig: InsightDatabaseConfig
    private lateinit var insightService: InsightService
    
    /**
     * Initialize the application
     */
    fun initialize() {
        logger.info("Initializing Insight Service...")
        
        try {
            // Initialize database configuration
            databaseConfig = InsightDatabaseConfig(
                configPath = "database",
                migrateOnStartup = true
            )
            
            // Create insight configuration from application.conf
            val insightConfig = InsightConfiguration(
                reportOutputPath = config.getString("insight.report-output-path"),
                cacheEnabled = config.getBoolean("insight.cache.enabled"),
                cacheMaxSize = config.getInt("insight.cache.max-size"),
                cacheTtlMinutes = config.getInt("insight.cache.ttl-minutes"),
                queryTimeoutSeconds = config.getInt("insight.analytics-engine.query-timeout-seconds"),
                maxResultRows = config.getInt("insight.analytics-engine.max-result-rows")
            )
            
            // Initialize insight service
            insightService = InsightService(
                configuration = insightConfig,
                databaseConfig = databaseConfig
            )
            
            logger.info("Insight Service initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize Insight Service", e)
            throw e
        }
    }
    
    /**
     * Start the HTTP server
     */
    fun startServer(port: Int = 8080) {
        logger.info("Starting Insight Service HTTP server on port $port...")
        
        val server = embeddedServer(Netty, port = port) {
            configureRouting()
            configurePlugins()
        }
        
        server.start(wait = true)
    }
    
    /**
     * Configure Ktor routing
     */
    private fun Application.configureRouting() {
        routing {
            // Health check endpoint
            get("/health") {
                call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
            }
            
            // API routes
            route("/api/v1") {
                // Analytics Queries
                route("/queries") {
                    get {
                        val createdBy = call.request.queryParameters["createdBy"]
                        val queryType = call.request.queryParameters["queryType"]?.let { 
                            try { com.ataiva.eden.insight.model.QueryType.valueOf(it) } catch (e: Exception) { null }
                        }
                        val isActive = call.request.queryParameters["isActive"]?.toBoolean()
                        val tags = call.request.queryParameters.getAll("tag") ?: emptyList()
                        
                        val queries = runBlocking {
                            insightService.getQueries(createdBy, queryType, tags, isActive)
                        }
                        
                        call.respond(queries)
                    }
                    
                    get("/{id}") {
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val query = runBlocking { insightService.getQuery(id) }
                        
                        if (query != null) {
                            call.respond(query)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                    
                    // Additional query endpoints would be defined here
                }
                
                // Reports
                route("/reports") {
                    // Report endpoints would be defined here
                }
                
                // Dashboards
                route("/dashboards") {
                    // Dashboard endpoints would be defined here
                }
                
                // Metrics
                route("/metrics") {
                    // Metric endpoints would be defined here
                }
                
                // KPIs
                route("/kpis") {
                    // KPI endpoints would be defined here
                }
                
                // Analytics
                route("/analytics") {
                    // Analytics endpoints would be defined here
                }
            }
        }
    }
    
    /**
     * Configure Ktor plugins
     */
    private fun Application.configurePlugins() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    /**
     * Shutdown the application
     */
    fun shutdown() {
        logger.info("Shutting down Insight Service...")
        
        try {
            // Close database connections
            if (::databaseConfig.isInitialized) {
                databaseConfig.close()
            }
            
            logger.info("Insight Service shutdown complete")
        } catch (e: Exception) {
            logger.error("Error during shutdown", e)
        }
    }
}

/**
 * Main entry point
 */
fun main() {
    val application = InsightApplication()
    
    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        application.shutdown()
    })
    
    try {
        // Initialize and start the application
        application.initialize()
        application.startServer()
    } catch (e: Exception) {
        System.err.println("Failed to start Insight Service: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}