package com.ataiva.eden.ai.engines

import com.ataiva.eden.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Reinforcement Learning Engine implementation
 * Provides Q-Learning, Deep Q-Network, and Policy Gradient algorithms
 * for autonomous resource optimization and decision making
 */
class ReinforcementLearningEngineImpl : ReinforcementLearningEngine {
    
    private val agents = ConcurrentHashMap<String, RLAgent>()
    private val environments = ConcurrentHashMap<String, RLEnvironment>()
    private val qTables = ConcurrentHashMap<String, QTable>()
    private val policies = ConcurrentHashMap<String, Policy>()
    
    override suspend fun createAgent(config: ReinforcementConfig): ReinforcementAgent = withContext(Dispatchers.IO) {
        logger.info { "Creating reinforcement learning agent: ${config.algorithm}" }
        
        val agentId = generateAgentId(config.algorithm)
        val agent = RLAgent(
            id = agentId,
            config = config,
            qTable = when (config.algorithm) {
                RLAlgorithm.Q_LEARNING -> QTable(config.environment.stateSpace.dimensions, config.environment.actionSpace.dimensions)
                else -> null
            },
            policy = Policy(config.environment.actionSpace.actions),
            explorationRate = config.agentConfig.explorationStrategy.parameters["epsilon"] ?: 0.1
        )
        
        agents[agentId] = agent
        
        // Create corresponding environment
        val environment = RLEnvironment(
            id = "${agentId}_env",
            config = config.environment,
            currentState = generateInitialState(config.environment),
            episodeCount = 0,
            totalReward = 0.0
        )
        
        environments["${agentId}_env"] = environment
        
        ReinforcementAgent(
            id = agentId,
            config = config,
            status = AgentStatus.CREATED,
            performance = AgentPerformance(
                averageReward = 0.0,
                episodeRewards = emptyList(),
                explorationRate = agent.explorationRate,
                convergenceMetrics = ConvergenceMetrics(
                    converged = false,
                    convergenceEpisode = null,
                    stabilityScore = 0.0
                )
            ),
            createdAt = Instant.now()
        )
    }
    
