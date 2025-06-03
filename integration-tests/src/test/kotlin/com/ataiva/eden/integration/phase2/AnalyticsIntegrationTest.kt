package com.ataiva.eden.integration.phase2

import com.ataiva.eden.analytics.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for Phase 2 Advanced Analytics features
 * Tests end-to-end analytics workflows and ML model integration
 */
@DisplayName("Phase 2 Analytics Integration Tests")
class AnalyticsIntegrationTest {
    
    private lateinit var analytics: AdvancedAnalytics
    
    @BeforeEach
    fun setup() {
        // Initialize with test repositories
        analytics = DefaultAdvancedAnalytics(
            metricsRepository = TestMetricsRepository(),
            deploymentRepository = TestDeploymentRepository(),
            modelRepository = TestModelRepository()
        )
    }
    
    @Test
    @DisplayName("End-to-End Performance Analysis Workflow")
    fun testPerformanceAnalysisWorkflow() = runTest {
        // Given - A time range for analysis
        val timeRange = TimeRange(
            start = System.currentTimeMillis() - 24.hours.inWholeMilliseconds,
            end = System.currentTimeMillis()
        )
        
        // When - Performing comprehensive performance analysis
        val analysis = analytics.analyzePerformanceTrends(timeRange)
        
        // Then - Analysis should provide comprehensive insights
        assertNotNull(analysis, "Performance analysis should not be null")
        assertEquals(timeRange, analysis.timeRange, "Time range should match")
        
        // Verify trend analysis
        assertNotNull(analysis.responseTimeTrend, "Response time trend should be analyzed")
        assertNotNull(analysis.errorRateTrend, "Error rate trend should be analyzed")
        assertNotNull(analysis.throughputTrend, "Throughput trend should be analyzed")
        
        // Verify seasonality detection
        assertNotNull(analysis.seasonality, "Seasonality analysis should be performed")
        
        // Verify correlation analysis
        assertNotNull(analysis.correlations, "Correlation matrix should be generated")
        assertTrue(
            analysis.correlations.responseTimeCpu >= -1.0 && analysis.correlations.responseTimeCpu <= 1.0,
            "Correlation values should be between -1 and 1"
        )
        
        // Verify actionable recommendations
        assertTrue(analysis.recommendations.isNotEmpty(), "Should provide actionable recommendations")
        assertTrue(analysis.summary.isNotEmpty(), "Should provide analysis summary")
    }
    
    @Test
    @DisplayName("Multi-Algorithm Anomaly Detection Integration")
    fun testAnomalyDetectionIntegration() = runTest {
        // Given - Metrics with known anomalies
        val metricsWithAnomalies = generateMetricsWithAnomalies()
        
        // When - Running anomaly detection
        val anomalies = analytics.detectAnomalies(metricsWithAnomalies)
        
        // Then - Should detect anomalies using multiple algorithms
        assertNotNull(anomalies, "Anomaly detection should return results")
        assertTrue(anomalies.isNotEmpty(), "Should detect anomalies in test data")
        
        // Verify different anomaly types are detected
        val anomalyTypes = anomalies.map { it.type }.toSet()
        assertTrue(anomalyTypes.contains(AnomalyType.STATISTICAL), "Should detect statistical anomalies")
        assertTrue(anomalyTypes.contains(AnomalyType.TIME_SERIES), "Should detect time series anomalies")
        
        // Verify anomaly severity classification
        val severities = anomalies.map { it.severity }.toSet()
        assertTrue(severities.isNotEmpty(), "Should classify anomaly severities")
        
        // Verify confidence scoring
        anomalies.forEach { anomaly ->
            assertTrue(
                anomaly.confidence >= 0.0 && anomaly.confidence <= 1.0,
                "Anomaly confidence should be between 0 and 1"
            )
            assertNotNull(anomaly.description, "Each anomaly should have a description")
        }
    }
    
