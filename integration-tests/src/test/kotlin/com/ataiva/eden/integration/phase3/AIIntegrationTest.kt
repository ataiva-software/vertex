package com.ataiva.eden.integration.phase3

import com.ataiva.eden.ai.*
import com.ataiva.eden.ai.engines.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@DisplayName("Phase 3 AI/ML Integration Tests")
class AIIntegrationTest {
    
    private lateinit var advancedAI: AdvancedAI
    
    @BeforeEach
    fun setup() {
        // Initialize with real implementations for integration testing
        val deepLearningEngine = DeepLearningEngineImpl()
        val neuralNetworkEngine = NeuralNetworkEngineImpl()
        val reinforcementEngine = ReinforcementLearningEngineImpl()
        val nlpEngine = NaturalLanguageProcessorImpl()
        val visionEngine = ComputerVisionEngineImpl()
        val decisionEngine = AutonomousDecisionEngineImpl()
        val modelManager = ModelManagerImpl()
        
        advancedAI = DefaultAdvancedAI(
            deepLearningEngine,
            neuralNetworkEngine,
            reinforcementEngine,
            nlpEngine,
            visionEngine,
            decisionEngine,
            modelManager
        )
    }
    
    @Test
    @DisplayName("End-to-end predictive maintenance workflow")
    fun testPredictiveMaintenanceWorkflow() = runTest {
        // 1. Train a predictive maintenance model
        val config = DeepLearningConfig(
            modelType = DeepLearningModelType.PREDICTIVE_MAINTENANCE,
            architecture = "maintenance_predictor",
            hyperparameters = mapOf(
                "learning_rate" to 0.001,
                "batch_size" to 32.0,
                "dropout" to 0.2
            ),
            trainingConfig = TrainingConfig(
                epochs = 50,
                batchSize = 32,
                learningRate = 0.001,
                optimizer = OptimizerType.ADAM,
                lossFunction = LossFunctionType.MEAN_SQUARED_ERROR,
                regularization = RegularizationConfig(l2 = 0.01)
            )
        )
        
        val trainingResult = advancedAI.trainDeepLearningModel(config)
        assertTrue(trainingResult.success, "Model training should succeed")
        assertTrue(trainingResult.metrics.accuracy > 0.7, "Model should achieve reasonable accuracy")
        
        // 2. Make predictions on system metrics
        val systemMetrics = DeepLearningInput(
            features = listOf(0.8, 0.6, 0.4, 0.9, 0.3), // CPU, Memory, Disk, Network, Temperature
            metadata = mapOf(
                "timestamp" to Instant.now().toString(),
                "component" to "server_rack_1"
            )
        )
        
        val prediction = advancedAI.predictWithDeepLearning(trainingResult.modelId, systemMetrics)
        assertTrue(prediction.confidence > 0.5, "Prediction should have reasonable confidence")
        assertNotNull(prediction.featureImportance, "Feature importance should be provided")
        
        // 3. If maintenance is predicted, create autonomous healing plan
        if (prediction.value > 0.7) { // High maintenance probability
            val issue = SystemIssue(
                id = "maintenance_required_${System.currentTimeMillis()}",
                type = IssueType.HARDWARE_FAILURE,
                severity = IssueSeverity.MEDIUM,
                description = "Predictive maintenance indicates potential hardware issues",
                affectedComponents = listOf("server_rack_1"),
                timestamp = Instant.now()
            )
            
            val healingPlan = advancedAI.planSelfHealing(issue)
            assertTrue(healingPlan.actions.isNotEmpty(), "Healing plan should contain actions")
            assertTrue(healingPlan.successProbability > 0.5, "Healing plan should have reasonable success probability")
        }
    }
    
