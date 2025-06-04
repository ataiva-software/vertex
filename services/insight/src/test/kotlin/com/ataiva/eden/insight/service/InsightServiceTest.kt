package com.ataiva.eden.insight.service

import com.ataiva.eden.insight.model.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for InsightService.
 * Ensures 100% test coverage and regression prevention as emphasized in the roadmap.
 */
@DisplayName("Insight Service Tests")
class InsightServiceTest {
    
    private lateinit var insightService: InsightService
    private lateinit var testConfiguration: InsightConfiguration
    
    @BeforeEach
    fun setUp() {
        testConfiguration = InsightConfiguration(
            maxQueryTimeout = 30000,
            maxResultRows = 1000,
            cacheEnabled = true,
            cacheTtl = 300,
            reportOutputPath = System.getProperty("java.io.tmpdir") + "/test-reports",
            maxConcurrentQueries = 5
        )
        insightService = InsightService(testConfiguration)
    }
    
    // ============================================================================
    // Query Management Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Query Management")
    inner class QueryManagementTests {
        
        @Test
        @DisplayName("Should create analytics query successfully")
        fun `should create analytics query successfully`() = runBlocking {
            // Given
            val name = "Test Query"
            val description = "Test query description"
            val queryText = "SELECT * FROM users"
            val queryType = QueryType.SELECT
            val createdBy = "test_user"
            val tags = listOf("users", "test")
            
            // When
            val query = insightService.createQuery(
                name = name,
                description = description,
                queryText = queryText,
                queryType = queryType,
                createdBy = createdBy,
                tags = tags
            )
            
            // Then
            assertNotNull(query.id)
            assertEquals(name, query.name)
            assertEquals(description, query.description)
            assertEquals(queryText, query.queryText)
            assertEquals(queryType, query.queryType)
            assertEquals(createdBy, query.createdBy)
            assertEquals(tags, query.tags)
            assertTrue(query.isActive)
            assertTrue(query.createdAt > 0)
        }
        
        @Test
        @DisplayName("Should retrieve queries with filtering")
        fun `should retrieve queries with filtering`() = runBlocking {
            // Given
            val user1 = "user1"
            val user2 = "user2"
            
            insightService.createQuery("Query 1", null, "SELECT 1", QueryType.SELECT, emptyMap(), user1, listOf("tag1"))
            insightService.createQuery("Query 2", null, "SELECT 2", QueryType.AGGREGATE, emptyMap(), user2, listOf("tag2"))
            insightService.createQuery("Query 3", null, "SELECT 3", QueryType.SELECT, emptyMap(), user1, listOf("tag1"))
            
            // When
            val allQueries = insightService.getQueries()
            val user1Queries = insightService.getQueries(createdBy = user1)
            val selectQueries = insightService.getQueries(queryType = QueryType.SELECT)
            val tag1Queries = insightService.getQueries(tags = listOf("tag1"))
            
            // Then
            assertEquals(3, allQueries.size)
            assertEquals(2, user1Queries.size)
            assertEquals(2, selectQueries.size)
            assertEquals(2, tag1Queries.size)
            
            assertTrue(user1Queries.all { it.createdBy == user1 })
            assertTrue(selectQueries.all { it.queryType == QueryType.SELECT })
            assertTrue(tag1Queries.all { it.tags.contains("tag1") })
        }
        
        @Test
        @DisplayName("Should update query successfully")
        fun `should update query successfully`() = runBlocking {
            // Given
            val originalQuery = insightService.createQuery(
                "Original", "Original description", "SELECT 1", QueryType.SELECT, emptyMap(), "user", emptyList()
            )
            
            // When
            val updatedQuery = insightService.updateQuery(
                id = originalQuery.id,
                name = "Updated",
                description = "Updated description",
                queryText = "SELECT 2",
                isActive = false
            )
            
            // Then
            assertNotNull(updatedQuery)
            assertEquals("Updated", updatedQuery!!.name)
            assertEquals("Updated description", updatedQuery.description)
            assertEquals("SELECT 2", updatedQuery.queryText)
            assertFalse(updatedQuery.isActive)
            assertTrue(updatedQuery.lastModified > originalQuery.lastModified)
        }
        
        @Test
        @DisplayName("Should delete query successfully")
        fun `should delete query successfully`() = runBlocking {
            // Given
            val query = insightService.createQuery("Test", null, "SELECT 1", QueryType.SELECT, emptyMap(), "user", emptyList())
            
            // When
            val deleted = insightService.deleteQuery(query.id)
            val retrieved = insightService.getQuery(query.id)
            
            // Then
            assertTrue(deleted)
            assertNull(retrieved)
        }
        
        @Test
        @DisplayName("Should execute query successfully")
        fun `should execute query successfully`() = runBlocking {
            // Given
            val query = insightService.createQuery("Test", null, "SELECT * FROM users", QueryType.SELECT, emptyMap(), "user", emptyList())
            val parameters = mapOf("limit" to "10")
            
            // When
            val result = insightService.executeQuery(query.id, parameters, "test_user")
            
            // Then
            assertEquals(query.id, result.queryId)
            assertNotNull(result.executionId)
            assertNotNull(result.data)
            assertNotNull(result.metadata)
            assertTrue(result.metadata.totalRows >= 0)
            assertTrue(result.metadata.executionTimeMs >= 0)
        }
        
        @Test
        @DisplayName("Should fail to execute inactive query")
        fun `should fail to execute inactive query`() = runBlocking {
            // Given
            val query = insightService.createQuery("Test", null, "SELECT 1", QueryType.SELECT, emptyMap(), "user", emptyList())
            insightService.updateQuery(query.id, isActive = false)
            
            // When & Then
            assertThrows<IllegalStateException> {
                runBlocking {
                    insightService.executeQuery(query.id, emptyMap(), "test_user")
                }
            }
        }
        
        @Test
        @DisplayName("Should execute raw query successfully")
        fun `should execute raw query successfully`() = runBlocking {
            // Given
            val request = QueryRequest(
                queryText = "SELECT COUNT(*) FROM users",
                parameters = mapOf("active" to "true"),
                limit = 100
            )
            
            // When
            val response = insightService.executeRawQuery(request, "test_user")
            
            // Then
            assertTrue(response.success)
            assertNotNull(response.data)
            assertNotNull(response.metadata)
            assertNotNull(response.executionId)
            assertNull(response.error)
        }
    }
    
