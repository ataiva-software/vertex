package com.ataiva.eden.flow.service

import com.ataiva.eden.flow.model.*
import com.ataiva.eden.flow.engine.WorkflowEngine
import com.ataiva.eden.flow.engine.StepExecutor
import com.ataiva.eden.flow.engine.ValidationResult
import com.ataiva.eden.flow.engine.StepDefinition
import com.ataiva.eden.flow.engine.RetryPolicy
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

class FlowServiceTest {
    
    private lateinit var databaseService: EdenDatabaseService
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var workflowExecutionRepository: WorkflowExecutionRepository
    private lateinit var workflowStepRepository: WorkflowStepRepository
    private lateinit var workflowEngine: WorkflowEngine
    private lateinit var stepExecutor: StepExecutor
    private lateinit var flowService: FlowService
    
    private val testUserId = "test-user-123"
    private val testWorkflowName = "test-workflow"
    private val testWorkflowDefinition = mapOf(
        "steps" to listOf(
            mapOf(
                "name" to "step1",
                "type" to "http_request",
                "config" to mapOf("url" to "https://api.example.com")
            ),
            mapOf(
                "name" to "step2",
                "type" to "shell_command",
                "config" to mapOf("command" to "echo 'Hello World'")
            )
        )
    )
    
    @BeforeEach
    fun setUp() {
        // Mock dependencies
        databaseService = mock()
        workflowRepository = mock()
        workflowExecutionRepository = mock()
        workflowStepRepository = mock()
        workflowEngine = mock()
        stepExecutor = mock()
        
        // Configure database service mocks
        whenever(databaseService.workflowRepository).thenReturn(workflowRepository)
        whenever(databaseService.workflowExecutionRepository).thenReturn(workflowExecutionRepository)
        whenever(databaseService.workflowStepRepository).thenReturn(workflowStepRepository)
        
        // Configure workflow engine mock
        whenever(workflowEngine.validateDefinition(any())).thenReturn(ValidationResult(true, emptyList()))
        whenever(workflowEngine.parseSteps(any())).thenReturn(
            listOf(
                StepDefinition(
                    name = "step1",
                    type = "http_request",
                    inputData = mapOf("url" to "https://api.example.com"),
                    config = emptyMap(),
                    dependsOn = emptyList(),
                    condition = null,
                    retryPolicy = RetryPolicy(),
                    timeout = 300
                ),
                StepDefinition(
                    name = "step2",
                    type = "shell_command",
                    inputData = mapOf("command" to "echo 'Hello World'"),
                    config = emptyMap(),
                    dependsOn = emptyList(),
                    condition = null,
                    retryPolicy = RetryPolicy(),
                    timeout = 300
                )
            )
        )
        
        // Create service instance
        flowService = FlowService(
            databaseService = databaseService,
            workflowEngine = workflowEngine,
            stepExecutor = stepExecutor
        )
    }
    
