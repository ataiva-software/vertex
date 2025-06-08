package com.ataiva.eden.monitor.service

import com.ataiva.eden.monitor.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Comprehensive test suite for MonitorService
 * Ensures regression validation for all monitoring functionality
 */
class MonitorServiceTest {
    
    private lateinit var monitorService: MonitorService
    
    @BeforeTest
    fun setup() {
        monitorService = MonitorService()
    }
    
    @AfterTest
    fun teardown() {
        monitorService.shutdown()
    }
    
    // System Metrics Tests
    
    @Test
    fun `getSystemMetrics should return valid system metrics`() = runBlocking {
        val metrics = monitorService.getSystemMetrics()
        
        assertNotNull(metrics)
        assertTrue(metrics.timestamp > 0)
        assertTrue(metrics.cpuUsage >= 0.0)
        assertTrue(metrics.memoryUsage >= 0.0)
        assertTrue(metrics.diskUsage >= 0.0)
        assertTrue(metrics.uptime >= 0)
        assertNotNull(metrics.networkIO)
        assertTrue(metrics.networkIO.bytesIn >= 0)
        assertTrue(metrics.networkIO.bytesOut >= 0)
    }
    
    @Test
    fun `getSystemMetrics should store metrics for historical tracking`() = runBlocking {
        // Get metrics multiple times
        repeat(3) {
            monitorService.getSystemMetrics()
        }
        
        // Check that historical data is available
        val cpuHistory = monitorService.getHistoricalMetrics("system.cpu", 1)
        val memoryHistory = monitorService.getHistoricalMetrics("system.memory", 1)
        val diskHistory = monitorService.getHistoricalMetrics("system.disk", 1)
        
        assertTrue(cpuHistory.isNotEmpty())
        assertTrue(memoryHistory.isNotEmpty())
        assertTrue(diskHistory.isNotEmpty())
        
        // Verify data structure
        cpuHistory.forEach { metric ->
            assertTrue(metric.timestamp > 0)
            assertTrue(metric.value >= 0.0)
        }
    }
    
    @Test
    fun `getSystemMetrics should have reasonable metric values`() = runBlocking {
        val metrics = monitorService.getSystemMetrics()
        
        // CPU usage should be between 0 and 100
        assertTrue(metrics.cpuUsage >= 0.0 && metrics.cpuUsage <= 100.0)
        
        // Memory usage should be between 0 and 100
        assertTrue(metrics.memoryUsage >= 0.0 && metrics.memoryUsage <= 100.0)
        
        // Disk usage should be between 0 and 100
        assertTrue(metrics.diskUsage >= 0.0 && metrics.diskUsage <= 100.0)
        
        // Network IO should be positive
        assertTrue(metrics.networkIO.bytesIn > 0)
        assertTrue(metrics.networkIO.bytesOut > 0)
    }
    
    // Service Metrics Tests
    
    @Test
    fun `getServiceMetrics should return health status for all services`() = runBlocking {
        val serviceMetrics = monitorService.getServiceMetrics()
        
        assertNotNull(serviceMetrics)
        assertTrue(serviceMetrics.isNotEmpty())
        
        val expectedServices = setOf("vault", "flow", "task", "monitor", "sync", "insight", "hub", "api-gateway")
        val actualServices = serviceMetrics.map { it.serviceName }.toSet()
        
        assertEquals(expectedServices, actualServices)
        
        serviceMetrics.forEach { service ->
            assertNotNull(service.serviceName)
            assertTrue(service.status in setOf("healthy", "unhealthy"))
            assertTrue(service.responseTime > 0)
            assertTrue(service.uptime >= 0)
            assertTrue(service.timestamp > 0)
            assertTrue(service.errorRate >= 0.0)
            assertTrue(service.requestCount >= 0)
        }
    }
    