    @Test
    @DisplayName("Predictive Analytics and Resource Forecasting")
    fun testPredictiveAnalyticsIntegration() = runTest {
        // Given - A prediction horizon
        val horizon = 24.hours
        
        // When - Generating resource predictions
        val prediction = analytics.predictResourceUsage(horizon)
        
        // Then - Should provide comprehensive resource forecasting
        assertNotNull(prediction, "Resource prediction should not be null")
        assertEquals(horizon, prediction.horizon, "Prediction horizon should match")
        
        // Verify CPU prediction
        assertNotNull(prediction.cpuPrediction, "CPU prediction should be provided")
        assertTrue(prediction.cpuPrediction.predictions.isNotEmpty(), "CPU predictions should not be empty")
        assertTrue(prediction.cpuPrediction.confidence >= 0.0, "CPU prediction confidence should be valid")
        
        // Verify memory prediction
        assertNotNull(prediction.memoryPrediction, "Memory prediction should be provided")
        assertTrue(prediction.memoryPrediction.predictions.isNotEmpty(), "Memory predictions should not be empty")
        
        // Verify disk prediction
        assertNotNull(prediction.diskPrediction, "Disk prediction should be provided")
        assertTrue(prediction.diskPrediction.predictions.isNotEmpty(), "Disk predictions should not be empty")
        
        // Verify network prediction
        assertNotNull(prediction.networkPrediction, "Network prediction should be provided")
        assertTrue(prediction.networkPrediction.predictions.isNotEmpty(), "Network predictions should not be empty")
        
        // Verify overall confidence and recommendations
        assertTrue(
            prediction.confidence >= 0.0 && prediction.confidence <= 1.0,
            "Overall prediction confidence should be valid"
        )
        assertTrue(prediction.recommendations.isNotEmpty(), "Should provide actionable recommendations")
    }
    
    @Test
    @DisplayName("Machine Learning Model Training and Evaluation")
    fun testMLModelIntegration() = runTest {
        // Given - Training data for anomaly detection
        val trainingData = TrainingData(
            features = generateMLFeatures(),
            labels = generateMLLabels(),
            metadata = mapOf("algorithm" to "isolation_forest", "version" to "1.0")
        )
        
        // When - Training an anomaly detection model
        val trainingResult = analytics.trainModel(ModelType.ANOMALY_DETECTION, trainingData)
        
        // Then - Model should be trained successfully
        assertNotNull(trainingResult, "Training result should not be null")
        assertTrue(trainingResult.success, "Model training should succeed")
        assertNotNull(trainingResult.modelId, "Model ID should be generated")
        assertTrue(trainingResult.accuracy >= 0.0, "Training accuracy should be valid")
        assertNotNull(trainingResult.message, "Training message should be provided")
        
        // When - Evaluating the trained model
        val testData = TestData(
            features = generateMLFeatures().take(20),
            labels = generateMLLabels().take(20)
        )
        val evaluation = analytics.evaluateModel(trainingResult.modelId, testData)
        
        // Then - Model evaluation should provide comprehensive metrics
        assertNotNull(evaluation, "Model evaluation should not be null")
        assertEquals(trainingResult.modelId, evaluation.modelId, "Model ID should match")
        
        // Verify evaluation metrics
        assertTrue(evaluation.accuracy >= 0.0 && evaluation.accuracy <= 1.0, "Accuracy should be valid")
        assertTrue(evaluation.precision >= 0.0 && evaluation.precision <= 1.0, "Precision should be valid")
        assertTrue(evaluation.recall >= 0.0 && evaluation.recall <= 1.0, "Recall should be valid")
        assertTrue(evaluation.f1Score >= 0.0 && evaluation.f1Score <= 1.0, "F1 score should be valid")
        assertTrue(evaluation.rmse >= 0.0, "RMSE should be non-negative")
        assertTrue(evaluation.mae >= 0.0, "MAE should be non-negative")
        
        // Verify confusion matrix
        assertNotNull(evaluation.confusionMatrix, "Confusion matrix should be provided")
        val cm = evaluation.confusionMatrix
        assertTrue(cm.tp >= 0 && cm.fp >= 0 && cm.tn >= 0 && cm.fn >= 0, "Confusion matrix values should be valid")
        
        // Verify ROC curve
        assertNotNull(evaluation.rocCurve, "ROC curve should be provided")
        assertTrue(evaluation.rocCurve.auc >= 0.0 && evaluation.rocCurve.auc <= 1.0, "AUC should be valid")
    }
    
