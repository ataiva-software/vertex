package com.ataiva.eden.monitoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Health check status
 */
enum class HealthStatus {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}

/**
 * Health check result
 */
@Serializable
data class HealthCheckResult(
    val name: String,
    val status: HealthStatus,
    val message: String? = null,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val details: Map<String, String> = emptyMap()
)

/**
 * Aggregate health check result
 */
@Serializable
data class AggregateHealthResult(
    val status: HealthStatus,
    val checks: List<HealthCheckResult>,
    val timestamp: Long = Instant.now().toEpochMilli()
)

/**
 * Health check interface
 */
interface HealthCheck {
    /**
     * Name of the health check
     */
    val name: String
    
    /**
     * Timeout for the health check
     */
    val timeout: Duration
        get() = 5.seconds
    
    /**
     * Execute the health check
     */
    suspend fun check(): HealthCheckResult
}

/**
 * Health check registry
 */
class HealthCheckRegistry {
    private val checks = mutableListOf<HealthCheck>()
    
    /**
     * Register a health check
     */
    fun register(check: HealthCheck) {
        checks.add(check)
    }
    
    /**
     * Run all health checks
     */
    suspend fun runHealthChecks(): AggregateHealthResult = coroutineScope {
        val results = checks.map { check ->
            async {
                try {
                    withTimeout(check.timeout) {
                        check.check()
                    }
                } catch (e: Exception) {
                    HealthCheckResult(
                        name = check.name,
                        status = HealthStatus.DOWN,
                        message = "Health check failed: ${e.message}",
                        details = mapOf("exception" to e.javaClass.name)
                    )
                }
            }
        }.awaitAll()
        
        val overallStatus = when {
            results.any { it.status == HealthStatus.DOWN } -> HealthStatus.DOWN
            results.any { it.status == HealthStatus.DEGRADED } -> HealthStatus.DEGRADED
            results.all { it.status == HealthStatus.UP } -> HealthStatus.UP
            else -> HealthStatus.UNKNOWN
        }
        
        AggregateHealthResult(
            status = overallStatus,
            checks = results
        )
    }
}

/**
 * Database health check
 */
class DatabaseHealthCheck(
    private val checkConnection: suspend () -> Boolean,
    override val name: String = "database",
    override val timeout: Duration = 3.seconds
) : HealthCheck {
    override suspend fun check(): HealthCheckResult {
        return try {
            val isConnected = checkConnection()
            if (isConnected) {
                HealthCheckResult(
                    name = name,
                    status = HealthStatus.UP,
                    message = "Database connection is healthy"
                )
            } else {
                HealthCheckResult(
                    name = name,
                    status = HealthStatus.DOWN,
                    message = "Database connection failed"
                )
            }
        } catch (e: Exception) {
            HealthCheckResult(
                name = name,
                status = HealthStatus.DOWN,
                message = "Database health check failed: ${e.message}",
                details = mapOf("exception" to e.javaClass.name)
            )
        }
    }
}

/**
 * Redis health check
 */
class RedisHealthCheck(
    private val checkConnection: suspend () -> Boolean,
    override val name: String = "redis",
    override val timeout: Duration = 3.seconds
) : HealthCheck {
    override suspend fun check(): HealthCheckResult {
        return try {
            val isConnected = checkConnection()
            if (isConnected) {
                HealthCheckResult(
                    name = name,
                    status = HealthStatus.UP,
                    message = "Redis connection is healthy"
                )
            } else {
                HealthCheckResult(
                    name = name,
                    status = HealthStatus.DOWN,
                    message = "Redis connection failed"
                )
            }
        } catch (e: Exception) {
            HealthCheckResult(
                name = name,
                status = HealthStatus.DOWN,
                message = "Redis health check failed: ${e.message}",
                details = mapOf("exception" to e.javaClass.name)
            )
        }
    }
}

/**
 * Dependency service health check
 */
class DependencyHealthCheck(
    private val serviceName: String,
    private val checkHealth: suspend () -> Boolean,
    override val name: String = "dependency-$serviceName",
    override val timeout: Duration = 5.seconds
) : HealthCheck {
    override suspend fun check(): HealthCheckResult {
        return try {
            val isHealthy = checkHealth()
            if (isHealthy) {
                HealthCheckResult(
                    name = name,
                    status = HealthStatus.UP,
                    message = "$serviceName service is healthy"
                )
            } else {
                HealthCheckResult(
                    name = name,
                    status = HealthStatus.DOWN,
                    message = "$serviceName service is unhealthy"
                )
            }
        } catch (e: Exception) {
            HealthCheckResult(
                name = name,
                status = HealthStatus.DOWN,
                message = "$serviceName health check failed: ${e.message}",
                details = mapOf("exception" to e.javaClass.name)
            )
        }
    }
}

/**
 * Disk space health check
 */
class DiskSpaceHealthCheck(
    private val path: String,
    private val thresholdPercent: Int = 90,
    override val name: String = "disk-space",
    override val timeout: Duration = 3.seconds
) : HealthCheck {
    override suspend fun check(): HealthCheckResult = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(path)
            val totalSpace = file.totalSpace
            val freeSpace = file.freeSpace
            val usedPercent = ((totalSpace - freeSpace) * 100) / totalSpace
            
            val status = if (usedPercent >= thresholdPercent) HealthStatus.DEGRADED else HealthStatus.UP
            val message = "Disk space usage: $usedPercent% (threshold: $thresholdPercent%)"
            
            HealthCheckResult(
                name = name,
                status = status,
                message = message,
                details = mapOf(
                    "path" to path,
                    "totalSpace" to "${totalSpace / (1024 * 1024)} MB",
                    "freeSpace" to "${freeSpace / (1024 * 1024)} MB",
                    "usedPercent" to "$usedPercent%"
                )
            )
        } catch (e: Exception) {
            HealthCheckResult(
                name = name,
                status = HealthStatus.DOWN,
                message = "Disk space check failed: ${e.message}",
                details = mapOf("exception" to e.javaClass.name)
            )
        }
    }
}

/**
 * Memory health check
 */
class MemoryHealthCheck(
    private val thresholdPercent: Int = 90,
    override val name: String = "memory",
    override val timeout: Duration = 3.seconds
) : HealthCheck {
    override suspend fun check(): HealthCheckResult {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usedPercent = (usedMemory * 100) / maxMemory
        
        val status = if (usedPercent >= thresholdPercent) HealthStatus.DEGRADED else HealthStatus.UP
        val message = "Memory usage: $usedPercent% (threshold: $thresholdPercent%)"
        
        return HealthCheckResult(
            name = name,
            status = status,
            message = message,
            details = mapOf(
                "maxMemory" to "${maxMemory / (1024 * 1024)} MB",
                "totalMemory" to "${totalMemory / (1024 * 1024)} MB",
                "freeMemory" to "${freeMemory / (1024 * 1024)} MB",
                "usedMemory" to "${usedMemory / (1024 * 1024)} MB",
                "usedPercent" to "$usedPercent%"
            )
        )
    }
}