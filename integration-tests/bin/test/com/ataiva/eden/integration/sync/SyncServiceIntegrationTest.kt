package com.ataiva.eden.integration.sync

import com.ataiva.eden.sync.controller.SyncController
import com.ataiva.eden.sync.model.*
import com.ataiva.eden.sync.service.SyncService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncServiceIntegrationTest {
    
    private lateinit var server: NettyApplicationEngine
    private lateinit var client: HttpClient
    private lateinit var syncService: SyncService
    private val baseUrl = "http://localhost:8080"
    
    @BeforeAll
    fun setup() {
        // Initialize sync service
        syncService = SyncService()
        
        // Start test server
        server = embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            
            routing {
                val syncController = SyncController(syncService)
                syncController.configureRoutes(this)
            }
        }.start(wait = false)
        
        // Initialize HTTP client
        client = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
        
        // Wait for server to start
        Thread.sleep(1000)
    }
    
    @AfterAll
    fun teardown() {
        client.close()
        server.stop(1000, 2000)
    }
    
    @Test
    fun `complete sync workflow integration test`() = runBlocking {
        // 1. Create a data source
        val dataSourceRequest = CreateDataSourceRequest(
            name = "Test Database Source",
            type = SourceType.DATABASE,
            connectionConfig = ConnectionConfig(
                host = "localhost",
                port = 5432,
                database = "source_db",
                username = "source_user",
                password = "source_pass",
                additionalProperties = mapOf("ssl" to "true")
            ),
            schema = SchemaDefinition(
                name = "source_schema",
                fields = listOf(
                    FieldDefinition("id", FieldType.INTEGER, true, true),
                    FieldDefinition("name", FieldType.STRING, true, false),
                    FieldDefinition("email", FieldType.STRING, false, false),
                    FieldDefinition("created_at", FieldType.TIMESTAMP, true, false)
                )
            )
        )
        
        val dataSourceResponse = client.post("$baseUrl/api/v1/sync/sources") {
            contentType(ContentType.Application.Json)
            setBody(dataSourceRequest)
        }
        
        assertEquals(HttpStatusCode.Created, dataSourceResponse.status)
        val createdSource = dataSourceResponse.body<DataSourceResponse>()
        assertNotNull(createdSource.id)
        assertEquals("Test Database Source", createdSource.name)
        
        // 2. Test data source connection
        val connectionTestResponse = client.post("$baseUrl/api/v1/sync/sources/${createdSource.id}/test")
        assertEquals(HttpStatusCode.OK, connectionTestResponse.status)
        val connectionTest = connectionTestResponse.body<ConnectionTestResponse>()
        assertTrue(connectionTest.success)
        
        // 3. Create a destination
        val destinationRequest = CreateDestinationRequest(
            name = "Test API Destination",
            type = DestinationType.REST_API,
            connectionConfig = ConnectionConfig(
                host = "api.example.com",
                port = 443,
                database = null,
                username = "api_user",
                password = "api_key",
                additionalProperties = mapOf(
                    "protocol" to "https",
                    "endpoint" to "/api/v1/data",
                    "authentication" to "bearer"
                )
            ),
            schema = SchemaDefinition(
                name = "destination_schema",
                fields = listOf(
                    FieldDefinition("user_id", FieldType.INTEGER, true, true),
                    FieldDefinition("full_name", FieldType.STRING, true, false),
                    FieldDefinition("email_address", FieldType.STRING, false, false),
                    FieldDefinition("registration_date", FieldType.TIMESTAMP, true, false)
                )
            )
        )
        
        val destinationResponse = client.post("$baseUrl/api/v1/sync/destinations") {
            contentType(ContentType.Application.Json)
            setBody(destinationRequest)
        }
        
        assertEquals(HttpStatusCode.Created, destinationResponse.status)
        val createdDestination = destinationResponse.body<DestinationResponse>()
        assertNotNull(createdDestination.id)
        
        // 4. Create data mapping with transformations
        val mappingRequest = CreateMappingRequest(
            name = "Database to API Mapping",
            sourceSchemaId = "source-schema-1",
            destinationSchemaId = "dest-schema-1",
            fieldMappings = listOf(
                FieldMapping("id", "user_id"),
                FieldMapping("name", "full_name"),
                FieldMapping("email", "email_address"),
                FieldMapping("created_at", "registration_date")
            ),
            transformations = listOf(
                DataTransformation(
                    id = "name_transform",
                    type = TransformationType.FORMAT_CONVERSION,
                    sourceField = "name",
                    targetField = "full_name",
                    parameters = mapOf(
                        "operation" to "uppercase",
                        "trim" to "true"
                    )
                ),
                DataTransformation(
                    id = "email_validation",
                    type = TransformationType.CONDITIONAL,
                    sourceField = "email",
                    targetField = "email_address",
                    parameters = mapOf(
                        "condition" to "not_empty",
                        "default_value" to "no-email@example.com"
                    )
                )
            ),
            validationRules = listOf(
                ValidationRule(
                    field = "full_name",
                    type = ValidationType.NOT_NULL,
                    parameters = emptyMap(),
                    errorMessage = "Full name is required"
                ),
                ValidationRule(
                    field = "email_address",
                    type = ValidationType.REGEX_MATCH,
                    parameters = mapOf("pattern" to "^[A-Za-z0-9+_.-]+@(.+)$"),
                    errorMessage = "Invalid email format"
                ),
                ValidationRule(
                    field = "full_name",
                    type = ValidationType.MIN_LENGTH,
                    parameters = mapOf("minLength" to "2"),
                    errorMessage = "Name must be at least 2 characters"
                )
            )
        )
        
        val mappingResponse = client.post("$baseUrl/api/v1/sync/mappings") {
            contentType(ContentType.Application.Json)
            setBody(mappingRequest)
        }
        
        assertEquals(HttpStatusCode.Created, mappingResponse.status)
        val createdMapping = mappingResponse.body<MappingResponse>()
        assertNotNull(createdMapping.id)
        
        // 5. Create sync job with schedule and configuration
        val syncJobRequest = CreateSyncJobRequest(
            name = "Daily User Sync",
            description = "Synchronize user data from database to API daily",
            sourceId = createdSource.id,
            destinationId = createdDestination.id,
            mappingId = createdMapping.id,
            schedule = SyncSchedule(
                type = ScheduleType.CRON,
                cronExpression = "0 2 * * *", // Daily at 2 AM
                intervalMinutes = null,
                timezone = "UTC"
            ),
            configuration = SyncConfiguration(
                batchSize = 500,
                maxRetries = 3,
                retryDelaySeconds = 60,
                timeoutSeconds = 600,
                enableValidation = true,
                enableTransformation = true,
                conflictResolution = ConflictResolution.SOURCE_WINS,
                parallelism = 2
            )
        )
        
        val syncJobResponse = client.post("$baseUrl/api/v1/sync/jobs") {
            contentType(ContentType.Application.Json)
            setBody(syncJobRequest)
        }
        
        assertEquals(HttpStatusCode.Created, syncJobResponse.status)
        val createdJob = syncJobResponse.body<SyncJobResponse>()
        assertNotNull(createdJob.id)
        assertEquals("Daily User Sync", createdJob.name)
        assertEquals(SyncStatus.IDLE, createdJob.status)
        
        // 6. Execute sync job
        val executeResponse = client.post("$baseUrl/api/v1/sync/jobs/${createdJob.id}/execute")
        assertEquals(HttpStatusCode.Accepted, executeResponse.status)
        val executionResult = executeResponse.body<SyncExecutionResponse>()
        assertNotNull(executionResult.executionId)
        
        // 7. Wait for execution to complete and check status
        delay(2000) // Wait for sync to complete
        
        val jobStatusResponse = client.get("$baseUrl/api/v1/sync/jobs/${createdJob.id}")
        assertEquals(HttpStatusCode.OK, jobStatusResponse.status)
        val updatedJob = jobStatusResponse.body<SyncJobResponse>()
        assertTrue(updatedJob.status in listOf(SyncStatus.COMPLETED, SyncStatus.RUNNING))
        
        // 8. Check execution history
        val executionsResponse = client.get("$baseUrl/api/v1/sync/executions?jobId=${createdJob.id}")
        assertEquals(HttpStatusCode.OK, executionsResponse.status)
        val executionsList = executionsResponse.body<SyncExecutionListResponse>()
        assertTrue(executionsList.executions.isNotEmpty())
        
        val execution = executionsList.executions.first()
        assertEquals(executionResult.executionId, execution.id)
        assertTrue(execution.recordsProcessed > 0)
        
        // 9. Get specific execution details
        val executionDetailResponse = client.get("$baseUrl/api/v1/sync/executions/${execution.id}")
        assertEquals(HttpStatusCode.OK, executionDetailResponse.status)
        val executionDetail = executionDetailResponse.body<SyncExecutionHistoryResponse>()
        assertEquals(execution.id, executionDetail.id)
        assertNotNull(executionDetail.metrics)
        
        // 10. Update sync job configuration
        val updateRequest = UpdateSyncJobRequest(
            name = "Updated Daily User Sync",
            description = "Updated description for user sync",
            schedule = SyncSchedule(
                type = ScheduleType.INTERVAL,
                cronExpression = null,
                intervalMinutes = 120, // Every 2 hours
                timezone = "UTC"
            ),
            configuration = SyncConfiguration(
                batchSize = 1000, // Increased batch size
                maxRetries = 5,
                retryDelaySeconds = 30,
                timeoutSeconds = 900,
                enableValidation = true,
                enableTransformation = true,
                conflictResolution = ConflictResolution.MERGE,
                parallelism = 4
            ),
            enabled = true
        )
        
        val updateResponse = client.put("$baseUrl/api/v1/sync/jobs/${createdJob.id}") {
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }
        
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updatedJobResponse = updateResponse.body<SyncJobResponse>()
        assertEquals("Updated Daily User Sync", updatedJobResponse.name)
        assertEquals(ScheduleType.INTERVAL, updatedJobResponse.schedule?.type)
        assertEquals(1000, updatedJobResponse.configuration.batchSize)
        
        // 11. Test pagination for sync jobs
        val jobsResponse = client.get("$baseUrl/api/v1/sync/jobs?page=0&size=10")
        assertEquals(HttpStatusCode.OK, jobsResponse.status)
        val jobsList = jobsResponse.body<SyncJobListResponse>()
        assertTrue(jobsList.jobs.isNotEmpty())
        assertEquals(0, jobsList.page)
        assertEquals(10, jobsList.size)
        
        // 12. Stop sync job if still running
        val stopResponse = client.post("$baseUrl/api/v1/sync/jobs/${createdJob.id}/stop")
        assertTrue(stopResponse.status in listOf(HttpStatusCode.OK, HttpStatusCode.NotFound))
        
        // 13. Delete sync job
        val deleteResponse = client.delete("$baseUrl/api/v1/sync/jobs/${createdJob.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        
        // 14. Verify job is deleted
        val deletedJobResponse = client.get("$baseUrl/api/v1/sync/jobs/${createdJob.id}")
        assertEquals(HttpStatusCode.NotFound, deletedJobResponse.status)
    }
    
    @Test
    fun `error handling integration test`() = runBlocking {
        // Test creating sync job with invalid source ID
        val invalidJobRequest = CreateSyncJobRequest(
            name = "Invalid Job",
            description = null,
            sourceId = "non-existent-source",
            destinationId = "non-existent-destination",
            mappingId = "non-existent-mapping",
            schedule = null,
            configuration = SyncConfiguration()
        )
        
        val errorResponse = client.post("$baseUrl/api/v1/sync/jobs") {
            contentType(ContentType.Application.Json)
            setBody(invalidJobRequest)
        }
        
        assertEquals(HttpStatusCode.BadRequest, errorResponse.status)
        val error = errorResponse.body<ErrorResponse>()
        assertTrue(error.error.contains("Source not found"))
        
        // Test getting non-existent job
        val notFoundResponse = client.get("$baseUrl/api/v1/sync/jobs/non-existent-job")
        assertEquals(HttpStatusCode.NotFound, notFoundResponse.status)
        
        // Test executing non-existent job
        val executeErrorResponse = client.post("$baseUrl/api/v1/sync/jobs/non-existent-job/execute")
        assertEquals(HttpStatusCode.BadRequest, executeErrorResponse.status)
        
        // Test invalid data source creation
        val invalidSourceRequest = CreateDataSourceRequest(
            name = "", // Invalid empty name
            type = SourceType.DATABASE,
            connectionConfig = ConnectionConfig(
                host = "localhost",
                port = 5432,
                database = "testdb",
                username = "user",
                password = "pass"
            ),
            schema = null
        )
        
        val sourceErrorResponse = client.post("$baseUrl/api/v1/sync/sources") {
            contentType(ContentType.Application.Json)
            setBody(invalidSourceRequest)
        }
        
        assertEquals(HttpStatusCode.BadRequest, sourceErrorResponse.status)
    }
    
    @Test
    fun `concurrent sync execution test`() = runBlocking {
        // Create test data source, destination, and mapping
        val source = createTestDataSource()
        val destination = createTestDestination()
        val mapping = createTestMapping()
        
        // Create multiple sync jobs
        val job1 = createTestSyncJob("Concurrent Job 1", source.id, destination.id, mapping.id)
        val job2 = createTestSyncJob("Concurrent Job 2", source.id, destination.id, mapping.id)
        val job3 = createTestSyncJob("Concurrent Job 3", source.id, destination.id, mapping.id)
        
        // Execute all jobs concurrently
        val execution1Response = client.post("$baseUrl/api/v1/sync/jobs/${job1.id}/execute")
        val execution2Response = client.post("$baseUrl/api/v1/sync/jobs/${job2.id}/execute")
        val execution3Response = client.post("$baseUrl/api/v1/sync/jobs/${job3.id}/execute")
        
        assertEquals(HttpStatusCode.Accepted, execution1Response.status)
        assertEquals(HttpStatusCode.Accepted, execution2Response.status)
        assertEquals(HttpStatusCode.Accepted, execution3Response.status)
        
        // Wait for executions to complete
        delay(3000)
        
        // Verify all executions completed successfully
        val executions1 = client.get("$baseUrl/api/v1/sync/executions?jobId=${job1.id}")
            .body<SyncExecutionListResponse>()
        val executions2 = client.get("$baseUrl/api/v1/sync/executions?jobId=${job2.id}")
            .body<SyncExecutionListResponse>()
        val executions3 = client.get("$baseUrl/api/v1/sync/executions?jobId=${job3.id}")
            .body<SyncExecutionListResponse>()
        
        assertTrue(executions1.executions.isNotEmpty())
        assertTrue(executions2.executions.isNotEmpty())
        assertTrue(executions3.executions.isNotEmpty())
        
        // Verify no interference between concurrent executions
        assertTrue(executions1.executions.first().recordsProcessed > 0)
        assertTrue(executions2.executions.first().recordsProcessed > 0)
        assertTrue(executions3.executions.first().recordsProcessed > 0)
    }
    
    // Helper methods for creating test data
    private suspend fun createTestDataSource(): DataSourceResponse {
        val request = CreateDataSourceRequest(
            name = "Integration Test Source",
            type = SourceType.DATABASE,
            connectionConfig = ConnectionConfig(
                host = "localhost",
                port = 5432,
                database = "testdb",
                username = "testuser",
                password = "testpass"
            ),
            schema = null
        )
        
        return client.post("$baseUrl/api/v1/sync/sources") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    private suspend fun createTestDestination(): DestinationResponse {
        val request = CreateDestinationRequest(
            name = "Integration Test Destination",
            type = DestinationType.REST_API,
            connectionConfig = ConnectionConfig(
                host = "api.test.com",
                port = 443,
                database = null,
                username = "apiuser",
                password = "apikey"
            ),
            schema = null
        )
        
        return client.post("$baseUrl/api/v1/sync/destinations") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    private suspend fun createTestMapping(): MappingResponse {
        val request = CreateMappingRequest(
            name = "Integration Test Mapping",
            sourceSchemaId = "test-source-schema",
            destinationSchemaId = "test-dest-schema",
            fieldMappings = listOf(FieldMapping("id", "id")),
            transformations = emptyList(),
            validationRules = emptyList()
        )
        
        return client.post("$baseUrl/api/v1/sync/mappings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    private suspend fun createTestSyncJob(
        name: String,
        sourceId: String,
        destinationId: String,
        mappingId: String
    ): SyncJobResponse {
        val request = CreateSyncJobRequest(
            name = name,
            description = "Integration test sync job",
            sourceId = sourceId,
            destinationId = destinationId,
            mappingId = mappingId,
            schedule = null,
            configuration = SyncConfiguration(batchSize = 100)
        )
        
        return client.post("$baseUrl/api/v1/sync/jobs") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}