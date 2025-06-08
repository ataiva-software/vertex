package com.ataiva.eden.flow.engine

import kotlinx.serialization.json.*

/**
 * Workflow engine for parsing and validating workflow definitions
 */
class WorkflowEngine {
    
    /**
     * Validate workflow definition
     */
    fun validateDefinition(definition: Map<String, Any>): ValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            // Check required fields
            if (!definition.containsKey("steps")) {
                errors.add("Workflow must contain 'steps' field")
            }
            
            val steps = definition["steps"]
            if (steps !is List<*>) {
                errors.add("'steps' must be a list")
            } else {
                // Validate each step
                steps.forEachIndexed { index, step ->
                    if (step !is Map<*, *>) {
                        errors.add("Step $index must be an object")
                    } else {
                        val stepMap = step as Map<String, Any>
                        validateStep(stepMap, index, errors)
                    }
                }
            }
            
            // Check for circular dependencies
            if (errors.isEmpty()) {
                val circularDeps = checkCircularDependencies(definition)
                if (circularDeps.isNotEmpty()) {
                    errors.addAll(circularDeps)
                }
            }
            
        } catch (e: Exception) {
            errors.add("Invalid workflow definition format: ${e.message}")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Parse workflow steps from definition
     */
    fun parseSteps(definition: Map<String, Any>): List<StepDefinition> {
        val steps = definition["steps"] as? List<*> ?: return emptyList()
        
        return steps.mapIndexed { index, step ->
            val stepMap = step as Map<String, Any>
            StepDefinition(
                name = stepMap["name"] as String,
                type = stepMap["type"] as String,
                inputData = stepMap["input"] as? Map<String, Any>,
                config = stepMap["config"] as? Map<String, Any> ?: emptyMap(),
                dependsOn = stepMap["dependsOn"] as? List<String> ?: emptyList(),
                condition = stepMap["condition"] as? String,
                retryPolicy = parseRetryPolicy(stepMap["retry"] as? Map<String, Any>),
                timeout = stepMap["timeout"] as? Int ?: 300 // 5 minutes default
            )
        }
    }
    
    /**
     * Validate individual step
     */
    private fun validateStep(step: Map<String, Any>, index: Int, errors: MutableList<String>) {
        // Required fields
        if (!step.containsKey("name")) {
            errors.add("Step $index missing required field 'name'")
        }
        
        if (!step.containsKey("type")) {
            errors.add("Step $index missing required field 'type'")
        } else {
            val type = step["type"] as? String
            if (type !in SUPPORTED_STEP_TYPES) {
                errors.add("Step $index has unsupported type '$type'. Supported types: ${SUPPORTED_STEP_TYPES.joinToString(", ")}")
            }
        }
        
        // Validate step name format
        val name = step["name"] as? String
        if (name != null && !name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            errors.add("Step $index name '$name' contains invalid characters. Use only letters, numbers, underscore, and dash")
        }
        
        // Validate timeout
        val timeout = step["timeout"]
        if (timeout != null && timeout !is Int) {
            errors.add("Step $index timeout must be an integer (seconds)")
        }
        
        // Validate retry policy
        val retry = step["retry"]
        if (retry != null && retry !is Map<*, *>) {
            errors.add("Step $index retry policy must be an object")
        }
        
        // Validate condition
        val condition = step["condition"]
        if (condition != null && condition !is String) {
            errors.add("Step $index condition must be a string")
        }
    }
    
    /**
     * Check for circular dependencies in workflow steps
     */
    private fun checkCircularDependencies(definition: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        val steps = definition["steps"] as? List<*> ?: return errors
        
        // Build dependency graph
        val dependencies = mutableMapOf<String, List<String>>()
        val stepNames = mutableSetOf<String>()
        
        steps.forEach { step ->
            val stepMap = step as Map<String, Any>
            val name = stepMap["name"] as String
            val dependsOn = stepMap["dependsOn"] as? List<String> ?: emptyList()
            
            stepNames.add(name)
            dependencies[name] = dependsOn
        }
        
        // Check if all dependencies exist
        dependencies.forEach { (stepName, deps) ->
            deps.forEach { dep ->
                if (dep !in stepNames) {
                    errors.add("Step '$stepName' depends on non-existent step '$dep'")
                }
            }
        }
        
        // Check for circular dependencies using DFS
        if (errors.isEmpty()) {
            val visited = mutableSetOf<String>()
            val recursionStack = mutableSetOf<String>()
            
            fun hasCycle(node: String): Boolean {
                if (recursionStack.contains(node)) {
                    return true
                }
                if (visited.contains(node)) {
                    return false
                }
                
                visited.add(node)
                recursionStack.add(node)
                
                dependencies[node]?.forEach { dep ->
                    if (hasCycle(dep)) {
                        return true
                    }
                }
                
                recursionStack.remove(node)
                return false
            }
            
            stepNames.forEach { stepName ->
                if (!visited.contains(stepName) && hasCycle(stepName)) {
                    errors.add("Circular dependency detected involving step '$stepName'")
                }
            }
        }
        
        return errors
    }
    
    /**
     * Parse retry policy from step configuration
     */
    private fun parseRetryPolicy(retryConfig: Map<String, Any>?): RetryPolicy {
        if (retryConfig == null) {
            return RetryPolicy()
        }
        
        return RetryPolicy(
            maxAttempts = retryConfig["maxAttempts"] as? Int ?: 3,
            backoffStrategy = retryConfig["backoffStrategy"] as? String ?: "exponential",
            initialDelay = retryConfig["initialDelay"] as? Int ?: 1000,
            maxDelay = retryConfig["maxDelay"] as? Int ?: 30000,
            retryOn = retryConfig["retryOn"] as? List<String> ?: listOf("error", "timeout")
        )
    }
    
    companion object {
        val SUPPORTED_STEP_TYPES = setOf(
            "http_request",
            "shell_command",
            "sql_query",
            "file_operation",
            "email_notification",
            "slack_notification",
            "webhook",
            "delay",
            "condition",
            "parallel",
            "loop",
            "script",
            "docker_run",
            "kubernetes_deploy"
        )
    }
}

/**
 * Workflow validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Step definition parsed from workflow
 */
data class StepDefinition(
    val name: String,
    val type: String,
    val inputData: Map<String, Any>?,
    val config: Map<String, Any>,
    val dependsOn: List<String>,
    val condition: String?,
    val retryPolicy: RetryPolicy,
    val timeout: Int
)

/**
 * Retry policy for step execution
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val backoffStrategy: String = "exponential",
    val initialDelay: Int = 1000,
    val maxDelay: Int = 30000,
    val retryOn: List<String> = listOf("error", "timeout")
)