    override suspend fun train(agentId: String, environment: Environment): ReinforcementTrainingResult = withContext(Dispatchers.IO) {
        logger.info { "Training reinforcement learning agent: $agentId" }
        
        val agent = agents[agentId] ?: throw IllegalArgumentException("Agent not found: $agentId")
        val rlEnv = environments["${agentId}_env"] ?: throw IllegalArgumentException("Environment not found for agent: $agentId")
        
        val startTime = System.currentTimeMillis()
        val episodeRewards = mutableListOf<Double>()
        var convergenceEpisode: Int? = null
        
        try {
            for (episode in 0 until agent.config.trainingConfig.episodes) {
                val episodeReward = runEpisode(agent, rlEnv, episode)
                episodeRewards.add(episodeReward)
                
                // Update exploration rate
                updateExplorationRate(agent, episode)
                
                // Check for convergence
                if (episode > 100 && checkConvergence(episodeRewards, episode)) {
                    convergenceEpisode = episode
                    logger.info { "Agent $agentId converged at episode $episode" }
                }
                
                // Log progress
                if (episode % 100 == 0) {
                    val avgReward = episodeRewards.takeLast(100).average()
                    logger.info { "Episode $episode: Average reward = $avgReward, Exploration rate = ${agent.explorationRate}" }
                }
            }
            
            val trainingTime = System.currentTimeMillis() - startTime
            val totalReward = episodeRewards.sum()
            val averageReward = episodeRewards.average()
            
            // Update agent performance
            val updatedAgent = agent.copy(
                performance = AgentPerformance(
                    averageReward = averageReward,
                    episodeRewards = episodeRewards,
                    explorationRate = agent.explorationRate,
                    convergenceMetrics = ConvergenceMetrics(
                        converged = convergenceEpisode != null,
                        convergenceEpisode = convergenceEpisode,
                        stabilityScore = calculateStabilityScore(episodeRewards)
                    )
                )
            )
            agents[agentId] = updatedAgent
            
            logger.info { "Training completed for agent $agentId: Average reward = $averageReward" }
            
            ReinforcementTrainingResult(
                agentId = agentId,
                episodes = agent.config.trainingConfig.episodes,
                totalReward = totalReward,
                averageReward = averageReward,
                convergenceEpisode = convergenceEpisode,
                trainingTime = trainingTime,
                finalPolicy = PolicyMetrics(
                    averageReturn = averageReward,
                    standardDeviation = calculateStandardDeviation(episodeRewards),
                    maxReturn = episodeRewards.maxOrNull() ?: 0.0,
                    minReturn = episodeRewards.minOrNull() ?: 0.0
                )
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to train agent: $agentId" }
            throw e
        }
    }
    
    override suspend fun getOptimalAction(agentId: String, state: State): Action = withContext(Dispatchers.IO) {
        val agent = agents[agentId] ?: throw IllegalArgumentException("Agent not found: $agentId")
        
        val action = when (agent.config.algorithm) {
            RLAlgorithm.Q_LEARNING -> getQLearningAction(agent, state, greedy = true)
            RLAlgorithm.DEEP_Q_NETWORK -> getDQNAction(agent, state, greedy = true)
            RLAlgorithm.POLICY_GRADIENT -> getPolicyGradientAction(agent, state)
            else -> getRandomAction(agent.config.environment.actionSpace)
        }
        
        Action(
            type = action.type,
            parameters = action.parameters,
            confidence = action.confidence,
            description = generateActionDescription(action, state),
            recommendations = generateActionRecommendations(action, state)
        )
    }
    
    private suspend fun runEpisode(agent: RLAgent, environment: RLEnvironment, episode: Int): Double {
        var state = resetEnvironment(environment)
        var totalReward = 0.0
        var step = 0
        
        while (step < agent.config.trainingConfig.maxStepsPerEpisode) {
            // Select action
            val action = selectAction(agent, state, episode)
            
            // Execute action and observe reward and next state
            val (nextState, reward, done) = executeAction(environment, state, action)
            
            // Update agent (Q-table, policy, etc.)
            updateAgent(agent, state, action, reward, nextState, done)
            
            totalReward += reward
            state = nextState
            step++
            
            if (done) break
        }
        
        return totalReward
    }
    
    private fun selectAction(agent: RLAgent, state: State, episode: Int): RLAction {
        return when (agent.config.algorithm) {
            RLAlgorithm.Q_LEARNING -> getQLearningAction(agent, state, greedy = false)
            RLAlgorithm.DEEP_Q_NETWORK -> getDQNAction(agent, state, greedy = false)
            RLAlgorithm.POLICY_GRADIENT -> getPolicyGradientAction(agent, state)
            else -> getRandomAction(agent.config.environment.actionSpace)
        }
    }
    
    private fun getQLearningAction(agent: RLAgent, state: State, greedy: Boolean): RLAction {
        val qTable = agent.qTable ?: throw IllegalStateException("Q-table not initialized")
        val stateIndex = stateToIndex(state, agent.config.environment.stateSpace)
        
        val actionIndex = if (!greedy && Random.nextDouble() < agent.explorationRate) {
            // Explore: random action
            Random.nextInt(agent.config.environment.actionSpace.actions.size)
        } else {
            // Exploit: best action according to Q-table
            qTable.getBestAction(stateIndex)
        }
        
        val actionName = agent.config.environment.actionSpace.actions[actionIndex]
        
        return RLAction(
            type = actionName,
            parameters = generateActionParameters(actionName, state),
            confidence = if (greedy) qTable.getQValue(stateIndex, actionIndex) else 0.5
        )
    }
    
    private fun getDQNAction(agent: RLAgent, state: State, greedy: Boolean): RLAction {
        // Simplified DQN action selection
        // In a real implementation, this would use a neural network
        val actionIndex = if (!greedy && Random.nextDouble() < agent.explorationRate) {
            Random.nextInt(agent.config.environment.actionSpace.actions.size)
        } else {
            // Use a simple heuristic for demonstration
            selectBestActionHeuristic(state, agent.config.environment.actionSpace)
        }
        
        val actionName = agent.config.environment.actionSpace.actions[actionIndex]
        
        return RLAction(
            type = actionName,
            parameters = generateActionParameters(actionName, state),
            confidence = if (greedy) 0.8 else 0.5
        )
    }
    
    private fun getPolicyGradientAction(agent: RLAgent, state: State): RLAction {
        val policy = agent.policy ?: throw IllegalStateException("Policy not initialized")
        val actionProbabilities = policy.getActionProbabilities(state)
        
        // Sample action according to policy
        val actionIndex = sampleFromDistribution(actionProbabilities)
        val actionName = agent.config.environment.actionSpace.actions[actionIndex]
        
        return RLAction(
            type = actionName,
            parameters = generateActionParameters(actionName, state),
            confidence = actionProbabilities[actionIndex]
        )
    }
    
    private fun getRandomAction(actionSpace: ActionSpaceConfig): RLAction {
        val actionIndex = Random.nextInt(actionSpace.actions.size)
        val actionName = actionSpace.actions[actionIndex]
        
        return RLAction(
            type = actionName,
            parameters = emptyMap(),
            confidence = 1.0 / actionSpace.actions.size
        )
    }
    
    private fun executeAction(environment: RLEnvironment, state: State, action: RLAction): Triple<State, Double, Boolean> {
        // Simulate environment dynamics
        val nextState = simulateStateTransition(state, action, environment.config)
        val reward = calculateReward(state, action, nextState, environment.config.rewardFunction)
        val done = isTerminalState(nextState, environment.config)
        
        return Triple(nextState, reward, done)
    }
    
    private fun updateAgent(agent: RLAgent, state: State, action: RLAction, reward: Double, nextState: State, done: Boolean) {
        when (agent.config.algorithm) {
            RLAlgorithm.Q_LEARNING -> updateQTable(agent, state, action, reward, nextState, done)
            RLAlgorithm.POLICY_GRADIENT -> updatePolicy(agent, state, action, reward)
            else -> { /* No update needed for some algorithms */ }
        }
    }
    
    private fun updateQTable(agent: RLAgent, state: State, action: RLAction, reward: Double, nextState: State, done: Boolean) {
        val qTable = agent.qTable ?: return
        val stateIndex = stateToIndex(state, agent.config.environment.stateSpace)
        val actionIndex = agent.config.environment.actionSpace.actions.indexOf(action.type)
        val nextStateIndex = stateToIndex(nextState, agent.config.environment.stateSpace)
        
        val currentQ = qTable.getQValue(stateIndex, actionIndex)
        val maxNextQ = if (done) 0.0 else qTable.getMaxQValue(nextStateIndex)
        
        val newQ = currentQ + agent.config.trainingConfig.learningRate * 
                  (reward + agent.config.trainingConfig.discountFactor * maxNextQ - currentQ)
        
        qTable.setQValue(stateIndex, actionIndex, newQ)
    }
    
    private fun updatePolicy(agent: RLAgent, state: State, action: RLAction, reward: Double) {
        val policy = agent.policy ?: return
        // Simplified policy gradient update
        policy.updateActionProbability(state, action.type, reward)
    }
    
    private fun simulateStateTransition(state: State, action: RLAction, envConfig: EnvironmentConfig): State {
        val newFeatures = state.features.toMutableMap()
        
        // Simulate state transitions based on action type
        when (action.type) {
            "scale_up" -> {
                newFeatures["cpu"] = minOf(1.0, (newFeatures["cpu"] ?: 0.5) + 0.1)
                newFeatures["memory"] = minOf(1.0, (newFeatures["memory"] ?: 0.5) + 0.1)
            }
            "scale_down" -> {
                newFeatures["cpu"] = maxOf(0.0, (newFeatures["cpu"] ?: 0.5) - 0.1)
                newFeatures["memory"] = maxOf(0.0, (newFeatures["memory"] ?: 0.5) - 0.1)
            }
            "optimize" -> {
                newFeatures["efficiency"] = minOf(1.0, (newFeatures["efficiency"] ?: 0.5) + 0.05)
            }
            "restart" -> {
                newFeatures["health"] = 1.0
                newFeatures["errors"] = 0.0
            }
        }
        
        // Add some randomness
        newFeatures.keys.forEach { key ->
            newFeatures[key] = newFeatures[key]!! + Random.nextGaussian() * 0.01
        }
        
        return State(
            features = newFeatures,
            timestamp = Instant.now(),
            episodeStep = state.episodeStep + 1
        )
    }
    
    private fun calculateReward(state: State, action: RLAction, nextState: State, rewardConfig: RewardFunctionConfig): Double {
        return when (rewardConfig.type) {
            RewardType.SPARSE -> calculateSparseReward(state, nextState)
            RewardType.DENSE -> calculateDenseReward(state, action, nextState)
            RewardType.SHAPED -> calculateShapedReward(state, action, nextState, rewardConfig.parameters)
            RewardType.MULTI_OBJECTIVE -> calculateMultiObjectiveReward(state, action, nextState, rewardConfig.parameters)
        }
    }
    
    private fun calculateSparseReward(state: State, nextState: State): Double {
        val currentHealth = state.features["health"] ?: 0.5
        val nextHealth = nextState.features["health"] ?: 0.5
        return if (nextHealth > 0.8) 1.0 else 0.0
    }
    
    private fun calculateDenseReward(state: State, action: RLAction, nextState: State): Double {
        val healthImprovement = (nextState.features["health"] ?: 0.5) - (state.features["health"] ?: 0.5)
        val efficiencyImprovement = (nextState.features["efficiency"] ?: 0.5) - (state.features["efficiency"] ?: 0.5)
        val costPenalty = when (action.type) {
            "scale_up" -> -0.1
            "restart" -> -0.05
            else -> 0.0
        }
        
        return healthImprovement + efficiencyImprovement + costPenalty
    }
    
    private fun calculateShapedReward(state: State, action: RLAction, nextState: State, parameters: Map<String, Double>): Double {
        val baseReward = calculateDenseReward(state, action, nextState)
        val shapingBonus = parameters["shaping_factor"] ?: 0.1
        
        // Add potential-based shaping
        val currentPotential = calculatePotential(state)
        val nextPotential = calculatePotential(nextState)
        val shaping = shapingBonus * (nextPotential - currentPotential)
        
        return baseReward + shaping
    }
    
    private fun calculateMultiObjectiveReward(state: State, action: RLAction, nextState: State, parameters: Map<String, Double>): Double {
        val performanceWeight = parameters["performance_weight"] ?: 0.4
        val costWeight = parameters["cost_weight"] ?: 0.3
        val reliabilityWeight = parameters["reliability_weight"] ?: 0.3
        
        val performanceReward = (nextState.features["performance"] ?: 0.5) - (state.features["performance"] ?: 0.5)
        val costReward = -((nextState.features["cost"] ?: 0.5) - (state.features["cost"] ?: 0.5))
        val reliabilityReward = (nextState.features["reliability"] ?: 0.5) - (state.features["reliability"] ?: 0.5)
        
        return performanceWeight * performanceReward + 
               costWeight * costReward + 
               reliabilityWeight * reliabilityReward
    }
    
    private fun calculatePotential(state: State): Double {
        // Simple potential function based on system health
        return state.features["health"] ?: 0.5
    }
    
    private fun isTerminalState(state: State, envConfig: EnvironmentConfig): Boolean {
        // Terminal conditions
        val health = state.features["health"] ?: 0.5
        val errors = state.features["errors"] ?: 0.0
        
        return health < 0.1 || errors > 0.9 || state.episodeStep >= 1000
    }
    
    private fun resetEnvironment(environment: RLEnvironment): State {
        return generateInitialState(environment.config)
    }
    
    private fun generateInitialState(envConfig: EnvironmentConfig): State {
        val features = mutableMapOf<String, Double>()
        
        // Initialize common system metrics
        features["cpu"] = Random.nextDouble(0.3, 0.7)
        features["memory"] = Random.nextDouble(0.3, 0.7)
        features["disk"] = Random.nextDouble(0.2, 0.6)
        features["network"] = Random.nextDouble(0.1, 0.5)
        features["health"] = Random.nextDouble(0.6, 0.9)
        features["performance"] = Random.nextDouble(0.5, 0.8)
        features["efficiency"] = Random.nextDouble(0.4, 0.7)
        features["cost"] = Random.nextDouble(0.3, 0.6)
        features["reliability"] = Random.nextDouble(0.6, 0.9)
        features["errors"] = Random.nextDouble(0.0, 0.2)
        
        return State(
            features = features,
            timestamp = Instant.now(),
            episodeStep = 0
        )
    }
    
    private fun stateToIndex(state: State, stateSpace: StateSpaceConfig): Int {
        // Simplified state discretization
        val cpuBin = ((state.features["cpu"] ?: 0.5) * 10).toInt().coerceIn(0, 9)
        val memoryBin = ((state.features["memory"] ?: 0.5) * 10).toInt().coerceIn(0, 9)
        return cpuBin * 10 + memoryBin
    }
    
    private fun generateActionParameters(actionName: String, state: State): Map<String, Double> {
        return when (actionName) {
            "scale_up" -> mapOf("factor" to 1.5, "max_instances" to 10.0)
            "scale_down" -> mapOf("factor" to 0.8, "min_instances" to 1.0)
            "optimize" -> mapOf("optimization_level" to 0.8)
            "restart" -> mapOf("graceful" to 1.0)
            else -> emptyMap()
        }
    }
    
    private fun selectBestActionHeuristic(state: State, actionSpace: ActionSpaceConfig): Int {
        val cpu = state.features["cpu"] ?: 0.5
        val memory = state.features["memory"] ?: 0.5
        val health = state.features["health"] ?: 0.5
        
        return when {
            cpu > 0.8 || memory > 0.8 -> actionSpace.actions.indexOf("scale_up").takeIf { it >= 0 } ?: 0
            health < 0.3 -> actionSpace.actions.indexOf("restart").takeIf { it >= 0 } ?: 0
            cpu < 0.3 && memory < 0.3 -> actionSpace.actions.indexOf("scale_down").takeIf { it >= 0 } ?: 0
            else -> actionSpace.actions.indexOf("optimize").takeIf { it >= 0 } ?: 0
        }
    }
    
    private fun sampleFromDistribution(probabilities: List<Double>): Int {
        val random = Random.nextDouble()
        var cumulative = 0.0
        
        for (i in probabilities.indices) {
            cumulative += probabilities[i]
            if (random <= cumulative) return i
        }
        
        return probabilities.size - 1
    }
    
    private fun updateExplorationRate(agent: RLAgent, episode: Int) {
        val strategy = agent.config.agentConfig.explorationStrategy
        when (strategy.type) {
            ExplorationType.EPSILON_GREEDY -> {
                val decay = strategy.parameters["decay"] ?: 0.995
                val minEpsilon = strategy.parameters["min_epsilon"] ?: 0.01
                agent.explorationRate = maxOf(minEpsilon, agent.explorationRate * decay)
            }
            else -> { /* Other exploration strategies */ }
        }
    }
    
    private fun checkConvergence(episodeRewards: List<Double>, episode: Int): Boolean {
        if (episode < 100) return false
        
        val recentRewards = episodeRewards.takeLast(50)
        val previousRewards = episodeRewards.drop(episode - 100).take(50)
        
        val recentAvg = recentRewards.average()
        val previousAvg = previousRewards.average()
        
        return abs(recentAvg - previousAvg) < 0.01
    }
    
    private fun calculateStabilityScore(episodeRewards: List<Double>): Double {
        if (episodeRewards.size < 10) return 0.0
        
        val recentRewards = episodeRewards.takeLast(50)
        val stdDev = calculateStandardDeviation(recentRewards)
        val mean = recentRewards.average()
        
        return if (mean > 0) 1.0 - (stdDev / mean).coerceIn(0.0, 1.0) else 0.0
    }
    
    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
    
    private fun generateActionDescription(action: Action, state: State): String {
        return when (action.type) {
            "scale_up" -> "Scale up resources to handle increased load (CPU: ${state.features["cpu"]}, Memory: ${state.features["memory"]})"
            "scale_down" -> "Scale down resources to reduce costs (Current utilization low)"
            "optimize" -> "Optimize system configuration for better performance"
            "restart" -> "Restart services to resolve health issues (Health: ${state.features["health"]})"
            else -> "Execute ${action.type} action"
        }
    }
    
    private fun generateActionRecommendations(action: Action, state: State): List<String> {
        return when (action.type) {
            "scale_up" -> listOf(
                "Monitor resource utilization after scaling",
                "Consider auto-scaling policies",
                "Review cost implications"
            )
            "scale_down" -> listOf(
                "Ensure minimum capacity requirements",
                "Monitor performance metrics",
                "Have rollback plan ready"
            )
            "optimize" -> listOf(
                "Backup current configuration",
                "Test in staging environment first",
                "Monitor performance improvements"
            )
            "restart" -> listOf(
                "Ensure graceful shutdown",
                "Check for data persistence",
                "Monitor service recovery"
            )
            else -> listOf("Monitor action execution", "Have rollback plan ready")
        }
    }
    
    private fun generateAgentId(algorithm: RLAlgorithm): String {
        return "${algorithm.name.lowercase()}_agent_${System.currentTimeMillis()}"
    }
}

// Helper classes

private data class RLAgent(
    val id: String,
    val config: ReinforcementConfig,
    val qTable: QTable?,
    val policy: Policy?,
    var explorationRate: Double,
    val performance: AgentPerformance = AgentPerformance(0.0, emptyList(), explorationRate, ConvergenceMetrics(false, null, 0.0))
)

private data class RLEnvironment(
    val id: String,
    val config: EnvironmentConfig,
    val currentState: State,
    val episodeCount: Int,
    val totalReward: Double
)

private data class RLAction(
    val type: String,
    val parameters: Map<String, Double>,
    val confidence: Double
)

private class QTable(private val stateSize: Int, private val actionSize: Int) {
    private val table = Array(100) { DoubleArray(actionSize) { 0.0 } } // Simplified fixed size
    