    // ============================================================================
    // Report Management Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Report Management")
    inner class ReportManagementTests {
        
        @Test
        @DisplayName("Should create report template successfully")
        fun `should create report template successfully`() = runBlocking {
            // Given
            val name = "Test Template"
            val description = "Test template description"
            val templateContent = "# Report\n{{data}}"
            val category = "test"
            val createdBy = "test_user"
            
            // When
            val template = insightService.createReportTemplate(
                name = name,
                description = description,
                templateContent = templateContent,
                category = category,
                createdBy = createdBy
            )
            
            // Then
            assertNotNull(template.id)
            assertEquals(name, template.name)
            assertEquals(description, template.description)
            assertEquals(templateContent, template.templateContent)
            assertEquals(category, template.category)
            assertEquals(createdBy, template.createdBy)
        }
        
        @Test
        @DisplayName("Should create report successfully")
        fun `should create report successfully`() = runBlocking {
            // Given
            val template = insightService.createReportTemplate(
                "Template", null, "Content", emptyList(), listOf(ReportFormat.PDF), "test", "user"
            )
            
            val name = "Test Report"
            val description = "Test report description"
            val parameters = mapOf("param1" to "value1")
            val recipients = listOf("user@example.com")
            val createdBy = "test_user"
            
            // When
            val report = insightService.createReport(
                name = name,
                description = description,
                templateId = template.id,
                parameters = parameters,
                recipients = recipients,
                createdBy = createdBy
            )
            
            // Then
            assertNotNull(report.id)
            assertEquals(name, report.name)
            assertEquals(description, report.description)
            assertEquals(template.id, report.templateId)
            assertEquals(parameters, report.parameters)
            assertEquals(recipients, report.recipients)
            assertEquals(createdBy, report.createdBy)
            assertTrue(report.isActive)
        }
        
        @Test
        @DisplayName("Should generate report asynchronously")
        fun `should generate report asynchronously`() = runBlocking {
            // Given
            val template = insightService.createReportTemplate(
                "Template", null, "# Report\n{{timestamp}}", emptyList(), listOf(ReportFormat.PDF), "test", "user"
            )
            val report = insightService.createReport(
                "Report", null, template.id, emptyMap(), null, emptyList(), ReportFormat.PDF, "user"
            )
            
            val request = ReportGenerationRequest(
                reportId = report.id,
                parameters = mapOf("param1" to "value1"),
                format = ReportFormat.PDF,
                async = true
            )
            
            // When
            val response = insightService.generateReport(request, "test_user")
            
            // Then
            assertTrue(response.success)
            assertNotNull(response.executionId)
            assertEquals("Report generation started", response.message)
            assertNull(response.error)
        }
        
        @Test
        @DisplayName("Should generate report synchronously")
        fun `should generate report synchronously`() = runBlocking {
            // Given
            val template = insightService.createReportTemplate(
                "Template", null, "# Report\n{{timestamp}}", emptyList(), listOf(ReportFormat.PDF), "test", "user"
            )
            val report = insightService.createReport(
                "Report", null, template.id, emptyMap(), null, emptyList(), ReportFormat.PDF, "user"
            )
            
            val request = ReportGenerationRequest(
                reportId = report.id,
                async = false
            )
            
            // When
            val response = insightService.generateReport(request, "test_user")
            
            // Then
            assertTrue(response.success)
            assertNotNull(response.executionId)
            assertNotNull(response.downloadUrl)
            assertEquals("Report generated successfully", response.message)
        }
        
        @Test
        @DisplayName("Should fail to generate report with invalid template")
        fun `should fail to generate report with invalid template`() = runBlocking {
            // Given
            val request = ReportGenerationRequest(
                reportId = "invalid_report_id",
                async = false
            )
            
            // When
            val response = insightService.generateReport(request, "test_user")
            
            // Then
            assertFalse(response.success)
            assertNotNull(response.error)
            assertTrue(response.error!!.contains("Report not found"))
        }
        
        @Test
        @DisplayName("Should retrieve report templates with filtering")
        fun `should retrieve report templates with filtering`() = runBlocking {
            // Given
            insightService.createReportTemplate("Template 1", null, "Content", emptyList(), listOf(ReportFormat.PDF), "category1", "user")
            insightService.createReportTemplate("Template 2", null, "Content", emptyList(), listOf(ReportFormat.PDF), "category2", "user")
            insightService.createReportTemplate("Template 3", null, "Content", emptyList(), listOf(ReportFormat.PDF), "category1", "user")
            
            // When
            val allTemplates = insightService.getReportTemplates()
            val category1Templates = insightService.getReportTemplates("category1")
            
            // Then
            assertEquals(3, allTemplates.size)
            assertEquals(2, category1Templates.size)
            assertTrue(category1Templates.all { it.category == "category1" })
        }
    }
    
