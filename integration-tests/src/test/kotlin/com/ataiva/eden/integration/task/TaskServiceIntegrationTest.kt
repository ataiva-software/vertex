package com.ataiva.eden.integration.task

import com.ataiva.eden.task.service.TaskService
import com.ataiva.eden.task.model.*
import com.ataiva.eden.task.engine.TaskExecutor
import com.ataiva.eden.task.engine.TaskScheduler
import com.ataiva.eden.task.queue.TaskQueue
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.testing.fixtures.DatabaseTestFixtures
import com.ataiva.eden.testing.builders.TaskTestDataBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskServiceIntegrationTest {
    
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("eden_test")
            .withUsername("eden_test")
            .withPassword("eden_test")
    }
    
    private lateinit var databaseService: EdenDatabaseService
    private lateinit var taskExecutor: TaskExecutor
    private lateinit var taskScheduler: TaskScheduler
    private lateinit var taskQueue: TaskQueue
    private lateinit var taskService: TaskService
    private lateinit var testFixtures: DatabaseTestFixtures
    
    private val testUserId = "integration-test-user"
    private val testTaskBuilder = TaskTestDataBuilder()
    
    @BeforeAll
    fun setUpAll() {
        // Initialize database service with test container
        databaseService = EdenDatabaseService(
            host = postgres.host,
            port = postgres.getMappedPort(5432),
            database = postgres.databaseName,
            username = postgres.username,
            password = postgres.password
        )
        
        // Initialize test fixtures
        testFixtures = DatabaseTestFixtures(databaseService)
        
        // Initialize task components
        taskExecutor = TaskExecutor()
        taskScheduler = TaskScheduler()
        taskQueue = TaskQueue()
        
        // Initialize task service
        taskService = TaskService(
            databaseService = databaseService,
            taskExecutor = taskExecutor,
            taskScheduler = taskScheduler,
            taskQueue = taskQueue
        )
    }
    
    @BeforeEach
    fun setUp() = runTest {
        // Clean database before each test
        testFixtures.cleanDatabase()
        
        // Set up test data
        testFixtures.setupTestUser(testUserId)
    }
    
    @AfterEach
    fun tearDown() = runTest {
        // Clean up after each test
        testFixtures.cleanDatabase()
    }
    
    @Test
    fun `should create and retrieve task successfully`() = runTest {
        // Given
        val createRequest = CreateTaskRequest(
            name = "integration-test-task",
            description = "Integration test task",
            taskType = "http_request",
            configuration = mapOf(
                "url" to "https://httpbin.org/get",
                "method" to "GET",
                "timeout" to 30
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        // When - Create task
        val createResult = taskService.createTask(createRequest)
        
        // Then - Verify creation
        assertTrue(createResult.isSuccess())
        val createdTask = createResult.getOrNull()
        assertNotNull(createdTask)
        assertEquals("integration-test-task", createdTask.name)
        assertEquals("Integration test task", createdTask.description)
        assertEquals("http_request", createdTask.taskType)
        assertTrue(createdTask.isActive)
        
        // When - Retrieve task
        val getResult = taskService.getTask(createdTask.id, testUserId)
        
        // Then - Verify retrieval
        assertTrue(getResult.isSuccess())
        val retrievedTask = getResult.getOrNull()
        assertNotNull(retrievedTask)
        assertEquals(createdTask.id, retrievedTask.id)
        assertEquals(createdTask.name, retrievedTask.name)
        assertEquals(createdTask.configuration, retrievedTask.configuration)
    }
    
    @Test
    fun `should create scheduled task and verify scheduling`() = runTest {
        // Given
        val cronExpression = "0 9 * * *" // Daily at 9 AM
        val createRequest = CreateTaskRequest(
            name = "scheduled-task",
            description = "Scheduled integration test task",
            taskType = "shell_command",
            configuration = mapOf(
                "command" to "echo 'Hello World'",
                "workingDirectory" to "/tmp"
            ),
            scheduleCron = cronExpression,
            userId = testUserId
        )
        
        // When
        val result = taskService.createTask(createRequest)
        
        // Then
        assertTrue(result.isSuccess())
        val task = result.getOrNull()
        assertNotNull(task)
        assertEquals(cronExpression, task.scheduleCron)
        
        // Verify task is scheduled
        assertTrue(taskScheduler.isTaskScheduled(task.id))
    }
    
    @Test
    fun `should execute task and track execution lifecycle`() = runTest {
        // Given - Create a simple task
        val createRequest = CreateTaskRequest(
            name = "execution-test-task",
            description = "Task for execution testing",
            taskType = "http_request",
            configuration = mapOf(
                "url" to "https://httpbin.org/delay/1",
                "method" to "GET",
                "timeout" to 10
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val task = createResult.getOrNull()!!
        
        // When - Execute task
        val executeRequest = ExecuteTaskRequest(
            taskId = task.id,
            userId = testUserId,
            priority = 7,
            inputData = mapOf("test" to "data")
        )
        
        val executeResult = taskService.executeTask(executeRequest)
        
        // Then - Verify execution started
        assertTrue(executeResult.isSuccess())
        val execution = executeResult.getOrNull()
        assertNotNull(execution)
        assertEquals(task.id, execution.taskId)
        assertEquals("queued", execution.status)
        assertEquals(7, execution.priority)
        
        // Wait for execution to process
        delay(2.seconds)
        
        // Verify execution completed
        val getExecutionResult = taskService.getExecution(execution.id, testUserId)
        assertTrue(getExecutionResult.isSuccess())
        val completedExecution = getExecutionResult.getOrNull()
        assertNotNull(completedExecution)
        assertTrue(completedExecution.status in listOf("completed", "running"))
    }
    
    @Test
    fun `should handle task execution failure gracefully`() = runTest {
        // Given - Create a task that will fail
        val createRequest = CreateTaskRequest(
            name = "failing-task",
            description = "Task designed to fail",
            taskType = "http_request",
            configuration = mapOf(
                "url" to "https://invalid-domain-that-does-not-exist.com",
                "method" to "GET",
                "timeout" to 5
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val task = createResult.getOrNull()!!
        
        // When - Execute failing task
        val executeRequest = ExecuteTaskRequest(
            taskId = task.id,
            userId = testUserId,
            priority = 5
        )
        
        val executeResult = taskService.executeTask(executeRequest)
        assertTrue(executeResult.isSuccess())
        val execution = executeResult.getOrNull()!!
        
        // Wait for execution to fail
        delay(3.seconds)
        
        // Then - Verify execution failed gracefully
        val getExecutionResult = taskService.getExecution(execution.id, testUserId)
        assertTrue(getExecutionResult.isSuccess())
        val failedExecution = getExecutionResult.getOrNull()
        assertNotNull(failedExecution)
        assertEquals("failed", failedExecution.status)
        assertNotNull(failedExecution.errorMessage)
    }
    
    @Test
    fun `should update task configuration and maintain consistency`() = runTest {
        // Given - Create initial task
        val createRequest = CreateTaskRequest(
            name = "update-test-task",
            description = "Original description",
            taskType = "file_operation",
            configuration = mapOf(
                "operation" to "read",
                "path" to "/tmp/test.txt"
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val originalTask = createResult.getOrNull()!!
        
        // When - Update task
        val updateRequest = UpdateTaskRequest(
            taskId = originalTask.id,
            description = "Updated description",
            configuration = mapOf(
                "operation" to "write",
                "path" to "/tmp/updated.txt",
                "content" to "Hello World"
            ),
            scheduleCron = "0 */6 * * *", // Every 6 hours
            userId = testUserId
        )
        
        val updateResult = taskService.updateTask(updateRequest)
        
        // Then - Verify update
        assertTrue(updateResult.isSuccess())
        val updatedTask = updateResult.getOrNull()
        assertNotNull(updatedTask)
        assertEquals("Updated description", updatedTask.description)
        assertEquals("0 */6 * * *", updatedTask.scheduleCron)
        assertEquals(3, updatedTask.configuration.size)
        assertEquals("write", updatedTask.configuration["operation"])
        
        // Verify task is rescheduled
        assertTrue(taskScheduler.isTaskScheduled(updatedTask.id))
    }
    
    @Test
    fun `should list tasks with filtering and pagination`() = runTest {
        // Given - Create multiple tasks of different types
        val tasks = listOf(
            CreateTaskRequest(
                name = "http-task-1",
                description = "HTTP task 1",
                taskType = "http_request",
                configuration = mapOf("url" to "https://httpbin.org/get"),
                scheduleCron = null,
                userId = testUserId
            ),
            CreateTaskRequest(
                name = "http-task-2",
                description = "HTTP task 2",
                taskType = "http_request",
                configuration = mapOf("url" to "https://httpbin.org/post"),
                scheduleCron = null,
                userId = testUserId
            ),
            CreateTaskRequest(
                name = "shell-task-1",
                description = "Shell task 1",
                taskType = "shell_command",
                configuration = mapOf("command" to "echo 'test'"),
                scheduleCron = null,
                userId = testUserId
            )
        )
        
        // Create all tasks
        for (taskRequest in tasks) {
            val result = taskService.createTask(taskRequest)
            assertTrue(result.isSuccess())
        }
        
        // When - List all tasks
        val listAllRequest = ListTasksRequest(userId = testUserId)
        val listAllResult = taskService.listTasks(listAllRequest)
        
        // Then - Verify all tasks returned
        assertTrue(listAllResult.isSuccess())
        val allTasks = listAllResult.getOrNull()
        assertNotNull(allTasks)
        assertEquals(3, allTasks.size)
        
        // When - Filter by task type
        val filterRequest = ListTasksRequest(
            userId = testUserId,
            taskType = "http_request"
        )
        val filterResult = taskService.listTasks(filterRequest)
        
        // Then - Verify filtered results
        assertTrue(filterResult.isSuccess())
        val httpTasks = filterResult.getOrNull()
        assertNotNull(httpTasks)
        assertEquals(2, httpTasks.size)
        assertTrue(httpTasks.all { it.taskType == "http_request" })
    }
    
    @Test
    fun `should handle concurrent task executions`() = runTest {
        // Given - Create a task
        val createRequest = CreateTaskRequest(
            name = "concurrent-task",
            description = "Task for concurrency testing",
            taskType = "http_request",
            configuration = mapOf(
                "url" to "https://httpbin.org/delay/2",
                "method" to "GET",
                "timeout" to 10
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val task = createResult.getOrNull()!!
        
        // When - Execute task multiple times concurrently
        val executions = mutableListOf<TaskExecutionResponse>()
        repeat(3) { index ->
            val executeRequest = ExecuteTaskRequest(
                taskId = task.id,
                userId = testUserId,
                priority = index + 1,
                inputData = mapOf("execution" to index.toString())
            )
            
            val executeResult = taskService.executeTask(executeRequest)
            assertTrue(executeResult.isSuccess())
            executions.add(executeResult.getOrNull()!!)
        }
        
        // Then - Verify all executions were queued
        assertEquals(3, executions.size)
        assertTrue(executions.all { it.status == "queued" })
        assertTrue(executions.all { it.taskId == task.id })
        
        // Wait for executions to process
        delay(5.seconds)
        
        // Verify executions completed
        for (execution in executions) {
            val getResult = taskService.getExecution(execution.id, testUserId)
            assertTrue(getResult.isSuccess())
            val completedExecution = getResult.getOrNull()
            assertNotNull(completedExecution)
            assertTrue(completedExecution.status in listOf("completed", "running"))
        }
    }
    
    @Test
    fun `should cancel queued execution successfully`() = runTest {
        // Given - Create a slow task
        val createRequest = CreateTaskRequest(
            name = "slow-task",
            description = "Slow task for cancellation testing",
            taskType = "http_request",
            configuration = mapOf(
                "url" to "https://httpbin.org/delay/10",
                "method" to "GET",
                "timeout" to 30
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val task = createResult.getOrNull()!!
        
        // When - Execute task
        val executeRequest = ExecuteTaskRequest(
            taskId = task.id,
            userId = testUserId,
            priority = 1
        )
        
        val executeResult = taskService.executeTask(executeRequest)
        assertTrue(executeResult.isSuccess())
        val execution = executeResult.getOrNull()!!
        
        // Cancel execution immediately
        val cancelResult = taskService.cancelExecution(execution.id, testUserId)
        
        // Then - Verify cancellation
        assertTrue(cancelResult.isSuccess())
        
        // Verify execution status
        val getResult = taskService.getExecution(execution.id, testUserId)
        assertTrue(getResult.isSuccess())
        val cancelledExecution = getResult.getOrNull()
        assertNotNull(cancelledExecution)
        assertEquals("cancelled", cancelledExecution.status)
    }
    
    @Test
    fun `should delete task and clean up resources`() = runTest {
        // Given - Create a scheduled task
        val createRequest = CreateTaskRequest(
            name = "delete-test-task",
            description = "Task for deletion testing",
            taskType = "shell_command",
            configuration = mapOf("command" to "echo 'test'"),
            scheduleCron = "0 0 * * *",
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val task = createResult.getOrNull()!!
        
        // Verify task is scheduled
        assertTrue(taskScheduler.isTaskScheduled(task.id))
        
        // When - Delete task
        val deleteResult = taskService.deleteTask(task.id, testUserId)
        
        // Then - Verify deletion
        assertTrue(deleteResult.isSuccess())
        
        // Verify task is deactivated
        val getResult = taskService.getTask(task.id, testUserId)
        assertTrue(getResult.isSuccess())
        val deactivatedTask = getResult.getOrNull()
        assertNotNull(deactivatedTask)
        assertFalse(deactivatedTask.isActive)
        
        // Verify task is unscheduled
        assertFalse(taskScheduler.isTaskScheduled(task.id))
    }
    
    @Test
    fun `should generate accurate task and execution statistics`() = runTest {
        // Given - Create multiple tasks and executions
        val tasks = mutableListOf<TaskResponse>()
        
        // Create HTTP tasks
        repeat(3) { index ->
            val createRequest = CreateTaskRequest(
                name = "stats-http-task-$index",
                description = "HTTP task for stats",
                taskType = "http_request",
                configuration = mapOf("url" to "https://httpbin.org/get"),
                scheduleCron = if (index == 0) "0 9 * * *" else null,
                userId = testUserId
            )
            
            val result = taskService.createTask(createRequest)
            assertTrue(result.isSuccess())
            tasks.add(result.getOrNull()!!)
        }
        
        // Create shell tasks
        repeat(2) { index ->
            val createRequest = CreateTaskRequest(
                name = "stats-shell-task-$index",
                description = "Shell task for stats",
                taskType = "shell_command",
                configuration = mapOf("command" to "echo 'test'"),
                scheduleCron = null,
                userId = testUserId
            )
            
            val result = taskService.createTask(createRequest)
            assertTrue(result.isSuccess())
            tasks.add(result.getOrNull()!!)
        }
        
        // Execute some tasks
        for (task in tasks.take(3)) {
            val executeRequest = ExecuteTaskRequest(
                taskId = task.id,
                userId = testUserId,
                priority = 5
            )
            taskService.executeTask(executeRequest)
        }
        
        // When - Get task statistics
        val taskStatsResult = taskService.getTaskStats(testUserId)
        
        // Then - Verify task statistics
        assertTrue(taskStatsResult.isSuccess())
        val taskStats = taskStatsResult.getOrNull()
        assertNotNull(taskStats)
        assertEquals(5, taskStats.totalTasks)
        assertEquals(5, taskStats.activeTasks)
        assertEquals(0, taskStats.inactiveTasks)
        assertEquals(1, taskStats.scheduledTasks)
        assertEquals(3L, taskStats.tasksByType["http_request"])
        assertEquals(2L, taskStats.tasksByType["shell_command"])
        
        // When - Get execution statistics for a task
        val executionStatsResult = taskService.getExecutionStats(tasks[0].id, testUserId)
        
        // Then - Verify execution statistics
        assertTrue(executionStatsResult.isSuccess())
        val executionStats = executionStatsResult.getOrNull()
        assertNotNull(executionStats)
        assertTrue(executionStats.totalExecutions >= 1)
        
        // When - Get queue statistics
        val queueStatsResult = taskService.getQueueStats()
        
        // Then - Verify queue statistics
        assertTrue(queueStatsResult.isSuccess())
        val queueStats = queueStatsResult.getOrNull()
        assertNotNull(queueStats)
        assertTrue(queueStats.queuedCount >= 0)
    }
    
    @Test
    fun `should handle invalid task configurations gracefully`() = runTest {
        // Given - Invalid HTTP request configuration
        val invalidRequest = CreateTaskRequest(
            name = "invalid-task",
            description = "Task with invalid configuration",
            taskType = "http_request",
            configuration = mapOf(
                "url" to "not-a-valid-url",
                "method" to "INVALID_METHOD"
            ),
            scheduleCron = null,
            userId = testUserId
        )
        
        // When
        val result = taskService.createTask(invalidRequest)
        
        // Then
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertTrue(error.contains("Invalid task configuration"))
    }
    
    @Test
    fun `should handle invalid cron expressions gracefully`() = runTest {
        // Given - Invalid cron expression
        val invalidCronRequest = CreateTaskRequest(
            name = "invalid-cron-task",
            description = "Task with invalid cron",
            taskType = "shell_command",
            configuration = mapOf("command" to "echo 'test'"),
            scheduleCron = "invalid cron expression",
            userId = testUserId
        )
        
        // When
        val result = taskService.createTask(invalidCronRequest)
        
        // Then
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertTrue(error.contains("Invalid cron expression"))
    }
    
    @Test
    fun `should enforce user access control`() = runTest {
        // Given - Create task with one user
        val createRequest = CreateTaskRequest(
            name = "access-control-task",
            description = "Task for access control testing",
            taskType = "shell_command",
            configuration = mapOf("command" to "echo 'test'"),
            scheduleCron = null,
            userId = testUserId
        )
        
        val createResult = taskService.createTask(createRequest)
        assertTrue(createResult.isSuccess())
        val task = createResult.getOrNull()!!
        
        // When - Try to access with different user
        val otherUserId = "other-user-123"
        val getResult = taskService.getTask(task.id, otherUserId)
        
        // Then - Verify access denied
        assertTrue(getResult.isError())
        assertEquals("Access denied", getResult.getErrorOrNull())
        
        // When - Try to execute with different user
        val executeRequest = ExecuteTaskRequest(
            taskId = task.id,
            userId = otherUserId
        )
        val executeResult = taskService.executeTask(executeRequest)
        
        // Then - Verify access denied
        assertTrue(executeResult.isError())
        assertEquals("Access denied", executeResult.getErrorOrNull())
    }
}