package com.ataiva.eden.integration.insight

import com.ataiva.eden.insight.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertNotNull

/**
 * Integration tests for the Insight Service.
 * Tests end-to-end functionality including HTTP API endpoints and real service integration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Insight Service Integration Tests")
class InsightServiceIntegrationTest {
    
    private lateinit var client: HttpClient
    private val baseUrl = "http://localhost:8080"
    
    @BeforeAll
    fun setUp() {
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
    
    @AfterAll
    fun tearDown() {
        client.close()
    }
    
    // ============================================================================
    // Service Health and Status Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Service Health and Status")
    inner class ServiceHealthTests {
        
        @Test
        @DisplayName("Should return service information")
        fun `should return service information`() = runBlocking {
            // When
            val response = client.get("$baseUrl/")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val serviceInfo = response.body<ServiceInfo>()
            assertEquals("Eden Insight Service", serviceInfo.name)
            assertEquals("1.0.0", serviceInfo.version)
            assertEquals("running", serviceInfo.status)
        }
        
        @Test
        @DisplayName("Should return healthy status")
        fun `should return healthy status`() = runBlocking {
            // When
            val response = client.get("$baseUrl/health")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val healthCheck = response.body<HealthCheck>()
            assertEquals("healthy", healthCheck.status)
            assertEquals("insight", healthCheck.service)
            assertTrue(healthCheck.uptime >= 0)
        }
        
        @Test
        @DisplayName("Should return readiness status")
        fun `should return readiness status`() = runBlocking {
            // When
            val response = client.get("$baseUrl/ready")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val readiness = response.body<Map<String, Any>>()
            assertEquals("ready", readiness["status"])
            assertNotNull(readiness["checks"])
        }
        
        @Test
        @DisplayName("Should return comprehensive metrics")
        fun `should return comprehensive metrics`() = runBlocking {
            // When
            val response = client.get("$baseUrl/metrics")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val metrics = response.body<Map<String, Any>>()
            assertEquals("insight", metrics["service"])
            assertEquals("1.0.0", metrics["version"])
            assertNotNull(metrics["uptime_ms"])
            assertNotNull(metrics["memory_usage"])
            assertNotNull(metrics["system_analytics"])
        }
        
        @Test
        @DisplayName("Should return detailed status information")
        fun `should return detailed status information`() = runBlocking {
            // When
            val response = client.get("$baseUrl/status")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val status = response.body<Map<String, Any>>()
            assertEquals("Eden Insight Service", status["service"])
            assertEquals("operational", status["status"])
            assertNotNull(status["features"])
            assertNotNull(status["statistics"])
            assertNotNull(status["performance"])
        }
        
        @Test
        @DisplayName("Should return API documentation")
        fun `should return API documentation`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/docs")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val docs = response.body<Map<String, Any>>()
            assertEquals("Eden Insight Service", docs["service"])
            assertEquals("/api/v1", docs["base_url"])
            assertNotNull(docs["endpoints"])
        }
    }
    
    // ============================================================================
    // Query Management Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Query Management API")
    inner class QueryManagementIntegrationTests {
        
        @Test
        @DisplayName("Should create and retrieve query via API")
        fun `should create and retrieve query via API`() = runBlocking {
            // Given
            val createRequest = CreateQueryRequest(
                name = "Integration Test Query",
                description = "Test query for integration testing",
                queryText = "SELECT * FROM users WHERE active = true",
                queryType = QueryType.SELECT,
                createdBy = "integration_test",
                tags = listOf("integration", "test")
            )
            
            // When - Create query
            val createResponse = client.post("$baseUrl/api/v1/queries") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            
            // Then - Verify creation
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val createResult = createResponse.body<ApiResponse<AnalyticsQuery>>()
            assertTrue(createResult.success)
            assertNotNull(createResult.data)
            
            val createdQuery = createResult.data!!
            assertEquals(createRequest.name, createdQuery.name)
            assertEquals(createRequest.queryText, createdQuery.queryText)
            
            // When - Retrieve query
            val getResponse = client.get("$baseUrl/api/v1/queries/${createdQuery.id}")
            
            // Then - Verify retrieval
            assertEquals(HttpStatusCode.OK, getResponse.status)
            val getResult = getResponse.body<ApiResponse<AnalyticsQuery>>()
            assertTrue(getResult.success)
            assertEquals(createdQuery.id, getResult.data!!.id)
        }
        
        @Test
        @DisplayName("Should execute query via API")
        fun `should execute query via API`() = runBlocking {
            // Given - Create a query first
            val createRequest = CreateQueryRequest(
                name = "Execution Test Query",
                queryText = "SELECT COUNT(*) FROM users",
                queryType = QueryType.AGGREGATE,
                createdBy = "integration_test"
            )
            
            val createResponse = client.post("$baseUrl/api/v1/queries") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            val createdQuery = createResponse.body<ApiResponse<AnalyticsQuery>>().data!!
            
            // When - Execute query
            val executeRequest = ExecuteQueryRequest(
                parameters = mapOf("limit" to "100"),
                executedBy = "integration_test"
            )
            
            val executeResponse = client.post("$baseUrl/api/v1/queries/${createdQuery.id}/execute") {
                contentType(ContentType.Application.Json)
                setBody(executeRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, executeResponse.status)
            val executeResult = executeResponse.body<ApiResponse<AnalyticsResult>>()
            assertTrue(executeResult.success)
            assertNotNull(executeResult.data)
            
            val result = executeResult.data!!
            assertEquals(createdQuery.id, result.queryId)
            assertNotNull(result.executionId)
            assertNotNull(result.data)
            assertNotNull(result.metadata)
        }
        
        @Test
        @DisplayName("Should execute raw query via API")
        fun `should execute raw query via API`() = runBlocking {
            // Given
            val queryRequest = QueryRequest(
                queryText = "SELECT 'Hello World' as message",
                parameters = emptyMap(),
                limit = 10
            )
            
            // When
            val response = client.post("$baseUrl/api/v1/queries/execute") {
                contentType(ContentType.Application.Json)
                setBody(queryRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<QueryResponse>()
            assertTrue(result.success)
            assertNotNull(result.data)
            assertNotNull(result.metadata)
            assertNotNull(result.executionId)
        }
        
        @Test
        @DisplayName("Should update query via API")
        fun `should update query via API`() = runBlocking {
            // Given - Create a query first
            val createRequest = CreateQueryRequest(
                name = "Original Query",
                queryText = "SELECT 1",
                queryType = QueryType.SELECT,
                createdBy = "integration_test"
            )
            
            val createResponse = client.post("$baseUrl/api/v1/queries") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            val createdQuery = createResponse.body<ApiResponse<AnalyticsQuery>>().data!!
            
            // When - Update query
            val updateRequest = UpdateQueryRequest(
                name = "Updated Query",
                queryText = "SELECT 2",
                tags = listOf("updated")
            )
            
            val updateResponse = client.put("$baseUrl/api/v1/queries/${createdQuery.id}") {
                contentType(ContentType.Application.Json)
                setBody(updateRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, updateResponse.status)
            val updateResult = updateResponse.body<ApiResponse<AnalyticsQuery>>()
            assertTrue(updateResult.success)
            
            val updatedQuery = updateResult.data!!
            assertEquals("Updated Query", updatedQuery.name)
            assertEquals("SELECT 2", updatedQuery.queryText)
            assertEquals(listOf("updated"), updatedQuery.tags)
        }
        
        @Test
        @DisplayName("Should list queries with filtering")
        fun `should list queries with filtering`() = runBlocking {
            // Given - Create multiple queries
            val user1Queries = listOf("Query 1", "Query 2")
            val user2Queries = listOf("Query 3")
            
            user1Queries.forEach { name ->
                client.post("$baseUrl/api/v1/queries") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateQueryRequest(name, null, "SELECT 1", QueryType.SELECT, emptyMap(), "user1", listOf("tag1")))
                }
            }
            
            user2Queries.forEach { name ->
                client.post("$baseUrl/api/v1/queries") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateQueryRequest(name, null, "SELECT 2", QueryType.AGGREGATE, emptyMap(), "user2", listOf("tag2")))
                }
            }
            
            // When - Get all queries
            val allResponse = client.get("$baseUrl/api/v1/queries")
            val allResult = allResponse.body<ApiResponse<List<AnalyticsQuery>>>()
            
            // When - Get filtered queries
            val user1Response = client.get("$baseUrl/api/v1/queries?createdBy=user1")
            val user1Result = user1Response.body<ApiResponse<List<AnalyticsQuery>>>()
            
            val selectResponse = client.get("$baseUrl/api/v1/queries?queryType=SELECT")
            val selectResult = selectResponse.body<ApiResponse<List<AnalyticsQuery>>>()
            
            // Then
            assertTrue(allResult.data!!.size >= 3)
            assertEquals(2, user1Result.data!!.size)
            assertTrue(user1Result.data!!.all { it.createdBy == "user1" })
            assertTrue(selectResult.data!!.all { it.queryType == QueryType.SELECT })
        }
    }
    
    // ============================================================================
    // Report Management Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Report Management API")
    inner class ReportManagementIntegrationTests {
        
        @Test
        @DisplayName("Should create report template and report via API")
        fun `should create report template and report via API`() = runBlocking {
            // Given - Create template first
            val templateRequest = CreateReportTemplateRequest(
                name = "Integration Test Template",
                description = "Template for integration testing",
                templateContent = "# Report\nGenerated: {{timestamp}}\nData: {{data}}",
                category = "integration",
                createdBy = "integration_test"
            )
            
            val templateResponse = client.post("$baseUrl/api/v1/report-templates") {
                contentType(ContentType.Application.Json)
                setBody(templateRequest)
            }
            
            assertEquals(HttpStatusCode.Created, templateResponse.status)
            val templateResult = templateResponse.body<ApiResponse<ReportTemplate>>()
            val template = templateResult.data!!
            
            // When - Create report
            val reportRequest = CreateReportRequest(
                name = "Integration Test Report",
                description = "Report for integration testing",
                templateId = template.id,
                parameters = mapOf("data" to "test_data"),
                format = ReportFormat.PDF,
                createdBy = "integration_test"
            )
            
            val reportResponse = client.post("$baseUrl/api/v1/reports") {
                contentType(ContentType.Application.Json)
                setBody(reportRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Created, reportResponse.status)
            val reportResult = reportResponse.body<ApiResponse<Report>>()
            assertTrue(reportResult.success)
            
            val report = reportResult.data!!
            assertEquals(reportRequest.name, report.name)
            assertEquals(template.id, report.templateId)
            assertEquals(reportRequest.parameters, report.parameters)
        }
        
        @Test
        @DisplayName("Should generate report via API")
        fun `should generate report via API`() = runBlocking {
            // Given - Create template and report
            val templateRequest = CreateReportTemplateRequest(
                name = "Generation Test Template",
                templateContent = "# Test Report\n{{timestamp}}",
                category = "test",
                createdBy = "integration_test"
            )
            
            val templateResponse = client.post("$baseUrl/api/v1/report-templates") {
                contentType(ContentType.Application.Json)
                setBody(templateRequest)
            }
            val template = templateResponse.body<ApiResponse<ReportTemplate>>().data!!
            
            val reportRequest = CreateReportRequest(
                name = "Generation Test Report",
                templateId = template.id,
                createdBy = "integration_test"
            )
            
            val reportResponse = client.post("$baseUrl/api/v1/reports") {
                contentType(ContentType.Application.Json)
                setBody(reportRequest)
            }
            val report = reportResponse.body<ApiResponse<Report>>().data!!
            
            // When - Generate report asynchronously
            val generateRequest = ReportGenerationRequest(
                reportId = report.id,
                async = true
            )
            
            val generateResponse = client.post("$baseUrl/api/v1/reports/${report.id}/generate") {
                contentType(ContentType.Application.Json)
                setBody(generateRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Accepted, generateResponse.status)
            val generateResult = generateResponse.body<ReportGenerationResponse>()
            assertTrue(generateResult.success)
            assertNotNull(generateResult.executionId)
            assertEquals("Report generation started", generateResult.message)
        }
        
        @Test
        @DisplayName("Should list report templates with filtering")
        fun `should list report templates with filtering`() = runBlocking {
            // Given - Create templates in different categories
            val categories = listOf("category1", "category2", "category1")
            categories.forEachIndexed { index, category ->
                val request = CreateReportTemplateRequest(
                    name = "Template $index",
                    templateContent = "Content $index",
                    category = category,
                    createdBy = "integration_test"
                )
                
                client.post("$baseUrl/api/v1/report-templates") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }
            
            // When - Get all templates
            val allResponse = client.get("$baseUrl/api/v1/report-templates")
            val allResult = allResponse.body<ApiResponse<List<ReportTemplate>>>()
            
            // When - Get filtered templates
            val category1Response = client.get("$baseUrl/api/v1/report-templates?category=category1")
            val category1Result = category1Response.body<ApiResponse<List<ReportTemplate>>>()
            
            // Then
            assertTrue(allResult.data!!.size >= 3)
            assertEquals(2, category1Result.data!!.size)
            assertTrue(category1Result.data!!.all { it.category == "category1" })
        }
    }
    
    // ============================================================================
    // Dashboard Management Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Dashboard Management API")
    inner class DashboardManagementIntegrationTests {
        
        @Test
        @DisplayName("Should create and retrieve dashboard via API")
        fun `should create and retrieve dashboard via API`() = runBlocking {
            // Given
            val widgets = listOf(
                DashboardWidget(
                    id = "widget1",
                    type = WidgetType.CHART,
                    title = "Test Chart",
                    configuration = WidgetConfiguration(chartType = ChartType.LINE),
                    position = WidgetPosition(0, 0, 6, 4)
                ),
                DashboardWidget(
                    id = "widget2",
                    type = WidgetType.METRIC,
                    title = "Test Metric",
                    configuration = WidgetConfiguration(),
                    position = WidgetPosition(6, 0, 6, 4)
                )
            )
            
            val createRequest = CreateDashboardRequest(
                name = "Integration Test Dashboard",
                description = "Dashboard for integration testing",
                widgets = widgets,
                permissions = DashboardPermissions(owner = "integration_test"),
                createdBy = "integration_test",
                tags = listOf("integration", "test")
            )
            
            // When - Create dashboard
            val createResponse = client.post("$baseUrl/api/v1/dashboards") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            
            // Then - Verify creation
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val createResult = createResponse.body<ApiResponse<Dashboard>>()
            assertTrue(createResult.success)
            
            val dashboard = createResult.data!!
            assertEquals(createRequest.name, dashboard.name)
            assertEquals(2, dashboard.widgets.size)
            
            // When - Get dashboard data
            val dataResponse = client.get("$baseUrl/api/v1/dashboards/${dashboard.id}/data")
            
            // Then - Verify data retrieval
            assertEquals(HttpStatusCode.OK, dataResponse.status)
            val dataResult = dataResponse.body<ApiResponse<DashboardDataResponse>>()
            assertTrue(dataResult.success)
            
            val dashboardData = dataResult.data!!
            assertEquals(dashboard.id, dashboardData.dashboardId)
            assertEquals(2, dashboardData.widgets.size)
        }
        
        @Test
        @DisplayName("Should update dashboard via API")
        fun `should update dashboard via API`() = runBlocking {
            // Given - Create dashboard first
            val createRequest = CreateDashboardRequest(
                name = "Original Dashboard",
                widgets = emptyList(),
                permissions = DashboardPermissions(owner = "integration_test"),
                createdBy = "integration_test"
            )
            
            val createResponse = client.post("$baseUrl/api/v1/dashboards") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            val dashboard = createResponse.body<ApiResponse<Dashboard>>().data!!
            
            // When - Update dashboard
            val newWidgets = listOf(
                DashboardWidget("w1", WidgetType.GAUGE, "Gauge", WidgetConfiguration(), WidgetPosition(0, 0, 3, 3))
            )
            
            val updateRequest = UpdateDashboardRequest(
                name = "Updated Dashboard",
                widgets = newWidgets,
                tags = listOf("updated")
            )
            
            val updateResponse = client.put("$baseUrl/api/v1/dashboards/${dashboard.id}") {
                contentType(ContentType.Application.Json)
                setBody(updateRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, updateResponse.status)
            val updateResult = updateResponse.body<ApiResponse<Dashboard>>()
            assertTrue(updateResult.success)
            
            val updatedDashboard = updateResult.data!!
            assertEquals("Updated Dashboard", updatedDashboard.name)
            assertEquals(1, updatedDashboard.widgets.size)
            assertEquals(listOf("updated"), updatedDashboard.tags)
        }
    }
    
    // ============================================================================
    // Analytics Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Analytics API")
    inner class AnalyticsIntegrationTests {
        
        @Test
        @DisplayName("Should get system analytics overview")
        fun `should get system analytics overview`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/analytics/overview")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<Map<String, Any>>>()
            assertTrue(result.success)
            
            val analytics = result.data!!
            assertTrue(analytics.containsKey("system_metrics"))
            assertTrue(analytics.containsKey("service_health"))
            assertTrue(analytics.containsKey("performance_stats"))
        }
        
        @Test
        @DisplayName("Should get usage statistics")
        fun `should get usage statistics`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/analytics/usage")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<Map<String, Any>>>()
            assertTrue(result.success)
            
            val usage = result.data!!
            assertTrue(usage.containsKey("total_queries"))
            assertTrue(usage.containsKey("total_reports"))
            assertTrue(usage.containsKey("total_dashboards"))
        }
        
        @Test
        @DisplayName("Should get performance analytics")
        fun `should get performance analytics`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/analytics/performance")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<Map<String, Any>>>()
            assertTrue(result.success)
            
            val performance = result.data!!
            assertTrue(performance.containsKey("avg_query_execution_time"))
            assertTrue(performance.containsKey("query_success_rate"))
        }
        
        @Test
        @DisplayName("Should analyze performance trends")
        fun `should analyze performance trends`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/analytics/trends")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<Map<String, Any>>>()
            assertTrue(result.success)
            assertNotNull(result.data)
            
            val trends = result.data!!
            assertTrue(trends.containsKey("response_time_trend"))
            assertTrue(trends.containsKey("error_rate_trend"))
            assertTrue(trends.containsKey("throughput_trend"))
        }
        
        @Test
        @DisplayName("Should detect anomalies")
        fun `should detect anomalies`() = runBlocking {
            // Given
            // First create a metric
            val createMetricResponse = client.post("$baseUrl/api/v1/metrics") {
                contentType(ContentType.Application.Json)
                setBody(CreateMetricRequest(
                    name = "Test Metric",
                    description = "Test metric for anomaly detection",
                    category = "test",
                    unit = "count",
                    aggregationType = AggregationType.COUNT,
                    queryId = "test_query"
                ))
            }
            
            val metric = createMetricResponse.body<ApiResponse<Metric>>().data!!
            
            // When
            val response = client.post("$baseUrl/api/v1/analytics/anomalies") {
                contentType(ContentType.Application.Json)
                setBody(AnomalyDetectionRequest(
                    metricIds = listOf(metric.id)
                ))
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<List<Map<String, Any>>>>()
            assertTrue(result.success)
            assertNotNull(result.data)
        }
        
        @Test
        @DisplayName("Should generate insights")
        fun `should generate insights`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/analytics/insights")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<List<Map<String, Any>>>>()
            assertTrue(result.success)
            assertNotNull(result.data)
        }
        
        @Test
        @DisplayName("Should predict resource usage")
        fun `should predict resource usage`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/analytics/predict/resources?horizonHours=24")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<Map<String, Any>>>()
            assertTrue(result.success)
            assertNotNull(result.data)
            
            val prediction = result.data!!
            assertTrue(prediction.containsKey("horizon_hours"))
            assertTrue(prediction.containsKey("cpu_prediction"))
            assertTrue(prediction.containsKey("memory_prediction"))
        }
        
        @Test
        @DisplayName("Should analyze metric trend")
        fun `should analyze metric trend`() = runBlocking {
            // Given
            // First create a metric
            val createMetricResponse = client.post("$baseUrl/api/v1/metrics") {
                contentType(ContentType.Application.Json)
                setBody(CreateMetricRequest(
                    name = "CPU Usage",
                    description = "System CPU usage",
                    category = "system",
                    unit = "%",
                    aggregationType = AggregationType.AVG,
                    queryId = "cpu_query"
                ))
            }
            
            val metric = createMetricResponse.body<ApiResponse<Metric>>().data!!
            
            // When
            val response = client.get("$baseUrl/api/v1/analytics/metrics/${metric.id}/trend")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<ApiResponse<Map<String, Any>>>()
            assertTrue(result.success)
            assertNotNull(result.data)
            
            val trend = result.data!!
            assertTrue(trend.containsKey("metric"))
            assertTrue(trend.containsKey("trend"))
            assertTrue(trend.containsKey("data"))
        }
    }
    
    // ============================================================================
    // Metrics and KPI Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Metrics and KPI API")
    inner class MetricsAndKPIIntegrationTests {
        
        @Test
        @DisplayName("Should create and retrieve metrics via API")
        fun `should create and retrieve metrics via API`() = runBlocking {
            // Given
            val createRequest = CreateMetricRequest(
                name = "Integration Test Metric",
                description = "Metric for integration testing",
                category = "integration",
                unit = "ms",
                aggregationType = AggregationType.AVG,
                queryId = "test_query",
                thresholds = listOf(
                    MetricThreshold(ThresholdLevel.WARNING, ComparisonOperator.GREATER_THAN, 100.0)
                )
            )
            
            // When - Create metric
            val createResponse = client.post("$baseUrl/api/v1/metrics") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val createResult = createResponse.body<ApiResponse<Metric>>()
            assertTrue(createResult.success)
            
            val metric = createResult.data!!
            assertEquals(createRequest.name, metric.name)
            assertEquals(createRequest.category, metric.category)
            assertEquals(createRequest.aggregationType, metric.aggregationType)
            
            // When - Get all metrics
            val listResponse = client.get("$baseUrl/api/v1/metrics")
            
            // Then
            assertEquals(HttpStatusCode.OK, listResponse.status)
            val listResult = listResponse.body<ApiResponse<List<Metric>>>()
            assertTrue(listResult.success)
            assertTrue(listResult.data!!.any { it.id == metric.id })
        }
        
        @Test
        @DisplayName("Should create and retrieve KPIs via API")
        fun `should create and retrieve KPIs via API`() = runBlocking {
            // Given
            val createRequest = CreateKPIRequest(
                name = "Integration Test KPI",
                description = "KPI for integration testing",
                targetValue = 99.9,
                currentValue = 99.5,
                unit = "%",
                category = "integration"
            )
            
            // When - Create KPI
            val createResponse = client.post("$baseUrl/api/v1/kpis") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val createResult = createResponse.body<ApiResponse<KPI>>()
            assertTrue(createResult.success)
            
            val kpi = createResult.data!!
            assertEquals(createRequest.name, kpi.name)
            assertEquals(createRequest.targetValue, kpi.targetValue)
            assertEquals(createRequest.currentValue, kpi.currentValue)
            
            // When - Get all KPIs
            val listResponse = client.get("$baseUrl/api/v1/kpis")
            
            // Then
            assertEquals(HttpStatusCode.OK, listResponse.status)
            val listResult = listResponse.body<ApiResponse<List<KPI>>>()
            assertTrue(listResult.success)
            assertTrue(listResult.data!!.any { it.id == kpi.id })
        }
    }
    
    // ============================================================================
    // Error Handling Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingIntegrationTests {
        
        @Test
        @DisplayName("Should handle 404 for non-existent resources")
        fun `should handle 404 for non-existent resources`() = runBlocking {
            // When
            val response = client.get("$baseUrl/api/v1/queries/non_existent_id")
            
            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
            val result = response.body<ApiResponse<AnalyticsQuery>>()
            assertFalse(result.success)
            assertNotNull(result.error)
        }
        
        @Test
        @DisplayName("Should handle 400 for invalid requests")
        fun `should handle 400 for invalid requests`() = runBlocking {
            // Given - Invalid request (missing required fields)
            val invalidRequest = mapOf("name" to "Test")
            
            // When
            val response = client.post("$baseUrl/api/v1/queries") {
                contentType(ContentType.Application.Json)
                setBody(invalidRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
        
        @Test
        @DisplayName("Should handle CORS preflight requests")
        fun `should handle CORS preflight requests`() = runBlocking {
            // When
            val response = client.options("$baseUrl/api/v1/queries") {
                header("Origin", "http://localhost:3000")
                header("Access-Control-Request-Method", "POST")
                header("Access-Control-Request-Headers", "Content-Type")
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers["Access-Control-Allow-Origin"])
            assertNotNull(response.headers["Access-Control-Allow-Methods"])
        }
    }
    
    // ============================================================================
    // Performance Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceIntegrationTests {
        
        @Test
        @DisplayName("Should handle concurrent API requests")
        fun `should handle concurrent API requests`() = runBlocking {
            // Given
            val requests = (1..10).map { i ->
                kotlinx.coroutines.async {
                    client.get("$baseUrl/api/v1/analytics/overview")
                }
            }
            
            // When
            val responses = requests.map { it.await() }
            
            // Then
            responses.forEach { response ->
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
        
        @Test
        @DisplayName("Should respond within acceptable time limits")
        fun `should respond within acceptable time limits`() = runBlocking {
            // When
            val startTime = System.currentTimeMillis()
            val response = client.get("$baseUrl/api/v1/analytics/overview")
            val endTime = System.currentTimeMillis()
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val responseTime = endTime - startTime
            assertTrue(responseTime < 5000, "Response time should be under 5 seconds, was ${responseTime}ms")
        }
    }
}

// ============================================================================
// Helper Data Classes for API Responses
// ============================================================================

@kotlinx.serialization.Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@kotlinx.serialization.Serializable
data class CreateQueryRequest(
    val name: String,
    val description: String? = null,
    val queryText: String,
    val queryType: QueryType,
    val parameters: Map<String, String> = emptyMap(),
    val createdBy: String,
    val tags: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class UpdateQueryRequest(
    val name: String? = null,
    val description: String? = null,
    val queryText: String? = null,
    val parameters: Map<String, String>? = null,
    val tags: List<String>? = null,
    val isActive: Boolean? = null
)

@kotlinx.serialization.Serializable
data class ExecuteQueryRequest(
    val parameters: Map<String, String> = emptyMap(),
    val executedBy: String = "system"
)

@kotlinx.serialization.Serializable
data class QueryRequest(
    val queryText: String,
    val parameters: Map<String, String> = emptyMap(),
    val limit: Int = 100
)

@kotlinx.serialization.Serializable
data class QueryResponse(
    val success: Boolean,
    val data: List<Map<String, Any?>>,
    val metadata: Map<String, Any?>,
    val executionId: String,
    val executionTime: Long
)

@kotlinx.serialization.Serializable
data class CreateReportTemplateRequest(
    val name: String,
    val description: String? = null,
    val templateContent: String,
    val category: String,
    val createdBy: String,
    val tags: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class CreateReportRequest(
    val name: String,
    val description: String? = null,
    val templateId: String,
    val parameters: Map<String, String> = emptyMap(),
    val format: ReportFormat = ReportFormat.PDF,
    val createdBy: String,
    val tags: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class ReportGenerationRequest(
    val reportId: String,
    val parameters: Map<String, String> = emptyMap(),
    val async: Boolean = false
)

@kotlinx.serialization.Serializable
data class ReportGenerationResponse(
    val success: Boolean,
    val executionId: String? = null,
    val message: String? = null,
    val reportUrl: String? = null
)

@kotlinx.serialization.Serializable
data class CreateMetricRequest(
    val name: String,
    val description: String? = null,
    val category: String,
    val unit: String,
    val aggregationType: AggregationType,
    val queryId: String,
    val thresholds: List<MetricThreshold> = emptyList()
)

@kotlinx.serialization.Serializable
data class CreateKPIRequest(
    val name: String,
    val description: String? = null,
    val targetValue: Double,
    val currentValue: Double,
    val unit: String,
    val category: String,
    val tags: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class AnomalyDetectionRequest(
    val metricIds: List<String>,
    val timeRangeHours: Int = 24,
    val sensitivity: Double = 0.8
)

@kotlinx.serialization.Serializable
data class ServiceInfo(
    val name: String,
    val version: String,
    val status: String,
    val uptime: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@kotlinx.serialization.Serializable
data class HealthCheck(
    val status: String,
    val service: String,
    val uptime: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val checks: Map<String, Boolean> = emptyMap()
)