    // ============================================================================
    // Dashboard Management Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Dashboard Management")
    inner class DashboardManagementTests {
        
        @Test
        @DisplayName("Should create dashboard successfully")
        fun `should create dashboard successfully`() = runBlocking {
            // Given
            val name = "Test Dashboard"
            val description = "Test dashboard description"
            val widgets = listOf(
                DashboardWidget(
                    id = "widget1",
                    type = WidgetType.CHART,
                    title = "Test Chart",
                    configuration = WidgetConfiguration(chartType = ChartType.LINE),
                    position = WidgetPosition(0, 0, 6, 4)
                )
            )
            val permissions = DashboardPermissions(owner = "test_user")
            val createdBy = "test_user"
            val tags = listOf("test", "dashboard")
            
            // When
            val dashboard = insightService.createDashboard(
                name = name,
                description = description,
                widgets = widgets,
                permissions = permissions,
                createdBy = createdBy,
                tags = tags
            )
            
            // Then
            assertNotNull(dashboard.id)
            assertEquals(name, dashboard.name)
            assertEquals(description, dashboard.description)
            assertEquals(widgets, dashboard.widgets)
            assertEquals(permissions, dashboard.permissions)
            assertEquals(createdBy, dashboard.createdBy)
            assertEquals(tags, dashboard.tags)
        }
        
        @Test
        @DisplayName("Should update dashboard successfully")
        fun `should update dashboard successfully`() = runBlocking {
            // Given
            val originalDashboard = insightService.createDashboard(
                "Original", null, emptyList(), DashboardLayout(), DashboardPermissions("user"), "user", false, emptyList()
            )
            
            val newWidgets = listOf(
                DashboardWidget("w1", WidgetType.GAUGE, "Gauge", WidgetConfiguration(), WidgetPosition(0, 0, 3, 3))
            )
            
            // When
            val updatedDashboard = insightService.updateDashboard(
                id = originalDashboard.id,
                name = "Updated",
                widgets = newWidgets,
                tags = listOf("updated")
            )
            
            // Then
            assertNotNull(updatedDashboard)
            assertEquals("Updated", updatedDashboard!!.name)
            assertEquals(newWidgets, updatedDashboard.widgets)
            assertEquals(listOf("updated"), updatedDashboard.tags)
            assertTrue(updatedDashboard.lastModified > originalDashboard.lastModified)
        }
        
        @Test
        @DisplayName("Should get dashboard data successfully")
        fun `should get dashboard data successfully`() = runBlocking {
            // Given
            val widgets = listOf(
                DashboardWidget("w1", WidgetType.CHART, "Chart", WidgetConfiguration(chartType = ChartType.LINE), WidgetPosition(0, 0, 6, 4)),
                DashboardWidget("w2", WidgetType.METRIC, "Metric", WidgetConfiguration(), WidgetPosition(6, 0, 6, 4))
            )
            val dashboard = insightService.createDashboard(
                "Test", null, widgets, DashboardLayout(), DashboardPermissions("user"), "user"
            )
            
            val request = DashboardDataRequest(dashboardId = dashboard.id)
            
            // When
            val response = insightService.getDashboardData(request)
            
            // Then
            assertEquals(dashboard.id, response.dashboardId)
            assertEquals(2, response.widgets.size)
            assertTrue(response.lastUpdated > 0)
            
            response.widgets.forEach { widgetData ->
                assertNotNull(widgetData.widgetId)
                assertNotNull(widgetData.data)
            }
        }
        
        @Test
        @DisplayName("Should filter dashboards correctly")
        fun `should filter dashboards correctly`() = runBlocking {
            // Given
            val user1 = "user1"
            val user2 = "user2"
            
            insightService.createDashboard("Dashboard 1", null, emptyList(), DashboardLayout(), DashboardPermissions(user1), user1, true, listOf("tag1"))
            insightService.createDashboard("Dashboard 2", null, emptyList(), DashboardLayout(), DashboardPermissions(user2), user2, false, listOf("tag2"))
            insightService.createDashboard("Dashboard 3", null, emptyList(), DashboardLayout(), DashboardPermissions(user1), user1, false, listOf("tag1"))
            
            // When
            val allDashboards = insightService.getDashboards()
            val publicDashboards = insightService.getDashboards(isPublic = true)
            val tag1Dashboards = insightService.getDashboards(tags = listOf("tag1"))
            
            // Then
            assertEquals(3, allDashboards.size)
            assertEquals(1, publicDashboards.size)
            assertEquals(2, tag1Dashboards.size)
            
            assertTrue(publicDashboards.all { it.isPublic })
            assertTrue(tag1Dashboards.all { it.tags.contains("tag1") })
        }
    }
    