    fun getQValue(state: Int, action: Int): Double {
        return table[state.coerceIn(0, table.size - 1)][action.coerceIn(0, actionSize - 1)]
    }
    
    fun setQValue(state: Int, action: Int, value: Double) {
        table[state.coerceIn(0, table.size - 1)][action.coerceIn(0, actionSize - 1)] = value
    }
    
    fun getBestAction(state: Int): Int {
        val stateIndex = state.coerceIn(0, table.size - 1)
        return table[stateIndex].indices.maxByOrNull { table[stateIndex][it] } ?: 0
    }
    
    fun getMaxQValue(state: Int): Double {
        val stateIndex = state.coerceIn(0, table.size - 1)
        return table[stateIndex].maxOrNull() ?: 0.0
    }
}

private class Policy(private val actions: List<String>) {
    private val actionProbabilities = mutableMapOf<String, MutableMap<String, Double>>()
    
    fun getActionProbabilities(state: State): List<Double> {
        val stateKey = stateToKey(state)
        val probs = actionProbabilities.getOrPut(stateKey) {
            actions.associateWith { 1.0 / actions.size }.toMutableMap()
        }
        
        return actions.map { probs[it] ?: (1.0 / actions.size) }
    }
    
    fun updateActionProbability(state: State, action: String, reward: Double) {
        val stateKey = stateToKey(state)
        val probs = actionProbabilities.getOrPut(stateKey) {
            actions.associateWith { 1.0 / actions.size }.toMutableMap()
        }
        
        // Simple policy gradient update
        val learningRate = 0.01
        val currentProb = probs[action] ?: (1.0 / actions.size)
        val newProb = currentProb + learningRate * reward * (1.0 - currentProb)
        probs[action] = newProb.coerceIn(0.01, 0.99)
        
        // Normalize probabilities
        val sum = probs.values.sum()
        probs.keys.forEach { probs[it] = probs[it]!! / sum }
    }
    
    private fun stateToKey(state: State): String {
        return state.features.entries.sortedBy { it.key }
            .joinToString(",") { "${it.key}:${(it.value * 10).toInt()}" }
    }
}