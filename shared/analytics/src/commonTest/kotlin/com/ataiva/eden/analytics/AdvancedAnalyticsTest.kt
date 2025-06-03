package com.ataiva.eden.analytics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Comprehensive tests for the Advanced Analytics Engine
 * Tests ML algorithms, anomaly detection, and predictive analytics
 */
class AdvancedAnalyticsTest {
    
    private val mockMetricsRepository = MockMetricsRepository()
    private val mockDeploymentRepository = MockDeploymentRepository()
    private val mockModelRepository = MockModelRepository()
    
    private val analytics = DefaultAdvancedAnalytics(
        metricsRepository = mockMetricsRepository,
        deploymentRepository = mockDeploymentRepository,
        modelRepository = mockModelRepository
    )
    
    @Test
    fun testPerformanceTrendAnalysis() = runTest {
        // Given
        val timeRange = TimeRange(
            start = System.currentTimeMillis() - 24.hours.inWholeMilliseconds,
            end = System.currentTimeMillis()
        )
        
        // When
        val analysis = analytics.analyzePerformanceTrends(timeRange)
        
        // Then
        assertNotNull(analysis)
        assertEquals(timeRange, analysis.timeRange)
        assertNotNull(analysis.responseTimeTrend)
        assertNotNull(analysis.errorRateTrend)
        assertNotNull(analysis.throughputTrend)
        assertNotNull(analysis.seasonality)
        assertNotNull(analysis.correlations)
        assertTrue(analysis.recommendations.isNotEmpty())
    }
    
    @Test
    fun testAnomalyDetection() = runTest {
        // Given
        val metrics = generateTestMetrics()
        
        // When
        val anomalies = analytics.detectAnomalies(metrics)
        
        // Then
        assertNotNull(anomalies)
        // Should detect at least some anomalies in test data
        assertTrue(anomalies.isNotEmpty())
        
        // Verify anomaly properties
        anomalies.forEach { anomaly ->
            assertNotNull(anomaly.id)
            assertTrue(anomaly.timestamp > 0)
            assertNotNull(anomaly.type)
            assertNotNull(anomaly.severity)
            assertTrue(anomaly.confidence >= 0.0 && anomaly.confidence <= 1.0)
        }
    }
    
    @Test
    fun testResourcePrediction() = runTest {
        // Given
        val horizon = 24.hours
        
        // When
        val prediction = analytics.predictResourceUsage(horizon)
        
        // Then
        assertNotNull(prediction)
        assertEquals(horizon, prediction.horizon)
        assertNotNull(prediction.cpuPrediction)
        assertNotNull(prediction.memoryPrediction)
        assertNotNull(prediction.diskPrediction)
        assertNotNull(prediction.networkPrediction)
        assertTrue(prediction.confidence >= 0.0 && prediction.confidence <= 1.0)
        assertTrue(prediction.recommendations.isNotEmpty())
    }
    
    @Test
    fun testInsightGeneration() = runTest {
        // Given
        val context = AnalysisContext(
            timeRange = TimeRange(
                start = System.currentTimeMillis() - 24.hours.inWholeMilliseconds,
                end = System.currentTimeMillis()
            ),
            services = listOf("vault", "flow", "task"),
            environment = "production"
        )
        
        // When
        val insights = analytics.generateInsights(context)
        
        // Then
        assertNotNull(insights)
        // Should generate insights for production environment
        insights.forEach { insight ->
            assertNotNull(insight.id)
            assertNotNull(insight.category)
            assertNotNull(insight.title)
            assertNotNull(insight.description)
            assertTrue(insight.impact >= 0.0 && insight.impact <= 1.0)
            assertTrue(insight.confidence >= 0.0 && insight.confidence <= 1.0)
            assertTrue(insight.recommendations.isNotEmpty())
        }
    }
    
    @Test
    fun testDeploymentOptimization() = runTest {
        // Given
        val deploymentHistory = generateTestDeploymentHistory()
        
        // When
        val recommendation = analytics.optimizeDeploymentStrategy(deploymentHistory)
        
        // Then
        assertNotNull(recommendation)
        assertNotNull(recommendation.recommendedStrategy)
        assertTrue(recommendation.confidence >= 0.0 && recommendation.confidence <= 1.0)
        assertNotNull(recommendation.reasoning)
        assertTrue(recommendation.alternativeStrategies.isNotEmpty())
        assertTrue(recommendation.riskMitigation.isNotEmpty())
    }
    