    @Test
    fun `createWorkflow should create new workflow successfully`() = runTest {
        // Given
        val request = CreateWorkflowRequest(
            name = testWorkflowName,
            description = "Test workflow",
            definition = testWorkflowDefinition,
            userId = testUserId
        )
        
        val expectedWorkflow = createTestWorkflow()
        
        whenever(workflowRepository.findByNameAndUser(testWorkflowName, testUserId)).thenReturn(null)
        whenever(workflowRepository.create(any())).thenReturn(expectedWorkflow)
        
        // When
        val result = flowService.createWorkflow(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(testWorkflowName, response.name)
        assertEquals("Test workflow", response.description)
        assertEquals("active", response.status)
        assertEquals(1, response.version)
        
        verify(workflowEngine).validateDefinition(testWorkflowDefinition)
        verify(workflowRepository).findByNameAndUser(testWorkflowName, testUserId)
        verify(workflowRepository).create(any())
    }
    
    @Test
    fun `createWorkflow should fail when workflow already exists`() = runTest {
        // Given
        val request = CreateWorkflowRequest(
            name = testWorkflowName,
            description = "Test workflow",
            definition = testWorkflowDefinition,
            userId = testUserId
        )
        
        val existingWorkflow = createTestWorkflow()
        whenever(workflowRepository.findByNameAndUser(testWorkflowName, testUserId)).thenReturn(existingWorkflow)
        
        // When
        val result = flowService.createWorkflow(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Workflow with name '$testWorkflowName' already exists", result.getErrorOrNull())
        
        verify(workflowRepository).findByNameAndUser(testWorkflowName, testUserId)
        verify(workflowRepository, never()).create(any())
    }
    
    @Test
    fun `createWorkflow should fail with invalid definition`() = runTest {
        // Given
        val request = CreateWorkflowRequest(
            name = testWorkflowName,
            description = "Test workflow",
            definition = testWorkflowDefinition,
            userId = testUserId
        )
        
        whenever(workflowEngine.validateDefinition(any())).thenReturn(
            ValidationResult(false, listOf("Missing required field 'steps'"))
        )
        
        // When
        val result = flowService.createWorkflow(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Invalid workflow definition: Missing required field 'steps'", result.getErrorOrNull())
        
        verify(workflowEngine).validateDefinition(testWorkflowDefinition)
        verify(workflowRepository, never()).findByNameAndUser(any(), any())
    }
    
    @Test
    fun `getWorkflow should retrieve workflow successfully`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId)
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        
        // When
        val result = flowService.getWorkflow(workflowId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(workflowId, response.id)
        assertEquals(testWorkflowName, response.name)
        
        verify(workflowRepository).findById(workflowId)
    }
    
    @Test
    fun `getWorkflow should fail when workflow not found`() = runTest {
        // Given
        val workflowId = "non-existent-workflow"
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(null)
        
        // When
        val result = flowService.getWorkflow(workflowId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Workflow not found", result.getErrorOrNull())
        
        verify(workflowRepository).findById(workflowId)
    }
    
    @Test
    fun `getWorkflow should fail when user has no access`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId, userId = "other-user")
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        
        // When
        val result = flowService.getWorkflow(workflowId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Access denied", result.getErrorOrNull())
        
        verify(workflowRepository).findById(workflowId)
    }
    
    @Test
    fun `updateWorkflow should update workflow successfully`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId)
        val newDescription = "Updated description"
        
        val request = UpdateWorkflowRequest(
            workflowId = workflowId,
            description = newDescription,
            definition = null,
            userId = testUserId
        )
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowRepository.update(any())).thenReturn(true)
        
        // When
        val result = flowService.updateWorkflow(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(newDescription, response.description)
        
        verify(workflowRepository).findById(workflowId)
        verify(workflowRepository).update(any())
    }
    
