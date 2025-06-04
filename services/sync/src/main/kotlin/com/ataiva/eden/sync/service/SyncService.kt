package com.ataiva.eden.sync.service

import com.ataiva.eden.sync.model.*
import com.ataiva.eden.sync.engine.SyncEngine
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Core business logic for the Eden Sync Service
 * Manages data synchronization operations, sources, destinations, and mappings
 */
class SyncService(
    private val syncEngine: SyncEngine
) {
    // In-memory storage for demo - would be replaced with database repositories
    private val syncJobs = ConcurrentHashMap<String, SyncJob>()
    private val dataSources = ConcurrentHashMap<String, DataSource>()
    private val destinations = ConcurrentHashMap<String, SyncDestination>()
    private val mappings = ConcurrentHashMap<String, DataMapping>()
    private val executions = ConcurrentHashMap<String, SyncExecution>()
    
    // Active sync tracking
    private val activeSyncs = ConcurrentHashMap<String, Job>()
    
    suspend fun createSyncJob(request: CreateSyncJobRequest): SyncResult<SyncJob> {
        return try {
            // Validate source exists
            val source = dataSources[request.sourceId]
                ?: return SyncResult.Error("Data source not found: ${request.sourceId}")
            
            // Validate destination exists
            val destination = destinations[request.destinationId]
                ?: return SyncResult.Error("Destination not found: ${request.destinationId}")
            
            // Validate mapping exists
            val mapping = mappings[request.mappingId]
                ?: return SyncResult.Error("Mapping not found: ${request.mappingId}")
            
            // Validate source and destination are connected
            if (source.status != ConnectionStatus.CONNECTED) {
                return SyncResult.Error("Source is not connected: ${source.name}")
            }
            
            if (destination.status != ConnectionStatus.CONNECTED) {
                return SyncResult.Error("Destination is not connected: ${destination.name}")
            }
            
            val now = System.currentTimeMillis()
            val jobId = UUID.randomUUID().toString()
            
            val syncJob = SyncJob(
                id = jobId,
                name = request.name,
                sourceId = request.sourceId,
                destinationId = request.destinationId,
                mappingId = request.mappingId,
                status = SyncStatus.CREATED,
                schedule = request.schedule,
                configuration = request.configuration,
                createdAt = now,
                updatedAt = now,
                lastRunAt = null,
                nextRunAt = calculateNextRun(request.schedule),
                userId = request.userId
            )
            
            syncJobs[jobId] = syncJob
            SyncResult.Success(syncJob)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to create sync job: ${e.message}")
        }
    }
    
    suspend fun updateSyncJob(jobId: String, request: UpdateSyncJobRequest): SyncResult<SyncJob> {
        return try {
            val existingJob = syncJobs[jobId]
                ?: return SyncResult.Error("Sync job not found: $jobId")
            
            val updatedJob = existingJob.copy(
                name = request.name ?: existingJob.name,
                schedule = request.schedule ?: existingJob.schedule,
                configuration = request.configuration ?: existingJob.configuration,
                status = request.status ?: existingJob.status,
                updatedAt = System.currentTimeMillis(),
                nextRunAt = if (request.schedule != null) calculateNextRun(request.schedule) else existingJob.nextRunAt
            )
            
            syncJobs[jobId] = updatedJob
            SyncResult.Success(updatedJob)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to update sync job: ${e.message}")
        }
    }
    
    suspend fun getSyncJob(jobId: String, userId: String): SyncResult<SyncJob> {
        return try {
            val job = syncJobs[jobId]
                ?: return SyncResult.Error("Sync job not found: $jobId")
            
            if (job.userId != userId) {
                return SyncResult.Error("Access denied to sync job: $jobId")
            }
            
            SyncResult.Success(job)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to get sync job: ${e.message}")
        }
    }
    
    suspend fun listSyncJobs(userId: String): SyncResult<List<SyncJob>> {
        return try {
            val userJobs = syncJobs.values.filter { it.userId == userId }
            SyncResult.Success(userJobs)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to list sync jobs: ${e.message}")
        }
    }
    
    suspend fun deleteSyncJob(jobId: String, userId: String): SyncResult<Unit> {
        return try {
            val job = syncJobs[jobId]
                ?: return SyncResult.Error("Sync job not found: $jobId")
            
            if (job.userId != userId) {
                return SyncResult.Error("Access denied to sync job: $jobId")
            }
            
            // Cancel if running
            activeSyncs[jobId]?.cancel()
            activeSyncs.remove(jobId)
            
            syncJobs.remove(jobId)
            SyncResult.Success(Unit)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to delete sync job: ${e.message}")
        }
    }
    
    suspend fun executeSyncJob(request: ExecuteSyncRequest): SyncResult<SyncExecution> {
        return try {
            val job = syncJobs[request.jobId]
                ?: return SyncResult.Error("Sync job not found: ${request.jobId}")
            
            if (job.userId != request.userId) {
                return SyncResult.Error("Access denied to sync job: ${request.jobId}")
            }
            
            // Check if already running
            if (activeSyncs.containsKey(request.jobId)) {
                return SyncResult.Error("Sync job is already running: ${request.jobId}")
            }
            
            val executionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            val execution = SyncExecution(
                id = executionId,
                jobId = request.jobId,
                status = SyncStatus.RUNNING,
                startedAt = now,
                completedAt = null,
                metrics = SyncMetrics()
            )
            
            executions[executionId] = execution
            
            // Update job status and last run time
            syncJobs[request.jobId] = job.copy(
                status = SyncStatus.RUNNING,
                lastRunAt = now,
                updatedAt = now
            )
            
            // Start async execution
            val syncJob = CoroutineScope(Dispatchers.IO).launch {
                performSyncExecution(executionId, job, request.overrideConfiguration)
            }
            
            activeSyncs[request.jobId] = syncJob
            
            SyncResult.Success(execution)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to execute sync job: ${e.message}")
        }
    }
    
    suspend fun getSyncExecution(executionId: String, userId: String): SyncResult<SyncExecution> {
        return try {
            val execution = executions[executionId]
                ?: return SyncResult.Error("Sync execution not found: $executionId")
            
            val job = syncJobs[execution.jobId]
                ?: return SyncResult.Error("Associated sync job not found")
            
            if (job.userId != userId) {
                return SyncResult.Error("Access denied to sync execution: $executionId")
            }
            
            SyncResult.Success(execution)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to get sync execution: ${e.message}")
        }
    }
    
    suspend fun listSyncExecutions(jobId: String?, userId: String, limit: Int = 50): SyncResult<List<SyncExecution>> {
        return try {
            val userExecutions = if (jobId != null) {
                val job = syncJobs[jobId]
                    ?: return SyncResult.Error("Sync job not found: $jobId")
                
                if (job.userId != userId) {
                    return SyncResult.Error("Access denied to sync job: $jobId")
                }
                
                executions.values.filter { it.jobId == jobId }
            } else {
                val userJobIds = syncJobs.values.filter { it.userId == userId }.map { it.id }.toSet()
                executions.values.filter { it.jobId in userJobIds }
            }
            
            val sortedExecutions = userExecutions
                .sortedByDescending { it.startedAt }
                .take(limit)
            
            SyncResult.Success(sortedExecutions)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to list sync executions: ${e.message}")
        }
    }
    
    suspend fun cancelSyncExecution(executionId: String, userId: String): SyncResult<Unit> {
        return try {
            val execution = executions[executionId]
                ?: return SyncResult.Error("Sync execution not found: $executionId")
            
            val job = syncJobs[execution.jobId]
                ?: return SyncResult.Error("Associated sync job not found")
            
            if (job.userId != userId) {
                return SyncResult.Error("Access denied to sync execution: $executionId")
            }
            
            // Cancel the running job
            activeSyncs[execution.jobId]?.cancel()
            activeSyncs.remove(execution.jobId)
            
            // Update execution status
            executions[executionId] = execution.copy(
                status = SyncStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            )
            
            // Update job status
            syncJobs[execution.jobId] = job.copy(
                status = SyncStatus.PAUSED,
                updatedAt = System.currentTimeMillis()
            )
            
            SyncResult.Success(Unit)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to cancel sync execution: ${e.message}")
        }
    }
    
    suspend fun getSyncStatus(): SyncResult<SyncStatusResponse> {
        return try {
            val now = System.currentTimeMillis()
            val oneDayAgo = now - (24 * 60 * 60 * 1000)
            
            val activeSyncs = executions.values.count { it.status == SyncStatus.RUNNING }
            val pendingSyncs = syncJobs.values.count { it.status == SyncStatus.SCHEDULED }
            val failedSyncs = executions.values.count { it.status == SyncStatus.FAILED }
            val completedToday = executions.values.count { 
                it.status == SyncStatus.COMPLETED && it.completedAt != null && it.completedAt!! > oneDayAgo 
            }
            
            val lastSyncAt = executions.values
                .filter { it.completedAt != null }
                .maxByOrNull { it.completedAt!! }
                ?.completedAt
            
            val systemLoad = activeSyncs.toDouble() / maxOf(1, Runtime.getRuntime().availableProcessors())
            
            val status = SyncStatusResponse(
                activeSyncs = activeSyncs,
                pendingSyncs = pendingSyncs,
                failedSyncs = failedSyncs,
                completedSyncsToday = completedToday,
                lastSyncAt = lastSyncAt,
                systemLoad = systemLoad
            )
            
            SyncResult.Success(status)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to get sync status: ${e.message}")
        }
    }
    
    // Data Source Management
    suspend fun createDataSource(request: CreateDataSourceRequest): SyncResult<DataSource> {
        return try {
            val sourceId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            val dataSource = DataSource(
                id = sourceId,
                name = request.name,
                type = request.type,
                connectionConfig = request.connectionConfig,
                status = ConnectionStatus.UNKNOWN,
                createdAt = now,
                updatedAt = now,
                lastTestedAt = null,
                userId = request.userId
            )
            
            dataSources[sourceId] = dataSource
            
            // Test connection asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                testDataSourceConnection(sourceId)
            }
            
            SyncResult.Success(dataSource)
            
        } catch (e: Exception) {
            SyncResult.Error("Failed to create data source: ${e.message}")
        }
    }
    
    suspend fun testConnectionAsync(request: TestConnectionRequest): SyncResult<TestConnectionResponse> {
        return try {
            val startTime = System.currentTimeMillis()
            
            // Simulate connection test - in real implementation, this would test actual connections
            val success = when (request.type) {
                SourceType.DATABASE -> testDatabaseConnection(request.connectionConfig)
                SourceType.REST_API -> testApiConnection(request.connectionConfig)
                SourceType.FILE_SYSTEM -> testFileSystemConnection(request.connectionConfig)
                SourceType.CLOUD_STORAGE -> testCloudStorageConnection(request.connectionConfig)
                SourceType.MESSAGE_QUEUE -> testMessageQueueConnection(request.connectionConfig)
                SourceType.WEBHOOK -> testWebhookConnection(request.connectionConfig)
            }
            
            val latency = System.currentTimeMillis() - startTime
            
            val response = TestConnectionResponse(
                success = success,
                message = if (success) "Connection successful" else "Connection failed",
                latencyMs = latency,
                details = mapOf(
                    "type" to request.type.name,
                    "host" to (request.connectionConfig.host ?: "N/A"),
                    "tested_at" to System.currentTimeMillis().toString()
                )
            )
            
            SyncResult.Success(response)
            
        } catch (e: Exception) {
            SyncResult.Error("Connection test failed: ${e.message}")
        }
    }
    
    // Private helper methods
    private fun calculateNextRun(schedule: SyncSchedule?): Long? {
        if (schedule == null || !schedule.enabled) return null
        
        val now = System.currentTimeMillis()
        return when (schedule.type) {
            ScheduleType.MANUAL -> null
            ScheduleType.INTERVAL -> {
                val intervalMs = (schedule.intervalMinutes ?: 60) * 60 * 1000
                now + intervalMs
            }
            ScheduleType.CRON -> {
                // Simplified cron calculation - in real implementation, use a cron library
                now + (60 * 60 * 1000) // Default to 1 hour
            }
            ScheduleType.REAL_TIME -> now + 1000 // 1 second for real-time
        }
    }
    
    private suspend fun performSyncExecution(executionId: String, job: SyncJob, overrideConfig: SyncConfiguration?) {
        try {
            val execution = executions[executionId] ?: return
            val config = overrideConfig ?: job.configuration
            
            // Get required components
            val source = dataSources[job.sourceId] ?: throw Exception("Source not found")
            val destination = destinations[job.destinationId] ?: throw Exception("Destination not found")
            val mapping = mappings[job.mappingId] ?: throw Exception("Mapping not found")
            
            // Use sync engine to perform the actual synchronization
            val result = syncEngine.executeSync(source, destination, mapping, config)
            
            val now = System.currentTimeMillis()
            val updatedExecution = execution.copy(
                status = if (result.success) SyncStatus.COMPLETED else SyncStatus.FAILED,
                completedAt = now,
                recordsProcessed = result.recordsProcessed,
                recordsSucceeded = result.recordsSucceeded,
                recordsFailed = result.recordsFailed,
                recordsSkipped = result.recordsSkipped,
                errorMessage = result.errorMessage,
                progressPercentage = 100.0,
                metrics = result.metrics
            )
            
            executions[executionId] = updatedExecution
            
            // Update job status
            syncJobs[job.id] = job.copy(
                status = if (result.success) SyncStatus.COMPLETED else SyncStatus.FAILED,
                updatedAt = now,
                nextRunAt = if (result.success) calculateNextRun(job.schedule) else null
            )
            
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val execution = executions[executionId] ?: return
            
            executions[executionId] = execution.copy(
                status = SyncStatus.FAILED,
                completedAt = now,
                errorMessage = e.message,
                progressPercentage = 0.0
            )
            
            syncJobs[job.id] = job.copy(
                status = SyncStatus.FAILED,
                updatedAt = now
            )
        } finally {
            activeSyncs.remove(job.id)
        }
    }
    
    private suspend fun testDataSourceConnection(sourceId: String) {
        val source = dataSources[sourceId] ?: return
        
        try {
            val testResult = testConnectionAsync(TestConnectionRequest(source.connectionConfig, source.type))
            val status = when (testResult) {
                is SyncResult.Success -> if (testResult.data.success) ConnectionStatus.CONNECTED else ConnectionStatus.ERROR
                is SyncResult.Error -> ConnectionStatus.ERROR
            }
            
            dataSources[sourceId] = source.copy(
                status = status,
                lastTestedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            dataSources[sourceId] = source.copy(
                status = ConnectionStatus.ERROR,
                lastTestedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
    
    // Connection test implementations (simplified for demo)
    private suspend fun testDatabaseConnection(config: ConnectionConfig): Boolean {
        delay(100) // Simulate connection test
        return config.host != null && config.database != null
    }
    
    private suspend fun testApiConnection(config: ConnectionConfig): Boolean {
        delay(50) // Simulate API test
        return config.host != null
    }
    
    private suspend fun testFileSystemConnection(config: ConnectionConfig): Boolean {
        delay(20) // Simulate file system test
        return true // File system is always available
    }
    
    private suspend fun testCloudStorageConnection(config: ConnectionConfig): Boolean {
        delay(200) // Simulate cloud connection test
        return config.apiKey != null
    }
    
    private suspend fun testMessageQueueConnection(config: ConnectionConfig): Boolean {
        delay(150) // Simulate message queue test
        return config.host != null && config.port != null
    }
    
    private suspend fun testWebhookConnection(config: ConnectionConfig): Boolean {
        delay(100) // Simulate webhook test
        return config.host != null
    }
}

/**
 * Result wrapper for sync service operations
 */
sealed class SyncResult<out T> {
    data class Success<T>(val data: T) : SyncResult<T>()
    data class Error(val message: String) : SyncResult<Nothing>()
}