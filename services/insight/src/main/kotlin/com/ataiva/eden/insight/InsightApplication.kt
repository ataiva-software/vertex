package com.ataiva.eden.insight

import com.ataiva.eden.insight.config.InsightDatabaseConfig
import com.ataiva.eden.insight.config.RedisCacheConfig
import com.ataiva.eden.insight.service.CacheService
import com.ataiva.eden.insight.service.InsightConfiguration
import com.ataiva.eden.insight.service.InsightService
import com.ataiva.eden.insight.service.MetricsService
import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory
import io.ktor.server.request.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import java.io.StringWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Main application class for the Insight Service.
 * Initializes the database, service, and starts the HTTP server.
 */
class InsightApplication {
    private val logger = LoggerFactory.getLogger(InsightApplication::class.java)
    private val config = ConfigFactory.load()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    // Thread pools for different types of operations
    private val ioThreadPool = Executors.newFixedThreadPool(32).asCoroutineDispatcher()
    private val cpuThreadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2
    ).asCoroutineDispatcher()
    private val schedulerThreadPool = Executors.newScheduledThreadPool(4)
    
    // Dispatchers for different types of operations
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ioDispatcher = Dispatchers.IO.limitedParallelism(32)
    val cpuDispatcher = cpuThreadPool
    val defaultDispatcher = Dispatchers.Default
    
    // Service components
    private lateinit var databaseConfig: InsightDatabaseConfig
    private lateinit var redisCacheConfig: RedisCacheConfig
    private lateinit var cacheService: CacheService
    private lateinit var metricsService: MetricsService
    private lateinit var insightService: InsightService
    
    // Metrics registry
    private val metricsRegistry = CollectorRegistry.defaultRegistry
    
    /**
     * Initialize the application
     */
    fun initialize() {
        logger.info("Initializing Insight Service...")
        
        try {
            // Initialize Prometheus metrics for JVM monitoring
            DefaultExports.initialize()
            
            // Initialize database configuration
            databaseConfig = InsightDatabaseConfig(
                configPath = "database",
                migrateOnStartup = true
            )
            
            // Initialize Redis cache configuration
            redisCacheConfig = RedisCacheConfig(
                configPath = "insight.cache.redis"
            )
            
            // Initialize cache service
            cacheService = CacheService(redisCacheConfig)
            
            // Initialize metrics service
            metricsService = MetricsService(metricsRegistry)
            
            // Create insight configuration from application.conf
            val insightConfig = InsightConfiguration(
                reportOutputPath = config.getString("insight.report-output-path"),
                cacheEnabled = true, // Always enable caching for high-load scenarios
                cacheMaxSize = config.getInt("insight.cache.in-memory.max-size"),
                cacheTtlMinutes = config.getInt("insight.cache.in-memory.ttl-minutes"),
                queryTimeoutSeconds = config.getInt("insight.analytics-engine.query-timeout-seconds"),
                maxResultRows = config.getInt("insight.analytics-engine.max-result-rows"),
                ioDispatcher = ioDispatcher,
                cpuDispatcher = cpuDispatcher
            )
            
            // Initialize insight service
            // Create model config for the analytics engine
            val modelConfig = com.ataiva.eden.insight.model.InsightConfiguration(
                maxQueryTimeout = insightConfig.queryTimeoutSeconds * 1000,
                maxResultRows = insightConfig.maxResultRows,
                cacheEnabled = insightConfig.cacheEnabled,
                cacheTtl = insightConfig.cacheTtlMinutes * 60,
                reportOutputPath = insightConfig.reportOutputPath,
                maxConcurrentQueries = 10
            )
            
            insightService = InsightService(
                serviceConfig = insightConfig,
                databaseConfig = databaseConfig
            )
            
            logger.info("Insight Service initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize Insight Service", e)
            throw e
        }
    }
    
    /**
     * Start the HTTP server with optimized settings for high load
     */
    fun startServer(port: Int = 8080) {
        logger.info("Starting Insight Service HTTP server on port $port...")
        
        // Configure Netty with optimized settings for high load
        val server = embeddedServer(Netty, port = port, configure = {
            // Optimize connection settings
            connectionGroupSize = 4                // Number of NIO selector threads
            workerGroupSize = 16                   // Number of worker threads
            callGroupSize = 32                     // Number of processing threads
            
            // Optimize socket options
            tcpKeepAlive = true                    // Keep connections alive
            
            // Configure request queue
            requestQueueLimit = 16384              // Increase request queue size
            runningLimit = 1000                    // Max number of running requests
            responseWriteTimeoutSeconds = 120      // Response write timeout
        }) {
            // Configure Ktor application
            configureRouting()
            configurePlugins()
        }
        
        // Start the server with graceful shutdown
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
            
            // Prometheus metrics endpoint
            get("/metrics") {
                // Update JVM metrics
                metricsService.updateJvmMemoryMetrics()
                metricsService.updateJvmThreadMetrics()
                
                // Update connection pool metrics
                if (::databaseConfig.isInitialized) {
                    val hikariDataSource = databaseConfig.getDataSource()
                    val poolMetrics = hikariDataSource.hikariPoolMXBean
                    if (poolMetrics != null) {
                        metricsService.updateConnectionPoolMetrics(
                            total = poolMetrics.totalConnections,
                            active = poolMetrics.activeConnections,
                            idle = poolMetrics.idleConnections,
                            waiting = poolMetrics.threadsAwaitingConnection
                        )
                    }
                }
                
                // Update cache metrics
                if (::cacheService.isInitialized) {
                    val cacheStats = cacheService.getStats()
                    val inMemoryStats = cacheStats["in_memory"] as? Map<String, Any>
                    if (inMemoryStats != null) {
                        val size = (inMemoryStats["size"] as? Number)?.toLong() ?: 0
                        metricsService.updateCacheSize("in_memory", size)
                    }
                    
                    val redisStats = cacheStats["redis"] as? Map<String, Any>
                    if (redisStats != null) {
                        val keyCount = (redisStats["key_count"] as? Number)?.toLong() ?: 0
                        metricsService.updateCacheSize("redis", keyCount)
                    }
                }
                
                // Generate metrics response
                val writer = StringWriter()
                TextFormat.write004(writer, metricsRegistry.metricFamilySamples())
                call.respondText(writer.toString(), ContentType.parse("text/plain"))
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
     * Configure Ktor plugins for optimal performance
     */
    private fun Application.configurePlugins() {
        // Content negotiation for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false  // Disable pretty printing in production for better performance
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = false  // Don't encode default values for better network performance
            })
        }
        
        // Enable CORS for API access
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
        }
        
        // Enable compression to reduce network traffic
        install(Compression) {
            gzip {
                priority = 1.0
                minimumSize(1024) // Only compress responses larger than 1kb
            }
            deflate {
                priority = 0.9
                minimumSize(1024)
            }
        }
        
        // Add default headers
        install(DefaultHeaders) {
            header(HttpHeaders.Server, "Eden Insight Service")
        }
        
        // Support for forwarded headers
        install(ForwardedHeaders)
        
        // Request logging using interceptors instead of CallLogging plugin
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
    }
    
    /**
     * Shutdown the application gracefully
     */
    fun shutdown() {
        logger.info("Shutting down Insight Service...")
        
        try {
            // Close Redis cache connections
            if (::redisCacheConfig.isInitialized) {
                redisCacheConfig.close()
                logger.info("Redis connections closed")
            }
            
            // Close database connections
            if (::databaseConfig.isInitialized) {
                databaseConfig.close()
                logger.info("Database connections closed")
            }
            
            // Shutdown thread pools
            logger.info("Shutting down thread pools...")
            ioThreadPool.close()
            cpuThreadPool.close()
            
            schedulerThreadPool.shutdown()
            try {
                if (!schedulerThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    schedulerThreadPool.shutdownNow()
                }
            } catch (e: InterruptedException) {
                schedulerThreadPool.shutdownNow()
                Thread.currentThread().interrupt()
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