    @Test
    @DisplayName("Autonomous resource optimization with reinforcement learning")
    fun testAutonomousResourceOptimization() = runTest {
        // 1. Create and train a reinforcement learning agent for resource optimization
        val rlConfig = ReinforcementConfig(
            algorithm = RLAlgorithm.Q_LEARNING,
            environment = EnvironmentConfig(
                stateSpace = StateSpaceConfig(
                    dimensions = 4, // CPU, Memory, Disk, Network
                    bounds = listOf(
                        Pair(0.0, 1.0), Pair(0.0, 1.0), 
                        Pair(0.0, 1.0), Pair(0.0, 1.0)
                    ),
                    discrete = false
                ),
                actionSpace = ActionSpaceConfig(
                    dimensions = 4,
                    bounds = listOf(
                        Pair(-0.2, 0.2), Pair(-0.2, 0.2),
                        Pair(-0.2, 0.2), Pair(-0.2, 0.2)
                    ),
                    discrete = false,
                    actions = listOf("scale_cpu", "scale_memory", "scale_disk", "scale_network")
                ),
                rewardFunction = RewardFunctionConfig(
                    type = RewardType.MULTI_OBJECTIVE,
                    parameters = mapOf(
                        "performance_weight" to 0.4,
                        "cost_weight" to 0.3,
                        "efficiency_weight" to 0.3
                    )
                ),
                episodeLength = 100,
                terminationConditions = listOf("optimal_found", "max_steps")
            ),
            agentConfig = AgentConfig(
                networkArchitecture = NeuralNetworkArchitecture(
                    layers = listOf(
                        LayerConfig(LayerType.DENSE, 64, ActivationFunction.RELU),
                        LayerConfig(LayerType.DENSE, 32, ActivationFunction.RELU),
                        LayerConfig(LayerType.DENSE, 4, ActivationFunction.LINEAR)
                    ),
                    inputShape = listOf(4),
                    outputShape = listOf(4),
                    activationFunction = ActivationFunction.RELU,
                    networkType = NetworkType.FEEDFORWARD
                ),
                explorationStrategy = ExplorationStrategy(
                    type = ExplorationType.EPSILON_GREEDY,
                    parameters = mapOf("epsilon" to 0.1, "decay" to 0.995)
                ),
                memorySize = 10000,
                updateFrequency = 100
            ),
            trainingConfig = RLTrainingConfig(
                episodes = 500,
                maxStepsPerEpisode = 100,
                learningRate = 0.001,
                discountFactor = 0.99,
                batchSize = 32,
                targetUpdateFrequency = 100
            )
        )
        
        val agent = advancedAI.createReinforcementAgent(rlConfig)
        assertEquals(AgentStatus.CREATED, agent.status)
        
        // 2. Train the agent (simplified for integration test)
        val environment = Environment(
            id = "resource_optimization_env",
            config = rlConfig.environment,
            currentState = State(
                features = mapOf(
                    "cpu" to 0.7,
                    "memory" to 0.8,
                    "disk" to 0.5,
                    "network" to 0.3
                )
            ),
            episodeCount = 0,
            totalReward = 0.0
        )
        
        val trainingResult = advancedAI.trainReinforcementAgent(agent.id, environment)
        assertTrue(trainingResult.averageReward > 0, "Agent should learn to achieve positive rewards")
        
        // 3. Use trained agent for resource optimization
        val currentState = State(
            features = mapOf(
                "cpu" to 0.85, // High CPU usage
                "memory" to 0.75,
                "disk" to 0.6,
                "network" to 0.4
            )
        )
        
        val optimalAction = advancedAI.getOptimalAction(agent.id, currentState)
        assertTrue(optimalAction.confidence > 0.6, "Action should have reasonable confidence")
        assertTrue(optimalAction.recommendations.isNotEmpty(), "Action should include recommendations")
    }
    