    @Test
    fun testModelTraining() = runTest {
        // Given
        val trainingData = TrainingData(
            features = listOf(
                listOf(1.0, 2.0, 3.0),
                listOf(2.0, 3.0, 4.0),
                listOf(3.0, 4.0, 5.0)
            ),
            labels = listOf(0.0, 1.0, 0.0)
        )
        
        // When
        val result = analytics.trainModel(ModelType.ANOMALY_DETECTION, trainingData)
        
        // Then
        assertNotNull(result)
        assertNotNull(result.modelId)
        assertTrue(result.success)
        assertTrue(result.accuracy >= 0.0 && result.accuracy <= 1.0)
        assertNotNull(result.message)
    }
    
    @Test
    fun testModelEvaluation() = runTest {
        // Given
        val modelId = "test-model-123"
        val testData = TestData(
            features = listOf(
                listOf(1.0, 2.0, 3.0),
                listOf(2.0, 3.0, 4.0)
            ),
            labels = listOf(0.0, 1.0)
        )
        
        // When
        val evaluation = analytics.evaluateModel(modelId, testData)
        
        // Then
        assertNotNull(evaluation)
        assertEquals(modelId, evaluation.modelId)
        assertTrue(evaluation.accuracy >= 0.0 && evaluation.accuracy <= 1.0)
        assertTrue(evaluation.precision >= 0.0 && evaluation.precision <= 1.0)
        assertTrue(evaluation.recall >= 0.0 && evaluation.recall <= 1.0)
        assertTrue(evaluation.f1Score >= 0.0 && evaluation.f1Score <= 1.0)
        assertTrue(evaluation.rmse >= 0.0)
        assertTrue(evaluation.mae >= 0.0)
        assertNotNull(evaluation.confusionMatrix)
        assertNotNull(evaluation.rocCurve)
    }
    
    @Test
    fun testRealtimeAnalytics() = runTest {
        // Given - analytics should start emitting real-time data
        
        // When
        val realtimeFlow = analytics.getRealtimeAnalytics()
        
        // Then
        assertNotNull(realtimeFlow)
        // Note: In a real test, we would collect from the flow
        // For now, just verify the flow is not null
    }
    
    // Helper methods for generating test data
    
    private fun generateTestMetrics(): List<MetricPoint> {
        val baseTime = System.currentTimeMillis()
        return (0..100).map { i ->
            MetricPoint(
                timestamp = baseTime + i * 60000, // 1 minute intervals
                value = 50.0 + (i % 10) * 5.0 + if (i == 95) 200.0 else 0.0, // Anomaly at index 95
                responseTime = 100.0 + (i % 5) * 10.0,
                errorRate = if (i % 20 == 0) 5.0 else 1.0, // Periodic error spikes
                throughput = 1000.0 - (i % 3) * 50.0,
                cpuUsage = 60.0 + (i % 7) * 5.0,
                memoryUsage = 70.0 + (i % 4) * 8.0
            )
        }
    }
    
    private fun generateTestDeploymentHistory(): List<DeploymentMetrics> {
        return listOf(
            DeploymentMetrics(
                deploymentId = "deploy-1",
                strategy = "rolling_update",
                duration = 5.minutes.inWholeMilliseconds,
                success = true,
                errorRate = 0.1,
                performanceImpact = 0.05,
                rollbackRequired = false,
                timestamp = System.currentTimeMillis() - 24.hours.inWholeMilliseconds
            ),
            DeploymentMetrics(
                deploymentId = "deploy-2",
                strategy = "blue_green",
                duration = 8.minutes.inWholeMilliseconds,
                success = true,
                errorRate = 0.05,
                performanceImpact = 0.02,
                rollbackRequired = false,
                timestamp = System.currentTimeMillis() - 12.hours.inWholeMilliseconds
            ),
            DeploymentMetrics(
                deploymentId = "deploy-3",
                strategy = "canary",
                duration = 15.minutes.inWholeMilliseconds,
                success = false,
                errorRate = 2.5,
                performanceImpact = 0.8,
                rollbackRequired = true,
                timestamp = System.currentTimeMillis() - 6.hours.inWholeMilliseconds
            )
        )
    }
}

