package com.ataiva.eden.flow.service

import org.slf4j.LoggerFactory
import java.util.Properties
import java.io.FileInputStream
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings
import java.util.concurrent.TimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

/**
 * Service for evaluating expressions in workflow conditions
 */
class ExpressionEvaluator {
    private val logger = LoggerFactory.getLogger(ExpressionEvaluator::class.java)
    private val scriptEngine = ScriptEngineManager().getEngineByName("nashorn")
    private val executor = Executors.newSingleThreadExecutor()
    
    private val maxExecutionTime: Long
    private val maxExpressionLength: Int
    private val allowedFunctions: Set<String>
    
    init {
        // Default configuration
        var execTime = 5000L
        var exprLength = 1000
        var functions = setOf("sum", "avg", "min", "max", "count", "if", "contains", "startsWith", "endsWith")
        
        try {
            // Try to load from environment or config file
            val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.properties"
            try {
                val configFile = java.io.File(configPath)
                if (configFile.exists()) {
                    val props = Properties()
                    props.load(FileInputStream(configFile))
                    
                    execTime = props.getProperty("expression.evaluation.max-execution-time", "5000").toLong()
                    exprLength = props.getProperty("expression.evaluation.max-expression-length", "1000").toInt()
                    
                    val funcList = props.getProperty("expression.evaluation.allowed-functions")
                    if (funcList != null) {
                        functions = funcList.split(",").map { it.trim() }.toSet()
                    }
                    
                    logger.info("Expression evaluator configuration loaded from $configPath")
                }
            } catch (e: Exception) {
                logger.warn("Failed to load expression evaluator configuration from file: ${e.message}")
            }
            
            // Override with environment variables if present
            System.getenv("EXPRESSION_MAX_EXECUTION_TIME")?.toLongOrNull()?.let { execTime = it }
            System.getenv("EXPRESSION_MAX_LENGTH")?.toIntOrNull()?.let { exprLength = it }
            System.getenv("EXPRESSION_ALLOWED_FUNCTIONS")?.let { 
                functions = it.split(",").map { func -> func.trim() }.toSet() 
            }
            
        } catch (e: Exception) {
            logger.error("Failed to initialize expression evaluator: ${e.message}")
        }
        
        maxExecutionTime = execTime
        maxExpressionLength = exprLength
        allowedFunctions = functions
    }
    
    /**
     * Evaluate an expression with the given context variables
     */
    fun evaluate(expression: String, context: Map<String, Any>?): Result<Boolean> {
        if (expression.length > maxExpressionLength) {
            logger.warn("Expression exceeds maximum length: ${expression.length} > $maxExpressionLength")
            return Result.failure(IllegalArgumentException("Expression exceeds maximum length"))
        }
        
        // Check for disallowed functions or patterns
        if (!isExpressionSafe(expression)) {
            logger.warn("Expression contains disallowed functions or patterns: $expression")
            return Result.failure(IllegalArgumentException("Expression contains disallowed functions or patterns"))
        }
        
        val bindings = SimpleBindings()
        context?.forEach { (key, value) -> bindings[key] = value }
        
        // Add helper functions
        addHelperFunctions(bindings)
        
        // Execute with timeout
        val task = executor.submit<Any> {
            try {
                scriptEngine.eval(expression, bindings)
            } catch (e: ScriptException) {
                logger.warn("Error evaluating expression: $expression, error: ${e.message}")
                throw e
            }
        }
        
        return try {
            val result = task.get(maxExecutionTime, TimeUnit.MILLISECONDS)
            
            when (result) {
                is Boolean -> Result.success(result)
                is Number -> Result.success(result.toInt() != 0)
                is String -> Result.success(result.isNotEmpty())
                null -> Result.success(false)
                else -> Result.success(true)
            }
        } catch (e: TimeoutException) {
            task.cancel(true)
            logger.warn("Expression evaluation timed out: $expression")
            Result.failure(TimeoutException("Expression evaluation timed out after $maxExecutionTime ms"))
        } catch (e: Exception) {
            logger.error("Failed to evaluate expression: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if the expression is safe to evaluate
     */
    private fun isExpressionSafe(expression: String): Boolean {
        // Check for Java imports, reflection, or system access
        val dangerousPatterns = listOf(
            "java\\s*\\.", 
            "javax\\s*\\.", 
            "System\\s*\\.", 
            "Runtime\\s*\\.",
            "Process\\s*\\.",
            "Class\\s*\\.",
            "ClassLoader\\s*\\.",
            "Thread\\s*\\.",
            "exec\\s*\\(",
            "eval\\s*\\(",
            "load\\s*\\(",
            "defineClass\\s*\\(",
            "forName\\s*\\("
        )
        
        for (pattern in dangerousPatterns) {
            if (Regex(pattern).containsMatchIn(expression)) {
                return false
            }
        }
        
        // Check for function calls
        val functionCalls = Regex("([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(").findAll(expression)
        for (match in functionCalls) {
            val functionName = match.groupValues[1].trim()
            if (functionName !in allowedFunctions && functionName != "Math") {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Add helper functions to the bindings
     */
    private fun addHelperFunctions(bindings: SimpleBindings) {
        // Add Math functions
        bindings["Math"] = Math::class.java
        
        // Add custom helper functions
        if ("sum" in allowedFunctions) {
            bindings["sum"] = { values: List<Number> -> values.sumOf { it.toDouble() } }
        }
        
        if ("avg" in allowedFunctions) {
            bindings["avg"] = { values: List<Number> -> 
                if (values.isEmpty()) 0.0 else values.sumOf { it.toDouble() } / values.size 
            }
        }
        
        if ("min" in allowedFunctions) {
            bindings["min"] = { values: List<Number> -> values.minOfOrNull { it.toDouble() } ?: 0.0 }
        }
        
        if ("max" in allowedFunctions) {
            bindings["max"] = { values: List<Number> -> values.maxOfOrNull { it.toDouble() } ?: 0.0 }
        }
        
        if ("count" in allowedFunctions) {
            bindings["count"] = { values: List<Any> -> values.size }
        }
        
        if ("contains" in allowedFunctions) {
            bindings["contains"] = { text: String, search: String -> text.contains(search) }
        }
        
        if ("startsWith" in allowedFunctions) {
            bindings["startsWith"] = { text: String, prefix: String -> text.startsWith(prefix) }
        }
        
        if ("endsWith" in allowedFunctions) {
            bindings["endsWith"] = { text: String, suffix: String -> text.endsWith(suffix) }
        }
    }
    
    /**
     * Shutdown the evaluator
     */
    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}