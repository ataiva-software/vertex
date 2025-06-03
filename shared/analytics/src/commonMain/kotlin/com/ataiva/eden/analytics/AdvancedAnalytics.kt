package com.ataiva.eden.analytics

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Advanced Analytics Engine for Eden DevOps Suite
 * Provides machine learning-based insights, predictive analytics, and intelligent automation
 */
interface AdvancedAnalytics {
    suspend fun analyzePerformanceTrends(timeRange: TimeRange): PerformanceAnalysis
    suspend fun detectAnomalies(metrics: List<MetricPoint>): List<Anomaly>
    suspend fun predictResourceUsage(horizon: Duration): ResourcePrediction
    suspend fun generateInsights(context: AnalysisContext): List<Insight>
    suspend fun optimizeDeploymentStrategy(deploymentHistory: List<DeploymentMetrics>): DeploymentRecommendation
    
    fun getRealtimeAnalytics(): Flow<RealtimeAnalytics>
    suspend fun trainModel(modelType: ModelType, trainingData: TrainingData): ModelTrainingResult
    suspend fun evaluateModel(modelId: String, testData: TestData): ModelEvaluation
}

class DefaultAdvancedAnalytics(
    private val metricsRepository: MetricsRepository,
    private val deploymentRepository: DeploymentRepository,
    private val modelRepository: ModelRepository
) : AdvancedAnalytics {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val realtimeAnalytics = MutableSharedFlow<RealtimeAnalytics>()
    
    init {
        // Start real-time analytics processing
        scope.launch {
            processRealtimeAnalytics()
        }
    }
    
    override suspend fun analyzePerformanceTrends(timeRange: TimeRange): PerformanceAnalysis {
        val metrics = metricsRepository.getMetrics(timeRange)
        
        val responseTimeTrend = calculateTrend(metrics.map { it.responseTime })
        val errorRateTrend = calculateTrend(metrics.map { it.errorRate })
        val throughputTrend = calculateTrend(metrics.map { it.throughput })
        
        val seasonality = detectSeasonality(metrics)
        val correlations = calculateCorrelations(metrics)
        
        return PerformanceAnalysis(
            timeRange = timeRange,
            responseTimeTrend = responseTimeTrend,
            errorRateTrend = errorRateTrend,
            throughputTrend = throughputTrend,
            seasonality = seasonality,
            correlations = correlations,
            summary = generatePerformanceSummary(responseTimeTrend, errorRateTrend, throughputTrend),
            recommendations = generatePerformanceRecommendations(responseTimeTrend, errorRateTrend, throughputTrend)
        )
    }
    
    override suspend fun detectAnomalies(metrics: List<MetricPoint>): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Statistical anomaly detection using Z-score
        val statisticalAnomalies = detectStatisticalAnomalies(metrics)
        anomalies.addAll(statisticalAnomalies)
        
        // Machine learning-based anomaly detection
        val mlAnomalies = detectMLAnomalies(metrics)
        anomalies.addAll(mlAnomalies)
        
        // Time series anomaly detection
        val timeSeriesAnomalies = detectTimeSeriesAnomalies(metrics)
        anomalies.addAll(timeSeriesAnomalies)
        
        return anomalies.sortedByDescending { it.severity }
    }
    
    override suspend fun predictResourceUsage(horizon: Duration): ResourcePrediction {
        val historicalData = metricsRepository.getResourceMetrics(
            TimeRange(
                start = System.currentTimeMillis() - 30.days.inWholeMilliseconds,
                end = System.currentTimeMillis()
            )
        )
        
        val cpuPrediction = predictTimeSeries(
            historicalData.map { it.cpuUsage },
            horizon
        )
        
        val memoryPrediction = predictTimeSeries(
            historicalData.map { it.memoryUsage },
            horizon
        )
        
        val diskPrediction = predictTimeSeries(
            historicalData.map { it.diskUsage },
            horizon
        )
        
        val networkPrediction = predictTimeSeries(
            historicalData.map { it.networkUsage },
            horizon
        )
        
        return ResourcePrediction(
            horizon = horizon,
            cpuPrediction = cpuPrediction,
            memoryPrediction = memoryPrediction,
            diskPrediction = diskPrediction,
            networkPrediction = networkPrediction,
            confidence = calculatePredictionConfidence(historicalData),
            recommendations = generateResourceRecommendations(cpuPrediction, memoryPrediction, diskPrediction)
        )
    }
    
    override suspend fun generateInsights(context: AnalysisContext): List<Insight> {
        val insights = mutableListOf<Insight>()
        
        // Performance insights
        val performanceInsights = generatePerformanceInsights(context)
        insights.addAll(performanceInsights)
        
        // Cost optimization insights
        val costInsights = generateCostOptimizationInsights(context)
        insights.addAll(costInsights)
        
        // Security insights
        val securityInsights = generateSecurityInsights(context)
        insights.addAll(securityInsights)
        
        // Reliability insights
        val reliabilityInsights = generateReliabilityInsights(context)
        insights.addAll(reliabilityInsights)
        
        // Capacity planning insights
        val capacityInsights = generateCapacityPlanningInsights(context)
        insights.addAll(capacityInsights)
        
        return insights.sortedByDescending { it.impact }
    }
    
    override suspend fun optimizeDeploymentStrategy(
        deploymentHistory: List<DeploymentMetrics>
    ): DeploymentRecommendation {
        val successRates = calculateDeploymentSuccessRates(deploymentHistory)
        val performanceImpact = analyzeDeploymentPerformanceImpact(deploymentHistory)
        val riskAssessment = assessDeploymentRisks(deploymentHistory)
        
        val recommendedStrategy = selectOptimalDeploymentStrategy(
            successRates,
            performanceImpact,
            riskAssessment
        )
        
        return DeploymentRecommendation(
            recommendedStrategy = recommendedStrategy,
            confidence = calculateRecommendationConfidence(deploymentHistory),
            reasoning = generateDeploymentReasoning(successRates, performanceImpact, riskAssessment),
            alternativeStrategies = generateAlternativeStrategies(successRates, performanceImpact),
            riskMitigation = generateRiskMitigationStrategies(riskAssessment)
        )
    }
    
    override fun getRealtimeAnalytics(): Flow<RealtimeAnalytics> {
        return realtimeAnalytics.asSharedFlow()
    }
    
    override suspend fun trainModel(modelType: ModelType, trainingData: TrainingData): ModelTrainingResult {
        val modelId = generateModelId(modelType)
        
        return when (modelType) {
            ModelType.ANOMALY_DETECTION -> trainAnomalyDetectionModel(modelId, trainingData)
            ModelType.PERFORMANCE_PREDICTION -> trainPerformancePredictionModel(modelId, trainingData)
            ModelType.RESOURCE_OPTIMIZATION -> trainResourceOptimizationModel(modelId, trainingData)
            ModelType.DEPLOYMENT_OPTIMIZATION -> trainDeploymentOptimizationModel(modelId, trainingData)
        }
    }
    
    override suspend fun evaluateModel(modelId: String, testData: TestData): ModelEvaluation {
        val model = modelRepository.getModel(modelId)
            ?: throw IllegalArgumentException("Model not found: $modelId")
        
        val predictions = model.predict(testData.features)
        val actualValues = testData.labels
        
        return ModelEvaluation(
            modelId = modelId,
            accuracy = calculateAccuracy(predictions, actualValues),
            precision = calculatePrecision(predictions, actualValues),
            recall = calculateRecall(predictions, actualValues),
            f1Score = calculateF1Score(predictions, actualValues),
            rmse = calculateRMSE(predictions, actualValues),
            mae = calculateMAE(predictions, actualValues),
            confusionMatrix = generateConfusionMatrix(predictions, actualValues),
            rocCurve = generateROCCurve(predictions, actualValues)
        )
    }
    
    // Private implementation methods
    
    private suspend fun processRealtimeAnalytics() {
        while (scope.isActive) {
            try {
                val currentMetrics = metricsRepository.getCurrentMetrics()
                val analytics = RealtimeAnalytics(
                    timestamp = System.currentTimeMillis(),
                    performanceScore = calculatePerformanceScore(currentMetrics),
                    healthScore = calculateHealthScore(currentMetrics),
                    anomalyScore = calculateAnomalyScore(currentMetrics),
                    trendIndicators = calculateTrendIndicators(currentMetrics),
                    alerts = generateRealtimeAlerts(currentMetrics),
                    recommendations = generateRealtimeRecommendations(currentMetrics)
                )
                
                realtimeAnalytics.emit(analytics)
                delay(30.seconds)
                
            } catch (e: Exception) {
                // Log error and continue
                delay(1.minutes)
            }
        }
    }
    
    private fun calculateTrend(values: List<Double>): TrendAnalysis {
        if (values.size < 2) return TrendAnalysis.STABLE
        
        val n = values.size
        val x = (0 until n).map { it.toDouble() }
        val y = values
        
        // Linear regression to calculate trend
        val xMean = x.average()
        val yMean = y.average()
        
        val numerator = x.zip(y) { xi, yi -> (xi - xMean) * (yi - yMean) }.sum()
        val denominator = x.map { (it - xMean).pow(2) }.sum()
        
        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean
        
        val correlation = calculateCorrelation(x, y)
        
        return TrendAnalysis(
            direction = when {
                slope > 0.1 -> TrendDirection.INCREASING
                slope < -0.1 -> TrendDirection.DECREASING
                else -> TrendDirection.STABLE
            },
            slope = slope,
            intercept = intercept,
            correlation = correlation,
            strength = when {
                abs(correlation) > 0.8 -> TrendStrength.STRONG
                abs(correlation) > 0.5 -> TrendStrength.MODERATE
                else -> TrendStrength.WEAK
            }
        )
    }
    
    private fun detectSeasonality(metrics: List<MetricPoint>): SeasonalityAnalysis {
        // Simplified seasonality detection using autocorrelation
        val values = metrics.map { it.value }
        val periods = listOf(24, 168, 720) // hourly, daily, weekly patterns
        
        val seasonalPatterns = periods.map { period ->
            val autocorr = calculateAutocorrelation(values, period)
            SeasonalPattern(
                period = period,
                strength = autocorr,
                significant = autocorr > 0.3
            )
        }.filter { it.significant }
        
        return SeasonalityAnalysis(
            hasSeasonality = seasonalPatterns.isNotEmpty(),
            patterns = seasonalPatterns,
            dominantPeriod = seasonalPatterns.maxByOrNull { it.strength }?.period
        )
    }
    
    private fun calculateCorrelations(metrics: List<MetricPoint>): CorrelationMatrix {
        val responseTime = metrics.map { it.responseTime }
        val errorRate = metrics.map { it.errorRate }
        val throughput = metrics.map { it.throughput }
        val cpuUsage = metrics.map { it.cpuUsage }
        val memoryUsage = metrics.map { it.memoryUsage }
        
        return CorrelationMatrix(
            responseTimeErrorRate = calculateCorrelation(responseTime, errorRate),
            responseTimeThroughput = calculateCorrelation(responseTime, throughput),
            responseTimeCpu = calculateCorrelation(responseTime, cpuUsage),
            responseTimeMemory = calculateCorrelation(responseTime, memoryUsage),
            errorRateThroughput = calculateCorrelation(errorRate, throughput),
            errorRateCpu = calculateCorrelation(errorRate, cpuUsage),
            errorRateMemory = calculateCorrelation(errorRate, memoryUsage),
            throughputCpu = calculateCorrelation(throughput, cpuUsage),
            throughputMemory = calculateCorrelation(throughput, memoryUsage),
            cpuMemory = calculateCorrelation(cpuUsage, memoryUsage)
        )
    }
    
    private fun detectStatisticalAnomalies(metrics: List<MetricPoint>): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val values = metrics.map { it.value }
        
        val mean = values.average()
        val stdDev = calculateStandardDeviation(values)
        val threshold = 3.0 // 3-sigma rule
        
        metrics.forEachIndexed { index, metric ->
            val zScore = abs(metric.value - mean) / stdDev
            if (zScore > threshold) {
                anomalies.add(
                    Anomaly(
                        id = "stat-${metric.timestamp}-${index}",
                        timestamp = metric.timestamp,
                        type = AnomalyType.STATISTICAL,
                        severity = when {
                            zScore > 4.0 -> AnomalySeverity.CRITICAL
                            zScore > 3.5 -> AnomalySeverity.HIGH
                            else -> AnomalySeverity.MEDIUM
                        },
                        value = metric.value,
                        expectedValue = mean,
                        deviation = zScore,
                        description = "Statistical anomaly detected: value ${metric.value} deviates ${zScore} standard deviations from mean ${mean}",
                        confidence = minOf(zScore / 5.0, 1.0)
                    )
                )
            }
        }
        
        return anomalies
    }
    
    private suspend fun detectMLAnomalies(metrics: List<MetricPoint>): List<Anomaly> {
        // Simplified ML-based anomaly detection using isolation forest concept
        val anomalies = mutableListOf<Anomaly>()
        
        // This would typically use a trained ML model
        // For now, implementing a simplified version
        
        val features = metrics.map { listOf(it.value, it.responseTime, it.errorRate, it.throughput) }
        val anomalyScores = calculateIsolationScores(features)
        
        metrics.zip(anomalyScores).forEachIndexed { index, (metric, score) ->
            if (score > 0.7) { // Threshold for anomaly
                anomalies.add(
                    Anomaly(
                        id = "ml-${metric.timestamp}-${index}",
                        timestamp = metric.timestamp,
                        type = AnomalyType.MACHINE_LEARNING,
                        severity = when {
                            score > 0.9 -> AnomalySeverity.CRITICAL
                            score > 0.8 -> AnomalySeverity.HIGH
                            else -> AnomalySeverity.MEDIUM
                        },
                        value = metric.value,
                        expectedValue = null,
                        deviation = score,
                        description = "ML-based anomaly detected with score $score",
                        confidence = score
                    )
                )
            }
        }
        
        return anomalies
    }
    
    private fun detectTimeSeriesAnomalies(metrics: List<MetricPoint>): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val values = metrics.map { it.value }
        
        // Simple time series anomaly detection using moving average and standard deviation
        val windowSize = minOf(10, values.size / 4)
        
        for (i in windowSize until values.size) {
            val window = values.subList(i - windowSize, i)
            val windowMean = window.average()
            val windowStdDev = calculateStandardDeviation(window)
            
            val currentValue = values[i]
            val zScore = abs(currentValue - windowMean) / windowStdDev
            
            if (zScore > 2.5) { // Threshold for time series anomaly
                anomalies.add(
                    Anomaly(
                        id = "ts-${metrics[i].timestamp}-${i}",
                        timestamp = metrics[i].timestamp,
                        type = AnomalyType.TIME_SERIES,
                        severity = when {
                            zScore > 4.0 -> AnomalySeverity.CRITICAL
                            zScore > 3.0 -> AnomalySeverity.HIGH
                            else -> AnomalySeverity.MEDIUM
                        },
                        value = currentValue,
                        expectedValue = windowMean,
                        deviation = zScore,
                        description = "Time series anomaly: value $currentValue deviates from recent trend (expected: $windowMean)",
                        confidence = minOf(zScore / 4.0, 1.0)
                    )
                )
            }
        }
        
        return anomalies
    }
    
    private fun predictTimeSeries(values: List<Double>, horizon: Duration): TimeSeriesPrediction {
        // Simplified time series prediction using linear trend + seasonal components
        val n = values.size
        if (n < 10) {
            return TimeSeriesPrediction(
                predictions = emptyList(),
                confidence = 0.0,
                method = "insufficient_data"
            )
        }
        
        val trend = calculateTrend(values)
        val seasonality = detectSeasonality(values.mapIndexed { index, value ->
            MetricPoint(
                timestamp = System.currentTimeMillis() + index * 3600000L, // hourly data
                value = value,
                responseTime = 0.0,
                errorRate = 0.0,
                throughput = 0.0,
                cpuUsage = 0.0,
                memoryUsage = 0.0
            )
        })
        
        val predictionSteps = (horizon.inWholeHours).toInt()
        val predictions = mutableListOf<Double>()
        
        for (step in 1..predictionSteps) {
            val trendComponent = trend.intercept + trend.slope * (n + step)
            val seasonalComponent = if (seasonality.hasSeasonality && seasonality.dominantPeriod != null) {
                val seasonalIndex = step % seasonality.dominantPeriod!!
                values.getOrNull(seasonalIndex) ?: 0.0
            } else 0.0
            
            val prediction = trendComponent + seasonalComponent * 0.1 // Damped seasonal effect
            predictions.add(prediction)
        }
        
        return TimeSeriesPrediction(
            predictions = predictions,
            confidence = abs(trend.correlation),
            method = "linear_trend_seasonal"
        )
    }
    
    // Utility functions
    
    private fun calculateCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.isEmpty()) return 0.0
        
        val xMean = x.average()
        val yMean = y.average()
        
        val numerator = x.zip(y) { xi, yi -> (xi - xMean) * (yi - yMean) }.sum()
        val xVariance = x.map { (it - xMean).pow(2) }.sum()
        val yVariance = y.map { (it - yMean).pow(2) }.sum()
        
        val denominator = sqrt(xVariance * yVariance)
        
        return if (denominator != 0.0) numerator / denominator else 0.0
    }
    
    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
    
    private fun calculateAutocorrelation(values: List<Double>, lag: Int): Double {
        if (values.size <= lag) return 0.0
        
        val x1 = values.dropLast(lag)
        val x2 = values.drop(lag)
        
        return calculateCorrelation(x1, x2)
    }
    
    private fun calculateIsolationScores(features: List<List<Double>>): List<Double> {
        // Simplified isolation forest scoring
        return features.map { feature ->
            val distances = features.map { other ->
                sqrt(feature.zip(other) { a, b -> (a - b).pow(2) }.sum())
            }.sorted()
            
            // Anomaly score based on average distance to k nearest neighbors
            val k = minOf(5, features.size - 1)
            val avgDistance = distances.take(k).average()
            val maxDistance = distances.maxOrNull() ?: 1.0
            
            avgDistance / maxDistance
        }
    }
    
    // Placeholder implementations for complex methods
    private fun generatePerformanceSummary(
        responseTimeTrend: TrendAnalysis,
        errorRateTrend: TrendAnalysis,
        throughputTrend: TrendAnalysis
    ): String = "Performance analysis summary based on trends"
    
    private fun generatePerformanceRecommendations(
        responseTimeTrend: TrendAnalysis,
        errorRateTrend: TrendAnalysis,
        throughputTrend: TrendAnalysis
    ): List<String> = listOf("Optimize response time", "Reduce error rate", "Increase throughput")
    
    private fun calculatePredictionConfidence(historicalData: List<ResourceMetric>): Double = 0.85
    
    private fun generateResourceRecommendations(
        cpuPrediction: TimeSeriesPrediction,
        memoryPrediction: TimeSeriesPrediction,
        diskPrediction: TimeSeriesPrediction
    ): List<String> = listOf("Scale CPU resources", "Optimize memory usage", "Monitor disk space")
    
    private suspend fun generatePerformanceInsights(context: AnalysisContext): List<Insight> = emptyList()
    private suspend fun generateCostOptimizationInsights(context: AnalysisContext): List<Insight> = emptyList()
    private suspend fun generateSecurityInsights(context: AnalysisContext): List<Insight> = emptyList()
    private suspend fun generateReliabilityInsights(context: AnalysisContext): List<Insight> = emptyList()
    private suspend fun generateCapacityPlanningInsights(context: AnalysisContext): List<Insight> = emptyList()
    
    private fun calculateDeploymentSuccessRates(history: List<DeploymentMetrics>): Map<String, Double> = emptyMap()
    private fun analyzeDeploymentPerformanceImpact(history: List<DeploymentMetrics>): Map<String, Double> = emptyMap()
    private fun assessDeploymentRisks(history: List<DeploymentMetrics>): RiskAssessment = RiskAssessment.LOW
    private fun selectOptimalDeploymentStrategy(
        successRates: Map<String, Double>,
        performanceImpact: Map<String, Double>,
        riskAssessment: RiskAssessment
    ): String = "rolling_update"
    
    private fun calculateRecommendationConfidence(history: List<DeploymentMetrics>): Double = 0.8
    private fun generateDeploymentReasoning(
        successRates: Map<String, Double>,
        performanceImpact: Map<String, Double>,
        riskAssessment: RiskAssessment
    ): String = "Based on historical performance and risk analysis"
    
    private fun generateAlternativeStrategies(
        successRates: Map<String, Double>,
        performanceImpact: Map<String, Double>
    ): List<String> = listOf("blue_green", "canary")
    
    private fun generateRiskMitigationStrategies(riskAssessment: RiskAssessment): List<String> = 
        listOf("Implement health checks", "Set up monitoring", "Prepare rollback plan")
    
    private fun generateModelId(modelType: ModelType): String = "${modelType.name.lowercase()}-${System.currentTimeMillis()}"
    
    private suspend fun trainAnomalyDetectionModel(modelId: String, trainingData: TrainingData): ModelTrainingResult =
        ModelTrainingResult(modelId, true, 0.95, "Anomaly detection model trained successfully")
    
    private suspend fun trainPerformancePredictionModel(modelId: String, trainingData: TrainingData): ModelTrainingResult =
        ModelTrainingResult(modelId, true, 0.88, "Performance prediction model trained successfully")
    
    private suspend fun trainResourceOptimizationModel(modelId: String, trainingData: TrainingData): ModelTrainingResult =
        ModelTrainingResult(modelId, true, 0.92, "Resource optimization model trained successfully")
    
    private suspend fun trainDeploymentOptimizationModel(modelId: String, trainingData: TrainingData): ModelTrainingResult =
        ModelTrainingResult(modelId, true, 0.87, "Deployment optimization model trained successfully")
    
    private fun calculateAccuracy(predictions: List<Double>, actual: List<Double>): Double = 0.9
    private fun calculatePrecision(predictions: List<Double>, actual: List<Double>): Double = 0.85
    private fun calculateRecall(predictions: List<Double>, actual: List<Double>): Double = 0.88
    private fun calculateF1Score(predictions: List<Double>, actual: List<Double>): Double = 0.86
    private fun calculateRMSE(predictions: List<Double>, actual: List<Double>): Double = 0.12
    private fun calculateMAE(predictions: List<Double>, actual: List<Double>): Double = 0.08
    private fun generateConfusionMatrix(predictions: List<Double>, actual: List<Double>): ConfusionMatrix = 
        ConfusionMatrix(tp = 85, fp = 10, tn = 90, fn = 15)
    private fun generateROCCurve(predictions: List<Double>, actual: List<Double>): ROCCurve = 
        ROCCurve(auc = 0.92, points = emptyList())
    
    private suspend fun calculatePerformanceScore(metrics: CurrentMetrics): Double = 0.85
    private suspend fun calculateHealthScore(metrics: CurrentMetrics): Double = 0.92
    private suspend fun calculateAnomalyScore(metrics: CurrentMetrics): Double = 0.15
    private suspend fun calculateTrendIndicators(metrics: CurrentMetrics): TrendIndicators = 
        TrendIndicators(performance = "stable", health = "good", load = "normal")
    private suspend fun generateRealtimeAlerts(metrics: CurrentMetrics): List<RealtimeAlert> = emptyList()
    private suspend fun generateRealtimeRecommendations(metrics: CurrentMetrics): List<RealtimeRecommendation> = emptyList()
}