    // ============================================================================
    // Metrics and KPI Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Metrics and KPI Management")
    inner class MetricsAndKPITests {
        
        @Test
        @DisplayName("Should create metric successfully")
        fun `should create metric successfully`() = runBlocking {
            // Given
            val name = "Response Time"
            val description = "Average response time"
            val category = "performance"
            val unit = "ms"
            val aggregationType = AggregationType.AVG
            val queryId = "response_time_query"
            val thresholds = listOf(
                MetricThreshold(ThresholdLevel.WARNING, ComparisonOperator.GREATER_THAN, 200.0)
            )
            
            // When
            val metric = insightService.createMetric(
                name = name,
                description = description,
                category = category,
                unit = unit,
                aggregationType = aggregationType,
                queryId = queryId,
                thresholds = thresholds
            )
            
            // Then
            assertNotNull(metric.id)
            assertEquals(name, metric.name)
            assertEquals(description, metric.description)
            assertEquals(category, metric.category)
            assertEquals(unit, metric.unit)
            assertEquals(aggregationType, metric.aggregationType)
            assertEquals(queryId, metric.queryId)
            assertEquals(thresholds, metric.thresholds)
            assertTrue(metric.isActive)
        }
        
        @Test
        @DisplayName("Should create KPI successfully")
        fun `should create KPI successfully`() = runBlocking {
            // Given
            val name = "System Uptime"
            val description = "Overall system availability"
            val targetValue = 99.9
            val currentValue = 99.8
            val unit = "%"
            val category = "reliability"
            val historicalData = listOf(
                KPIDataPoint(System.currentTimeMillis() - 86400000, 99.7, 99.9),
                KPIDataPoint(System.currentTimeMillis() - 43200000, 99.8, 99.9)
            )
            
            // When
            val kpi = insightService.createKPI(
                name = name,
                description = description,
                targetValue = targetValue,
                currentValue = currentValue,
                unit = unit,
                category = category,
                historicalData = historicalData
            )
            
            // Then
            assertNotNull(kpi.id)
            assertEquals(name, kpi.name)
            assertEquals(description, kpi.description)
            assertEquals(targetValue, kpi.targetValue)
            assertEquals(currentValue, kpi.currentValue)
            assertEquals(unit, kpi.unit)
            assertEquals(category, kpi.category)
            assertEquals(historicalData, kpi.historicalData)
            assertEquals(TrendDirection.DOWN, kpi.trend) // currentValue < targetValue
        }
        
        @Test
        @DisplayName("Should filter metrics by category")
        fun `should filter metrics by category`() = runBlocking {
            // Given
            insightService.createMetric("Metric 1", null, "performance", "ms", AggregationType.AVG, "query1")
            insightService.createMetric("Metric 2", null, "business", "count", AggregationType.COUNT, "query2")
            insightService.createMetric("Metric 3", null, "performance", "bytes", AggregationType.SUM, "query3")
            
            // When
            val allMetrics = insightService.getMetrics()
            val performanceMetrics = insightService.getMetrics(category = "performance")
            val businessMetrics = insightService.getMetrics(category = "business")
            
            // Then
            assertEquals(3, allMetrics.size)
            assertEquals(2, performanceMetrics.size)
            assertEquals(1, businessMetrics.size)
            
            assertTrue(performanceMetrics.all { it.category == "performance" })
            assertTrue(businessMetrics.all { it.category == "business" })
        }
        
        @Test
        @DisplayName("Should filter KPIs by category")
        fun `should filter KPIs by category`() = runBlocking {
            // Given
            insightService.createKPI("KPI 1", null, 100.0, 95.0, "%", "performance")
            insightService.createKPI("KPI 2", null, 1000.0, 1200.0, "count", "business")
            insightService.createKPI("KPI 3", null, 99.9, 99.8, "%", "performance")
            
            // When
            val allKPIs = insightService.getKPIs()
            val performanceKPIs = insightService.getKPIs(category = "performance")
            val businessKPIs = insightService.getKPIs(category = "business")
            
            // Then
            assertEquals(3, allKPIs.size)
            assertEquals(2, performanceKPIs.size)
            assertEquals(1, businessKPIs.size)
            
            assertTrue(performanceKPIs.all { it.category == "performance" })
            assertTrue(businessKPIs.all { it.category == "business" })
        }
    }
    