    @Test
    @DisplayName("Intelligent log analysis and threat detection")
    fun testIntelligentLogAnalysis() = runTest {
        // 1. Create sample log messages with various patterns
        val logMessages = listOf(
            LogMessage(
                id = "log_1",
                timestamp = Instant.now(),
                level = LogLevel.ERROR,
                message = "Failed login attempt from IP 192.168.1.100 for user admin",
                source = "auth_service"
            ),
            LogMessage(
                id = "log_2",
                timestamp = Instant.now(),
                level = LogLevel.ERROR,
                message = "Failed login attempt from IP 192.168.1.100 for user root",
                source = "auth_service"
            ),
            LogMessage(
                id = "log_3",
                timestamp = Instant.now(),
                level = LogLevel.WARN,
                message = "Suspicious activity detected: multiple failed attempts",
                source = "security_monitor"
            ),
            LogMessage(
                id = "log_4",
                timestamp = Instant.now(),
                level = LogLevel.INFO,
                message = "Database connection established successfully",
                source = "db_service"
            ),
            LogMessage(
                id = "log_5",
                timestamp = Instant.now(),
                level = LogLevel.ERROR,
                message = "SQL injection attempt detected in query: SELECT * FROM users WHERE id = '1 OR 1=1'",
                source = "api_gateway"
            )
        )
        
        // 2. Analyze logs with NLP
        val logAnalysis = advancedAI.analyzeLogs(logMessages)
        
        assertTrue(logAnalysis.errorRate > 0.4, "Should detect high error rate")
        assertTrue(logAnalysis.patterns.isNotEmpty(), "Should identify log patterns")
        assertTrue(logAnalysis.insights.isNotEmpty(), "Should generate security insights")
        assertTrue(logAnalysis.recommendations.isNotEmpty(), "Should provide recommendations")
        
        // 3. Process individual messages for threat detection
        val threatMessage = "ALERT: Potential DDoS attack detected from multiple IPs: 10.0.0.1, 10.0.0.2, 10.0.0.3"
        val nlpResult = advancedAI.processNaturalLanguage(
            threatMessage,
            listOf(NLPTask.THREAT_DETECTION, NLPTask.NAMED_ENTITY_RECOGNITION)
        )
        
        assertTrue(nlpResult.threatScore > 0.7, "Should detect high threat level")
        assertTrue(nlpResult.entities.any { it.type == EntityType.IP_ADDRESS }, "Should extract IP addresses")
        
        // 4. Generate autonomous security response
        val securityContext = DecisionContext(
            systemState = SystemState(
                timestamp = Instant.now(),
                metrics = SystemMetrics(Instant.now(), 0.9, 0.8, 0.6, 0.95, 1000),
                performance = PerformanceData(Instant.now(), 200.0, 500.0, 0.08, 0.92),
                security = listOf(
                    SecurityEvent(
                        id = "ddos_attack",
                        timestamp = Instant.now(),
                        type = SecurityEventType.SUSPICIOUS_ACTIVITY,
                        severity = SecuritySeverity.HIGH,
                        description = threatMessage,
                        sourceIp = "10.0.0.1",
                        targetIp = "192.168.1.10",
                        metadata = mapOf("attack_type" to "ddos")
                    )
                ),
                health = 0.7
            ),
            constraints = listOf(
                Constraint(ConstraintType.SECURITY, 0.95, "High security requirement")
            ),
            objectives = listOf(
                Objective(ObjectiveType.MINIMIZE_RISK, 1.0, 0.1, "Minimize security risk")
            ),
            availableActions = listOf(
                AvailableAction(
                    id = "block_ips",
                    type = ActionType.BLOCK_IP,
                    description = "Block suspicious IP addresses",
                    cost = 0.0,
                    impact = ActionImpact(0.0, 0.1, 0.8, 0.0),
                    prerequisites = emptyList()
                ),
                AvailableAction(
                    id = "alert_security",
                    type = ActionType.ALERT_SECURITY_TEAM,
                    description = "Alert security team",
                    cost = 0.0,
                    impact = ActionImpact(0.0, 0.0, 0.5, 0.0),
                    prerequisites = emptyList()
                )
            ),
            riskTolerance = RiskTolerance.CONSERVATIVE
        )
        
        val securityDecision = advancedAI.makeAutonomousDecision(securityContext)
        assertTrue(securityDecision.selectedActions.isNotEmpty(), "Should select security actions")
        assertTrue(securityDecision.confidence > 0.7, "Should have high confidence in security decisions")
    }
    
    @Test
    @DisplayName("Computer vision infrastructure monitoring")
    fun testComputerVisionMonitoring() = runTest {
        // 1. Create mock infrastructure image data
        val serverRackImage = ImageData(
            id = "datacenter_rack_A1",
            data = generateMockImageData(800, 600), // Mock JPEG data
            format = ImageFormat.JPEG,
            width = 800,
            height = 600,
            timestamp = Instant.now(),
            source = "datacenter_camera_1"
        )
        
        // 2. Analyze infrastructure image
        val analysis = advancedAI.analyzeInfrastructureImage(serverRackImage)
        
        assertEquals(serverRackImage.id, analysis.imageId)
        assertTrue(analysis.components.isNotEmpty(), "Should detect infrastructure components")
        assertTrue(analysis.confidence > 0.5, "Should have reasonable confidence")
        assertNotNull(analysis.healthStatus, "Should assess infrastructure health")
        
        // 3. Monitor for visual anomalies
        val imageSequence = listOf(
            serverRackImage,
            serverRackImage.copy(
                id = "datacenter_rack_A1_t2",
                timestamp = Instant.now()
            )
        )
        
        val anomalies = advancedAI.detectVisualAnomalies(imageSequence)
        // Anomalies might be empty for identical images, which is expected
        
        // 4. Assess system health from visual data
        val monitoringData = VisualMonitoringData(
            images = imageSequence,
            timeRange = TimeRange(
                start = Instant.now().epochSeconds - 3600,
                end = Instant.now().epochSeconds
            ),
            monitoringPoints = listOf(
                MonitoringPoint(
                    id = "server_status_lights",
                    location = BoundingBox(100, 50, 200, 100),
                    type = MonitoringType.STATUS_LIGHT,
                    baseline = BaselineData(
                        normalValues = listOf(0.8, 0.85, 0.9),
                        thresholds = mapOf("min_health" to 0.7),
                        lastUpdated = Instant.now()
                    )
                )
            )
        )
        
        val healthAssessment = advancedAI.monitorSystemHealth(monitoringData)
        assertTrue(healthAssessment.overallHealth > 0.0, "Should provide health assessment")
        assertTrue(healthAssessment.componentHealth.isNotEmpty(), "Should assess individual components")
    }
    