    @Test
    fun `getServiceMetrics should store service metrics for tracking`() = runBlocking {
        monitorService.getServiceMetrics()
        
        // Check that service metrics are stored
        val responseTimeHistory = monitorService.getHistoricalMetrics("service.vault.response_time", 1)
        val errorRateHistory = monitorService.getHistoricalMetrics("service.vault.error_rate", 1)
        
        assertTrue(responseTimeHistory.isNotEmpty())
        assertTrue(errorRateHistory.isNotEmpty())
    }
    
    // Alert Management Tests
    
    @Test
    fun `createAlertRule should create valid alert rule`() = runBlocking {
        val request = CreateAlertRequest(
            name = "High CPU Alert",
            description = "Alert when CPU usage exceeds 80%",
            metricName = "system.cpu",
            condition = "greater_than",
            threshold = 80.0,
            severity = "high",
            enabled = true
        )
        
        val alertRule = monitorService.createAlertRule(request)
        
        assertNotNull(alertRule)
        assertNotNull(alertRule.id)
        assertEquals(request.name, alertRule.name)
        assertEquals(request.description, alertRule.description)
        assertEquals(request.metricName, alertRule.metricName)
        assertEquals(request.condition, alertRule.condition)
        assertEquals(request.threshold, alertRule.threshold)
        assertEquals(request.severity, alertRule.severity)
        assertEquals(request.enabled, alertRule.enabled)
        assertTrue(alertRule.createdAt > 0)
        assertTrue(alertRule.updatedAt > 0)
    }
    
    @Test
    fun `getAlertRules should return all created alert rules`() = runBlocking {
        // Create multiple alert rules
        val requests = listOf(
            CreateAlertRequest("CPU Alert", "CPU monitoring", "system.cpu", "greater_than", 80.0, "high"),
            CreateAlertRequest("Memory Alert", "Memory monitoring", "system.memory", "greater_than", 90.0, "critical"),
            CreateAlertRequest("Disk Alert", "Disk monitoring", "system.disk", "greater_than", 85.0, "medium")
        )
        
        val createdRules = requests.map { monitorService.createAlertRule(it) }
        
        val retrievedRules = monitorService.getAlertRules()
        
        assertEquals(createdRules.size, retrievedRules.size)
        
        createdRules.forEach { created ->
            val retrieved = retrievedRules.find { it.id == created.id }
            assertNotNull(retrieved)
            assertEquals(created.name, retrieved.name)
            assertEquals(created.metricName, retrieved.metricName)
        }
    }
    
    @Test
    fun `getActiveAlerts should return currently active alerts`() = runBlocking {
        val activeAlerts = monitorService.getActiveAlerts()
        
        assertNotNull(activeAlerts)
        // Initially should be empty or contain system-generated alerts
        activeAlerts.forEach { alert ->
            assertNotNull(alert.id)
            assertNotNull(alert.ruleId)
            assertNotNull(alert.ruleName)
            assertTrue(alert.severity in setOf("low", "medium", "high", "critical"))
            assertNotNull(alert.message)
            assertTrue(alert.triggeredAt > 0)
        }
    }
    
    @Test
    fun `acknowledgeAlert should acknowledge existing alert`() = runBlocking {
        // This test assumes there might be active alerts from system monitoring
        val activeAlerts = monitorService.getActiveAlerts()
        
        if (activeAlerts.isNotEmpty()) {
            val alertToAcknowledge = activeAlerts.first()
            val acknowledgedBy = "test-user"
            
            val success = monitorService.acknowledgeAlert(alertToAcknowledge.id, acknowledgedBy)
            assertTrue(success)
            
            // Verify the alert is acknowledged
            val updatedAlerts = monitorService.getActiveAlerts()
            val acknowledgedAlert = updatedAlerts.find { it.id == alertToAcknowledge.id }
            
            if (acknowledgedAlert != null) {
                assertTrue(acknowledgedAlert.acknowledged)
                assertEquals(acknowledgedBy, acknowledgedAlert.acknowledgedBy)
                assertNotNull(acknowledgedAlert.acknowledgedAt)
            }
        }
    }
    
    @Test
    fun `acknowledgeAlert should return false for non-existent alert`() = runBlocking {
        val success = monitorService.acknowledgeAlert("non-existent-alert", "test-user")
        assertFalse(success)
    }
    
