package com.ataiva.eden.sync.service

import com.ataiva.eden.sync.model.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SyncServiceTest {
    
    private lateinit var syncService: SyncService
    
    @BeforeEach
    fun setup() {
        syncService = SyncService()
    }
    
    // Data Source Tests
    @Test
    fun `createDataSource should create valid data source`() {
        val connectionConfig = ConnectionConfig(
            host = "localhost",
            port = 5432,
            database = "testdb",
            username = "testuser",
            password = "testpass",
            additionalProperties = mapOf("ssl" to "true")
        )
        
        val schema = SchemaDefinition(
            name = "test_schema",
            fields = listOf(
                FieldDefinition("id", FieldType.INTEGER, true, true),
                FieldDefinition("name", FieldType.STRING, true, false)
            )
        )
        
        val dataSource = syncService.createDataSource(
            name = "Test Database",
            type = SourceType.DATABASE,
            connectionConfig = connectionConfig,
            schema = schema
        )
        
        assertNotNull(dataSource.id)
        assertEquals("Test Database", dataSource.name)
        assertEquals(SourceType.DATABASE, dataSource.type)
        assertEquals(connectionConfig, dataSource.connectionConfig)
        assertEquals(schema, dataSource.schema)
        assertTrue(dataSource.createdAt > 0)
        assertTrue(dataSource.updatedAt > 0)
    }
    
    @Test
    fun `createDataSource should throw exception for invalid name`() {
        val connectionConfig = ConnectionConfig(
            host = "localhost",
            port = 5432,
            database = "testdb",
            username = "testuser",
            password = "testpass"
        )
        
        assertThrows<IllegalArgumentException> {
            syncService.createDataSource("", SourceType.DATABASE, connectionConfig, null)
        }
        
        assertThrows<IllegalArgumentException> {
            syncService.createDataSource("   ", SourceType.DATABASE, connectionConfig, null)
        }
    }
    
    @Test
    fun `getDataSource should return existing data source`() {
        val dataSource = createTestDataSource()
        val retrieved = syncService.getDataSource(dataSource.id)
        
        assertNotNull(retrieved)
        assertEquals(dataSource.id, retrieved.id)
        assertEquals(dataSource.name, retrieved.name)
    }
    
    @Test
    fun `getDataSource should return null for non-existent id`() {
        val retrieved = syncService.getDataSource("non-existent-id")
        assertNull(retrieved)
    }
    
    @Test
    fun `getDataSources should return all data sources`() {
        val source1 = createTestDataSource("Source 1")
        val source2 = createTestDataSource("Source 2")
        
        val sources = syncService.getDataSources()
        
        assertTrue(sources.size >= 2)
        assertTrue(sources.any { it.id == source1.id })
        assertTrue(sources.any { it.id == source2.id })
    }
    
    @Test
    fun `testDataSourceConnection should return success for valid connection`() = runBlocking {
        val dataSource = createTestDataSource()
        val result = syncService.testDataSourceConnection(dataSource.id)
        
        assertTrue(result.success)
        assertEquals("Connection successful", result.message)
        assertTrue(result.responseTimeMs > 0)
    }
    
    @Test
    fun `testDataSourceConnection should throw exception for non-existent source`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                syncService.testDataSourceConnection("non-existent-id")
            }
        }
    }
    
    // Destination Tests
    @Test
    fun `createDestination should create valid destination`() {
        val connectionConfig = ConnectionConfig(
            host = "localhost",
            port = 5432,
            database = "destdb",
            username = "destuser",
            password = "destpass"
        )
        
        val destination = syncService.createDestination(
            name = "Test Destination",
            type = DestinationType.DATABASE,
            connectionConfig = connectionConfig,
            schema = null
        )
        
        assertNotNull(destination.id)
        assertEquals("Test Destination", destination.name)
        assertEquals(DestinationType.DATABASE, destination.type)
        assertEquals(connectionConfig, destination.connectionConfig)
    }
    
    @Test
    fun `testDestinationConnection should return success for valid connection`() = runBlocking {
        val destination = createTestDestination()
        val result = syncService.testDestinationConnection(destination.id)
        
        assertTrue(result.success)
        assertEquals("Connection successful", result.message)
        assertTrue(result.responseTimeMs > 0)
    }
    
    // Data Mapping Tests
    @Test
    fun `createDataMapping should create valid mapping`() {
        val sourceSchema = SchemaDefinition(
            name = "source_schema",
            fields = listOf(
                FieldDefinition("src_id", FieldType.INTEGER, true, true),
                FieldDefinition("src_name", FieldType.STRING, true, false)
            )
        )
        
        val destSchema = SchemaDefinition(
            name = "dest_schema",
            fields = listOf(
                FieldDefinition("dest_id", FieldType.INTEGER, true, true),
                FieldDefinition("dest_name", FieldType.STRING, true, false)
            )
        )
        
        val fieldMappings = listOf(
            FieldMapping("src_id", "dest_id"),
            FieldMapping("src_name", "dest_name")
        )
        
        val transformations = listOf(
            DataTransformation(
                id = "transform1",
                type = TransformationType.FIELD_RENAME,
                sourceField = "src_name",
                targetField = "dest_name",
                parameters = mapOf("operation" to "rename")
            )
        )
        
        val validationRules = listOf(
            ValidationRule(
                field = "dest_name",
                type = ValidationType.NOT_NULL,
                parameters = emptyMap(),
                errorMessage = "Name cannot be null"
            )
        )
        
        val mapping = syncService.createDataMapping(
            name = "Test Mapping",
            sourceSchemaId = "source-schema-1",
            destinationSchemaId = "dest-schema-1",
            fieldMappings = fieldMappings,
            transformations = transformations,
            validationRules = validationRules
        )
        
        assertNotNull(mapping.id)
        assertEquals("Test Mapping", mapping.name)
        assertEquals(fieldMappings, mapping.fieldMappings)
        assertEquals(transformations, mapping.transformations)
        assertEquals(validationRules, mapping.validationRules)
    }
    
    // Sync Job Tests
    @Test
    fun `createSyncJob should create valid sync job`() {
        val source = createTestDataSource()
        val destination = createTestDestination()
        val mapping = createTestDataMapping()
        
        val schedule = SyncSchedule(
            type = ScheduleType.CRON,
            cronExpression = "0 0 * * *",
            intervalMinutes = null,
            timezone = "UTC"
        )
        
        val configuration = SyncConfiguration(
            batchSize = 1000,
            maxRetries = 3,
            retryDelaySeconds = 30,
            timeoutSeconds = 300,
            enableValidation = true,
            enableTransformation = true,
            conflictResolution = ConflictResolution.SOURCE_WINS,
            parallelism = 2
        )
        
        val syncJob = syncService.createSyncJob(
            name = "Test Sync Job",
            description = "A test sync job",
            sourceId = source.id,
            destinationId = destination.id,
            mappingId = mapping.id,
            schedule = schedule,
            configuration = configuration
        )
        
        assertNotNull(syncJob.id)
        assertEquals("Test Sync Job", syncJob.name)
        assertEquals("A test sync job", syncJob.description)
        assertEquals(source.id, syncJob.sourceId)
        assertEquals(destination.id, syncJob.destinationId)
        assertEquals(mapping.id, syncJob.mappingId)
        assertEquals(SyncStatus.IDLE, syncJob.status)
        assertEquals(schedule, syncJob.schedule)
        assertEquals(configuration, syncJob.configuration)
        assertTrue(syncJob.enabled)
    }
    
    @Test
    fun `createSyncJob should throw exception for non-existent source`() {
        val destination = createTestDestination()
        val mapping = createTestDataMapping()
        
        assertThrows<IllegalArgumentException> {
            syncService.createSyncJob(
                name = "Test Job",
                description = null,
                sourceId = "non-existent-source",
                destinationId = destination.id,
                mappingId = mapping.id,
                schedule = null,
                configuration = SyncConfiguration()
            )
        }
    }
    
    @Test
    fun `createSyncJob should throw exception for non-existent destination`() {
        val source = createTestDataSource()
        val mapping = createTestDataMapping()
        
        assertThrows<IllegalArgumentException> {
            syncService.createSyncJob(
                name = "Test Job",
                description = null,
                sourceId = source.id,
                destinationId = "non-existent-destination",
                mappingId = mapping.id,
                schedule = null,
                configuration = SyncConfiguration()
            )
        }
    }
    
    @Test
    fun `updateSyncJob should update existing job`() {
        val syncJob = createTestSyncJob()
        
        val newSchedule = SyncSchedule(
            type = ScheduleType.INTERVAL,
            cronExpression = null,
            intervalMinutes = 60,
            timezone = "UTC"
        )
        
        val updatedJob = syncService.updateSyncJob(
            jobId = syncJob.id,
            name = "Updated Job Name",
            description = "Updated description",
            schedule = newSchedule,
            configuration = null,
            enabled = false
        )
        
        assertNotNull(updatedJob)
        assertEquals("Updated Job Name", updatedJob!!.name)
        assertEquals("Updated description", updatedJob.description)
        assertEquals(newSchedule, updatedJob.schedule)
        assertFalse(updatedJob.enabled)
        assertTrue(updatedJob.updatedAt > syncJob.updatedAt)
    }
    
    @Test
    fun `updateSyncJob should return null for non-existent job`() {
        val updatedJob = syncService.updateSyncJob(
            jobId = "non-existent-job",
            name = "Updated Name",
            description = null,
            schedule = null,
            configuration = null,
            enabled = null
        )
        
        assertNull(updatedJob)
    }
    
    @Test
    fun `deleteSyncJob should delete existing job`() {
        val syncJob = createTestSyncJob()
        
        val deleted = syncService.deleteSyncJob(syncJob.id)
        assertTrue(deleted)
        
        val retrieved = syncService.getSyncJob(syncJob.id)
        assertNull(retrieved)
    }
    
    @Test
    fun `deleteSyncJob should return false for non-existent job`() {
        val deleted = syncService.deleteSyncJob("non-existent-job")
        assertFalse(deleted)
    }
    
    @Test
    fun `getSyncJobs should filter by status`() {
        val job1 = createTestSyncJob("Job 1")
        val job2 = createTestSyncJob("Job 2")
        
        // Simulate different statuses
        syncService.syncJobs[job1.id] = job1.copy(status = SyncStatus.RUNNING)
        syncService.syncJobs[job2.id] = job2.copy(status = SyncStatus.COMPLETED)
        
        val runningJobs = syncService.getSyncJobs(0, 10, SyncStatus.RUNNING)
        val completedJobs = syncService.getSyncJobs(0, 10, SyncStatus.COMPLETED)
        
        assertTrue(runningJobs.any { it.id == job1.id })
        assertFalse(runningJobs.any { it.id == job2.id })
        
        assertTrue(completedJobs.any { it.id == job2.id })
        assertFalse(completedJobs.any { it.id == job1.id })
    }
    
    @Test
    fun `executeSyncJob should start job execution`() = runBlocking {
        val syncJob = createTestSyncJob()
        
        val executionId = syncService.executeSyncJob(syncJob.id)
        
        assertNotNull(executionId)
        
        // Verify job status changed to RUNNING
        val updatedJob = syncService.getSyncJob(syncJob.id)
        assertEquals(SyncStatus.RUNNING, updatedJob?.status)
        
        // Wait a bit for execution to complete
        kotlinx.coroutines.delay(100)
        
        // Verify execution history was created
        val executions = syncService.getSyncExecutions(syncJob.id, 0, 10)
        assertTrue(executions.isNotEmpty())
        assertEquals(executionId, executions.first().id)
    }
    
    @Test
    fun `executeSyncJob should throw exception for non-existent job`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                syncService.executeSyncJob("non-existent-job")
            }
        }
    }
    
    @Test
    fun `stopSyncJob should stop running job`() = runBlocking {
        val syncJob = createTestSyncJob()
        
        // Start the job
        syncService.executeSyncJob(syncJob.id)
        
        // Stop the job
        val stopped = syncService.stopSyncJob(syncJob.id)
        assertTrue(stopped)
        
        // Verify job status changed
        val updatedJob = syncService.getSyncJob(syncJob.id)
        assertTrue(updatedJob?.status == SyncStatus.STOPPED || updatedJob?.status == SyncStatus.COMPLETED)
    }
    
    @Test
    fun `stopSyncJob should return false for non-running job`() {
        val syncJob = createTestSyncJob()
        
        val stopped = syncService.stopSyncJob(syncJob.id)
        assertFalse(stopped)
    }
    
    // Helper methods
    private fun createTestDataSource(name: String = "Test Data Source"): DataSource {
        val connectionConfig = ConnectionConfig(
            host = "localhost",
            port = 5432,
            database = "testdb",
            username = "testuser",
            password = "testpass"
        )
        
        return syncService.createDataSource(
            name = name,
            type = SourceType.DATABASE,
            connectionConfig = connectionConfig,
            schema = null
        )
    }
    
    private fun createTestDestination(name: String = "Test Destination"): SyncDestination {
        val connectionConfig = ConnectionConfig(
            host = "localhost",
            port = 5432,
            database = "destdb",
            username = "destuser",
            password = "destpass"
        )
        
        return syncService.createDestination(
            name = name,
            type = DestinationType.DATABASE,
            connectionConfig = connectionConfig,
            schema = null
        )
    }
    
    private fun createTestDataMapping(name: String = "Test Mapping"): DataMapping {
        val sourceSchema = SchemaDefinition(
            name = "source_schema",
            fields = listOf(FieldDefinition("id", FieldType.INTEGER, true, true))
        )
        
        val destSchema = SchemaDefinition(
            name = "dest_schema",
            fields = listOf(FieldDefinition("id", FieldType.INTEGER, true, true))
        )
        
        return syncService.createDataMapping(
            name = name,
            sourceSchemaId = "source-schema-1",
            destinationSchemaId = "dest-schema-1",
            fieldMappings = listOf(FieldMapping("id", "id")),
            transformations = emptyList(),
            validationRules = emptyList()
        )
    }
    
    private fun createTestSyncJob(name: String = "Test Sync Job"): SyncJob {
        val source = createTestDataSource()
        val destination = createTestDestination()
        val mapping = createTestDataMapping()
        
        return syncService.createSyncJob(
            name = name,
            description = "Test sync job description",
            sourceId = source.id,
            destinationId = destination.id,
            mappingId = mapping.id,
            schedule = null,
            configuration = SyncConfiguration()
        )
    }
}