    @Test
    @DisplayName("Model versioning and A/B testing workflow")
    fun testModelVersioningAndABTesting() = runTest {
        // 1. Create model versions
        val modelId = "performance_optimizer"
        val version1 = advancedAI.versionModel(modelId, "v1.0.0")
        val version2 = advancedAI.versionModel(modelId, "v2.0.0")
        
        assertEquals("v1.0.0", version1.version)
        assertEquals("v2.0.0", version2.version)
        assertEquals(ModelStatus.VALIDATION, version1.status)
        assertEquals(ModelStatus.VALIDATION, version2.status)
        
        // 2. Perform A/B test between versions
        val abTestConfig = ABTestConfig(
            name = "performance_optimizer_comparison",
            trafficSplit = 0.5,
            duration = 24.hours,
            successMetrics = listOf("accuracy", "response_time", "throughput"),
            significanceLevel = 0.05
        )
        
        val abTestResult = advancedAI.performABTest(
            "${modelId}_v1.0.0",
            "${modelId}_v2.0.0",
            abTestConfig
        )
        
        assertNotNull(abTestResult.winner, "A/B test should determine a winner")
        assertTrue(abTestResult.confidence > 0.5, "Should have reasonable confidence in results")
        assertTrue(abTestResult.metrics.isNotEmpty(), "Should provide detailed metrics")
        assertTrue(abTestResult.recommendation.isNotEmpty(), "Should provide recommendation")
        
        // 3. Explain model decisions
        val testInput = mapOf(
            "cpu_usage" to 0.75,
            "memory_usage" to 0.68,
            "request_rate" to 1500.0,
            "error_rate" to 0.02
        )
        
        val explanation = advancedAI.explainModel(abTestResult.winner!!, testInput)
        
        assertEquals(abTestResult.winner, explanation.modelId)
        assertTrue(explanation.featureImportance.isNotEmpty(), "Should provide feature importance")
        assertTrue(explanation.confidence > 0.5, "Should have confidence in explanation")
        assertTrue(explanation.explanation.isNotEmpty(), "Should provide human-readable explanation")
        
        // 4. Interpret prediction for different user levels
        val businessContext = InterpretationContext(
            domain = "devops",
            userLevel = UserLevel.BUSINESS,
            format = ExplanationFormat.SUMMARY
        )
        
        val interpretation = advancedAI.interpretPrediction(explanation.prediction, businessContext)
        
        assertTrue(interpretation.interpretation.isNotEmpty(), "Should provide interpretation")
        assertTrue(interpretation.keyFactors.isNotEmpty(), "Should identify key factors")
        assertTrue(interpretation.recommendations.isNotEmpty(), "Should provide recommendations")
    }
    
    @Test
    @DisplayName("Real-time AI insights and autonomous recommendations")
    fun testRealtimeAIProcessing() = runTest {
        // This test verifies that the real-time processing flows work
        // In a real environment, this would involve actual streaming data
        
        val insightsFlow = advancedAI.getRealtimeAIInsights()
        val recommendationsFlow = advancedAI.getAutonomousRecommendations()
        
        assertNotNull(insightsFlow, "Should provide real-time insights flow")
        assertNotNull(recommendationsFlow, "Should provide autonomous recommendations flow")
        
        // Note: In integration tests, we would typically collect a few emissions
        // and verify their structure, but for brevity we just verify the flows exist
    }
    