    // ============================================================================
    // Analytics and System Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Analytics and System Information")
    inner class AnalyticsAndSystemTests {
        
        @Test
        @DisplayName("Should get system analytics successfully")
        fun `should get system analytics successfully`() {
            // When
            val analytics = insightService.getSystemAnalytics()
            
            // Then
            assertNotNull(analytics)
            assertTrue(analytics.containsKey("system_metrics"))
            assertTrue(analytics.containsKey("service_health"))
            assertTrue(analytics.containsKey("performance_stats"))
            assertTrue(analytics.containsKey("usage_analytics"))
            assertTrue(analytics.containsKey("active_queries"))
            assertTrue(analytics.containsKey("cache_stats"))
            assertTrue(analytics.containsKey("timestamp"))
            
            val timestamp = analytics["timestamp"] as Long
            assertTrue(timestamp > 0)
        }
        
        @Test
        @DisplayName("Should get usage statistics successfully")
        fun `should get usage statistics successfully`() = runBlocking {
            // Given - Create some test data
            insightService.createQuery("Query 1", null, "SELECT 1", QueryType.SELECT, emptyMap(), "user", emptyList())
            insightService.createQuery("Query 2", null, "SELECT 2", QueryType.AGGREGATE, emptyMap(), "user", emptyList())
            
            val template = insightService.createReportTemplate("Template", null, "Content", emptyList(), listOf(ReportFormat.PDF), "test", "user")
            insightService.createReport("Report", null, template.id, emptyMap(), null, emptyList(), ReportFormat.PDF, "user")
            
            insightService.createDashboard("Dashboard", null, emptyList(), DashboardLayout(), DashboardPermissions("user"), "user")
            
            // When
            val usage = insightService.getUsageStatistics()
            
            // Then
            assertNotNull(usage)
            assertTrue(usage.containsKey("total_queries"))
            assertTrue(usage.containsKey("total_reports"))
            assertTrue(usage.containsKey("total_dashboards"))
            assertTrue(usage.containsKey("active_queries"))
            assertTrue(usage.containsKey("active_reports"))
            
            assertEquals(2, usage["total_queries"])
            assertEquals(1, usage["total_reports"])
            assertEquals(1, usage["total_dashboards"])
        }
        
        @Test
        @DisplayName("Should get performance analytics successfully")
        fun `should get performance analytics successfully`() = runBlocking {
            // Given - Execute some queries to generate performance data
            val query = insightService.createQuery("Test Query", null, "SELECT * FROM users", QueryType.SELECT, emptyMap(), "user", emptyList())
            insightService.executeQuery(query.id, emptyMap(), "test_user")
            
            // When
            val performance = insightService.getPerformanceAnalytics()
            
            // Then
            assertNotNull(performance)
            assertTrue(performance.containsKey("avg_query_execution_time"))
            assertTrue(performance.containsKey("query_success_rate"))
            assertTrue(performance.containsKey("total_executions_last_hour"))
            assertTrue(performance.containsKey("failed_executions_last_hour"))
            assertTrue(performance.containsKey("cache_hit_rate"))
            assertTrue(performance.containsKey("active_connections"))
            
            val successRate = performance["query_success_rate"] as Double
            assertTrue(successRate >= 0.0 && successRate <= 1.0)
        }
    }
    