    @Test
    @DisplayName("Real-Time Analytics and Intelligent Insights")
    fun testRealtimeAnalyticsIntegration() = runTest {
        // Given - Analytics context for insight generation
        val context = AnalysisContext(
            timeRange = TimeRange(
                start = System.currentTimeMillis() - 24.hours.inWholeMilliseconds,
                end = System.currentTimeMillis()
            ),
            services = listOf("vault", "flow", "task", "monitor"),
            environment = "production",
            includeHistorical = true
        )
        
        // When - Generating intelligent insights
        val insights = analytics.generateInsights(context)
        
        // Then - Should provide comprehensive insights across categories
        assertNotNull(insights, "Insights should not be null")
        assertTrue(insights.isNotEmpty(), "Should generate insights for production environment")
        
        // Verify insight categories
        val categories = insights.map { it.category }.toSet()
        assertTrue(categories.isNotEmpty(), "Should cover multiple insight categories")
        
        // Verify insight quality
        insights.forEach { insight ->
            assertNotNull(insight.id, "Each insight should have an ID")
            assertNotNull(insight.title, "Each insight should have a title")
            assertNotNull(insight.description, "Each insight should have a description")
            assertTrue(
                insight.impact >= 0.0 && insight.impact <= 1.0,
                "Insight impact should be between 0 and 1"
            )
            assertTrue(
                insight.confidence >= 0.0 && insight.confidence <= 1.0,
                "Insight confidence should be between 0 and 1"
            )
            assertTrue(insight.recommendations.isNotEmpty(), "Each insight should have recommendations")
        }
        
        // Verify insights are sorted by impact
        val impacts = insights.map { it.impact }
        assertEquals(impacts.sortedDescending(), impacts, "Insights should be sorted by impact")
    }
    
    @Test
    @DisplayName("Deployment Strategy Optimization with ML")
    fun testDeploymentOptimizationIntegration() = runTest {
        // Given - Historical deployment data
        val deploymentHistory = generateDeploymentHistory()
        
        // When - Optimizing deployment strategy
        val recommendation = analytics.optimizeDeploymentStrategy(deploymentHistory)
        
        // Then - Should provide ML-driven deployment recommendations
        assertNotNull(recommendation, "Deployment recommendation should not be null")
        assertNotNull(recommendation.recommendedStrategy, "Should recommend a deployment strategy")
        assertTrue(
            recommendation.confidence >= 0.0 && recommendation.confidence <= 1.0,
            "Recommendation confidence should be valid"
        )
        assertNotNull(recommendation.reasoning, "Should provide reasoning for recommendation")
        assertTrue(recommendation.alternativeStrategies.isNotEmpty(), "Should provide alternative strategies")
        assertTrue(recommendation.riskMitigation.isNotEmpty(), "Should provide risk mitigation strategies")
        
        // Verify recommendation is based on historical performance
        assertTrue(recommendation.reasoning.isNotEmpty(), "Reasoning should be detailed")
    }
    
    // Helper methods for generating test data
    
    private fun generateMetricsWithAnomalies(): List<MetricPoint> {
        val baseTime = System.currentTimeMillis()
        val metrics = mutableListOf<MetricPoint>()
        
        // Generate normal metrics
        for (i in 0..100) {
            metrics.add(
                MetricPoint(
                    timestamp = baseTime + i * 60000,
                    value = 50.0 + kotlin.math.sin(i * 0.1) * 10.0,
                    responseTime = 100.0 + kotlin.math.cos(i * 0.05) * 20.0,
                    errorRate = 0.5 + kotlin.math.sin(i * 0.2) * 0.3,
                    throughput = 1000.0 + kotlin.math.cos(i * 0.15) * 100.0,
                    cpuUsage = 60.0 + kotlin.math.sin(i * 0.08) * 15.0,
                    memoryUsage = 70.0 + kotlin.math.cos(i * 0.12) * 10.0
                )
            )
        }
        
        // Inject statistical anomalies
        metrics[25] = metrics[25].copy(value = 200.0, responseTime = 500.0) // High value anomaly
        metrics[50] = metrics[50].copy(errorRate = 15.0) // High error rate anomaly
        metrics[75] = metrics[75].copy(cpuUsage = 95.0, memoryUsage = 90.0) // Resource anomaly
        
        return metrics
    }
    
    private fun generateMLFeatures(): List<List<Double>> {
        return (0..100).map { i ->
            listOf(
                50.0 + kotlin.math.sin(i * 0.1) * 10.0, // Response time
                0.5 + kotlin.math.sin(i * 0.2) * 0.3,   // Error rate
                1000.0 + kotlin.math.cos(i * 0.15) * 100.0, // Throughput
                60.0 + kotlin.math.sin(i * 0.08) * 15.0  // CPU usage
            )
        }
    }
    
    private fun generateMLLabels(): List<Double> {
        return (0..100).map { i ->
            // Label as anomaly (1.0) for specific indices, normal (0.0) otherwise
            if (i in listOf(25, 50, 75, 90)) 1.0 else 0.0
        }
    }
    