// Mock implementations for testing

class MockMetricsRepository : MetricsRepository {
    override suspend fun getMetrics(timeRange: TimeRange): List<MetricPoint> {
        return generateMockMetrics(timeRange)
    }
    
    override suspend fun getResourceMetrics(timeRange: TimeRange): List<ResourceMetric> {
        return generateMockResourceMetrics(timeRange)
    }
    
    override suspend fun getCurrentMetrics(): CurrentMetrics {
        return CurrentMetrics(
            timestamp = System.currentTimeMillis(),
            responseTime = 120.0,
            errorRate = 0.5,
            throughput = 950.0,
            cpuUsage = 65.0,
            memoryUsage = 72.0,
            activeConnections = 150,
            queueDepth = 25
        )
    }
    
    private fun generateMockMetrics(timeRange: TimeRange): List<MetricPoint> {
        val duration = timeRange.end - timeRange.start
        val intervals = (duration / 60000).toInt() // 1-minute intervals
        
        return (0 until intervals).map { i ->
            MetricPoint(
                timestamp = timeRange.start + i * 60000,
                value = 50.0 + kotlin.math.sin(i * 0.1) * 10.0,
                responseTime = 100.0 + kotlin.math.cos(i * 0.05) * 20.0,
                errorRate = if (i % 30 == 0) 2.0 else 0.5,
                throughput = 1000.0 + kotlin.math.sin(i * 0.2) * 100.0,
                cpuUsage = 60.0 + kotlin.math.sin(i * 0.15) * 15.0,
                memoryUsage = 70.0 + kotlin.math.cos(i * 0.12) * 10.0
            )
        }
    }
    
    private fun generateMockResourceMetrics(timeRange: TimeRange): List<ResourceMetric> {
        val duration = timeRange.end - timeRange.start
        val intervals = (duration / 300000).toInt() // 5-minute intervals
        
        return (0 until intervals).map { i ->
            ResourceMetric(
                timestamp = timeRange.start + i * 300000,
                cpuUsage = 60.0 + kotlin.math.sin(i * 0.1) * 20.0,
                memoryUsage = 70.0 + kotlin.math.cos(i * 0.08) * 15.0,
                diskUsage = 40.0 + kotlin.math.sin(i * 0.05) * 10.0,
                networkUsage = 30.0 + kotlin.math.cos(i * 0.12) * 20.0
            )
        }
    }
}

class MockDeploymentRepository : DeploymentRepository {
    override suspend fun getDeploymentHistory(timeRange: TimeRange): List<DeploymentMetrics> {
        return listOf(
            DeploymentMetrics(
                deploymentId = "mock-deploy-1",
                strategy = "rolling_update",
                duration = 300000, // 5 minutes
                success = true,
                errorRate = 0.1,
                performanceImpact = 0.05,
                rollbackRequired = false,
                timestamp = timeRange.start + 3600000 // 1 hour after start
            ),
            DeploymentMetrics(
                deploymentId = "mock-deploy-2",
                strategy = "blue_green",
                duration = 480000, // 8 minutes
                success = true,
                errorRate = 0.05,
                performanceImpact = 0.02,
                rollbackRequired = false,
                timestamp = timeRange.start + 7200000 // 2 hours after start
            )
        )
    }
}

class MockModelRepository : ModelRepository {
    override suspend fun getModel(modelId: String): MLModel? {
        return MockMLModel(modelId)
    }
    
    override suspend fun saveModel(model: MLModel): Boolean {
        return true
    }
}

class MockMLModel(private val modelId: String) : MLModel {
    override suspend fun predict(features: List<List<Double>>): List<Double> {
        // Simple mock prediction - return random values between 0 and 1
        return features.map { kotlin.math.sin(it.sum()) * 0.5 + 0.5 }
    }
    
    override suspend fun train(trainingData: TrainingData): ModelTrainingResult {
        return ModelTrainingResult(
            modelId = modelId,
            success = true,
            accuracy = 0.85,
            message = "Mock model traine
d successfully"
        )
    }
}