    // ============================================================================
    // Error Handling Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle non-existent query gracefully")
        fun `should handle non-existent query gracefully`() {
            // When
            val query = insightService.getQuery("non_existent_id")
            
            // Then
            assertNull(query)
        }
        
        @Test
        @DisplayName("Should handle non-existent report gracefully")
        fun `should handle non-existent report gracefully`() {
            // When
            val report = insightService.getReport("non_existent_id")
            
            // Then
            assertNull(report)
        }
        
        @Test
        @DisplayName("Should handle non-existent dashboard gracefully")
        fun `should handle non-existent dashboard gracefully`() {
            // When
            val dashboard = insightService.getDashboard("non_existent_id")
            
            // Then
            assertNull(dashboard)
        }
        
        @Test
        @DisplayName("Should handle invalid query execution gracefully")
        fun `should handle invalid query execution gracefully`() {
            // When & Then
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    insightService.executeQuery("invalid_query_id", emptyMap(), "user")
                }
            }
        }
        
        @Test
        @DisplayName("Should handle invalid dashboard data request gracefully")
        fun `should handle invalid dashboard data request gracefully`() {
            // When & Then
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    insightService.getDashboardData(DashboardDataRequest("invalid_dashboard_id"))
                }
            }
        }
        
        @Test
        @DisplayName("Should handle update of non-existent query gracefully")
        fun `should handle update of non-existent query gracefully`() = runBlocking {
            // When
            val result = insightService.updateQuery("non_existent_id", name = "Updated")
            
            // Then
            assertNull(result)
        }
        
        @Test
        @DisplayName("Should handle deletion of non-existent query gracefully")
        fun `should handle deletion of non-existent query gracefully`() = runBlocking {
            // When
            val result = insightService.deleteQuery("non_existent_id")
            
            // Then
            assertFalse(result)
        }
    }
    
    // ============================================================================
    // Integration and Edge Case Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Integration and Edge Cases")
    inner class IntegrationAndEdgeCaseTests {
        
        @Test
        @DisplayName("Should handle concurrent query executions")
        fun `should handle concurrent query executions`() = runBlocking {
            // Given
            val query = insightService.createQuery("Concurrent Test", null, "SELECT * FROM users", QueryType.SELECT, emptyMap(), "user", emptyList())
            
            // When - Execute multiple queries concurrently
            val results = (1..5).map { i ->
                kotlinx.coroutines.async {
                    insightService.executeQuery(query.id, mapOf("iteration" to i.toString()), "user_$i")
                }
            }.map { it.await() }
            
            // Then
            assertEquals(5, results.size)
            results.forEach { result ->
                assertEquals(query.id, result.queryId)
                assertNotNull(result.executionId)
                assertNotNull(result.data)
            }
        }
        
        @Test
        @DisplayName("Should handle large result sets within limits")
        fun `should handle large result sets within limits`() = runBlocking {
            // Given
            val query = insightService.createQuery("Large Query", null, "SELECT * FROM large_table", QueryType.SELECT, emptyMap(), "user", emptyList())
            
            // When
            val result = insightService.executeQuery(query.id, emptyMap(), "user")
            
            // Then
            assertNotNull(result)
            assertTrue(result.data.size <= testConfiguration.maxResultRows)
        }
        
        @Test