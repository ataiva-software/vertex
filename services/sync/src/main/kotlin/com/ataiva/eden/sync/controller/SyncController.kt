package com.ataiva.eden.sync.controller

import com.ataiva.eden.sync.model.*
import com.ataiva.eden.sync.service.SyncService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

/**
 * REST API controller for sync service operations
 */
class SyncController(private val syncService: SyncService) {
    
    fun configureRoutes(routing: Routing) {
        routing {
            route("/api/v1/sync") {
                
                // Sync Job Management
                post("/jobs") {
                    try {
                        val request = call.receive<CreateSyncJobRequest>()
                        val syncJob = syncService.createSyncJob(
                            name = request.name,
                            description = request.description,
                            sourceId = request.sourceId,
                            destinationId = request.destinationId,
                            mappingId = request.mappingId,
                            schedule = request.schedule,
                            configuration = request.configuration
                        )
                        call.respond(HttpStatusCode.Created, SyncJobResponse.from(syncJob))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create sync job"))
                    }
                }
                
                get("/jobs") {
                    try {
                        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                        val status = call.request.queryParameters["status"]?.let { SyncStatus.valueOf(it.uppercase()) }
                        
                        val jobs = syncService.getSyncJobs(page, size, status)
                        val response = SyncJobListResponse(
                            jobs = jobs.map { SyncJobResponse.from(it) },
                            page = page,
                            size = size,
                            total = jobs.size
                        )
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve sync jobs"))
                    }
                }
                
                get("/jobs/{id}") {
                    try {
                        val jobId = call.parameters["id"] ?: throw IllegalArgumentException("Job ID is required")
                        val job = syncService.getSyncJob(jobId)
                        if (job != null) {
                            call.respond(HttpStatusCode.OK, SyncJobResponse.from(job))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Sync job not found"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve sync job"))
                    }
                }
                
                put("/jobs/{id}") {
                    try {
                        val jobId = call.parameters["id"] ?: throw IllegalArgumentException("Job ID is required")
                        val request = call.receive<UpdateSyncJobRequest>()
                        val updatedJob = syncService.updateSyncJob(
                            jobId = jobId,
                            name = request.name,
                            description = request.description,
                            schedule = request.schedule,
                            configuration = request.configuration,
                            enabled = request.enabled
                        )
                        if (updatedJob != null) {
                            call.respond(HttpStatusCode.OK, SyncJobResponse.from(updatedJob))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Sync job not found"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update sync job"))
                    }
                }
                
                delete("/jobs/{id}") {
                    try {
                        val jobId = call.parameters["id"] ?: throw IllegalArgumentException("Job ID is required")
                        val deleted = syncService.deleteSyncJob(jobId)
                        if (deleted) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Sync job not found"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete sync job"))
                    }
                }
                
                // Sync Execution
                post("/jobs/{id}/execute") {
                    try {
                        val jobId = call.parameters["id"] ?: throw IllegalArgumentException("Job ID is required")
                        val executionId = syncService.executeSyncJob(jobId)
                        call.respond(HttpStatusCode.Accepted, SyncExecutionResponse(executionId))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to execute sync job"))
                    }
                }
                
                post("/jobs/{id}/stop") {
                    try {
                        val jobId = call.parameters["id"] ?: throw IllegalArgumentException("Job ID is required")
                        val stopped = syncService.stopSyncJob(jobId)
                        if (stopped) {
                            call.respond(HttpStatusCode.OK, MessageResponse("Sync job stopped successfully"))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Sync job not found or not running"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to stop sync job"))
                    }
                }
                
                // Data Source Management
                post("/sources") {
                    try {
                        val request = call.receive<CreateDataSourceRequest>()
                        val dataSource = syncService.createDataSource(
                            name = request.name,
                            type = request.type,
                            connectionConfig = request.connectionConfig,
                            schema = request.schema
                        )
                        call.respond(HttpStatusCode.Created, DataSourceResponse.from(dataSource))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create data source"))
                    }
                }
                
                get("/sources") {
                    try {
                        val sources = syncService.getDataSources()
                        val response = sources.map { DataSourceResponse.from(it) }
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve data sources"))
                    }
                }
                
                get("/sources/{id}") {
                    try {
                        val sourceId = call.parameters["id"] ?: throw IllegalArgumentException("Source ID is required")
                        val source = syncService.getDataSource(sourceId)
                        if (source != null) {
                            call.respond(HttpStatusCode.OK, DataSourceResponse.from(source))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Data source not found"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve data source"))
                    }
                }
                
                post("/sources/{id}/test") {
                    try {
                        val sourceId = call.parameters["id"] ?: throw IllegalArgumentException("Source ID is required")
                        val result = syncService.testDataSourceConnection(sourceId)
                        call.respond(HttpStatusCode.OK, ConnectionTestResponse.from(result))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to test data source connection"))
                    }
                }
                
                // Destination Management
                post("/destinations") {
                    try {
                        val request = call.receive<CreateDestinationRequest>()
                        val destination = syncService.createDestination(
                            name = request.name,
                            type = request.type,
                            connectionConfig = request.connectionConfig,
                            schema = request.schema
                        )
                        call.respond(HttpStatusCode.Created, DestinationResponse.from(destination))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create destination"))
                    }
                }
                
                get("/destinations") {
                    try {
                        val destinations = syncService.getDestinations()
                        val response = destinations.map { DestinationResponse.from(it) }
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve destinations"))
                    }
                }
                
                post("/destinations/{id}/test") {
                    try {
                        val destinationId = call.parameters["id"] ?: throw IllegalArgumentException("Destination ID is required")
                        val result = syncService.testDestinationConnection(destinationId)
                        call.respond(HttpStatusCode.OK, ConnectionTestResponse.from(result))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to test destination connection"))
                    }
                }
                
                // Data Mapping Management
                post("/mappings") {
                    try {
                        val request = call.receive<CreateMappingRequest>()
                        val mapping = syncService.createDataMapping(
                            name = request.name,
                            sourceSchemaId = request.sourceSchemaId,
                            destinationSchemaId = request.destinationSchemaId,
                            fieldMappings = request.fieldMappings,
                            transformations = request.transformations,
                            validationRules = request.validationRules
                        )
                        call.respond(HttpStatusCode.Created, MappingResponse.from(mapping))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create data mapping"))
                    }
                }
                
                get("/mappings") {
                    try {
                        val mappings = syncService.getDataMappings()
                        val response = mappings.map { MappingResponse.from(it) }
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve data mappings"))
                    }
                }
                
                // Execution History
                get("/executions") {
                    try {
                        val jobId = call.request.queryParameters["jobId"]
                        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                        
                        val executions = syncService.getSyncExecutions(jobId, page, size)
                        val response = SyncExecutionListResponse(
                            executions = executions.map { SyncExecutionHistoryResponse.from(it) },
                            page = page,
                            size = size,
                            total = executions.size
                        )
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve sync executions"))
                    }
                }
                
                get("/executions/{id}") {
                    try {
                        val executionId = call.parameters["id"] ?: throw IllegalArgumentException("Execution ID is required")
                        val execution = syncService.getSyncExecution(executionId)
                        if (execution != null) {
                            call.respond(HttpStatusCode.OK, SyncExecutionHistoryResponse.from(execution))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Sync execution not found"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to retrieve sync execution"))
                    }
                }
            }
        }
    }
}

