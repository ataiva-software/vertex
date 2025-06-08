package com.ataiva.eden.ai

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

// Deep Learning Models

@Serializable
data class DeepLearningConfig(
    val modelType: DeepLearningModelType,
    val architecture: String,
    val hyperparameters: Map<String, Double>,
    val trainingConfig: TrainingConfig,
    val validationSplit: Double = 0.2,
    val earlyStopping: Boolean = true,
    val checkpointFrequency: Int = 100
)

@Serializable
enum class DeepLearningModelType {
    PREDICTIVE_MAINTENANCE,
    PERFORMANCE_FORECASTING,
    ANOMALY_DETECTION,
    RESOURCE_OPTIMIZATION,
    SECURITY_THREAT_DETECTION
}

@Serializable
data class TrainingConfig(
    val epochs: Int,
    val batchSize: Int,
    val learningRate: Double,
    val optimizer: OptimizerType,
    val lossFunction: LossFunctionType,
    val regularization: RegularizationConfig?
)

@Serializable
enum class OptimizerType {
    ADAM, SGD, RMSPROP, ADAGRAD
}

@Serializable
enum class LossFunctionType {
    MEAN_SQUARED_ERROR,
    CROSS_ENTROPY,
    BINARY_CROSS_ENTROPY,
    HUBER_LOSS,
    CUSTOM
}

@Serializable
data class RegularizationConfig(
    val l1: Double = 0.0,
    val l2: Double = 0.0,
    val dropout: Double = 0.0
)

@Serializable
data class DeepLearningInput(
    val features: List<Double>,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Instant? = null
)

@Serializable
data class DeepLearningPrediction(
    val value: Double,
    val confidence: Double,
    val uncertainty: Double,
    val featureImportance: Map<String, Double>,
    val timestamp: Instant
)

// Neural Network Models

@Serializable
data class NeuralNetworkArchitecture(
    val layers: List<LayerConfig>,
    val inputShape: List<Int>,
    val outputShape: List<Int>,
    val activationFunction: ActivationFunction,
    val networkType: NetworkType
)

@Serializable
data class LayerConfig(
    val type: LayerType,
    val units: Int,
    val activation: ActivationFunction,
    val dropout: Double = 0.0,
    val regularization: RegularizationConfig? = null
)

@Serializable
enum class LayerType {
    DENSE, CONVOLUTIONAL, LSTM, GRU, ATTENTION, TRANSFORMER
}

@Serializable
enum class ActivationFunction {
    RELU, SIGMOID, TANH, SOFTMAX, LEAKY_RELU, ELU, SWISH
}

@Serializable
enum class NetworkType {
    FEEDFORWARD, CONVOLUTIONAL, RECURRENT, TRANSFORMER, AUTOENCODER
}

@Serializable
data class NeuralNetwork(
    val id: String,
    val architecture: NeuralNetworkArchitecture,
    val status: NetworkStatus,
    val createdAt: Instant,
    val lastTrainedAt: Instant? = null
)

@Serializable
enum class NetworkStatus {
    CREATED, TRAINING, TRAINED, DEPLOYED, ARCHIVED
}

@Serializable
data class NeuralTrainingData(
    val features: List<List<Double>>,
    val labels: List<List<Double>>,
    val validationFeatures: List<List<Double>>? = null,
    val validationLabels: List<List<Double>>? = null
)

@Serializable
data class NeuralTestData(
    val features: List<List<Double>>,
    val labels: List<List<Double>>
)

@Serializable
data class NeuralTrainingResult(
    val networkId: String,
    val trainingLoss: List<Double>,
    val validationLoss: List<Double>,
    val trainingAccuracy: List<Double>,
    val validationAccuracy: List<Double>,
    val epochs: Int,
    val trainingTime: Long,
    val finalMetrics: TrainingMetrics
)

@Serializable
data class NeuralEvaluation(
    val networkId: String,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val confusionMatrix: List<List<Int>>,
    val classificationReport: Map<String, ClassificationMetrics>
)

@Serializable
data class ClassificationMetrics(
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val support: Int
)

// Reinforcement Learning Models

@Serializable
data class ReinforcementConfig(
    val algorithm: RLAlgorithm,
    val environment: EnvironmentConfig,
    val agentConfig: AgentConfig,
    val trainingConfig: RLTrainingConfig
)

