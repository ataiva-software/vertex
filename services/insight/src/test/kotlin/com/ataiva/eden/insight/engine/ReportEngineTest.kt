package com.ataiva.eden.insight.engine

import com.ataiva.eden.insight.model.*
import com.ataiva.eden.insight.repository.AnalyticsQueryRepository
import com.ataiva.eden.insight.repository.QueryExecutionRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportEngineTest {

    private lateinit var reportEngine: ReportEngine
    private lateinit var analyticsEngine: AnalyticsEngine
    private lateinit var analyticsQueryRepository: AnalyticsQueryRepository
    private lateinit var queryExecutionRepository: QueryExecutionRepository
    private lateinit var reportOutputPath: String
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setUp() {
        reportOutputPath = tempDir.toString()
        analyticsEngine = mockk()
        analyticsQueryRepository = mockk()
        queryExecutionRepository = mockk()
        
        reportEngine = ReportEngine(
            analyticsEngine,
            analyticsQueryRepository,
            queryExecutionRepository,
            reportOutputPath
        )
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `test PDF report generation`() = runBlocking {
        // Arrange
        val report = createTestReport()
        val template = createTestTemplate()
        val parameters = mapOf("param1" to "value1", "param2" to "value2")
        
        // Mock query execution
        val queryId = "test_query_1"
        val query = AnalyticsQuery(
            id = queryId,
            name = "Test Query",
            queryText = "SELECT * FROM test",
            queryType = QueryType.SELECT,
            createdBy = "system"
        )
        
        val queryResult = AnalyticsResult(
            queryId = queryId,
            executionId = "exec_1",
            data = listOf(
                mapOf("id" to 1, "name" to "Item 1", "value" to 100),
                mapOf("id" to 2, "name" to "Item 2", "value" to 200)
            ),
            metadata = ResultMetadata(
                totalRows = 2,
                columns = listOf(
                    ColumnInfo("id", DataType.INTEGER),
                    ColumnInfo("name", DataType.STRING),
                    ColumnInfo("value", DataType.INTEGER)
                ),
                executionTimeMs = 100,
                dataSource = "test",
                queryHash = "hash"
            )
        )
        
        coEvery { analyticsQueryRepository.findById(queryId) } returns query
        coEvery { analyticsEngine.executeQuery(query, parameters) } returns queryResult
        
        // Act
        val outputPath = reportEngine.generateReport(report, template, parameters, ReportFormat.PDF)
        
        // Assert
        assertTrue(File(outputPath).exists(), "Report file should exist")
        assertTrue(outputPath.endsWith(".pdf"), "Report should be a PDF file")
        
        // Verify
        coVerify { analyticsQueryRepository.findById(queryId) }
        coVerify { analyticsEngine.executeQuery(query, parameters) }
    }
    
    @Test
    fun `test Excel report generation`() = runBlocking {
        // Arrange
        val report = createTestReport()
        val template = createTestTemplate()
        val parameters = mapOf("param1" to "value1", "param2" to "value2")
        
        // Mock query execution
        val queryId = "test_query_1"
        val query = AnalyticsQuery(
            id = queryId,
            name = "Test Query",
            queryText = "SELECT * FROM test",
            queryType = QueryType.SELECT,
            createdBy = "system"
        )
        
        val queryResult = AnalyticsResult(
            queryId = queryId,
            executionId = "exec_1",
            data = listOf(
                mapOf("id" to 1, "name" to "Item 1", "value" to 100),
                mapOf("id" to 2, "name" to "Item 2", "value" to 200)
            ),
            metadata = ResultMetadata(
                totalRows = 2,
                columns = listOf(
                    ColumnInfo("id", DataType.INTEGER),
                    ColumnInfo("name", DataType.STRING),
                    ColumnInfo("value", DataType.INTEGER)
                ),
                executionTimeMs = 100,
                dataSource = "test",
                queryHash = "hash"
            )
        )
        
        coEvery { analyticsQueryRepository.findById(queryId) } returns query
        coEvery { analyticsEngine.executeQuery(query, parameters) } returns queryResult
        
        // Act
        val outputPath = reportEngine.generateReport(report, template, parameters, ReportFormat.EXCEL)
        
        // Assert
        assertTrue(File(outputPath).exists(), "Report file should exist")
        assertTrue(outputPath.endsWith(".xlsx"), "Report should be an Excel file")
        
        // Verify
        coVerify { analyticsQueryRepository.findById(queryId) }
        coVerify { analyticsEngine.executeQuery(query, parameters) }
    }
    
    @Test
    fun `test CSV report generation`() = runBlocking {
        // Arrange
        val report = createTestReport()
        val template = createTestTemplate()
        val parameters = mapOf("param1" to "value1", "param2" to "value2")
        
        // Mock query execution
        val queryId = "test_query_1"
        val query = AnalyticsQuery(
            id = queryId,
            name = "Test Query",
            queryText = "SELECT * FROM test",
            queryType = QueryType.SELECT,
            createdBy = "system"
        )
        
        val queryResult = AnalyticsResult(
            queryId = queryId,
            executionId = "exec_1",
            data = listOf(
                mapOf("id" to 1, "name" to "Item 1", "value" to 100),
                mapOf("id" to 2, "name" to "Item 2", "value" to 200)
            ),
            metadata = ResultMetadata(
                totalRows = 2,
                columns = listOf(
                    ColumnInfo("id", DataType.INTEGER),
                    ColumnInfo("name", DataType.STRING),
                    ColumnInfo("value", DataType.INTEGER)
                ),
                executionTimeMs = 100,
                dataSource = "test",
                queryHash = "hash"
            )
        )
        
        coEvery { analyticsQueryRepository.findById(queryId) } returns query
        coEvery { analyticsEngine.executeQuery(query, parameters) } returns queryResult
        
        // Act
        val outputPath = reportEngine.generateReport(report, template, parameters, ReportFormat.CSV)
        
        // Assert
        assertTrue(File(outputPath).exists(), "Report file should exist")
        assertTrue(outputPath.endsWith(".csv"), "Report should be a CSV file")
        
        // Verify
        coVerify { analyticsQueryRepository.findById(queryId) }
        coVerify { analyticsEngine.executeQuery(query, parameters) }
    }
    
    @Test
    fun `test HTML report generation`() = runBlocking {
        // Arrange
        val report = createTestReport()
        val template = createTestTemplate()
        val parameters = mapOf("param1" to "value1", "param2" to "value2")
        
        // Mock query execution
        val queryId = "test_query_1"
        val query = AnalyticsQuery(
            id = queryId,
            name = "Test Query",
            queryText = "SELECT * FROM test",
            queryType = QueryType.SELECT,
            createdBy = "system"
        )
        
        val queryResult = AnalyticsResult(
            queryId = queryId,
            executionId = "exec_1",
            data = listOf(
                mapOf("id" to 1, "name" to "Item 1", "value" to 100),
                mapOf("id" to 2, "name" to "Item 2", "value" to 200)
            ),
            metadata = ResultMetadata(
                totalRows = 2,
                columns = listOf(
                    ColumnInfo("id", DataType.INTEGER),
                    ColumnInfo("name", DataType.STRING),
                    ColumnInfo("value", DataType.INTEGER)
                ),
                executionTimeMs = 100,
                dataSource = "test",
                queryHash = "hash"
            )
        )
        
        coEvery { analyticsQueryRepository.findById(queryId) } returns query
        coEvery { analyticsEngine.executeQuery(query, parameters) } returns queryResult
        
        // Act
        val outputPath = reportEngine.generateReport(report, template, parameters, ReportFormat.HTML)
        
        // Assert
        assertTrue(File(outputPath).exists(), "Report file should exist")
        assertTrue(outputPath.endsWith(".html"), "Report should be an HTML file")
        
        // Verify
        coVerify { analyticsQueryRepository.findById(queryId) }
        coVerify { analyticsEngine.executeQuery(query, parameters) }
    }
    
    @Test
    fun `test error handling during report generation`() = runBlocking {
        // Arrange
        val report = createTestReport()
        val template = createTestTemplate()
        val parameters = mapOf("param1" to "value1", "param2" to "value2")
        
        // Mock query execution to throw an exception
        val queryId = "test_query_1"
        val query = AnalyticsQuery(
            id = queryId,
            name = "Test Query",
            queryText = "SELECT * FROM test",
            queryType = QueryType.SELECT,
            createdBy = "system"
        )
        
        coEvery { analyticsQueryRepository.findById(queryId) } returns query
        coEvery { analyticsEngine.executeQuery(query, parameters) } throws RuntimeException("Test exception")
        
        // Act & Assert
        try {
            reportEngine.generateReport(report, template, parameters, ReportFormat.PDF)
            assert(false) { "Should have thrown an exception" }
        } catch (e: ReportGenerationException) {
            assertEquals("Failed to generate report: Test exception", e.message)
        }
        
        // Verify
        coVerify { analyticsQueryRepository.findById(queryId) }
        coVerify { analyticsEngine.executeQuery(query, parameters) }
    }
    
    // Helper methods
    
    private fun createTestReport(): Report {
        return Report(
            id = "test_report_1",
            name = "Test Report",
            description = "Test report description",
            templateId = "test_template_1",
            parameters = emptyMap(),
            format = ReportFormat.PDF,
            createdBy = "system"
        )
    }
    
    private fun createTestTemplate(): ReportTemplate {
        return ReportTemplate(
            id = "test_template_1",
            name = "Test Template",
            description = "Test template description",
            templateContent = """
                # Test Report
                
                Generated: {{timestamp}}
                
                ## SECTION: Test Section
                This is a test section.
                
                ## QUERY: test_query_1
                
                ## END SECTION
                
                ## SECTION: Chart Section
                
                {{CHART:bar,Test Chart,test_query_1}}
                
                ## END SECTION
            """.trimIndent(),
            requiredParameters = listOf(
                ParameterDefinition("param1", DataType.STRING, true),
                ParameterDefinition("param2", DataType.STRING, true)
            ),
            supportedFormats = listOf(ReportFormat.PDF, ReportFormat.EXCEL, ReportFormat.CSV, ReportFormat.HTML),
            createdBy = "system"
        )
    }
}