// Request/Response DTOs
@Serializable
data class CreateSyncJobRequest(
    val name: String,
    val description: String?,
    val sourceId: String,
    val destinationId: String,
    val mappingId: String,
    val schedule: SyncSchedule?,
    val configuration: SyncConfiguration
)

@Serializable
data class UpdateSyncJobRequest(
    val name: String?,
    val description: String?,
    val schedule: SyncSchedule?,
    val configuration: SyncConfiguration?,
    val enabled: Boolean?
)

@Serializable
data class CreateDataSourceRequest(
    val name: String,
    val type: SourceType,
    val connectionConfig: ConnectionConfig,
    val schema: SchemaDefinition?
)

@Serializable
data class CreateDestinationRequest(
    val name: String,
    val type: DestinationType,
    val connectionConfig: ConnectionConfig,
    val schema: SchemaDefinition?
)

@Serializable
data class CreateMappingRequest(
    val name: String,
    val sourceSchemaId: String,
    val destinationSchemaId: String,
    val fieldMappings: List<FieldMapping>,
    val transformations: List<DataTransformation>,
    val validationRules: List<ValidationRule>
)

@Serializable
data class SyncJobResponse(
    val id: String,
    val name: String,
    val description: String?,
    val sourceId: String,
    val destinationId: String,
    val mappingId: String,
    val status: SyncStatus,
    val schedule: SyncSchedule?,
    val configuration: SyncConfiguration,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExecutionAt: Long?
) {
    companion object {
        fun from(syncJob: SyncJob): SyncJobResponse {
            return SyncJobResponse(
                id = syncJob.id,
                name = syncJob.name,
                description = syncJob.description,
                sourceId = syncJob.sourceId,
                destinationId = syncJob.destinationId,
                mappingId = syncJob.mappingId,
                status = syncJob.status,
                schedule = syncJob.schedule,
                configuration = syncJob.configuration,
                enabled = syncJob.enabled,
                createdAt = syncJob.createdAt,
                updatedAt = syncJob.updatedAt,
                lastExecutionAt = syncJob.lastExecutionAt
            )
        }
    }
}

