package com.ataiva.eden.monitoring

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Service dependency information
 */
@Serializable
data class ServiceDependency(
    val sourceService: String,
    val targetService: String,
    val endpoint: String,
    val callCount: Long = 0,
    val errorCount: Long = 0,
    val averageLatencyMs: Double = 0.0,
    val lastUpdated: Long = Instant.now().toEpochMilli()
)

/**
 * Service dependency graph
 */
@Serializable
data class ServiceDependencyGraph(
    val services: List<String>,
    val dependencies: List<ServiceDependency>,
    val timestamp: Long = Instant.now().toEpochMilli()
)

/**
 * Service dependency mapper
 * 
 * This class tracks service dependencies and generates a dependency graph
 * that can be used to visualize service relationships.
 */
class ServiceDependencyMapper(
    openTelemetry: OpenTelemetry,
    private val serviceName: String
) {
    private val dependencies = ConcurrentHashMap<String, ServiceDependency>()
    private val callCounts = ConcurrentHashMap<String, AtomicLong>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val latencySums = ConcurrentHashMap<String, AtomicLong>()
    private val latencyCounts = ConcurrentHashMap<String, AtomicLong>()
    
    private val meter = openTelemetry.getMeter("com.ataiva.eden.monitoring.dependencies")
    private val dependencyCallCounter: LongCounter
    private val dependencyErrorCounter: LongCounter
    
    init {
        dependencyCallCounter = meter.counterBuilder("service.dependency.calls")
            .setDescription("Number of calls to dependent services")
            .setUnit("1")
            .build()
        
        dependencyErrorCounter = meter.counterBuilder("service.dependency.errors")
            .setDescription("Number of errors in calls to dependent services")
            .setUnit("1")
            .build()
    }
    
    /**
     * Record a service dependency call
     */
    fun recordDependencyCall(
        targetService: String,
        endpoint: String,
        latencyMs: Long,
        isError: Boolean = false
    ) {
        val key = "$targetService:$endpoint"
        
        // Update metrics
        val attributes = Attributes.builder()
            .put("source_service", serviceName)
            .put("target_service", targetService)
            .put("endpoint", endpoint)
            .build()
        
        dependencyCallCounter.add(1, attributes)
        if (isError) {
            dependencyErrorCounter.add(1, attributes)
        }
        
        // Update internal counters
        callCounts.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        if (isError) {
            errorCounts.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        }
        
        // Update latency tracking
        latencySums.computeIfAbsent(key) { AtomicLong(0) }.addAndGet(latencyMs)
        latencyCounts.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        
        // Update dependency information
        val callCount = callCounts[key]?.get() ?: 0
        val errorCount = errorCounts[key]?.get() ?: 0
        val latencySum = latencySums[key]?.get() ?: 0
        val latencyCount = latencyCounts[key]?.get() ?: 1
        val averageLatency = latencySum.toDouble() / latencyCount.toDouble()
        
        val dependency = ServiceDependency(
            sourceService = serviceName,
            targetService = targetService,
            endpoint = endpoint,
            callCount = callCount,
            errorCount = errorCount,
            averageLatencyMs = averageLatency,
            lastUpdated = Instant.now().toEpochMilli()
        )
        
        dependencies[key] = dependency
    }
    
    /**
     * Get all service dependencies
     */
    fun getDependencies(): List<ServiceDependency> {
        return dependencies.values.toList()
    }
    
    /**
     * Generate a service dependency graph
     */
    fun generateDependencyGraph(): ServiceDependencyGraph {
        val allDependencies = dependencies.values.toList()
        val services = mutableSetOf(serviceName)
        
        allDependencies.forEach { dependency ->
            services.add(dependency.sourceService)
            services.add(dependency.targetService)
        }
        
        return ServiceDependencyGraph(
            services = services.toList(),
            dependencies = allDependencies
        )
    }
    
    /**
     * Export the dependency graph to a JSON file
     */
    suspend fun exportDependencyGraph(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val graph = generateDependencyGraph()
            val json = Json { 
                prettyPrint = true 
                encodeDefaults = true
            }
            val jsonString = json.encodeToString(graph)
            
            File(filePath).writeText(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Export the dependency graph in DOT format for visualization with Graphviz
     */
    suspend fun exportDependencyGraphAsDot(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val graph = generateDependencyGraph()
            val dotBuilder = StringBuilder()
            
            dotBuilder.append("digraph ServiceDependencies {\n")
            dotBuilder.append("  rankdir=LR;\n")
            dotBuilder.append("  node [shape=box, style=filled, fillcolor=lightblue];\n\n")
            
            // Add nodes
            graph.services.forEach { service ->
                dotBuilder.append("  \"$service\";\n")
            }
            
            dotBuilder.append("\n")
            
            // Add edges
            graph.dependencies.forEach { dependency ->
                val label = "calls: ${dependency.callCount}, " +
                        "errors: ${dependency.errorCount}, " +
                        "avg latency: ${String.format("%.2f", dependency.averageLatencyMs)}ms"
                
                val color = if (dependency.errorCount > 0) "red" else "black"
                val penWidth = (1 + Math.log10(dependency.callCount.toDouble().coerceAtLeast(1.0))).coerceIn(1.0, 5.0)
                
                dotBuilder.append("  \"${dependency.sourceService}\" -> \"${dependency.targetService}\" " +
                        "[label=\"$label\", color=$color, penwidth=$penWidth];\n")
            }
            
            dotBuilder.append("}\n")
            
            File(filePath).writeText(dotBuilder.toString())
            true
        } catch (e: Exception) {
            false
        }
    }
}