@Serializable
enum class RLAlgorithm {
    Q_LEARNING, DEEP_Q_NETWORK, POLICY_GRADIENT, ACTOR_CRITIC, PPO, SAC
}

@Serializable
data class EnvironmentConfig(
    val stateSpace: StateSpaceConfig,
    val actionSpace: ActionSpaceConfig,
    val rewardFunction: RewardFunctionConfig,
    val episodeLength: Int,
    val terminationConditions: List<String>
)

@Serializable
data class StateSpaceConfig(
    val dimensions: Int,
    val bounds: List<Pair<Double, Double>>,
    val discrete: Boolean = false
)

@Serializable
data class ActionSpaceConfig(
    val dimensions: Int,
    val bounds: List<Pair<Double, Double>>,
    val discrete: Boolean = true,
    val actions: List<String>
)

@Serializable
data class RewardFunctionConfig(
    val type: RewardType,
    val parameters: Map<String, Double>,
    val shaping: Boolean = false
)

@Serializable
enum class RewardType {
    SPARSE, DENSE, SHAPED, MULTI_OBJECTIVE
}

@Serializable
data class AgentConfig(
    val networkArchitecture: NeuralNetworkArchitecture,
    val explorationStrategy: ExplorationStrategy,
    val memorySize: Int,
    val updateFrequency: Int
)

@Serializable
data class ExplorationStrategy(
    val type: ExplorationType,
    val parameters: Map<String, Double>
)

@Serializable
enum class ExplorationType {
    EPSILON_GREEDY, BOLTZMANN, UCB, THOMPSON_SAMPLING
}

@Serializable
data class RLTrainingConfig(
    val episodes: Int,
    val maxStepsPerEpisode: Int,
    val learningRate: Double,
    val discountFactor: Double,
    val batchSize: Int,
    val targetUpdateFrequency: Int
)

@Serializable
data class ReinforcementAgent(
    val id: String,
    val config: ReinforcementConfig,
    val status: AgentStatus,
    val performance: AgentPerformance,
    val createdAt: Instant
)

@Serializable
enum class AgentStatus {
    CREATED, TRAINING, TRAINED, DEPLOYED, RETIRED
}

@Serializable
data class AgentPerformance(
    val averageReward: Double,
    val episodeRewards: List<Double>,
    val explorationRate: Double,
    val convergenceMetrics: ConvergenceMetrics
)

@Serializable
data class ConvergenceMetrics(
    val converged: Boolean,
    val convergenceEpisode: Int?,
    val stabilityScore: Double
)

@Serializable
data class Environment(
    val id: String,
    val config: EnvironmentConfig,
    val currentState: State,
    val episodeCount: Int,
    val totalReward: Double
)

@Serializable
data class State(
    val features: Map<String, Double>,
    val timestamp: Instant = Clock.System.now(),
    val episodeStep: Int = 0
)

@Serializable
data class Action(
    val type: String,
    val parameters: Map<String, Double>,
    val confidence: Double,
    val description: String,
    val recommendations: List<String>
)

@Serializable
data class ReinforcementTrainingResult(
    val agentId: String,
    val episodes: Int,
    val totalReward: Double,
    val averageReward: Double,
    val convergenceEpisode: Int?,
    val trainingTime: Long,
    val finalPolicy: PolicyMetrics
)

@Serializable
data class PolicyMetrics(
    val averageReturn: Double,
    val standardDeviation: Double,
    val maxReturn: Double,
    val minReturn: Double
)

// Natural Language Processing Models

@Serializable
enum class NLPTask {
    SENTIMENT_ANALYSIS,
    NAMED_ENTITY_RECOGNITION,
    TEXT_CLASSIFICATION,
    SUMMARIZATION,
    QUESTION_ANSWERING,
    THREAT_DETECTION,
    LOG_ANALYSIS,
    CODE_DOCUMENTATION
}

@Serializable
data class NLPResult(
    val originalText: String,
    val tasks: List<NLPTask>,
    val sentimentScore: Double = 0.0,
    val threatScore: Double = 0.0,
    val entities: List<NamedEntity> = emptyList(),
    val classification: TextClassification? = null,
    val summary: String? = null,
    val confidence: Double,
    val processingTime: Long
)

@Serializable
data class NamedEntity(
    val text: String,
    val type: EntityType,
    val confidence: Double,
    val startIndex: Int,
    val endIndex: Int
)