@Serializable
data class DataSourceResponse(
    val id: String,
    val name: String,
    val type: SourceType,
    val connectionConfig: ConnectionConfig,
    val schema: SchemaDefinition?,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun from(dataSource: DataSource): DataSourceResponse {
            return DataSourceResponse(
                id = dataSource.id,
                name = dataSource.name,
                type = dataSource.type,
                connectionConfig = dataSource.connectionConfig,
                schema = dataSource.schema,
                createdAt = dataSource.createdAt,
                updatedAt = dataSource.updatedAt
            )
        }
    }
}

@Serializable
data class DestinationResponse(
    val id: String,
    val name: String,
    val type: DestinationType,
    val connectionConfig: ConnectionConfig,
    val schema: SchemaDefinition?,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun from(destination: SyncDestination): DestinationResponse {
            return DestinationResponse(
                id = destination.id,
                name = destination.name,
                type = destination.type,
                connectionConfig = destination.connectionConfig,
                schema = destination.schema,
                createdAt = destination.createdAt,
                updatedAt = destination.updatedAt
            )
        }
    }
}

@Serializable
data class MappingResponse(
    val id: String,
    val name: String,
    val sourceSchema: SchemaDefinition,
    val destinationSchema: SchemaDefinition,
    val fieldMappings: List<FieldMapping>,
    val transformations: List<DataTransformation>,
    val validationRules: List<ValidationRule>,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun from(mapping: DataMapping): MappingResponse {
            return MappingResponse(
                id = mapping.id,
                name = mapping.name,
                sourceSchema = mapping.sourceSchema,
                destinationSchema = mapping.destinationSchema,
                fieldMappings = mapping.fieldMappings,
                transformations = mapping.transformations,
                validationRules = mapping.validationRules,
                createdAt = mapping.createdAt,
                updatedAt = mapping.updatedAt
            )
        }
    }
}

@Serializable
data class SyncExecutionHistoryResponse(
    val id: String,
    val jobId: String,
    val status: SyncStatus,
    val startTime: Long,
    val endTime: Long?,
    val recordsProcessed: Long,
    val recordsSucceeded: Long,
    val recordsFailed: Long,
    val recordsSkipped: Long,
    val errorMessage: String?,
    val metrics: SyncMetrics?
) {
    companion object {
        fun from(execution: SyncExecutionHistory): SyncExecutionHistoryResponse {
            return SyncExecutionHistoryResponse(
                id = execution.id,
                jobId = execution.jobId,
                status = execution.status,
                startTime = execution.startTime,
                endTime = execution.endTime,
                recordsProcessed = execution.recordsProcessed,
                recordsSucceeded = execution.recordsSucceeded,
                recordsFailed = execution.recordsFailed,
                recordsSkipped = execution.recordsSkipped,
                errorMessage = execution.errorMessage,
                metrics = execution.metrics
            )
        }
    }
}

@Serializable
data class ConnectionTestResponse(
    val success: Boolean,
    val message: String,
    val responseTimeMs: Long,
    val details: Map<String, String>
) {
    companion object {
        fun from(result: ConnectionTestResult): ConnectionTestResponse {
            return ConnectionTestResponse(
                success = result.success,
                message = result.message,
                responseTimeMs = result.responseTimeMs,
                details = result.details
            )
        }
    }
}

@Serializable
data class SyncJobListResponse(
    val jobs: List<SyncJobResponse>,
    val page: Int,
    val size: Int,
    val total: Int
)

@Serializable
data class SyncExecutionListResponse(
    val executions: List<SyncExecutionHistoryResponse>,
    val page: Int,
    val size: Int,
    val total: Int
)

@Serializable
data class SyncExecutionResponse(
    val executionId: String
)

@Serializable
data class MessageResponse(
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String
)