    @Test
    fun `deleteWorkflow should archive workflow successfully`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId)
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowExecutionRepository.findByWorkflowId(workflowId)).thenReturn(emptyList())
        whenever(workflowRepository.updateStatus(workflowId, "archived")).thenReturn(true)
        
        // When
        val result = flowService.deleteWorkflow(workflowId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository).findByWorkflowId(workflowId)
        verify(workflowRepository).updateStatus(workflowId, "archived")
    }
    
    @Test
    fun `deleteWorkflow should fail when there are running executions`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId)
        val runningExecution = createTestExecution().copy(workflowId = workflowId, status = "running")
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowExecutionRepository.findByWorkflowId(workflowId)).thenReturn(listOf(runningExecution))
        
        // When
        val result = flowService.deleteWorkflow(workflowId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Cannot delete workflow with running executions", result.getErrorOrNull())
        
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository).findByWorkflowId(workflowId)
        verify(workflowRepository, never()).updateStatus(any(), any())
    }
    
    @Test
    fun `listWorkflows should return active workflows by default`() = runTest {
        // Given
        val request = ListWorkflowsRequest(userId = testUserId)
        
        val workflows = listOf(
            createTestWorkflow(),
            createTestWorkflow().copy(id = "workflow-2", name = "workflow-2")
        )
        whenever(workflowRepository.findActiveByUserId(testUserId)).thenReturn(workflows)
        
        // When
        val result = flowService.listWorkflows(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        
        verify(workflowRepository).findActiveByUserId(testUserId)
    }
    
    @Test
    fun `listWorkflows should filter by status when specified`() = runTest {
        // Given
        val request = ListWorkflowsRequest(userId = testUserId, status = "active")
        
        val workflows = listOf(
            createTestWorkflow().copy(status = "active"),
            createTestWorkflow().copy(id = "workflow-2", name = "workflow-2", status = "active")
        )
        whenever(workflowRepository.findByUserId(testUserId)).thenReturn(workflows)
        
        // When
        val result = flowService.listWorkflows(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        assertTrue(response.all { it.status == "active" })
        
        verify(workflowRepository).findByUserId(testUserId)
    }
    
    @Test
    fun `executeWorkflow should start execution successfully`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId)
        val execution = createTestExecution().copy(workflowId = workflowId)
        
        val request = ExecuteWorkflowRequest(
            workflowId = workflowId,
            userId = testUserId,
            inputData = mapOf("key" to "value")
        )
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowExecutionRepository.create(any())).thenReturn(execution)
        
        // When
        val result = flowService.executeWorkflow(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(workflowId, response.workflowId)
        assertEquals("pending", response.status)
        
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository).create(any())
    }
    
    @Test
    fun `executeWorkflow should fail when workflow is not active`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId, status = "archived")
        
        val request = ExecuteWorkflowRequest(
            workflowId = workflowId,
            userId = testUserId
        )
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        
        // When
        val result = flowService.executeWorkflow(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Workflow is not active", result.getErrorOrNull())
        
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository, never()).create(any())
    }
    
    @Test
    fun `getExecution should retrieve execution successfully`() = runTest {
        // Given
        val executionId = "execution-123"
        val workflowId = "workflow-123"
        val execution = createTestExecution().copy(id = executionId, workflowId = workflowId)
        val workflow = createTestWorkflow().copy(id = workflowId)
        
        whenever(workflowExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        
        // When
        val result = flowService.getExecution(executionId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(executionId, response.id)
        assertEquals(workflowId, response.workflowId)
        
        verify(workflowExecutionRepository).findById(executionId)
        verify(workflowRepository).findById(workflowId)
    }
    
    @Test
    fun `cancelExecution should cancel running execution successfully`() = runTest {
        // Given
        val executionId = "execution-123"
        val workflowId = "workflow-123"
        val execution = createTestExecution().copy(id = executionId, workflowId = workflowId, status = "running")
        val workflow = createTestWorkflow().copy(id = workflowId)
        
        whenever(workflowExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowExecutionRepository.updateStatus(eq(executionId), eq("cancelled"), any())).thenReturn(true)
        
        // When
        val result = flowService.cancelExecution(executionId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        
        verify(workflowExecutionRepository).findById(executionId)
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository).updateStatus(eq(executionId), eq("cancelled"), any())
    }
    
    @Test
    fun `cancelExecution should fail when execution cannot be cancelled`() = runTest {
        // Given
        val executionId = "execution-123"
        val workflowId = "workflow-123"
        val execution = createTestExecution().copy(id = executionId, workflowId = workflowId, status = "completed")
        val workflow = createTestWorkflow().copy(id = workflowId)
        
        whenever(workflowExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        
        // When
        val result = flowService.cancelExecution(executionId, testUserId)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Execution cannot be cancelled (status: completed)", result.getErrorOrNull())
        
        verify(workflowExecutionRepository).findById(executionId)
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository, never()).updateStatus(any(), any(), any())
    }
    
    @Test
    fun `listExecutions should return executions for user`() = runTest {
        // Given
        val request = ListExecutionsRequest(userId = testUserId)
        
        val executions = listOf(
            createTestExecution(),
            createTestExecution().copy(id = "execution-2")
        )
        whenever(workflowExecutionRepository.findByTriggeredBy(testUserId)).thenReturn(executions)
        
        // When
        val result = flowService.listExecutions(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        
        verify(workflowExecutionRepository).findByTriggeredBy(testUserId)
    }
    
    @Test
    fun `getExecutionSteps should return steps for execution`() = runTest {
        // Given
        val executionId = "execution-123"
        val workflowId = "workflow-123"
        val execution = createTestExecution().copy(id = executionId, workflowId = workflowId)
        val workflow = createTestWorkflow().copy(id = workflowId)
        
        val steps = listOf(
            createTestStep().copy(executionId = executionId, stepOrder = 1),
            createTestStep().copy(id = "step-2", executionId = executionId, stepOrder = 2)
        )
        
        whenever(workflowExecutionRepository.findById(executionId)).thenReturn(execution)
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowStepRepository.findByExecutionIdOrdered(executionId)).thenReturn(steps)
        
        // When
        val result = flowService.getExecutionSteps(executionId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        assertEquals(1, response[0].stepOrder)
        assertEquals(2, response[1].stepOrder)
        
        verify(workflowExecutionRepository).findById(executionId)
        verify(workflowRepository).findById(workflowId)
        verify(workflowStepRepository).findByExecutionIdOrdered(executionId)
    }
    
    @Test
    fun `getWorkflowStats should return statistics for user`() = runTest {
        // Given
        val expectedStats = WorkflowStats(
            totalWorkflows = 10,
            activeWorkflows = 8,
            pausedWorkflows = 1,
            archivedWorkflows = 1,
            recentlyCreated = 2,
            recentlyUpdated = 3
        )
        whenever(workflowRepository.getWorkflowStats(testUserId)).thenReturn(expectedStats)
        
        // When
        val result = flowService.getWorkflowStats(testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(10, stats.totalWorkflows)
        assertEquals(8, stats.activeWorkflows)
        assertEquals(1, stats.pausedWorkflows)
        assertEquals(1, stats.archivedWorkflows)
        
        verify(workflowRepository).getWorkflowStats(testUserId)
    }
    
    @Test
    fun `getExecutionStats should return execution statistics`() = runTest {
        // Given
        val workflowId = "workflow-123"
        val workflow = createTestWorkflow().copy(id = workflowId)
        val expectedStats = ExecutionStats(
            totalExecutions = 20,
            completedExecutions = 15,
            failedExecutions = 3,
            runningExecutions = 2,
            averageDurationMs = 5000.0,
            successRate = 0.75
        )
        
        whenever(workflowRepository.findById(workflowId)).thenReturn(workflow)
        whenever(workflowExecutionRepository.getExecutionStats(workflowId)).thenReturn(expectedStats)
        
        // When
        val result = flowService.getExecutionStats(workflowId, testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(20, stats.totalExecutions)
        assertEquals(15, stats.completedExecutions)
        assertEquals(3, stats.failedExecutions)
        assertEquals(2, stats.runningExecutions)
        assertEquals(0.75, stats.successRate)
        
        verify(workflowRepository).findById(workflowId)
        verify(workflowExecutionRepository).getExecutionStats(workflowId)
    }
    
    private fun createTestWorkflow(): Workflow {
        return Workflow(
            id = "test-workflow-id",
            name = testWorkflowName,
            description = "Test workflow",
            definition = testWorkflowDefinition,
            userId = testUserId,
            status = "active",
            version = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
    }
    
    private fun createTestExecution(): WorkflowExecution {
        return WorkflowExecution(
            id = "test-execution-id",
            workflowId = "test-workflow-id",
            triggeredBy = testUserId,
            status = "pending",
            inputData = mapOf("key" to "value"),
            outputData = null,
            errorMessage = null,
            startedAt = Clock.System.now(),
            completedAt = null,
            durationMs = null
        )
    }
    
    private fun createTestStep(): WorkflowStep {
        return WorkflowStep(
            id = "test-step-id",
            executionId = "test-execution-id",
            stepName = "test-step",
            stepType = "http_request",
            status = "pending",
            inputData = mapOf("url" to "https://api.example.com"),
            outputData = null,
            errorMessage = null,
            startedAt = null,
            completedAt = null,
            durationMs = null,
            stepOrder = 1
        )
    }
}