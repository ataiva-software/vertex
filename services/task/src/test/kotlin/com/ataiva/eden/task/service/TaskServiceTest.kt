package com.ataiva.eden.task.service

import com.ataiva.eden.task.model.*
import com.ataiva.eden.task.engine.TaskExecutor
import com.ataiva.eden.task.engine.TaskScheduler
import com.ataiva.eden.task.engine.ValidationResult
import com.ataiva.eden.task.engine.CronValidationResult
import com.ataiva.eden.task.queue.TaskQueue
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.repositories.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TaskServiceTest {
    
    private lateinit var databaseService: EdenDatabaseService
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskExecutionRepository: TaskExecutionRepository
    private lateinit var taskExecutor: TaskExecutor
    private lateinit var taskScheduler: TaskScheduler
    private lateinit var taskQueue: TaskQueue
    private lateinit var taskService: TaskService
    
    private val testUserId = "test-user-123"
    private val testTaskName = "test-task"
    private val testTaskType = "http_request"
    private val testConfiguration = mapOf(
        "url" to "https://api.example.com",
        "method" to "GET"
    )
    
    @BeforeEach
    fun setUp() {
        // Mock dependencies
        databaseService = mock()
        taskRepository = mock()
        taskExecutionRepository = mock()
        taskExecutor = mock()
        taskScheduler = mock()
        taskQueue = mock()
        
        // Configure database service mocks
        whenever(databaseService.taskRepository).thenReturn(taskRepository)
        whenever(databaseService.taskExecutionRepository).thenReturn(taskExecutionRepository)
        
        // Configure task executor mock
        whenever(taskExecutor.validateConfiguration(any(), any())).thenReturn(ValidationResult(true, emptyList()))
        
        // Configure task scheduler mock
        whenever(taskScheduler.validateCronExpression(any())).thenReturn(CronValidationResult(true, null))
        
        // Create service instance
        taskService = TaskService(
            databaseService = databaseService,
            taskExecutor = taskExecutor,
            taskScheduler = taskScheduler,
            taskQueue = taskQueue
        )
    }
    
    @Test
    fun `createTask should create new task successfully`() = runTest {
        // Given
        val request = CreateTaskRequest(
            name = testTaskName,
            description = "Test task",
            taskType = testTaskType,
            configuration = testConfiguration,
            scheduleCron = null,
            userId = testUserId
        )
        
        val expectedTask = createTestTask()
        
        whenever(taskRepository.findByNameAndUser(testTaskName, testUserId)).thenReturn(null)
        whenever(taskRepository.create(any())).thenReturn(expectedTask)
        
        // When
        val result = taskService.createTask(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(testTaskName, response.name)
        assertEquals("Test task", response.description)
        assertEquals(testTaskType, response.taskType)
        assertTrue(response.isActive)
        
        verify(taskExecutor).validateConfiguration(testTaskType, testConfiguration)
        verify(taskRepository).findByNameAndUser(testTaskName, testUserId)
        verify(taskRepository).create(any())
    }
    
    @Test
    fun `createTask should fail when task already exists`() = runTest {
        // Given
        val request = CreateTaskRequest(
            name = testTaskName,
            description = "Test task",
            taskType = testTaskType,
            configuration = testConfiguration,
            scheduleCron = null,
            userId = testUserId
        )
        
        val existingTask = createTestTask()
        whenever(taskRepository.findByNameAndUser(testTaskName, testUserId)).thenReturn(existingTask)
        
        // When
        val result = taskService.createTask(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Task with name '$testTaskName' already exists", result.getErrorOrNull())
        
        verify(taskRepository).findByNameAndUser(testTaskName, testUserId)
        verify(taskRepository, never()).create(any())
    }
    
    @Test
    fun `createTask should fail with invalid configuration`() = runTest {
        // Given
        val request = CreateTaskRequest(
            name = testTaskName,
            description = "Test task",
            taskType = testTaskType,
            configuration = testConfiguration,
            scheduleCron = null,
            userId = testUserId
        )
        
        whenever(taskExecutor.validateConfiguration(any(), any())).thenReturn(
            ValidationResult(false, listOf("Missing required parameter 'url'"))
        )
        
        // When
        val result = taskService.createTask(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Invalid task configuration: Missing required parameter 'url'", result.getErrorOrNull())
        
        verify(taskExecutor).validateConfiguration(testTaskType, testConfiguration)
        verify(taskRepository, never()).findByNameAndUser(any(), any())
    }
    
    @Test
    fun `createTask should schedule task when cron expression provided`() = runTest {
        // Given
        val cronExpression = "0 9 * * *"
        val request = CreateTaskRequest(
            name = testTaskName,
            description = "Test scheduled task",
            taskType = testTaskType,
            configuration = testConfiguration,
            scheduleCron = cronExpression,
            userId = testUserId
        )
        
        val expectedTask = createTestTask().copy(scheduleCron = cronExpression)
        
        whenever(taskRepository.findByNameAndUser(testTaskName, testUserId)).thenReturn(null)
        whenever(taskRepository.create(any())).thenReturn(expectedTask)
        
        // When
        val result = taskService.createTask(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(cronExpression, response.scheduleCron)
        
        verify(taskScheduler).validateCronExpression(cronExpression)
        verify(taskScheduler).scheduleTask(expectedTask)
        verify(taskRepository).create(any())
    }
    
    @Test
    fun `createTask should fail with invalid cron expression`() = runTest {
        // Given
        val invalidCron = "invalid cron"
        val request = CreateTaskRequest(
            name = testTaskName,
            description = "Test task",
            taskType = testTaskType,
            configuration = testConfiguration,
            scheduleCron = invalidCron,
            userId = testUserId
        )
        
        whenever(taskScheduler.validateCronExpression(invalidCron)).thenReturn(
            CronValidationResult(false, "Invalid cron expression format")
        )
        
        // When
        val result = taskService.createTask(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Invalid cron expression: Invalid cron expression format", result.getErrorOrNull())
        
        verify(taskScheduler).validateCronExpression(invalidCron)
        verify(taskRepository, never()).create(any())
    }
    
    @Test
    fun `getTask should retrieve task successfully`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId)
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        
        // When
        val result = taskService.getTask(taskId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(taskId, response.id)
        assertEquals(testTaskName, response.name)
        
        verify(taskRepository).findById(taskId)
    }
    
    @Test
    fun `getTask should fail when task not found`() = runTest {
        // Given
        val taskId = "non-existent-task"
        
        whenever(taskRepository.findById(taskId)).thenReturn(null)
        
        // When
        val result = taskService.getTask(taskId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Task not found", result.getErrorOrNull())
        
        verify(taskRepository).findById(taskId)
    }
    
    @Test
    fun `getTask should fail when user has no access`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId, userId = "other-user")
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        
        // When
        val result = taskService.getTask(taskId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Access denied", result.getErrorOrNull())
        
        verify(taskRepository).findById(taskId)
    }
    
    @Test
    fun `updateTask should update task successfully`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId)
        val newDescription = "Updated description"
        
        val request = UpdateTaskRequest(
            taskId = taskId,
            description = newDescription,
            configuration = null,
            scheduleCron = null,
            userId = testUserId
        )
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        whenever(taskRepository.update(any())).thenReturn(true)
        
        // When
        val result = taskService.updateTask(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(newDescription, response.description)
        
        verify(taskRepository).findById(taskId)
        verify(taskRepository).update(any())
    }
    
    @Test
    fun `deleteTask should deactivate task successfully`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId)
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        whenever(taskExecutionRepository.findByTaskId(taskId)).thenReturn(emptyList())
        whenever(taskRepository.updateStatus(taskId, false)).thenReturn(true)
        
        // When
        val result = taskService.deleteTask(taskId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        
        verify(taskRepository).findById(taskId)
        verify(taskExecutionRepository).findByTaskId(taskId)
        verify(taskRepository).updateStatus(taskId, false)
        verify(taskScheduler).unscheduleTask(taskId)
    }
    
    @Test
    fun `deleteTask should fail when there are running executions`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId)
        val runningExecution = createTestExecution().copy(taskId = taskId, status = "running")
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        whenever(taskExecutionRepository.findByTaskId(taskId)).thenReturn(listOf(runningExecution))
        
        // When
        val result = taskService.deleteTask(taskId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Cannot delete task with running executions", result.getErrorOrNull())
        
        verify(taskRepository).findById(taskId)
        verify(taskExecutionRepository).findByTaskId(taskId)
        verify(taskRepository, never()).updateStatus(any(), any())
    }
    
    @Test
    fun `listTasks should return active tasks by default`() = runTest {
        // Given
        val request = ListTasksRequest(userId = testUserId)
        
        val tasks = listOf(
            createTestTask(),
            createTestTask().copy(id = "task-2", name = "task-2")
        )
        whenever(taskRepository.findActiveByUserId(testUserId)).thenReturn(tasks)
        
        // When
        val result = taskService.listTasks(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        
        verify(taskRepository).findActiveByUserId(testUserId)
    }
    
    @Test
    fun `listTasks should filter by task type when specified`() = runTest {
        // Given
        val request = ListTasksRequest(userId = testUserId, taskType = "http_request")
        
        val tasks = listOf(
            createTestTask().copy(taskType = "http_request"),
            createTestTask().copy(id = "task-2", name = "task-2", taskType = "http_request")
        )
        whenever(taskRepository.findByTypeAndUser("http_request", testUserId)).thenReturn(tasks)
        
        // When
        val result = taskService.listTasks(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        assertTrue(response.all { it.taskType == "http_request" })
        
        verify(taskRepository).findByTypeAndUser("http_request", testUserId)
    }
    
    @Test
    fun `executeTask should start execution successfully`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId)
        val execution = createTestExecution().copy(taskId = taskId)
        
        val request = ExecuteTaskRequest(
            taskId = taskId,
            userId = testUserId,
            priority = 5,
            inputData = mapOf("key" to "value")
        )
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        whenever(taskExecutionRepository.create(any())).thenReturn(execution)
        
        // When
        val result = taskService.executeTask(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(taskId, response.taskId)
        assertEquals("queued", response.status)
        assertEquals(5, response.priority)
        
        verify(taskRepository).findById(taskId)
        verify(taskExecutionRepository).create(any())
        verify(taskQueue).enqueue(execution)
    }
    
    @Test
    fun `executeTask should fail when task is not active`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId, isActive = false)
        
        val request = ExecuteTaskRequest(
            taskId = taskId,
            userId = testUserId
        )
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        
        // When
        val result = taskService.executeTask(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Task is not active", result.getErrorOrNull())
        
        verify(taskRepository).findById(taskId)
        verify(taskExecutionRepository, never()).create(any())
    }
    
    @Test
    fun `getExecution should retrieve execution successfully`() = runTest {
        // Given
        val executionId = "execution-123"
        val taskId = "task-123"
        val execution = createTestExecution().copy(id = executionId, taskId = taskId)
        val task = createTestTask().copy(id = taskId)
        
        whenever(taskExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        
        // When
        val result = taskService.getExecution(executionId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(executionId, response.id)
        assertEquals(taskId, response.taskId)
        
        verify(taskExecutionRepository).findById(executionId)
        verify(taskRepository).findById(taskId)
    }
    
    @Test
    fun `cancelExecution should cancel queued execution successfully`() = runTest {
        // Given
        val executionId = "execution-123"
        val taskId = "task-123"
        val execution = createTestExecution().copy(id = executionId, taskId = taskId, status = "queued")
        val task = createTestTask().copy(id = taskId)
        
        whenever(taskExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        whenever(taskQueue.remove(executionId)).thenReturn(true)
        whenever(taskExecutionRepository.cancel(eq(executionId), any())).thenReturn(true)
        
        // When
        val result = taskService.cancelExecution(executionId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        
        verify(taskExecutionRepository).findById(executionId)
        verify(taskRepository).findById(taskId)
        verify(taskQueue).remove(executionId)
        verify(taskExecutionRepository).cancel(eq(executionId), any())
    }
    
    @Test
    fun `cancelExecution should fail when execution cannot be cancelled`() = runTest {
        // Given
        val executionId = "execution-123"
        val taskId = "task-123"
        val execution = createTestExecution().copy(id = executionId, taskId = taskId, status = "completed")
        val task = createTestTask().copy(id = taskId)
        
        whenever(taskExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        
        // When
        val result = taskService.cancelExecution(executionId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Execution cannot be cancelled (status: completed)", result.getErrorOrNull())
        
        verify(taskExecutionRepository).findById(executionId)
        verify(taskRepository).findById(taskId)
        verify(taskQueue, never()).remove(any())
    }
    
    @Test
    fun `listExecutions should return executions for user`() = runTest {
        // Given
        val request = ListExecutionsRequest(userId = testUserId)
        
        val userTasks = listOf(createTestTask(), createTestTask().copy(id = "task-2"))
        val executions = listOf(
            createTestExecution().copy(taskId = userTasks[0].id),
            createTestExecution().copy(id = "execution-2", taskId = userTasks[1].id)
        )
        
        whenever(taskRepository.findByUserId(testUserId)).thenReturn(userTasks)
        whenever(taskExecutionRepository.findByTaskId(userTasks[0].id)).thenReturn(listOf(executions[0]))
        whenever(taskExecutionRepository.findByTaskId(userTasks[1].id)).thenReturn(listOf(executions[1]))
        
        // When
        val result = taskService.listExecutions(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        
        verify(taskRepository).findByUserId(testUserId)
    }
    
    @Test
    fun `getTaskStats should return statistics for user`() = runTest {
        // Given
        val expectedStats = TaskStats(
            totalTasks = 10,
            activeTasks = 8,
            inactiveTasks = 2,
            scheduledTasks = 3,
            tasksByType = mapOf("http_request" to 5L, "shell_command" to 3L, "file_operation" to 2L),
            recentlyCreated = 2,
            recentlyUpdated = 1
        )
        whenever(taskRepository.getTaskStats(testUserId)).thenReturn(expectedStats)
        
        // When
        val result = taskService.getTaskStats(testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(10, stats.totalTasks)
        assertEquals(8, stats.activeTasks)
        assertEquals(2, stats.inactiveTasks)
        assertEquals(3, stats.scheduledTasks)
        
        verify(taskRepository).getTaskStats(testUserId)
    }
    
    @Test
    fun `getExecutionStats should return execution statistics`() = runTest {
        // Given
        val taskId = "task-123"
        val task = createTestTask().copy(id = taskId)
        val expectedStats = TaskExecutionStats(
            totalExecutions = 20,
            completedExecutions = 15,
            failedExecutions = 3,
            runningExecutions = 1,
            queuedExecutions = 1,
            cancelledExecutions = 0,
            averageDurationMs = 5000.0,
            successRate = 0.75,
            averageQueueTime = 2000.0
        )
        
        whenever(taskRepository.findById(taskId)).thenReturn(task)
        whenever(taskExecutionRepository.getExecutionStats(taskId)).thenReturn(expectedStats)
        
        // When
        val result = taskService.getExecutionStats(taskId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(20, stats.totalExecutions)
        assertEquals(15, stats.completedExecutions)
        assertEquals(3, stats.failedExecutions)
        assertEquals(0.75, stats.successRate)
        
        verify(taskRepository).findById(taskId)
        verify(taskExecutionRepository).getExecutionStats(taskId)
    }
    
    @Test
    fun `getQueueStats should return queue statistics`() = runTest {
        // Given
        val expectedStats = QueueStats(
            queuedCount = 5,
            runningCount = 2,
            highPriorityCount = 1,
            averagePriority = 5.5,
            oldestQueuedAt = Clock.System.now(),
            estimatedProcessingTime = 300000L
        )
        whenever(taskExecutionRepository.getQueueStats()).thenReturn(expectedStats)
        
        // When
        val result = taskService.getQueueStats()
        
        // Then
        assertTrue(result.isSuccess())
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(5, stats.queuedCount)
        assertEquals(2, stats.runningCount)
        assertEquals(1, stats.highPriorityCount)
        assertEquals(5.5, stats.averagePriority)
        
        verify(taskExecutionRepository).getQueueStats()
    }
    
    private fun createTestTask(): Task {
        return Task(
            id = "test-task-id",
            name = testTaskName,
            description = "Test task",
            taskType = testTaskType,
            configuration = testConfiguration,
            scheduleCron = null,
            userId = testUserId,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
    }
    
    private fun createTestExecution(): TaskExecution {
        return TaskExecution(
            id = "test-execution-id",
            taskId = "test-task-id",
            status = "queued",
            priority = 5,
            inputData = mapOf("key" to "value"),
            outputData = null,
            errorMessage = null,
            progressPercentage = 0,
            queuedAt = Clock.System.now(),
            startedAt = null,
            completedAt = null,
            durationMs = null
        )
    }
}