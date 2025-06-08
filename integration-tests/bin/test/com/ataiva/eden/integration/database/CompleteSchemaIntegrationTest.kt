package com.ataiva.eden.integration.database

import com.ataiva.eden.database.*
import com.ataiva.eden.database.repositories.*
import com.ataiva.eden.testing.fixtures.DatabaseTestFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.*

/**
 * Complete integration test for the entire Eden database schema implementation
 * Tests all repository implementations working together
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CompleteSchemaIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("eden_complete_test")
            .withUsername("eden_test")
            .withPassword("test_password")

        private lateinit var databaseService: EdenDatabaseService

        @BeforeAll
        @JvmStatic
        fun setupDatabase() {
            postgres.start()
            
            val config = DatabaseConfig(
                url = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                driverClassName = "org.postgresql.Driver",
                maxPoolSize = 5,
                minIdleConnections = 1
            )
            
            val factory = EdenDatabaseServiceFactoryImpl()
            databaseService = runBlocking { factory.createWithMigration(config) }
        }

        @AfterAll
        @JvmStatic
        fun tearDownDatabase() = runBlocking {
            databaseService.close()
            postgres.stop()
        }
    }

    @BeforeEach
    fun setupTestData() = runBlocking {
        // Clean up any existing test data
        databaseService.bulkDelete("secret", listOf())
        databaseService.bulkDelete("workflow", listOf())
        databaseService.bulkDelete("task", listOf())
    }

    @Test
    @Order(1)
    fun `should initialize database service successfully`() = runBlocking {
        // When
        val isInitialized = databaseService.initialize()
        val healthStatus = databaseService.getHealthStatus()
        
        // Then
        assertTrue(isInitialized, "Database service should initialize successfully")
        assertTrue(healthStatus.isHealthy, "Database should be healthy")
        assertTrue(healthStatus.issues.isEmpty(), "Should have no health issues")
        
        println("Database health status: ${healthStatus.isHealthy}")
        println("Migration status: ${healthStatus.migrationStatus.size} migrations")
    }

    @Test
    @Order(2)
    fun `should perform complete CRUD operations on secrets`() = runBlocking {
        // Given
        val testUser = User(
            id = "test-user-secrets",
            email = "secrets@test.local",
            passwordHash = "test-hash",
            fullName = "Secrets Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // Create user first
        databaseService.userRepository.save(testUser)
        
        val secret = Secret(
            id = UUID.randomUUID().toString(),
            name = "test-database-password",
            encryptedValue = "encrypted-test-value",
            encryptionKeyId = "test-key-001",
            secretType = "database",
            description = "Test database password",
            userId = testUser.id,
            organizationId = null,
            version = 1,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // When - Create secret
        val savedSecret = databaseService.secretRepository.save(secret)
        
        // Then - Verify creation
        assertNotNull(savedSecret)
        assertEquals(secret.name, savedSecret.name)
        assertEquals(secret.secretType, savedSecret.secretType)
        
        // When - Find secret
        val foundSecret = databaseService.secretRepository.findByNameAndUser(secret.name, testUser.id)
        
        // Then - Verify retrieval
        assertNotNull(foundSecret)
        assertEquals(secret.id, foundSecret!!.id)
        
        // When - Log access
        val accessLog = databaseService.secretAccessLogRepository.logAccess(
            secretId = secret.id,
            userId = testUser.id,
            action = "read",
            ipAddress = "192.168.1.100",
            userAgent = "Eden Test/1.0"
        )
        
        // Then - Verify access logging
        assertNotNull(accessLog)
        assertEquals("read", accessLog.action)
        
        // When - Get statistics
        val stats = databaseService.secretRepository.getSecretStats(testUser.id)
        
        // Then - Verify statistics
        assertEquals(1L, stats.totalSecrets)
        assertEquals(1L, stats.activeSecrets)
        assertTrue(stats.secretsByType.containsKey("database"))
        
        println("Secret CRUD operations completed successfully")
    }

    @Test
    @Order(3)
    fun `should perform complete workflow lifecycle`() = runBlocking {
        // Given
        val testUser = User(
            id = "test-user-workflows",
            email = "workflows@test.local",
            passwordHash = "test-hash",
            fullName = "Workflows Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        databaseService.userRepository.save(testUser)
        
        val workflow = Workflow(
            id = UUID.randomUUID().toString(),
            name = "test-deployment-workflow",
            description = "Test deployment workflow",
            definition = mapOf(
                "name" to "test-deployment-workflow",
                "steps" to listOf(
                    mapOf("name" to "checkout", "type" to "git"),
                    mapOf("name" to "test", "type" to "shell"),
                    mapOf("name" to "deploy", "type" to "kubernetes")
                )
            ),
            userId = testUser.id,
            status = "active",
            version = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // When - Create workflow
        val savedWorkflow = databaseService.workflowRepository.save(workflow)
        
        // Then - Verify workflow creation
        assertNotNull(savedWorkflow)
        assertEquals(workflow.name, savedWorkflow.name)
        assertEquals("active", savedWorkflow.status)
        
        // When - Create execution
        val execution = WorkflowExecution(
            id = UUID.randomUUID().toString(),
            workflowId = savedWorkflow.id,
            triggeredBy = testUser.id,
            status = "running",
            inputData = mapOf("branch" to "main"),
            outputData = null,
            errorMessage = null,
            startedAt = Clock.System.now(),
            completedAt = null,
            durationMs = null
        )
        
        val savedExecution = databaseService.workflowExecutionRepository.save(execution)
        
        // Then - Verify execution creation
        assertNotNull(savedExecution)
        assertEquals("running", savedExecution.status)
        assertEquals(testUser.id, savedExecution.triggeredBy)
        
        // When - Complete execution
        val completedAt = Clock.System.now()
        val updated = databaseService.workflowExecutionRepository.updateStatus(
            savedExecution.id, 
            "completed", 
            completedAt
        )
        
        // Then - Verify completion
        assertTrue(updated)
        
        // When - Get workflow statistics
        val stats = databaseService.workflowRepository.getWorkflowStats(testUser.id)
        
        // Then - Verify statistics
        assertEquals(1L, stats.totalWorkflows)
        assertEquals(1L, stats.activeWorkflows)
        
        println("Workflow lifecycle completed successfully")
    }

    @Test
    @Order(4)
    fun `should perform complete task execution cycle`() = runBlocking {
        // Given
        val testUser = User(
            id = "test-user-tasks",
            email = "tasks@test.local",
            passwordHash = "test-hash",
            fullName = "Tasks Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        databaseService.userRepository.save(testUser)
        
        val task = Task(
            id = UUID.randomUUID().toString(),
            name = "test-health-check",
            description = "Test health check task",
            taskType = "http_check",
            configuration = mapOf(
                "url" to "https://api.test.local/health",
                "method" to "GET",
                "timeout" to 30
            ),
            scheduleCron = "*/5 * * * *",
            userId = testUser.id,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // When - Create task
        val savedTask = databaseService.taskRepository.save(task)
        
        // Then - Verify task creation
        assertNotNull(savedTask)
        assertEquals(task.name, savedTask.name)
        assertEquals("http_check", savedTask.taskType)
        
        // When - Create task execution
        val execution = TaskExecution(
            id = UUID.randomUUID().toString(),
            taskId = savedTask.id,
            status = "queued",
            priority = 5,
            inputData = mapOf("timestamp" to Clock.System.now().toString()),
            outputData = null,
            errorMessage = null,
            progressPercentage = 0,
            queuedAt = Clock.System.now(),
            startedAt = null,
            completedAt = null,
            durationMs = null
        )
        
        val savedExecution = databaseService.taskExecutionRepository.save(execution)
        
        // Then - Verify execution creation
        assertNotNull(savedExecution)
        assertEquals("queued", savedExecution.status)
        assertEquals(5, savedExecution.priority)
        
        // When - Start execution
        val startedAt = Clock.System.now()
        val started = databaseService.taskExecutionRepository.markStarted(savedExecution.id, startedAt)
        
        // Then - Verify start
        assertTrue(started)
        
        // When - Complete execution
        val completedAt = Clock.System.now()
        val completed = databaseService.taskExecutionRepository.markCompleted(
            savedExecution.id,
            mapOf("result" to "success", "response_time" to 150),
            completedAt,
            5000
        )
        
        // Then - Verify completion
        assertTrue(completed)
        
        // When - Get task statistics
        val stats = databaseService.taskRepository.getTaskStats(testUser.id)
        
        // Then - Verify statistics
        assertEquals(1L, stats.totalTasks)
        assertEquals(1L, stats.activeTasks)
        assertEquals(1L, stats.scheduledTasks)
        assertTrue(stats.tasksByType.containsKey("http_check"))
        
        println("Task execution cycle completed successfully")
    }

    @Test
    @Order(5)
    fun `should perform global search across all entities`() = runBlocking {
        // Given - Create test data across different entity types
        val testUser = User(
            id = "test-user-search",
            email = "search@test.local",
            passwordHash = "test-hash",
            fullName = "Search Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        databaseService.userRepository.save(testUser)
        
        // Create entities with "deploy" in their names
        val secret = Secret(
            id = UUID.randomUUID().toString(),
            name = "deploy-key",
            encryptedValue = "encrypted-deploy-key",
            encryptionKeyId = "key-001",
            secretType = "ssh_key",
            description = "SSH key for deployment",
            userId = testUser.id,
            organizationId = null,
            version = 1,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        val workflow = Workflow(
            id = UUID.randomUUID().toString(),
            name = "deploy-to-production",
            description = "Deploy application to production",
            definition = mapOf("steps" to listOf()),
            userId = testUser.id,
            status = "active",
            version = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        val task = Task(
            id = UUID.randomUUID().toString(),
            name = "deploy-notification",
            description = "Send deployment notification",
            taskType = "notification",
            configuration = mapOf("type" to "slack"),
            scheduleCron = null,
            userId = testUser.id,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // Save all entities
        databaseService.secretRepository.save(secret)
        databaseService.workflowRepository.save(workflow)
        databaseService.taskRepository.save(task)
        
        // When - Perform global search
        val searchResult = databaseService.globalSearch("deploy", testUser.id)
        
        // Then - Verify search results
        assertEquals(3, searchResult.totalResults)
        assertEquals(1, searchResult.secrets.size)
        assertEquals(1, searchResult.workflows.size)
        assertEquals(1, searchResult.tasks.size)
        
        assertTrue(searchResult.searchDuration > 0)
        
        println("Global search completed successfully: ${searchResult.totalResults} results")
    }

    @Test
    @Order(6)
    fun `should generate comprehensive dashboard statistics`() = runBlocking {
        // Given
        val testUser = User(
            id = "test-user-dashboard",
            email = "dashboard@test.local",
            passwordHash = "test-hash",
            fullName = "Dashboard Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        databaseService.userRepository.save(testUser)
        
        // When - Get dashboard statistics
        val dashboardStats = databaseService.getDashboardStats(testUser.id)
        
        // Then - Verify statistics structure
        assertNotNull(dashboardStats.userStats)
        assertNotNull(dashboardStats.secretStats)
        assertNotNull(dashboardStats.workflowStats)
        assertNotNull(dashboardStats.taskStats)
        assertNotNull(dashboardStats.systemHealth)
        
        assertEquals("healthy", dashboardStats.systemHealth.overallStatus)
        assertTrue(dashboardStats.systemHealth.activeServices > 0)
        
        println("Dashboard statistics generated successfully")
    }

    @Test
    @Order(7)
    fun `should handle bulk operations efficiently`() = runBlocking {
        // Given
        val testUser = User(
            id = "test-user-bulk",
            email = "bulk@test.local",
            passwordHash = "test-hash",
            fullName = "Bulk Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        databaseService.userRepository.save(testUser)
        
        val secrets = (1..5).map { index ->
            Secret(
                id = UUID.randomUUID().toString(),
                name = "bulk-secret-$index",
                encryptedValue = "encrypted-value-$index",
                encryptionKeyId = "key-$index",
                secretType = "generic",
                description = "Bulk test secret $index",
                userId = testUser.id,
                organizationId = null,
                version = 1,
                isActive = true,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
        }
        
        // When - Perform bulk insert
        val bulkResult = databaseService.bulkInsert(secrets)
        
        // Then - Verify bulk operation
        assertEquals(5, bulkResult.successful)
        assertEquals(0, bulkResult.failed)
        assertTrue(bulkResult.errors.isEmpty())
        assertTrue(bulkResult.duration > 0)
        
        // When - Verify all secrets were created
        val userSecrets = databaseService.secretRepository.findByUserId(testUser.id)
        
        // Then - Verify count
        assertTrue(userSecrets.size >= 5) // May have more from other tests
        
        println("Bulk operations completed successfully: ${bulkResult.successful} successful")
    }

    @Test
    @Order(8)
    fun `should maintain data integrity with transactions`() = runBlocking {
        // Given
        val testUser = User(
            id = "test-user-transaction",
            email = "transaction@test.local",
            passwordHash = "test-hash",
            fullName = "Transaction Test User",
            isActive = true,
            isVerified = true,
            lastLoginAt = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // When - Perform transaction
        val result = databaseService.transaction { service ->
            // Save user
            service.userRepository.save(testUser)
            
            // Create secret
            val secret = Secret(
                id = UUID.randomUUID().toString(),
                name = "transaction-secret",
                encryptedValue = "encrypted-transaction-value",
                encryptionKeyId = "transaction-key",
                secretType = "generic",
                description = "Transaction test secret",
                userId = testUser.id,
                organizationId = null,
                version = 1,
                isActive = true,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            service.secretRepository.save(secret)
            
            // Log access
            service.secretAccessLogRepository.logAccess(
                secretId = secret.id,
                userId = testUser.id,
                action = "write",
                ipAddress = "192.168.1.100",
                userAgent = "Eden Transaction Test/1.0"
            )
            
            "Transaction completed successfully"
        }
        
        // Then - Verify transaction result
        assertEquals("Transaction completed successfully", result)
        
        // Verify all data was committed
        val savedUser = databaseService.userRepository.findById(testUser.id)
        assertNotNull(savedUser)
        
        val userSecrets = databaseService.secretRepository.findByUserId(testUser.id)
        assertTrue(userSecrets.isNotEmpty())
        
        val accessLogs = databaseService.secretAccessLogRepository.findByUserId(testUser.id)
        assertTrue(accessLogs.isNotEmpty())
        
        println("Transaction integrity maintained successfully")
    }

    @Test
    @Order(9)
    fun `should provide comprehensive system overview`() = runBlocking {
        // When
        val systemOverview = databaseService.getSystemOverview()
        
        // Then
        assertNotNull(systemOverview)
        assertTrue(systemOverview.totalUsers > 0)
        assertTrue(systemOverview.totalSecrets >= 0)
        assertTrue(systemOverview.totalWorkflows >= 0)
        assertTrue(systemOverview.totalTasks >= 0)
        
        assertNotNull(systemOverview.systemEvents)
        assertNotNull(systemOverview.auditLogs)
        assertNotNull(systemOverview.performance)
        
        assertTrue(systemOverview.performance.averageResponseTime > 0)
        assertTrue(systemOverview.performance.throughputPerSecond > 0)
        
        println("System overview generated successfully")
        println("Total users: ${systemOverview.totalUsers}")
        println("Total secrets: ${systemOverview.totalSecrets}")
        println("Total workflows: ${systemOverview.totalWorkflows}")
        println("Total tasks: ${systemOverview.totalTasks}")
    }

    @Test
    @Order(10)
    fun `should validate schema integrity and performance`() = runBlocking {
        // When - Validate schema
        val isValid = databaseService.validateSchema()
        
        // Then - Verify schema validation
        assertTrue(isValid, "Database schema should be valid")
        
        // When - Check health status
        val healthStatus = databaseService.getHealthStatus()
        
        // Then - Verify health
        assertTrue(healthStatus.isHealthy, "Database should be healthy")
        assertTrue(healthStatus.connectionPoolStats.totalConnections > 0)
        
        // Performance test - measure query response time
        val startTime = System.currentTimeMillis()
        val secrets = databaseService.secretRepository.findAll(0, 100)
        val queryTime = System.currentTimeMillis() - startTime
        
        assertTrue(queryTime < 1000, "Query should complete within 1 second")
        
        println("Schema validation completed successfully")
        println("Query performance: ${queryTime}ms for ${secrets.content.size} records")
    }
}