    // Dashboard Tests
    
    @Test
    fun `getAllDashboards should return default dashboards`() = runBlocking {
        val dashboards = monitorService.getAllDashboards()
        
        assertNotNull(dashboards)
        assertTrue(dashboards.isNotEmpty())
        
        // Should have at least the default dashboards
        val dashboardIds = dashboards.map { it.id }.toSet()
        assertTrue(dashboardIds.contains("system-overview"))
        assertTrue(dashboardIds.contains("services-health"))
        
        dashboards.forEach { dashboard ->
            assertNotNull(dashboard.id)
            assertNotNull(dashboard.name)
            assertNotNull(dashboard.description)
            assertTrue(dashboard.widgetCount >= 0)
            assertTrue(dashboard.lastUpdated > 0)
        }
    }
    
    @Test
    fun `getDashboard should return specific dashboard`() = runBlocking {
        val dashboard = monitorService.getDashboard("system-overview")
        
        assertNotNull(dashboard)
        assertEquals("system-overview", dashboard.id)
        assertEquals("System Overview", dashboard.name)
        assertTrue(dashboard.widgets.isNotEmpty())
        
        dashboard.widgets.forEach { widget ->
            assertNotNull(widget.id)
            assertNotNull(widget.title)
            assertNotNull(widget.type)
            assertNotNull(widget.config)
        }
    }
    
    @Test
    fun `getDashboard should return null for non-existent dashboard`() = runBlocking {
        val dashboard = monitorService.getDashboard("non-existent-dashboard")
        assertNull(dashboard)
    }
    
    // Log Search Tests
    
    @Test
    fun `searchLogs should return log entries`() = runBlocking {
        val logs = monitorService.searchLogs("test query", 10)
        
        assertNotNull(logs)
        assertTrue(logs.size <= 10)
        
        logs.forEach { log ->
            assertTrue(log.timestamp > 0)
            assertTrue(log.level in setOf("DEBUG", "INFO", "WARN", "ERROR"))
            assertNotNull(log.service)
            assertNotNull(log.message)
            assertNotNull(log.correlationId)
        }
    }
    
    @Test
    fun `searchLogs should respect limit parameter`() = runBlocking {
        val limit = 5
        val logs = monitorService.searchLogs("", limit)
        
        assertTrue(logs.size <= limit)
    }
    
    @Test
    fun `searchLogs should include query in results when provided`() = runBlocking {
        val query = "test-query"
        val logs = monitorService.searchLogs(query, 5)
        
        logs.forEach { log ->
            assertTrue(log.message.contains(query))
        }
    }
    
    @Test
    fun `searchLogs should return logs in descending timestamp order`() = runBlocking {
        val logs = monitorService.searchLogs("", 10)
        
        if (logs.size > 1) {
            for (i in 0 until logs.size - 1) {
                assertTrue(logs[i].timestamp >= logs[i + 1].timestamp)
            }
        }
    }
    
    // Historical Metrics Tests
    
    @Test
    fun `getHistoricalMetrics should return empty list for non-existent metric`() = runBlocking {
        val metrics = monitorService.getHistoricalMetrics("non.existent.metric", 1)
        assertTrue(metrics.isEmpty())
    }
    
    @Test
    fun `getHistoricalMetrics should respect time range`() = runBlocking {
        // Generate some metrics first
        monitorService.getSystemMetrics()
        
        val metrics = monitorService.getHistoricalMetrics("system.cpu", 24)
        
        if (metrics.isNotEmpty()) {
            val cutoffTime = System.currentTimeMillis() - (24 * 3600 * 1000)
            metrics.forEach { metric ->
                assertTrue(metric.timestamp >= cutoffTime)
            }
        }
    }
    
    @Test
    fun `getHistoricalMetrics should return sorted data`() = runBlocking {
        // Generate some metrics first
        repeat(3) {
            monitorService.getSystemMetrics()
            Thread.sleep(10) // Small delay to ensure different timestamps
        }
        
        val metrics = monitorService.getHistoricalMetrics("system.cpu", 1)
        
        if (metrics.size > 1) {
            for (i in 0 until metrics.size - 1) {
                assertTrue(metrics[i].timestamp <= metrics[i + 1].timestamp)
            }
        }
    }
    