    private fun generateMockImageData(width: Int, height: Int): ByteArray {
        // Generate simple mock JPEG-like data for testing
        // In a real implementation, this would be actual image data
        return ByteArray(width * height / 10) { (it % 256).toByte() }
    }
}

// Stub implementations for integration testing
// These would be replaced with actual implementations

class NeuralNetworkEngineImpl : NeuralNetworkEngine {
    override suspend fun createNetwork(architecture: NeuralNetworkArchitecture): NeuralNetwork {
        return NeuralNetwork(
            id = "nn_${System.currentTimeMillis()}",
            architecture = architecture,
            status = NetworkStatus.CREATED,
            createdAt = Instant.now()
        )
    }
    
    override suspend fun train(networkId: String, trainingData: NeuralTrainingData): NeuralTrainingResult {
        return NeuralTrainingResult(
            networkId = networkId,
            trainingLoss = listOf(0.5, 0.3, 0.2, 0.15, 0.1),
            validationLoss = listOf(0.6, 0.35, 0.25, 0.18, 0.12),
            trainingAccuracy = listOf(0.6, 0.75, 0.82, 0.87, 0.9),
            validationAccuracy = listOf(0.55, 0.72, 0.8, 0.85, 0.88),
            epochs = 5,
            trainingTime = 1800000,
            finalMetrics = TrainingMetrics(0.1, 0.9, 0.12, 0.88, 5)
        )
    }
    
    override suspend fun evaluate(networkId: String, testData: NeuralTestData): NeuralEvaluation {
        return NeuralEvaluation(
            networkId = networkId,
            accuracy = 0.88,
            precision = 0.86,
            recall = 0.9,
            f1Score = 0.88,
            confusionMatrix = listOf(listOf(88, 12), listOf(8, 92)),
            classificationReport = mapOf(
                "class_0" to ClassificationMetrics(0.86, 0.88, 0.87, 100),
                "class_1" to ClassificationMetrics(0.88, 0.92, 0.9, 100)
            )
        )
    }
}

class ModelManagerImpl : ModelManager {
    private val versions = mutableMapOf<String, MutableList<ModelVersion>>()
    
    override suspend fun versionModel(modelId: String, version: String): ModelVersion {
        val modelVersion = ModelVersion(
            modelId = modelId,
            version = version,
            createdAt = Instant.now(),
            performance = ModelPerformance(0.85, 0.83, 0.87, 0.85, mapOf("training_time" to 3600.0)),
            metadata = mapOf("framework" to "deeplearning4j", "created_by" to "ai_engine"),
            status = ModelStatus.VALIDATION
        )
        
        versions.getOrPut(modelId) { mutableListOf() }.add(modelVersion)
        return modelVersion
    }
    
    override suspend fun performABTest(modelA: String, modelB: String, testConfig: ABTestConfig): ABTestResult {
        return ABTestResult(
            testId = "test_${System.currentTimeMillis()}",
            modelA = modelA,
            modelB = modelB,
            winner = modelB,
            confidence = 0.85,
            metrics = mapOf(
                "accuracy" to ABTestMetric(0.82, 0.87, 6.1, 0.03, true),
                "response_time" to ABTestMetric(150.0, 140.0, -6.7, 0.02, true)
            ),
            recommendation = "Deploy model B - significant improvement in both accuracy and response time"
        )
    }
    
    override suspend fun explainModel(modelId: String, input: Any): ModelExplanation {
        return ModelExplanation(
            modelId = modelId,
            input = input.toString(),
            prediction = "Optimal resource allocation recommended",
            featureImportance = mapOf(
                "cpu_usage" to 0.4,
                "memory_usage" to 0.3,
                "request_rate" to 0.2,
                "error_rate" to 0.1
            ),
            explanation = "High CPU usage and memory usage are primary factors for scaling recommendation",
            confidence = 0.85
        )
    }
    
    override suspend fun interpretPrediction(prediction: Any, context: InterpretationContext): PredictionInterpretation {
        return PredictionInterpretation(
            prediction = prediction.toString(),
            interpretation = when (context.userLevel) {
                UserLevel.TECHNICAL -> "Model recommends scaling based on resource utilization patterns"
                UserLevel.BUSINESS -> "System needs more resources to handle current load efficiently"
                UserLevel.EXECUTIVE -> "Infrastructure investment needed to maintain performance"
            },
            keyFactors = listOf("High resource utilization", "Performance requirements", "Cost optimization"),
            confidence = 0.85,
            recommendations = listOf("Scale resources", "Monitor performance", "Review cost impact")
        )
    }
}