    private fun generateDeploymentHistory(): List<DeploymentMetrics> {
        return listOf(
            DeploymentMetrics(
                deploymentId = "deploy-rolling-1",
                strategy = "rolling_update",
                duration = 5.minutes.inWholeMilliseconds,
                success = true,
                errorRate = 0.1,
                performanceImpact = 0.05,
                rollbackRequired = false,
                timestamp = System.currentTimeMillis() - 72.hours.inWholeMilliseconds
            ),
            DeploymentMetrics(
                deploymentId = "deploy-blue-green-1",
                strategy = "blue_green",
                duration = 8.minutes.inWholeMilliseconds,
                success = true,
                errorRate = 0.05,
                performanceImpact = 0.02,
                rollbackRequired = false,
                timestamp = System.currentTimeMillis() - 48.hours.inWholeMilliseconds
            ),
            DeploymentMetrics(
                deploymentId = "deploy-canary-1",
                strategy = "canary",
                duration = 15.minutes.inWholeMilliseconds,
                success = false,
                errorRate = 2.5,
                performanceImpact = 0.8,
                rollbackRequired = true,
                timestamp = System.currentTimeMillis() - 24.hours.inWholeMilliseconds
            ),
            DeploymentMetrics(
                deploymentId = "deploy-rolling-2",
                strategy = "rolling_update",
                duration = 4.minutes.inWholeMilliseconds,
                success = true,
                errorRate = 0.08,
                performanceImpact = 0.03,
                rollbackRequired = false,
                timestamp = System.currentTimeMillis() - 12.hours.inWholeMilliseconds
            )
        )
    }
}

// Test repository implementations

class TestMetricsRepository : MetricsRepository {
    override suspend fun getMetrics(timeRange: TimeRange): List<MetricPoint> {
        val duration = timeRange.end - timeRange.start
        val intervals = (duration / 60000).toInt() // 1-minute intervals
        
        return (0 until intervals).map { i ->
            MetricPoint(
                timestamp = timeRange.start + i * 60000,
                value = 50.0 + kotlin.math.sin(i * 0.1) * 10.0,
                responseTime = 100.0 + kotlin.math.cos(i * 0.05) * 20.0,
                errorRate = 0.5 + kotlin.math.sin(i * 0.2) * 0.3,
                throughput = 1000.0 + kotlin.math.cos(i * 0.15) * 100.0,
                cpuUsage = 60.0 + kotlin.math.sin(i * 0.08) * 15.0,
                memoryUsage = 70.0 + kotlin.math.cos(i * 0.12) * 10.0
            )
        }
    }
    
    override suspend fun getResourceMetrics(timeRange: TimeRange): List<ResourceMetric> {
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
}

class TestDeploymentRepository : DeploymentRepository {
    override suspend fun getDeploymentHistory(timeRange: TimeRange): List<DeploymentMetrics> {
        return listOf(
            DeploymentMetrics(
                deploymentId = "test-deploy-1",
                strategy = "rolling_update",
                duration = 300000,
                success = true,
                errorRate = 0.1,
                performanceImpact = 0.05,
                rollbackRequired = false,
                timestamp = timeRange.start + 3600000
            ),
            DeploymentMetrics(
                deploymentId = "test-deploy-2",
                strategy = "blue_green",
                duration = 480000,
                success = true,
                errorRate = 0.05,
                performanceImpact = 0.02,
                rollbackRequired = false,
                timestamp = timeRange.start + 7200000
            )
        )
    }
}

class TestModelRepository : ModelRepository {
    private val models = mutableMapOf<String, MLModel>()
    
    override suspend fun getModel(modelId: String): MLModel? {
        return models[modelId] ?: TestMLModel(modelId)
    }
    
    override suspend fun saveModel(model: MLModel): Boolean {
        models[model.toString()] = model
        return true
    }
}

class TestMLModel(private val modelId: String) : MLModel {
    override suspend fun predict(features: List<List<Double>>): List<Double> {
        // Simple test prediction - return values based on feature sums
        return features.map { feature ->
            val sum = feature.sum()
            if (sum > 1000) 1.0 else 0.0 // Simple threshold-based prediction
        }
    }
    
    override suspend fun train(trainingData: TrainingData): ModelTrainingResult {
        return ModelTrainingResult(
            modelId = modelId,
            success = true,
            accuracy = 0.85,
            message = "Test model trained successfully"
        )
    }
}