    // Statistics Tests
    
    @Test
    fun `getMonitoringStats should return valid statistics`() = runBlocking {
        // Generate some activity first
        monitorService.getSystemMetrics()
        monitorService.getServiceMetrics()
        
        val stats = monitorService.getMonitoringStats()
        
        assertNotNull(stats)
        assertTrue(stats.totalMetricsCollected >= 0)
        assertTrue(stats.totalAlertsTriggered >= 0)
        assertTrue(stats.activeAlertsCount >= 0)
        assertTrue(stats.alertRulesCount >= 0)
        assertTrue(stats.dashboardsCount >= 0)
        assertTrue(stats.uptimeSeconds >= 0)
        assertTrue(stats.lastMetricCollectionTime > 0)
    }
    
    @Test
    fun `getMonitoringStats should reflect created alert rules`() = runBlocking {
        val initialStats = monitorService.getMonitoringStats()
        
        // Create an alert rule
        monitorService.createAlertRule(CreateAlertRequest(
            name = "Test Alert",
            description = "Test alert rule",
            metricName = "system.cpu",
            condition = "greater_than",
            threshold = 90.0,
            severity = "high"
        ))
        
        val updatedStats = monitorService.getMonitoringStats()
        
        assertEquals(initialStats.alertRulesCount + 1, updatedStats.alertRulesCount)
    }
    
    @Test
    fun `getMonitoringStats should show dashboard count`() = runBlocking {
        val stats = monitorService.getMonitoringStats()
        
        // Should have at least the default dashboards
        assertTrue(stats.dashboardsCount >= 2)
    }
    
    // Integration Tests
    
    @Test
    fun `system should handle concurrent metric collection`() = runBlocking {
        val jobs = (1..5).map {
            kotlinx.coroutines.async {
                monitorService.getSystemMetrics()
            }
        }
        
        val results = jobs.map { it.await() }
        
        assertEquals(5, results.size)
        results.forEach { metrics ->
            assertNotNull(metrics)
            assertTrue(metrics.timestamp > 0)
        }
    }
    
    @Test
    fun `system should handle multiple alert rule creation`() = runBlocking {
        val requests = (1..3).map { i ->
            CreateAlertRequest(
                name = "Test Alert $i",
                description = "Test alert rule $i",
                metricName = "system.cpu",
                condition = "greater_than",
                threshold = 80.0 + i,
                severity = "medium"
            )
        }
        
        val createdRules = requests.map { monitorService.createAlertRule(it) }
        val retrievedRules = monitorService.getAlertRules()
        
        assertTrue(retrievedRules.size >= createdRules.size)
        
        createdRules.forEach { created ->
            val found = retrievedRules.any { it.id == created.id }
            assertTrue(found)
        }
    }
    
    @Test
    fun `system should maintain data consistency across operations`() = runBlocking {
        // Perform various operations
        val initialStats = monitorService.getMonitoringStats()
        
        monitorService.getSystemMetrics()
        monitorService.getServiceMetrics()
        
        val alertRule = monitorService.createAlertRule(CreateAlertRequest(
            name = "Consistency Test Alert",
            description = "Testing data consistency",
            metricName = "system.memory",
            condition = "greater_than",
            threshold = 95.0,
            severity = "critical"
        ))
        
        val finalStats = monitorService.getMonitoringStats()
        
        // Verify statistics are updated correctly
        assertTrue(finalStats.totalMetricsCollected >= initialStats.totalMetricsCollected)
        assertEquals(initialStats.alertRulesCount + 1, finalStats.alertRulesCount)
        
        // Verify alert rule exists
        val retrievedRules = monitorService.getAlertRules()
        val foundRule = retrievedRules.find { it.id == alertRule.id }
        assertNotNull(foundRule)
        assertEquals(alertRule.name, foundRule.name)
    }
}