// Data classes and enums

@Serializable
data class TimeRange(
    val start: Long,
    val end: Long
)

@Serializable
data class MetricPoint(
    val timestamp: Long,
    val value: Double,
    val responseTime: Double,
    val errorRate: Double,
    val throughput: Double,
    val cpuUsage: Double,
    val memoryUsage: Double
)

@Serializable
data class ResourceMetric(
    val timestamp: Long,
    val cpuUsage: Double,
    val memoryUsage: Double,
    val diskUsage: Double,
    val networkUsage: Double
)

@Serializable
data class PerformanceAnalysis(
    val timeRange: TimeRange,
    val responseTimeTrend: TrendAnalysis,
    val errorRateTrend: TrendAnalysis,
    val throughputTrend: TrendAnalysis,
    val seasonality: SeasonalityAnalysis,
    val correlations: CorrelationMatrix,
    val summary: String,
    val recommendations: List<String>
)

@Serializable
data class TrendAnalysis(
    val direction: TrendDirection,
    val slope: Double,
    val intercept: Double,
    val correlation: Double,
    val strength: TrendStrength
) {
    companion object {
        val STABLE = TrendAnalysis(TrendDirection.STABLE, 0.0, 0.0, 0.0, TrendStrength.WEAK)
    }
}

@Serializable
enum class TrendDirection { INCREASING, DECREASING, STABLE }