@Serializable
enum class EntityType {
    PERSON, ORGANIZATION, LOCATION, DATE, TIME, IP_ADDRESS, URL, FILE_PATH, ERROR_CODE
}

@Serializable
data class TextClassification(
    val category: String,
    val confidence: Double,
    val alternativeCategories: List<Pair<String, Double>>
)

@Serializable
data class CodeContext(
    val language: String,
    val code: String,
    val filePath: String,
    val functions: List<FunctionInfo>,
    val classes: List<ClassInfo>,
    val dependencies: List<String>
)

@Serializable
data class FunctionInfo(
    val name: String,
    val parameters: List<ParameterInfo>,
    val returnType: String?,
    val description: String?
)

@Serializable
data class ParameterInfo(
    val name: String,
    val type: String?,
    val description: String?
)

@Serializable
data class ClassInfo(
    val name: String,
    val methods: List<FunctionInfo>,
    val properties: List<PropertyInfo>,
    val description: String?
)

@Serializable
data class PropertyInfo(
    val name: String,
    val type: String?,
    val description: String?
)

@Serializable
data class GeneratedDocumentation(
    val codeContext: CodeContext,
    val documentation: String,
    val apiDocumentation: String?,
    val examples: List<CodeExample>,
    val quality: DocumentationQuality
)

@Serializable
data class CodeExample(
    val title: String,
    val code: String,
    val description: String
)

@Serializable
data class DocumentationQuality(
    val completeness: Double,
    val clarity: Double,
    val accuracy: Double,
    val overallScore: Double
)

@Serializable
data class LogMessage(
    val id: String,
    val timestamp: Instant,
    val level: LogLevel,
    val message: String,
    val source: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL
}

@Serializable
data class LogAnalysis(
    val totalMessages: Int,
    val errorRate: Double,
    val patterns: List<LogPattern>,
    val anomalies: List<LogAnomaly>,
    val insights: List<LogInsight>,
    val recommendations: List<String>
)

@Serializable
data class LogPattern(
    val pattern: String,
    val frequency: Int,
    val severity: LogLevel,
    val description: String
)

@Serializable
data class LogAnomaly(
    val id: String,
    val message: String,
    val anomalyScore: Double,
    val type: AnomalyType,
    val timestamp: Instant
)

@Serializable
enum class AnomalyType {
    FREQUENCY, CONTENT, TIMING, SEVERITY
}

@Serializable
data class LogInsight(
    val type: InsightType,
    val description: String,
    val impact: String,
    val confidence: Double
)

@Serializable
enum class InsightType {
    ERROR_SPIKE, PERFORMANCE_DEGRADATION, SECURITY_ISSUE, SYSTEM_HEALTH
}

// Computer Vision Models

