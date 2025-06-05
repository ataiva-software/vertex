/**
 * This file contains the collectMetrics method implementation for the InsightService class.
 * Please add this method to the InsightService class to complete the implementation.
 */

/**
 * Collect metrics and update KPIs
 * 
 * This method is called periodically to collect system metrics and update KPIs.
 * It should be added to the InsightService class.
 */
private suspend fun collectMetrics() {
    try {
        // Collect system metrics
        val systemMetrics = analyticsEngine.getSystemAnalytics()
        
        // Update KPIs based on current metrics
        val kpis = kpiRepository.findAll()
        
        kpis.forEach { kpi ->
            try {
                // Extract relevant metric value based on KPI category
                val newValue = when (kpi.category) {
                    "performance" -> {
                        val performanceStats = systemMetrics["performance_stats"] as? Map<*, *>
                        when (kpi.name) {
                            "API Response Time" -> (performanceStats?.get("avg_query_time") as? Number)?.toDouble() ?: kpi.currentValue
                            "Error Rate" -> (performanceStats?.get("error_rate") as? Number)?.toDouble() ?: kpi.currentValue
                            "Throughput" -> (performanceStats?.get("throughput") as? Number)?.toDouble() ?: kpi.currentValue
                            else -> kpi.currentValue
                        }
                    }
                    "reliability" -> {
                        val systemStats = systemMetrics["system_metrics"] as? Map<*, *>
                        when (kpi.name) {
                            "System Uptime" -> (systemStats?.get("uptime_percentage") as? Number)?.toDouble() ?: kpi.currentValue
                            "Availability" -> (systemStats?.get("availability") as? Number)?.toDouble() ?: kpi.currentValue
                            else -> kpi.currentValue
                        }
                    }
                    "usage" -> {
                        val usageStats = systemMetrics["usage_analytics"] as? Map<*, *>
                        when (kpi.name) {
                            "Active Users" -> (usageStats?.get("active_users") as? Number)?.toDouble() ?: kpi.currentValue
                            "Query Volume" -> (usageStats?.get("query_volume") as? Number)?.toDouble() ?: kpi.currentValue
                            else -> kpi.currentValue
                        }
                    }
                    "resource" -> {
                        val resourceStats = systemMetrics["system_metrics"] as? Map<*, *>
                        when (kpi.name) {
                            "CPU Usage" -> (resourceStats?.get("cpu_usage") as? Number)?.toDouble() ?: kpi.currentValue
                            "Memory Usage" -> (resourceStats?.get("memory_usage") as? Number)?.toDouble() ?: kpi.currentValue
                            "Disk Usage" -> (resourceStats?.get("disk_usage") as? Number)?.toDouble() ?: kpi.currentValue
                            else -> kpi.currentValue
                        }
                    }
                    else -> kpi.currentValue
                }
                
                // Calculate trend direction
                val trend = when {
                    newValue > kpi.currentValue -> TrendDirection.UP
                    newValue < kpi.currentValue -> TrendDirection.DOWN
                    else -> TrendDirection.STABLE
                }
                
                // Create historical data point
                val dataPoint = KPIDataPoint(
                    timestamp = System.currentTimeMillis(),
                    value = newValue,
                    target = kpi.targetValue
                )
                
                // Update KPI with new value and trend
                val updatedKPI = kpi.copy(
                    currentValue = newValue,
                    trend = trend,
                    lastUpdated = System.currentTimeMillis(),
                    historicalData = (kpi.historicalData + dataPoint).takeLast(100) // Keep last 100 data points
                )
                
                // Save updated KPI
                kpiRepository.update(updatedKPI)
                
                // Store metric value in the database
                if (kpi.category == "performance" || kpi.category == "resource") {
                    val metricValue = MetricValue(
                        id = generateId("metric_value"),
                        metricId = kpi.id,
                        value = newValue,
                        timestamp = System.currentTimeMillis(),
                        dimensions = mapOf("source" to "system_metrics"),
                        metadata = emptyMap()
                    )
                    metricValueRepository.save(metricValue)
                }
            } catch (e: Exception) {
                // Log error but continue processing other KPIs
                println("Error updating KPI ${kpi.id}: ${e.message}")
            }
        }
    } catch (e: Exception) {
        // Log error but don't crash the background task
        println("Error collecting metrics: ${e.message}")
    }
}