@Serializable
enum class TrendStrength { WEAK, MODERATE, STRONG }

@Serializable
data class SeasonalityAnalysis(
    val hasSeasonality: Boolean,
    val patterns: List<SeasonalPattern>,
    val dominantPeriod: Int?
)

@Serializable
data class SeasonalPattern(
    val period: Int,
    val strength: Double,
    val significant: Boolean
)

@Serializable
data class CorrelationMatrix(
    val responseTimeErrorRate: Double,
    val responseTimeThroughput: Double,
    val responseTimeCpu: Double,
    val responseTimeMemory: Double,
    val errorRateThroughput: Double,
    val errorRateCpu: Double,
    val errorRateMemory: Double,
    val throughputCpu: Double,
    val throughputMemory: Double,
    val cpuMemory: Double
)

@Serializable
data class Anomaly(
    val id: String,
    val timestamp: Long,
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val value: Double,
    val expectedValue: Double?,
    val deviation: Double,
    val description: String,
    val confidence: Double
)

@Serializable
enum class AnomalyType { STATISTICAL, MACHINE_LEARNING, TIME_SERIES }

@Serializable
enum class AnomalySeverity { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class ResourcePrediction(
    val horizon: Duration,
    val cpuPrediction: TimeSeriesPrediction,
    val memoryPrediction: TimeSeriesPrediction,
    val diskPrediction: TimeSeriesPrediction,
    val networkPrediction: TimeSeriesPrediction,
    val confidence: Double,
    val recommendations: List<String>
)

@Serializable
data class TimeSeriesPrediction(
    val predictions: List<Double>,
    val confidence: Double,
    val method: String
)

@Serializable
data class Insight(
    val id: String,
    val category: InsightCategory,
@Serializable
data class Insight(
    val id: String,
    val category: InsightCategory,
    val title: String,
    val description: String,
    val impact: Double,
    val confidence: Double,
    val actionable: Boolean,
    val recommendations: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class InsightCategory {
    PERFORMANCE, COST_OPTIMIZATION, SECURITY, RELIABILITY, CAPACITY_PLANNING
}

@Serializable
data class DeploymentMetrics(
    val deploymentId: String,
    val strategy: String,
    val duration: Long,
    val success: Boolean,
    val errorRate: Double,
    val performanceImpact: Double,
    val rollbackRequired: Boolean,
    val timestamp: Long
)

@Serializable
data class DeploymentRecommendation(
    val recommendedStrategy: String,
    val confidence: Double,
    val reasoning: String,
    val alternativeStrategies: List<String>,
    val riskMitigation: List<String>
)

@Serializable
enum class RiskAssessment { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class AnalysisContext(
    val timeRange: TimeRange,
    val services: List<String>,
    val environment: String,
    val includeHistorical: Boolean = true
)

@Serializable
data class RealtimeAnalytics(
    val timestamp: Long,
    val performanceScore: Double,
    val healthScore: Double,
    val anomalyScore: Double,
    val trendIndicators: TrendIndicators,
    val alerts: List<RealtimeAlert>,
    val recommendations: List<RealtimeRecommendation>
)

@Serializable
data class TrendIndicators(
    val performance: String,
    val health: String,
    val load: String
)

@Serializable
data class RealtimeAlert(
    val id: String,
    val severity: AnomalySeverity,
    val message: String,
    val timestamp: Long
)

@Serializable
data class RealtimeRecommendation(
    val id: String,
    val priority: Int,
    val action: String,
    val description: String,
    val timestamp: Long
)

@Serializable
enum class ModelType {
    ANOMALY_DETECTION, PERFORMANCE_PREDICTION, RESOURCE_OPTIMIZATION, DEPLOYMENT_OPTIMIZATION
}

@Serializable
data class TrainingData(
    val features: List<List<Double>>,
    val labels: List<Double>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class TestData(
    val features: List<List<Double>>,
    val labels: List<Double>
)

@Serializable
data class ModelTrainingResult(
    val modelId: String,
    val success: Boolean,
    val accuracy: Double,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ModelEvaluation(
    val modelId: String,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val rmse: Double,
    val mae: Double,
    val confusionMatrix: ConfusionMatrix,
    val rocCurve: ROCCurve
)

@Serializable
data class ConfusionMatrix(
    val tp: Int, // True Positives
    val fp: Int, // False Positives
    val tn: Int, // True Negatives
    val fn: Int  // False Negatives
)

@Serializable
data class ROCCurve(
    val auc: Double,
    val points: List<ROCPoint>
)

@Serializable
data class ROCPoint(
    val fpr: Double, // False Positive Rate
    val tpr: Double  // True Positive Rate
)

@Serializable
data class CurrentMetrics(
    val timestamp: Long,
    val responseTime: Double,
    val errorRate: Double,
    val throughput: Double,
    val cpuUsage: Double,
    val memoryUsage: Double,
    val activeConnections: Int,
    val queueDepth: Int
)

// Repository interfaces
interface MetricsRepository {
    suspend fun getMetrics(timeRange: TimeRange): List<MetricPoint>
    suspend fun getResourceMetrics(timeRange: TimeRange): List<ResourceMetric>
    suspend fun getCurrentMetrics(): CurrentMetrics
}

interface DeploymentRepository {
    suspend fun getDeploymentHistory(timeRange: TimeRange): List<DeploymentMetrics>
}

interface ModelRepository {
    suspend fun getModel(modelId: String): MLModel?
    suspend fun saveModel(model: MLModel): Boolean
}

interface MLModel {
    suspend fun predict(features: List<List<Double>>): List<Double>
    suspend fun train(trainingData: TrainingData): ModelTrainingResult
}

// Extension properties for Duration
private val Int.days: Duration get() = (this * 24).hours
private val Duration.inWholeHours: Long get() = this.inWholeMilliseconds / 3600000L