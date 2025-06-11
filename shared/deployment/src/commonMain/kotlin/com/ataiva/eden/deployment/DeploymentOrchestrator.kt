package com.ataiva.eden.deployment

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Advanced deployment orchestration system for Eden DevOps Suite
 * Provides blue-green deployments, canary releases, rollback capabilities, and health monitoring
 */
interface DeploymentOrchestrator {
    suspend fun createDeployment(request: DeploymentRequest): DeploymentResult
    suspend fun getDeployment(deploymentId: String): Deployment?
    suspend fun listDeployments(filter: DeploymentFilter = DeploymentFilter.ALL): List<Deployment>
    suspend fun rollbackDeployment(deploymentId: String, targetVersion: String? = null): DeploymentResult
    suspend fun cancelDeployment(deploymentId: String): DeploymentResult
    
    fun watchDeployment(deploymentId: String): Flow<DeploymentEvent>
    fun getDeploymentLogs(deploymentId: String): Flow<DeploymentLog>
    
    suspend fun validateDeployment(request: DeploymentRequest): ValidationResult
    suspend fun estimateDeploymentTime(request: DeploymentRequest): Duration
}

class DefaultDeploymentOrchestrator(
    private val kubernetesClient: KubernetesClient,
    private val dockerRegistry: DockerRegistry,
    private val healthChecker: HealthChecker,
    private val notificationService: NotificationService
) : DeploymentOrchestrator {
    
    private val deployments = mutableMapOf<String, Deployment>()
    private val deploymentEvents = MutableSharedFlow<DeploymentEvent>()
    private val deploymentLogs = MutableSharedFlow<DeploymentLog>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override suspend fun createDeployment(request: DeploymentRequest): DeploymentResult {
        val deploymentId = generateDeploymentId()
        
        try {
            // Validate deployment request
            val validation = validateDeployment(request)
            if (!validation.isValid) {
                return DeploymentResult.failure(deploymentId, "Validation failed: ${validation.errors.joinToString()}")
            }
            
            // Create deployment record
            val deployment = Deployment(
                id = deploymentId,
                request = request,
                status = DeploymentStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                strategy = determineDeploymentStrategy(request)
            )
            
            deployments[deploymentId] = deployment
            emitEvent(DeploymentEvent.Created(deployment))
            
            // Start deployment process asynchronously
            scope.launch {
                executeDeployment(deployment)
            }
            
            return DeploymentResult.success(deploymentId, "Deployment initiated successfully")
            
        } catch (e: Exception) {
            return DeploymentResult.failure(deploymentId, "Failed to create deployment: ${e.message}")
        }
    }
    
    override suspend fun getDeployment(deploymentId: String): Deployment? {
        return deployments[deploymentId]
    }
    
    override suspend fun listDeployments(filter: DeploymentFilter): List<Deployment> {
        return deployments.values.filter { deployment ->
            when (filter) {
                DeploymentFilter.ALL -> true
                is DeploymentFilter.ByStatus -> deployment.status == filter.status
                is DeploymentFilter.ByEnvironment -> deployment.request.environment == filter.environment
                is DeploymentFilter.ByService -> deployment.request.serviceName == filter.serviceName
                is DeploymentFilter.ByTimeRange -> deployment.createdAt in filter.startTime..filter.endTime
            }
        }.sortedByDescending { it.createdAt }
    }
    
    override suspend fun rollbackDeployment(deploymentId: String, targetVersion: String?): DeploymentResult {
        val deployment = deployments[deploymentId]
            ?: return DeploymentResult.failure(deploymentId, "Deployment not found")
        
        if (deployment.status != DeploymentStatus.COMPLETED && deployment.status != DeploymentStatus.FAILED) {
            return DeploymentResult.failure(deploymentId, "Cannot rollback deployment in status: ${deployment.status}")
        }
        
        val rollbackVersion = targetVersion ?: deployment.previousVersion
            ?: return DeploymentResult.failure(deploymentId, "No previous version available for rollback")
        
        val rollbackRequest = deployment.request.copy(
            version = rollbackVersion,
            rollback = true
        )
        
        return createDeployment(rollbackRequest)
    }
    
    override suspend fun cancelDeployment(deploymentId: String): DeploymentResult {
        val deployment = deployments[deploymentId]
            ?: return DeploymentResult.failure(deploymentId, "Deployment not found")
        
        if (deployment.status.isTerminal()) {
            return DeploymentResult.failure(deploymentId, "Cannot cancel deployment in terminal status: ${deployment.status}")
        }
        
        // Update deployment status
        val updatedDeployment = deployment.copy(
            status = DeploymentStatus.CANCELLED,
            completedAt = System.currentTimeMillis(),
            error = "Deployment cancelled by user"
        )
        
        deployments[deploymentId] = updatedDeployment
        emitEvent(DeploymentEvent.Cancelled(updatedDeployment))
        
        return DeploymentResult.success(deploymentId, "Deployment cancelled successfully")
    }
    
    override fun watchDeployment(deploymentId: String): Flow<DeploymentEvent> {
        return deploymentEvents.asSharedFlow().filter { event ->
            event.deployment.id == deploymentId
        }
    }
    
    override fun getDeploymentLogs(deploymentId: String): Flow<DeploymentLog> {
        return deploymentLogs.asSharedFlow().filter { log ->
            log.deploymentId == deploymentId
        }
    }
    
    override suspend fun validateDeployment(request: DeploymentRequest): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate service name
        if (request.serviceName.isBlank()) {
            errors.add("Service name cannot be empty")
        }
        
        // Validate version
        if (request.version.isBlank()) {
            errors.add("Version cannot be empty")
        }
        
        // Validate environment
        if (request.environment.isBlank()) {
            errors.add("Environment cannot be empty")
        }
        
        // Validate image exists in registry
        try {
            val imageExists = dockerRegistry.imageExists(request.imageName, request.version)
            if (!imageExists) {
                errors.add("Image ${request.imageName}:${request.version} not found in registry")
            }
        } catch (e: Exception) {
            errors.add("Failed to validate image: ${e.message}")
        }
        
        // Validate Kubernetes resources
        try {
            kubernetesClient.validateResources(request.kubernetesManifests)
        } catch (e: Exception) {
            errors.add("Invalid Kubernetes manifests: ${e.message}")
        }
        
        // Validate resource requirements
        if (request.resources.cpu <= 0 || request.resources.memory <= 0) {
            errors.add("Invalid resource requirements")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    override suspend fun estimateDeploymentTime(request: DeploymentRequest): Duration {
        // Base deployment time
        var estimatedTime = 2.minutes
        
        // Add time based on deployment strategy
        when (request.strategy) {
            DeploymentStrategy.ROLLING_UPDATE -> estimatedTime += 1.minutes
            DeploymentStrategy.BLUE_GREEN -> estimatedTime += 3.minutes
            DeploymentStrategy.CANARY -> estimatedTime += 5.minutes
            DeploymentStrategy.RECREATE -> estimatedTime += 30.seconds
            else -> estimatedTime += 2.minutes // Default case for null or future strategies
        }
        
        // Add time based on replicas
        estimatedTime += (request.replicas * 10).seconds
        
        // Add time for health checks
        estimatedTime += request.healthCheck.timeout * request.healthCheck.retries
        
        return estimatedTime
    }
    
    private suspend fun executeDeployment(deployment: Deployment) {
        try {
            logDeployment(deployment.id, "Starting deployment execution")
            updateDeploymentStatus(deployment.id, DeploymentStatus.IN_PROGRESS)
            
            when (deployment.strategy) {
                DeploymentStrategy.ROLLING_UPDATE -> executeRollingUpdate(deployment)
                DeploymentStrategy.BLUE_GREEN -> executeBlueGreenDeployment(deployment)
                DeploymentStrategy.CANARY -> executeCanaryDeployment(deployment)
                DeploymentStrategy.RECREATE -> executeRecreateDeployment(deployment)
            }
            
            // Perform post-deployment health checks
            performHealthChecks(deployment)
            
            // Send success notification
            notificationService.sendDeploymentNotification(
                deployment,
                "Deployment completed successfully"
            )
            
            updateDeploymentStatus(deployment.id, DeploymentStatus.COMPLETED)
            logDeployment(deployment.id, "Deployment completed successfully")
            
        } catch (e: Exception) {
            logDeployment(deployment.id, "Deployment failed: ${e.message}", LogLevel.ERROR)
            updateDeploymentStatus(deployment.id, DeploymentStatus.FAILED, e.message)
            
            // Send failure notification
            notificationService.sendDeploymentNotification(
                deployment,
                "Deployment failed: ${e.message}"
            )
            
            // Attempt automatic rollback if configured
            if (deployment.request.autoRollback) {
                attemptAutoRollback(deployment)
            }
        }
    }
    
    private suspend fun executeRollingUpdate(deployment: Deployment) {
        logDeployment(deployment.id, "Executing rolling update deployment")
        
        val request = deployment.request
        val currentReplicas = kubernetesClient.getCurrentReplicas(request.serviceName, request.environment)
        
        // Update deployment with new image
        kubernetesClient.updateDeployment(
            serviceName = request.serviceName,
            environment = request.environment,
            image = "${request.imageName}:${request.version}",
            replicas = request.replicas,
            resources = request.resources
        )
        
        // Wait for rollout to complete
        kubernetesClient.waitForRollout(
            serviceName = request.serviceName,
            environment = request.environment,
            timeout = 10.minutes
        )
        
        logDeployment(deployment.id, "Rolling update completed")
    }
    
    private suspend fun executeBlueGreenDeployment(deployment: Deployment) {
        logDeployment(deployment.id, "Executing blue-green deployment")
        
        val request = deployment.request
        val greenEnvironment = "${request.environment}-green"
        
        try {
            // Deploy to green environment
            kubernetesClient.createDeployment(
                serviceName = request.serviceName,
                environment = greenEnvironment,
                image = "${request.imageName}:${request.version}",
                replicas = request.replicas,
                resources = request.resources
            )
            
            // Wait for green deployment to be ready
            kubernetesClient.waitForDeploymentReady(
                serviceName = request.serviceName,
                environment = greenEnvironment,
                timeout = 5.minutes
            )
            
            // Perform health checks on green environment
            val healthCheckPassed = performHealthChecksOnEnvironment(deployment, greenEnvironment)
            if (!healthCheckPassed) {
                throw Exception("Health checks failed on green environment")
            }
            
            // Switch traffic to green environment
            kubernetesClient.switchTraffic(
                serviceName = request.serviceName,
                fromEnvironment = request.environment,
                toEnvironment = greenEnvironment
            )
            
            // Clean up old blue environment after successful switch
            delay(1.minutes) // Grace period
            kubernetesClient.deleteDeployment(request.serviceName, request.environment)
            
            logDeployment(deployment.id, "Blue-green deployment completed")
            
        } catch (e: Exception) {
            // Clean up green environment on failure
            kubernetesClient.deleteDeployment(request.serviceName, greenEnvironment)
            throw e
        }
    }
    
    private suspend fun executeCanaryDeployment(deployment: Deployment) {
        logDeployment(deployment.id, "Executing canary deployment")
        
        val request = deployment.request
        val canaryEnvironment = "${request.environment}-canary"
        val canaryReplicas = maxOf(1, request.replicas / 10) // 10% traffic
        
        try {
            // Deploy canary version
            kubernetesClient.createDeployment(
                serviceName = request.serviceName,
                environment = canaryEnvironment,
                image = "${request.imageName}:${request.version}",
                replicas = canaryReplicas,
                resources = request.resources
            )
            
            // Configure traffic splitting (90% to stable, 10% to canary)
            kubernetesClient.configureTrafficSplit(
                serviceName = request.serviceName,
                stableEnvironment = request.environment,
                canaryEnvironment = canaryEnvironment,
                canaryWeight = 10
            )
            
            // Monitor canary for specified duration
            val monitoringDuration = request.canaryConfig?.monitoringDuration ?: 5.minutes
            logDeployment(deployment.id, "Monitoring canary for ${monitoringDuration}")
            
            val canaryMetrics = monitorCanaryDeployment(deployment, canaryEnvironment, monitoringDuration)
            
            // Evaluate canary metrics
            val canarySuccessful = evaluateCanaryMetrics(canaryMetrics, request.canaryConfig)
            
            if (canarySuccessful) {
                // Promote canary to full deployment
                logDeployment(deployment.id, "Canary successful, promoting to full deployment")
                
                // Scale up canary to full replicas
                kubernetesClient.scaleDeployment(
                    serviceName = request.serviceName,
                    environment = canaryEnvironment,
                    replicas = request.replicas
                )
                
                // Switch all traffic to canary
                kubernetesClient.switchTraffic(
                    serviceName = request.serviceName,
                    fromEnvironment = request.environment,
                    toEnvironment = canaryEnvironment
                )
                
                // Clean up old stable environment
                delay(1.minutes)
                kubernetesClient.deleteDeployment(request.serviceName, request.environment)
                
            } else {
                throw Exception("Canary deployment failed metrics evaluation")
            }
            
            logDeployment(deployment.id, "Canary deployment completed")
            
        } catch (e: Exception) {
            // Clean up canary environment on failure
            kubernetesClient.deleteDeployment(request.serviceName, canaryEnvironment)
            throw e
        }
    }
    
    private suspend fun executeRecreateDeployment(deployment: Deployment) {
        logDeployment(deployment.id, "Executing recreate deployment")
        
        val request = deployment.request
        
        // Delete existing deployment
        kubernetesClient.deleteDeployment(request.serviceName, request.environment)
        
        // Wait for pods to terminate
        kubernetesClient.waitForPodsTermination(
            serviceName = request.serviceName,
            environment = request.environment,
            timeout = 2.minutes
        )
        
        // Create new deployment
        kubernetesClient.createDeployment(
            serviceName = request.serviceName,
            environment = request.environment,
            image = "${request.imageName}:${request.version}",
            replicas = request.replicas,
            resources = request.resources
        )
        
        // Wait for new deployment to be ready
        kubernetesClient.waitForDeploymentReady(
            serviceName = request.serviceName,
            environment = request.environment,
            timeout = 5.minutes
        )
        
        logDeployment(deployment.id, "Recreate deployment completed")
    }
    
    private suspend fun performHealthChecks(deployment: Deployment): Boolean {
        logDeployment(deployment.id, "Performing health checks")
        
        val request = deployment.request
        val healthCheck = request.healthCheck
        
        repeat(healthCheck.retries) { attempt ->
            try {
                val isHealthy = healthChecker.checkHealth(
                    serviceName = request.serviceName,
                    environment = request.environment,
                    endpoint = healthCheck.endpoint,
                    timeout = healthCheck.timeout
                )
                
                if (isHealthy) {
                    logDeployment(deployment.id, "Health check passed")
                    return true
                }
                
                logDeployment(deployment.id, "Health check failed, attempt ${attempt + 1}/${healthCheck.retries}")
                
                if (attempt < healthCheck.retries - 1) {
                    delay(healthCheck.interval)
                }
                
            } catch (e: Exception) {
                logDeployment(deployment.id, "Health check error: ${e.message}", LogLevel.ERROR)
                if (attempt < healthCheck.retries - 1) {
                    delay(healthCheck.interval)
                }
            }
        }
        
        throw Exception("Health checks failed after ${healthCheck.retries} attempts")
    }
    
    private suspend fun performHealthChecksOnEnvironment(deployment: Deployment, environment: String): Boolean {
        return try {
            val healthCheck = deployment.request.healthCheck
            healthChecker.checkHealth(
                serviceName = deployment.request.serviceName,
                environment = environment,
                endpoint = healthCheck.endpoint,
                timeout = healthCheck.timeout
            )
        } catch (e: Exception) {
            logDeployment(deployment.id, "Health check failed on $environment: ${e.message}", LogLevel.ERROR)
            false
        }
    }
    
    private suspend fun monitorCanaryDeployment(
        deployment: Deployment,
        canaryEnvironment: String,
        duration: Duration
    ): CanaryMetrics {
        logDeployment(deployment.id, "Monitoring canary metrics")
        
        val startTime = System.currentTimeMillis()
        val endTime = startTime + duration.inWholeMilliseconds
        
        var errorRate = 0.0
        var responseTime = 0.0
        var requestCount = 0L
        
        while (System.currentTimeMillis() < endTime) {
            try {
                val metrics = healthChecker.getMetrics(
                    serviceName = deployment.request.serviceName,
                    environment = canaryEnvironment
                )
                
                errorRate = metrics.errorRate
                responseTime = metrics.averageResponseTime
                requestCount = metrics.requestCount
                
                logDeployment(
                    deployment.id,
                    "Canary metrics - Error Rate: $errorRate%, Response Time: ${responseTime}ms, Requests: $requestCount"
                )
                
                delay(30.seconds)
                
            } catch (e: Exception) {
                logDeployment(deployment.id, "Failed to collect canary metrics: ${e.message}", LogLevel.ERROR)
            }
        }
        
        return CanaryMetrics(
            errorRate = errorRate,
            averageResponseTime = responseTime,
            requestCount = requestCount,
            duration = duration
        )
    }
    
    private fun evaluateCanaryMetrics(metrics: CanaryMetrics, config: CanaryConfig?): Boolean {
        val thresholds = config ?: CanaryConfig()
        
        return metrics.errorRate <= thresholds.maxErrorRate &&
                metrics.averageResponseTime <= thresholds.maxResponseTime &&
                metrics.requestCount >= thresholds.minRequestCount
    }
    
    private suspend fun attemptAutoRollback(deployment: Deployment) {
        logDeployment(deployment.id, "Attempting automatic rollback")
        
        try {
            val previousVersion = deployment.previousVersion
            if (previousVersion != null) {
                rollbackDeployment(deployment.id, previousVersion)
                logDeployment(deployment.id, "Automatic rollback initiated")
            } else {
                logDeployment(deployment.id, "No previous version available for rollback", LogLevel.WARN)
            }
        } catch (e: Exception) {
            logDeployment(deployment.id, "Automatic rollback failed: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun determineDeploymentStrategy(request: DeploymentRequest): DeploymentStrategy {
        return request.strategy ?: when {
            request.environment.contains("prod") -> DeploymentStrategy.BLUE_GREEN
            request.canaryConfig != null -> DeploymentStrategy.CANARY
            else -> DeploymentStrategy.ROLLING_UPDATE
        }
    }
    
    private fun generateDeploymentId(): String {
        return "deploy-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
    
    private suspend fun updateDeploymentStatus(
        deploymentId: String,
        status: DeploymentStatus,
        error: String? = null
    ) {
        val deployment = deployments[deploymentId] ?: return
        val updatedDeployment = deployment.copy(
            status = status,
            error = error,
            completedAt = if (status.isTerminal()) System.currentTimeMillis() else null
        )
        
        deployments[deploymentId] = updatedDeployment
        emitEvent(DeploymentEvent.StatusChanged(updatedDeployment, status))
    }
    
    private suspend fun logDeployment(
        deploymentId: String,
        message: String,
        level: LogLevel = LogLevel.INFO
    ) {
        val log = DeploymentLog(
            deploymentId = deploymentId,
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message
        )
        deploymentLogs.emit(log)
    }
    
    private suspend fun emitEvent(event: DeploymentEvent) {
        deploymentEvents.emit(event)
    }
}

// Data classes and enums for deployment system
@Serializable
data class DeploymentRequest(
    val serviceName: String,
    val version: String,
    val environment: String,
    val imageName: String,
    val replicas: Int = 1,
    val strategy: DeploymentStrategy? = null,
    val resources: ResourceRequirements = ResourceRequirements(),
    val healthCheck: HealthCheckConfig = HealthCheckConfig(),
    val kubernetesManifests: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val autoRollback: Boolean = true,
    val rollback: Boolean = false,
    val canaryConfig: CanaryConfig? = null
)

@Serializable
data class Deployment(
    val id: String,
    val request: DeploymentRequest,
    val status: DeploymentStatus,
    val createdAt: Long,
    val completedAt: Long? = null,
    val strategy: DeploymentStrategy,
    val previousVersion: String? = null,
    val error: String? = null
)

@Serializable
data class DeploymentResult(
    val deploymentId: String,
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun success(deploymentId: String, message: String) = DeploymentResult(deploymentId, true, message)
        fun failure(deploymentId: String, message: String) = DeploymentResult(deploymentId, false, message)
    }
}

@Serializable
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

@Serializable
data class ResourceRequirements(
    val cpu: Double = 0.5, // CPU cores
    val memory: Long = 512, // Memory in MB
    val storage: Long = 1024 // Storage in MB
)

@Serializable
data class HealthCheckConfig(
    val endpoint: String = "/health",
    val timeout: Duration = 30.seconds,
    val interval: Duration = 10.seconds,
    val retries: Int = 3
)

@Serializable
data class CanaryConfig(
    val maxErrorRate: Double = 5.0, // Maximum error rate percentage
    val maxResponseTime: Double = 1000.0, // Maximum response time in ms
    val minRequestCount: Long = 100, // Minimum requests for evaluation
    val monitoringDuration: Duration = 5.minutes
)

@Serializable
data class CanaryMetrics(
    val errorRate: Double,
    val averageResponseTime: Double,
    val requestCount: Long,
    val duration: Duration
)

@Serializable
data class DeploymentLog(
    val deploymentId: String,
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

@Serializable
enum class DeploymentStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED;
    
    fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, CANCELLED)
}

@Serializable
enum class DeploymentStrategy {
    ROLLING_UPDATE, BLUE_GREEN, CANARY, RECREATE
}

@Serializable
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

sealed class DeploymentFilter {
    object ALL : DeploymentFilter()
    data class ByStatus(val status: DeploymentStatus) : DeploymentFilter()
    data class ByEnvironment(val environment: String) : DeploymentFilter()
    data class ByService(val serviceName: String) : DeploymentFilter()
    data class ByTimeRange(val startTime: Long, val endTime: Long) : DeploymentFilter()
}

sealed class DeploymentEvent {
    abstract val deployment: Deployment
    
    data class Created(override val deployment: Deployment) : DeploymentEvent()
    data class StatusChanged(override val deployment: Deployment, val newStatus: DeploymentStatus) : DeploymentEvent()
    data class Cancelled(override val deployment: Deployment) : DeploymentEvent()
}

// Interface definitions for external dependencies
interface KubernetesClient {
    suspend fun validateResources(manifests: List<String>)
    suspend fun getCurrentReplicas(serviceName: String, environment: String): Int
    suspend fun updateDeployment(serviceName: String, environment: String, image: String, replicas: Int, resources: ResourceRequirements)
    suspend fun createDeployment(serviceName: String, environment: String, image: String, replicas: Int, resources: ResourceRequirements)
    suspend fun deleteDeployment(serviceName: String, environment: String)
    suspend fun waitForRollout(serviceName: String, environment: String, timeout: Duration)
    suspend fun waitForDeploymentReady(serviceName: String, environment: String, timeout: Duration)
    suspend fun waitForPodsTermination(serviceName: String, environment: String, timeout: Duration)
    suspend fun switchTraffic(serviceName: String, fromEnvironment: String, toEnvironment: String)
    suspend fun configureTrafficSplit(serviceName: String, stableEnvironment: String, canaryEnvironment: String, canaryWeight: Int)
    suspend fun scaleDeployment(serviceName: String, environment: String, replicas: Int)
}

interface DockerRegistry {
    suspend fun imageExists(imageName: String, tag: String): Boolean
}

interface HealthChecker {
    suspend fun checkHealth(serviceName: String, environment: String, endpoint: String, timeout: Duration): Boolean
    suspend fun getMetrics(serviceName: String, environment: String): ServiceMetrics
}

interface NotificationService {
    suspend fun sendDeploymentNotification(deployment: Deployment, message: String)
}

@Serializable
data class ServiceMetrics(
    val errorRate: Double,
    val averageResponseTime: Double,
    val requestCount: Long
)