@Serializable
data class ImageData(
    val id: String,
    val data: ByteArray,
    val format: ImageFormat,
    val width: Int,
    val height: Int,
    val timestamp: Instant,
    val source: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ImageData
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
enum class ImageFormat {
    PNG, JPEG, GIF, BMP, TIFF
}

@Serializable
data class InfrastructureAnalysis(
    val imageId: String,
    val components: List<DetectedComponent>,
    val healthStatus: InfrastructureHealth,
    val issues: List<InfrastructureIssue>,
    val metrics: InfrastructureMetrics,
    val confidence: Double
)

@Serializable
data class DetectedComponent(
    val type: ComponentType,
    val boundingBox: BoundingBox,
    val confidence: Double,
    val status: ComponentStatus,
    val properties: Map<String, String>
)

@Serializable
enum class ComponentType {
    SERVER, NETWORK_DEVICE, STORAGE, CABLE, LED_INDICATOR, DISPLAY, RACK
}

@Serializable
data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

@Serializable
enum class ComponentStatus {
    HEALTHY, WARNING, CRITICAL, UNKNOWN
}

@Serializable
data class InfrastructureHealth(
    val overallScore: Double,
    val componentScores: Map<ComponentType, Double>,
    val issues: List<String>,
    val recommendations: List<String>
)

@Serializable
data class InfrastructureIssue(
    val id: String,
    val type: IssueType,
    val severity: IssueSeverity,
    val description: String,
    val location: BoundingBox,
    val confidence: Double
)

@Serializable
enum class IssueType {
    HARDWARE_FAILURE, OVERHEATING, CABLE_DISCONNECTED, LED_ERROR, HIGH_ERROR_RATE, PERFORMANCE_DEGRADATION
}

@Serializable
enum class IssueSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class InfrastructureMetrics(
    val temperatureIndicators: List<TemperatureReading>,
    val statusLights: List<StatusLight>,
    val cableConnections: List<CableConnection>,
    val displayReadings: List<DisplayReading>
)

@Serializable
data class TemperatureReading(
    val location: BoundingBox,
    val temperature: Double?,
    val status: TemperatureStatus
)

@Serializable
enum class TemperatureStatus {
    NORMAL, ELEVATED, HIGH, CRITICAL
}

@Serializable
data class StatusLight(
    val location: BoundingBox,
    val color: LightColor,
    val status: LightStatus
)

@Serializable
enum class LightColor {
    GREEN, YELLOW, RED, BLUE, WHITE
}

@Serializable
enum class LightStatus {
    ON, OFF, BLINKING
}

@Serializable
data class CableConnection(
    val location: BoundingBox,
    val connected: Boolean,
    val cableType: CableType
)

@Serializable
enum class CableType {
    ETHERNET, POWER, FIBER, USB, HDMI
}

@Serializable
data class DisplayReading(
    val location: BoundingBox,
    val text: String,
    val confidence: Double
)

@Serializable
data class VisualAnomaly(
    val id: String,
    val imageId: String,
    val type: VisualAnomalyType,
    val location: BoundingBox,
    val severity: AnomalySeverity,
    val description: String,
    val confidence: Double,
    val timestamp: Instant
)

@Serializable
enum class VisualAnomalyType {
    HARDWARE_CHANGE, UNEXPECTED_OBJECT, MISSING_COMPONENT, COLOR_ANOMALY, SHAPE_ANOMALY
}

@Serializable
enum class AnomalySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class VisualMonitoringData(
    val images: List<ImageData>,
    val timeRange: TimeRange,
    val monitoringPoints: List<MonitoringPoint>
)

@Serializable
data class MonitoringPoint(
    val id: String,
    val location: BoundingBox,
    val type: MonitoringType,
    val baseline: BaselineData
)

@Serializable
enum class MonitoringType {
    TEMPERATURE, STATUS_LIGHT, DISPLAY, CABLE_CONNECTION, GENERAL_HEALTH
}

@Serializable
data class BaselineData(
    val normalValues: List<Double>,
    val thresholds: Map<String, Double>,
    val lastUpdated: Instant
)

@Serializable
data class SystemHealthAssessment(
    val overallHealth: Double,
    val componentHealth: Map<String, Double>,
    val trends: List<HealthTrend>,
    val predictions: List<HealthPrediction>,
    val recommendations: List<String>
)

@Serializable
data class HealthTrend(
    val component: String,
    val trend: TrendDirection,
    val confidence: Double,
    val timeframe: Duration
)

@Serializable
enum class TrendDirection {
    IMPROVING, STABLE, DEGRADING
}

@Serializable
data class HealthPrediction(
    val component: String,
    val predictedHealth: Double,
    val timeHorizon: Duration,
    val confidence: Double
)

// Autonomous Decision Making Models

@Serializable
data class DecisionContext(
    val systemState: SystemState,
    val constraints: List<Constraint>,
    val objectives: List<Objective>,
    val availableActions: List<AvailableAction>,
    val riskTolerance: RiskTolerance
)

@Serializable
data class SystemState(
    val timestamp: Instant,
    val metrics: SystemMetrics,
    val performance: PerformanceData,
    val security: List<SecurityEvent>,
    val health: Double
)

@Serializable
data class SystemMetrics(
    val timestamp: Instant,
    val cpuUsage: Double,
    val memoryUsage: Double,
    val diskUsage: Double,
    val networkTraffic: Double,
    val activeConnections: Int
)

@Serializable
data class PerformanceData(
    val timestamp: Instant,
    val responseTime: Double,
    val throughput: Double,
    val errorRate: Double,
    val availability: Double
)

@Serializable
data class SecurityEvent(
    val id: String,
    val timestamp: Instant,
    val type: SecurityEventType,
    val severity: SecuritySeverity,
    val description: String,
    val sourceIp: String,
    val targetIp: String,
    val metadata: Map<String, String>
)

@Serializable
enum class SecurityEventType {
    INTRUSION_ATTEMPT, MALWARE_DETECTION, SUSPICIOUS_ACTIVITY, AUTHENTICATION_FAILURE, DATA_BREACH
}

@Serializable
enum class SecuritySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class Constraint(
    val type: ConstraintType,
    val value: Double,
    val description: String
)

@Serializable
enum class ConstraintType {
    BUDGET, PERFORMANCE, AVAILABILITY, SECURITY, COMPLIANCE
}

@Serializable
data class Objective(
    val type: ObjectiveType,
    val weight: Double,
    val target: Double,
    val description: String
)

@Serializable
enum class ObjectiveType {
    MINIMIZE_COST, MAXIMIZE_PERFORMANCE, MAXIMIZE_AVAILABILITY, MINIMIZE_RISK
}

@Serializable
data class AvailableAction(
    val id: String,
    val type: ActionType,
    val description: String,
    val cost: Double,
    val impact: ActionImpact,
    val prerequisites: List<String>
)

@Serializable
enum class ActionType {
    SCALE_UP, SCALE_DOWN, RESTART_SERVICE, DEPLOY_UPDATE, ROLLBACK, BLOCK_IP, ALERT_SECURITY_TEAM, OPTIMIZE_CONFIGURATION
}

@Serializable
data class ActionImpact(
    val performance: Double,
    val availability: Double,
    val security: Double,
    val cost: Double
)

@Serializable
enum class RiskTolerance {
    CONSERVATIVE, MODERATE, AGGRESSIVE
}

@Serializable
data class AutonomousDecision(
    val id: String,
    val context: DecisionContext,
    val selectedActions: List<SelectedAction>,
    val reasoning: DecisionReasoning,
    val confidence: Double,
    val estimatedOutcome: EstimatedOutcome,
    val timestamp: Instant
)

@Serializable
data class SelectedAction(
    val action: AvailableAction,
    val priority: Int,
    val scheduledTime: Instant,
    val dependencies: List<String>
)

@Serializable
data class DecisionReasoning(
    val primaryFactors: List<String>,
    val tradeoffs: List<Tradeoff>,
    val riskAssessment: RiskAssessment,
    val alternativeOptions: List<AlternativeOption>
)

@Serializable
data class Tradeoff(
    val aspect: String,
    val benefit: String,
    val cost: String,
    val justification: String
)

@Serializable
data class RiskAssessment(
    val overallRisk: RiskLevel,
    val riskFactors: List<RiskFactor>,
    val mitigationStrategies: List<String>
)

@Serializable
enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class RiskFactor(
    val factor: String,
    val probability: Double,
    val impact: Double,
    val riskScore: Double
)

@Serializable
data class AlternativeOption(
    val description: String,
    val pros: List<String>,
    val cons: List<String>,
    val score: Double
)

@Serializable
data class EstimatedOutcome(
    val performanceImprovement: Double,
    val costImpact: Double,
    val riskReduction: Double,
    val timeToEffect: Duration,
    val confidence: Double
)

@Serializable
data class SystemIssue(
    val id: String,
    val type: IssueType,
    val severity: IssueSeverity,
    val description: String,
    val affectedComponents: List<String>,
    val timestamp: Instant
)

@Serializable
data class HealingPlan(
    val issueId: String,
    val actions: List<HealingAction>,
    val estimatedRecoveryTime: Duration,
    val successProbability: Double,
    val rollbackPlan: RollbackPlan
)

@Serializable
data class HealingAction(
    val id: String,
    val type: ActionType,
    val description: String,
    val parameters: Map<String, String>,
    val order: Int,
    val timeout: Duration
)

@Serializable
data class RollbackPlan(
    val actions: List<HealingAction>,
    val triggers: List<RollbackTrigger>
)

@Serializable
data class RollbackTrigger(
    val condition: String,
    val threshold: Double,
    val description: String
)

@Serializable
data class ResourceConstraints(
    val maxCpuUsage: Double,
    val maxMemoryUsage: Double,
    val maxCost: Double,
    val availabilityRequirement: Double,
    val performanceRequirement: Double
)

@Serializable
data class ResourceAllocationPlan(
    val allocations: List<ResourceAllocation>,
    val totalCost: Double,
    val expectedPerformance: Double,
    val riskLevel: RiskLevel,
    val implementation: ImplementationPlan
)

@Serializable
data class ResourceAllocation(
    val resource: String,
    val currentAllocation: Double,
    val recommendedAllocation: Double,
    val justification: String,
    val impact: AllocationImpact
)

@Serializable
data class AllocationImpact(
    val performance: Double,
    val cost: Double,
    val availability: Double
)

@Serializable
data class ImplementationPlan(
    val phases: List<ImplementationPhase>,
    val totalDuration: Duration,
    val rollbackStrategy: String
)

@Serializable
data class ImplementationPhase(
    val name: String,
    val actions: List<String>,
    val duration: Duration,
    val dependencies: List<String>
)

// Model Management Models

@Serializable
data class ModelVersion(
    val modelId: String,
    val version: String,
    val createdAt: Instant,
    val performance: ModelPerformance,
    val metadata: Map<String, String>,
    val status: ModelStatus
)

@Serializable
enum class ModelStatus {
    TRAINING, VALIDATION, DEPLOYED, ARCHIVED, DEPRECATED
}

@Serializable
data class ModelPerformance(
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val customMetrics: Map<String, Double>
)

@Serializable
data class ABTestConfig(
    val name: String,
    val trafficSplit: Double,
    val duration: Duration,
    val successMetrics: List<String>,
    val significanceLevel: Double
)

@Serializable
data class ABTestResult(
    val testId: String,
    val modelA: String,
    val modelB: String,
    val winner: String?,
    val confidence: Double,
    val metrics: Map<String, ABTestMetric>,
    val recommendation: String
)

@Serializable
data class ABTestMetric(
    val modelAValue: Double,
    val modelBValue: Double,
    val improvement: Double,
    val pValue: Double,
    val significant: Boolean
)

@Serializable
data class ModelExplanation(
    val modelId: String,
    val input: String,
    val prediction: String,
    val featureImportance: Map<String, Double>,
    val explanation: String,
    val confidence: Double
)

@Serializable
data class InterpretationContext(
    val domain: String,
    val userLevel: UserLevel,
    val format: ExplanationFormat
)

@Serializable
enum class UserLevel {
    TECHNICAL, BUSINESS, EXECUTIVE
}

@Serializable
enum class ExplanationFormat {
    TEXT, VISUAL, INTERACTIVE, SUMMARY
}

@Serializable
data class PredictionInterpretation(
    val prediction: String,
    val interpretation: String,
    val keyFactors: List<String>,
    val confidence: Double,
    val recommendations: List<String>
)

// AI Insights and Recommendations

@Serializable
data class AIInsight(
    val id: String,
    val type: AIInsightType,
    val title: String,
    val description: String,
    val confidence: Double,
    val impact: AIImpact,
    val recommendations: List<String>,
    val timestamp: Instant
)

@Serializable
enum class AIInsightType {
    PERFORMANCE, SECURITY, RESOURCE_OPTIMIZATION, COST_OPTIMIZATION, RELIABILITY
}

@Serializable
enum class AIImpact {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class AutonomousRecommendation(
    val id: String,
    val type: RecommendationType,
    val title: String,
    val description: String,
    val priority: RecommendationPriority,
    val confidence: Double,
    val actions: List<RecommendedAction>,
    val estimatedImpact: String,
    val timestamp: Instant
)

@Serializable
enum class RecommendationType {
    AUTO_SCALING, SELF_HEALING, SECURITY_RESPONSE, PERFORMANCE_OPTIMIZATION, COST_OPTIMIZATION
}

@Serializable
enum class RecommendationPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class RecommendedAction(
    val type: ActionType,
    val description: String,
    val parameters: Map<String, String>
)

// Common Models

@Serializable
data class TimeRange(
    val start: Long,
    val end: Long
)

@Serializable
data class ModelTrainingResult(
    val modelId: String,
    val success: Boolean,
    val metrics: TrainingMetrics,
    val trainingTime: Long,
    val modelSize: Long,
    val version: String
)

@Serializable
data class TrainingMetrics(
    val loss: Double,
    val accuracy: Double,
    val validationLoss: Double,
    val validationAccuracy: Double,
    val epochs: Int
)

@Serializable
data class ModelEvaluation(
    val modelId: String,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val confusionMatrix: List<List<Int>>,
    val rocAuc: Double,
    val customMetrics: Map<String, Double>
)

typealias Duration = kotlin.time.Duration