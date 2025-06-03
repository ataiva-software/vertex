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
 * Natural Language Processing Engine implementation
 * Provides sentiment analysis, named entity recognition, text classification,
 * threat detection, and intelligent documentation generation
 */
class NaturalLanguageProcessorImpl : NaturalLanguageProcessor {
    
    private val sentimentModel = SentimentAnalysisModel()
    private val nerModel = NamedEntityRecognitionModel()
    private val threatDetectionModel = ThreatDetectionModel()
    private val textClassifier = TextClassificationModel()
    private val documentationGenerator = DocumentationGeneratorModel()
    private val logAnalyzer = LogAnalysisModel()
    
    override suspend fun process(text: String, tasks: List<NLPTask>): NLPResult = withContext(Dispatchers.IO) {
        logger.info { "Processing text with ${tasks.size} NLP tasks" }
        
        val startTime = System.currentTimeMillis()
        var sentimentScore = 0.0
        var threatScore = 0.0
        var entities = emptyList<NamedEntity>()
        var classification: TextClassification? = null
        var summary: String? = null
        var overallConfidence = 1.0
        
        try {
            for (task in tasks) {
                when (task) {
                    NLPTask.SENTIMENT_ANALYSIS -> {
                        val result = sentimentModel.analyzeSentiment(text)
                        sentimentScore = result.score
                        overallConfidence = minOf(overallConfidence, result.confidence)
                    }
                    
                    NLPTask.NAMED_ENTITY_RECOGNITION -> {
                        entities = nerModel.extractEntities(text)
                        overallConfidence = minOf(overallConfidence, entities.minOfOrNull { it.confidence } ?: 1.0)
                    }
                    
                    NLPTask.THREAT_DETECTION -> {
                        val result = threatDetectionModel.detectThreats(text)
                        threatScore = result.score
                        overallConfidence = minOf(overallConfidence, result.confidence)
                    }
                    
                    NLPTask.TEXT_CLASSIFICATION -> {
                        classification = textClassifier.classify(text)
                        overallConfidence = minOf(overallConfidence, classification?.confidence ?: 1.0)
                    }
                    
                    NLPTask.SUMMARIZATION -> {
                        summary = generateSummary(text)
                        overallConfidence = minOf(overallConfidence, 0.8) // Summary confidence
                    }
                    
                    else -> {
                        logger.warn { "Unsupported NLP task: $task" }
                    }
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            NLPResult(
                originalText = text,
                tasks = tasks,
                sentimentScore = sentimentScore,
                threatScore = threatScore,
                entities = entities,
                classification = classification,
                summary = summary,
                confidence = overallConfidence,
                processingTime = processingTime
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to process text with NLP tasks" }
            throw e
        }
    }
    
    override suspend fun generateDocumentation(codeContext: CodeContext): GeneratedDocumentation = withContext(Dispatchers.IO) {
        logger.info { "Generating documentation for ${codeContext.language} code" }
        
        try {
            val documentation = documentationGenerator.generateDocumentation(codeContext)
            val apiDocumentation = if (codeContext.functions.isNotEmpty()) {
                documentationGenerator.generateApiDocumentation(codeContext)
            } else null
            
            val examples = documentationGenerator.generateCodeExamples(codeContext)
            val quality = assessDocumentationQuality(documentation, codeContext)
            
            GeneratedDocumentation(
                codeContext = codeContext,
                documentation = documentation,
                apiDocumentation = apiDocumentation,
                examples = examples,
                quality = quality
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate documentation" }
            throw e
        }
    }
    
    override suspend fun analyzeLogs(logs: List<LogMessage>): LogAnalysis = withContext(Dispatchers.IO) {
        logger.info { "Analyzing ${logs.size} log messages" }
        
        try {
            val errorRate = logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }.toDouble() / logs.size
            val patterns = logAnalyzer.extractPatterns(logs)
            val anomalies = logAnalyzer.detectAnomalies(logs)
            val insights = logAnalyzer.generateInsights(logs, patterns, anomalies)
            val recommendations = logAnalyzer.generateRecommendations(insights, errorRate)
            
            LogAnalysis(
                totalMessages = logs.size,
                errorRate = errorRate,
                patterns = patterns,
                anomalies = anomalies,
                insights = insights,
                recommendations = recommendations
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to analyze logs" }
            throw e
        }
    }
    
    private fun generateSummary(text: String): String {
        // Simplified extractive summarization
        val sentences = text.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
        if (sentences.size <= 2) return text
        
        // Score sentences based on word frequency and position
        val wordFreq = text.lowercase().split(Regex("\\W+"))
            .filter { it.length > 3 }
            .groupingBy { it }
            .eachCount()
        
        val sentenceScores = sentences.mapIndexed { index, sentence ->
            val words = sentence.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
            val score = words.sumOf { wordFreq[it] ?: 0 } / words.size.toDouble()
            val positionBonus = if (index < sentences.size / 3) 0.1 else 0.0 // Boost early sentences
            index to score + positionBonus
        }
        
        // Select top sentences
        val topSentences = sentenceScores.sortedByDescending { it.second }
            .take(maxOf(1, sentences.size / 3))
            .sortedBy { it.first }
            .map { sentences[it.first] }
        
        return topSentences.joinToString(". ") + "."
    }
    
    private fun assessDocumentationQuality(documentation: String, codeContext: CodeContext): DocumentationQuality {
        val completeness = assessCompleteness(documentation, codeContext)
        val clarity = assessClarity(documentation)
        val accuracy = assessAccuracy(documentation, codeContext)
        val overallScore = (completeness + clarity + accuracy) / 3.0
        
        return DocumentationQuality(
            completeness = completeness,
            clarity = clarity,
            accuracy = accuracy,
            overallScore = overallScore
        )
    }
    
    private fun assessCompleteness(documentation: String, codeContext: CodeContext): Double {
        var score = 0.0
        var totalChecks = 0
        
        // Check if functions are documented
        if (codeContext.functions.isNotEmpty()) {
            totalChecks++
            val documentedFunctions = codeContext.functions.count { func ->
                documentation.contains(func.name, ignoreCase = true)
            }
            score += documentedFunctions.toDouble() / codeContext.functions.size
        }
        
        // Check if classes are documented
        if (codeContext.classes.isNotEmpty()) {
            totalChecks++
            val documentedClasses = codeContext.classes.count { cls ->
                documentation.contains(cls.name, ignoreCase = true)
            }
            score += documentedClasses.toDouble() / codeContext.classes.size
        }
        
        // Check for basic documentation elements
        totalChecks += 3
        if (documentation.contains("purpose", ignoreCase = true) || 
            documentation.contains("description", ignoreCase = true)) score += 1.0
        if (documentation.contains("parameter", ignoreCase = true) || 
            documentation.contains("argument", ignoreCase = true)) score += 1.0
        if (documentation.contains("return", ignoreCase = true) || 
            documentation.contains("output", ignoreCase = true)) score += 1.0
        
        return if (totalChecks > 0) score / totalChecks else 0.5
    }
    
    private fun assessClarity(documentation: String): Double {
        val sentences = documentation.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
        if (sentences.isEmpty()) return 0.0
        
        // Average sentence length (shorter is generally clearer)
        val avgSentenceLength = sentences.map { it.split("\\s+".toRegex()).size }.average()
        val lengthScore = maxOf(0.0, 1.0 - (avgSentenceLength - 15) / 20.0) // Optimal around 15 words
        
        // Check for clear structure indicators
        val structureScore = when {
            documentation.contains("## ") || documentation.contains("### ") -> 1.0 // Markdown headers
            documentation.contains("1.") || documentation.contains("- ") -> 0.8 // Lists
            documentation.contains(":") -> 0.6 // Colons for explanations
            else -> 0.4
        }
        
        return (lengthScore + structureScore) / 2.0
    }
    
    private fun assessAccuracy(documentation: String, codeContext: CodeContext): Double {
        var score = 0.0
        var totalChecks = 0
        
        // Check if mentioned functions actually exist
        val mentionedFunctions = extractMentionedFunctions(documentation)
        if (mentionedFunctions.isNotEmpty()) {
            totalChecks++
            val existingFunctions = mentionedFunctions.count { mentioned ->
                codeContext.functions.any { it.name == mentioned }
            }
            score += existingFunctions.toDouble() / mentionedFunctions.size
        }
        
        // Check language consistency
        totalChecks++
        if (documentation.contains(codeContext.language, ignoreCase = true)) {
            score += 1.0
        } else {
            score += 0.5 // Partial credit if language not explicitly mentioned
        }
        
        return if (totalChecks > 0) score / totalChecks else 0.7 // Default reasonable accuracy
    }
    
    private fun extractMentionedFunctions(documentation: String): List<String> {
        // Simple regex to find function-like mentions
        val functionPattern = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
        return functionPattern.findAll(documentation)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }
}

// Model implementations

private class SentimentAnalysisModel {
    private val positiveWords = setOf(
        "good", "great", "excellent", "amazing", "wonderful", "fantastic", "perfect", "love",
        "best", "awesome", "brilliant", "outstanding", "superb", "magnificent", "success",
        "working", "fixed", "resolved", "improved", "optimized", "efficient", "stable"
    )
    
    private val negativeWords = setOf(
        "bad", "terrible", "awful", "horrible", "worst", "hate", "failed", "broken",
        "error", "bug", "issue", "problem", "crash", "down", "slow", "timeout",
        "exception", "critical", "urgent", "emergency", "disaster", "failure"
    )
    
    fun analyzeSentiment(text: String): SentimentResult {
        val words = text.lowercase().split(Regex("\\W+"))
        val positiveCount = words.count { it in positiveWords }
        val negativeCount = words.count { it in negativeWords }
        val totalSentimentWords = positiveCount + negativeCount
        
        val score = if (totalSentimentWords > 0) {
            (positiveCount - negativeCount).toDouble() / totalSentimentWords
        } else {
            0.0 // Neutral
        }
        
        val confidence = if (totalSentimentWords > 0) {
            minOf(1.0, totalSentimentWords.toDouble() / 10.0) // More sentiment words = higher confidence
        } else {
            0.5 // Low confidence for neutral
        }
        
        return SentimentResult(score, confidence)
    }
}

private class NamedEntityRecognitionModel {
    private val ipPattern = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    private val urlPattern = Regex("https?://[\\w.-]+(?:/[\\w.-]*)*")
    private val filePathPattern = Regex("/[\\w.-/]+|[A-Z]:\\\\[\\w.-\\\\]+")
    private val errorCodePattern = Regex("\\b[A-Z]{2,}[0-9]{3,}\\b")
    private val datePattern = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{2}/\\d{2}/\\d{4}\\b")
    private val timePattern = Regex("\\b\\d{2}:\\d{2}(?::\\d{2})?\\b")
    
    fun extractEntities(text: String): List<NamedEntity> {
        val entities = mutableListOf<NamedEntity>()
        
        // Extract IP addresses
        ipPattern.findAll(text).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.IP_ADDRESS,
                confidence = 0.95,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }
        
        // Extract URLs
        urlPattern.findAll(text).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.URL,
                confidence = 0.9,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }
        
        // Extract file paths
        filePathPattern.findAll(text).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.FILE_PATH,
                confidence = 0.8,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }
        
        // Extract error codes
        errorCodePattern.findAll(text).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.ERROR_CODE,
                confidence = 0.85,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }
        
        // Extract dates
        datePattern.findAll(text).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.DATE,
                confidence = 0.9,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }
        
        // Extract times
        timePattern.findAll(text).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.TIME,
                confidence = 0.85,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }
        
        return entities
    }
}

private class ThreatDetectionModel {
    private val threatKeywords = setOf(
        "attack", "breach", "hack", "malware", "virus", "trojan", "phishing", "exploit",
        "vulnerability", "intrusion", "unauthorized", "suspicious", "malicious", "infected",
        "compromised", "backdoor", "rootkit", "ransomware", "ddos", "injection", "xss",
        "csrf", "privilege escalation", "buffer overflow", "sql injection", "brute force"
    )
    
    private val securityIndicators = setOf(
        "failed login", "access denied", "permission denied", "authentication failed",
        "invalid credentials", "blocked", "quarantined", "detected", "alert", "warning"
    )
    
    fun detectThreats(text: String): ThreatResult {
        val lowerText = text.lowercase()
        val threatCount = threatKeywords.count { lowerText.contains(it) }
        val indicatorCount = securityIndicators.count { lowerText.contains(it) }
        
        val totalMatches = threatCount + indicatorCount
        val score = when {
            totalMatches >= 3 -> 0.9
            totalMatches == 2 -> 0.7
            totalMatches == 1 -> 0.4
            else -> 0.1
        }
        
        val confidence = when {
            totalMatches >= 2 -> 0.9
            totalMatches == 1 -> 0.6
            else -> 0.3
        }
        
        return ThreatResult(score, confidence)
    }
}

private class TextClassificationModel {
    fun classify(text: String): TextClassification {
        val lowerText = text.lowercase()
        
        // Simple rule-based classification
        val categories = mapOf(
            "error" to listOf("error", "exception", "failed", "crash", "bug"),
            "warning" to listOf("warning", "warn", "caution", "alert"),
            "info" to listOf("info", "information", "notice", "log"),
            "security" to listOf("security", "auth", "login", "access", "permission"),
            "performance" to listOf("slow", "timeout", "performance", "latency", "response"),
            "system" to listOf("system", "server", "service", "process", "memory", "cpu")
        )
        
        val scores = categories.mapValues { (_, keywords) ->
            keywords.count { lowerText.contains(it) }.toDouble()
        }
        
        val bestCategory = scores.maxByOrNull { it.value }
        val totalScore = scores.values.sum()
        
        return if (bestCategory != null && bestCategory.value > 0) {
            val confidence = bestCategory.value / maxOf(1.0, totalScore)
            val alternatives = scores.filter { it.key != bestCategory.key && it.value > 0 }
                .map { it.key to it.value / totalScore }
                .sortedByDescending { it.second }
                .take(3)
            
            TextClassification(
                category = bestCategory.key,
                confidence = confidence,
                alternativeCategories = alternatives
            )
        } else {
            TextClassification(
                category = "general",
                confidence = 0.5,
                alternativeCategories = emptyList()
            )
        }
    }
}

private class DocumentationGeneratorModel {
    fun generateDocumentation(codeContext: CodeContext): String {
        val sb = StringBuilder()
        
        // Generate header
        sb.appendLine("# ${codeContext.filePath}")
        sb.appendLine()
        sb.appendLine("## Overview")
        sb.appendLine("This ${codeContext.language} module provides functionality for ${inferPurpose(codeContext)}.")
        sb.appendLine()
        
        // Document classes
        if (codeContext.classes.isNotEmpty()) {
            sb.appendLine("## Classes")
            sb.appendLine()
            codeContext.classes.forEach { cls ->
                sb.appendLine("### ${cls.name}")
                sb.appendLine(cls.description ?: "Class for ${cls.name.lowercase()} operations.")
                sb.appendLine()
                
                if (cls.properties.isNotEmpty()) {
                    sb.appendLine("#### Properties")
                    cls.properties.forEach { prop ->
                        sb.appendLine("- **${prop.name}** (${prop.type ?: "unknown"}): ${prop.description ?: "Property description"}")
                    }
                    sb.appendLine()
                }
                
                if (cls.methods.isNotEmpty()) {
                    sb.appendLine("#### Methods")
                    cls.methods.forEach { method ->
                        documentFunction(sb, method)
                    }
                }
            }
        }
        
        // Document functions
        if (codeContext.functions.isNotEmpty()) {
            sb.appendLine("## Functions")
            sb.appendLine()
            codeContext.functions.forEach { func ->
                documentFunction(sb, func)
            }
        }
        
        // Document dependencies
        if (codeContext.dependencies.isNotEmpty()) {
            sb.appendLine("## Dependencies")
            sb.appendLine()
            codeContext.dependencies.forEach { dep ->
                sb.appendLine("- $dep")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    fun generateApiDocumentation(codeContext: CodeContext): String {
        val sb = StringBuilder()
        
        sb.appendLine("# API Documentation")
        sb.appendLine()
        
        codeContext.functions.forEach { func ->
            sb.appendLine("## ${func.name}")
            sb.appendLine()
            sb.appendLine("**Description:** ${func.description ?: "Function description"}")
            sb.appendLine()
            
            if (func.parameters.isNotEmpty()) {
                sb.appendLine("**Parameters:**")
                func.parameters.forEach { param ->
                    sb.appendLine("- `${param.name}` (${param.type ?: "unknown"}): ${param.description ?: "Parameter description"}")
                }
                sb.appendLine()
            }
            
            sb.appendLine("**Returns:** ${func.returnType ?: "void"}")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    fun generateCodeExamples(codeContext: CodeContext): List<CodeExample> {
        val examples = mutableListOf<CodeExample>()
        
        // Generate basic usage example
        if (codeContext.functions.isNotEmpty()) {
            val mainFunction = codeContext.functions.first()
            val exampleCode = generateExampleUsage(mainFunction, codeContext.language)
            examples.add(CodeExample(
                title = "Basic Usage",
                code = exampleCode,
                description = "Basic example of how to use ${mainFunction.name}"
            ))
        }
        
        // Generate class instantiation example
        if (codeContext.classes.isNotEmpty()) {
            val mainClass = codeContext.classes.first()
            val exampleCode = generateClassExample(mainClass, codeContext.language)
            examples.add(CodeExample(
                title = "Class Usage",
                code = exampleCode,
                description = "Example of how to use ${mainClass.name} class"
            ))
        }
        
        return examples
    }
    
    private fun documentFunction(sb: StringBuilder, func: FunctionInfo) {
        sb.appendLine("#### ${func.name}")
        sb.appendLine(func.description ?: "Function description")
        sb.appendLine()
        
        if (func.parameters.isNotEmpty()) {
            sb.appendLine("**Parameters:**")
            func.parameters.forEach { param ->
                sb.appendLine("- `${param.name}` (${param.type ?: "unknown"}): ${param.description ?: "Parameter description"}")
            }
            sb.appendLine()
        }
        
        sb.appendLine("**Returns:** ${func.returnType ?: "void"}")
        sb.appendLine()
    }
    
    private fun inferPurpose(codeContext: CodeContext): String {
        val fileName = codeContext.filePath.substringAfterLast("/").substringBeforeLast(".")
        return when {
            fileName.contains("service", ignoreCase = true) -> "service operations"
            fileName.contains("controller", ignoreCase = true) -> "request handling"
            fileName.contains("repository", ignoreCase = true) -> "data access"
            fileName.contains("model", ignoreCase = true) -> "data modeling"
            fileName.contains("util", ignoreCase = true) -> "utility functions"
            fileName.contains("config", ignoreCase = true) -> "configuration management"
            else -> "core functionality"
        }
    }
    
    private fun generateExampleUsage(func: FunctionInfo, language: String): String {
        return when (language.lowercase()) {
            "kotlin" -> {
                val params = func.parameters.joinToString(", ") { "${it.name} = ${getDefaultValue(it.type)}" }
                "val result = ${func.name}($params)"
            }
            "java" -> {
                val params = func.parameters.joinToString(", ") { getDefaultValue(it.type) }
                "${func.returnType ?: "void"} result = ${func.name}($params);"
            }
            "python" -> {
                val params = func.parameters.joinToString(", ") { "${it.name}=${getDefaultValue(it.type)}" }
                "result = ${func.name}($params)"
            }
            else -> {
                val params = func.parameters.joinToString(", ") { it.name }
                "${func.name}($params)"
            }
        }
    }
    
    private fun generateClassExample(cls: ClassInfo, language: String): String {
        return when (language.lowercase()) {
            "kotlin" -> "val instance = ${cls.name}()\ninstance.${cls.methods.firstOrNull()?.name ?: "method"}()"
            "java" -> "${cls.name} instance = new ${cls.name}();\ninstance.${cls.methods.firstOrNull()?.name ?: "method"}();"
            "python" -> "instance = ${cls.name}()\ninstance.${cls.methods.firstOrNull()?.name ?: "method"}()"
            else -> "${cls.name} instance = new ${cls.name}()"
        }
    }
    
    private fun getDefaultValue(type: String?): String {
        return when (type?.lowercase()) {
            "string" -> "\"example\""
            "int", "integer" -> "0"
            "double", "float" -> "0.0"
            "boolean", "bool" -> "true"
            "list" -> "emptyList()"
            "map" -> "emptyMap()"
            else -> "null"
        }
    }
}

private class LogAnalysisModel {
    fun extractPatterns(logs: List<LogMessage>): List<LogPattern> {
        val patterns = mutableListOf<LogPattern>()
        
        // Group by message patterns
        val messageGroups = logs.groupBy { extractMessagePattern(it.message) }
        
        messageGroups.forEach { (pattern, messages) ->
            if (messages.size > 1) { // Only consider repeated patterns
                val severity = messages.map { it.level }.maxByOrNull { it.ordinal } ?: LogLevel.INFO
                patterns.add(LogPattern(
                    pattern = pattern,
                    frequency = messages.size,
                    severity = severity,
                    description = "Repeated pattern: $pattern"
                ))
            }
        }
        
        return patterns.sortedByDescending { it.frequency }
    }
    
    fun detectAnomalies(logs: List<LogMessage>): List<LogAnomaly> {
        val anomalies = mutableListOf<LogAnomaly>()
        
        // Detect frequency anomalies
        val hourlyGroups = logs.groupBy { it.timestamp.epochSeconds / 3600 }
        val avgHourlyCount = hourlyGroups.values.map { it.size }.average()
        
        hourlyGroups.forEach { (hour, hourLogs) ->
            val count = hourLogs.size
            if (count > avgHourlyCount * 2) { // Spike detection
                anomalies.add(LogAnomaly(
                    id = "freq_${hour}",
                    message = "High log frequency: $count messages in hour $hour",
                    anomalyScore = count / avgHourlyCount,
                    type = AnomalyType.FREQUENCY,
                    timestamp = Instant.fromEpochSeconds(hour * 3600)
                ))
            }
        }
        
        // Detect content anomalies
        val errorLogs = logs.filter { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }
        errorLogs.forEach { log ->
            if (isUnusualError(log.message)) {
                anomalies.add(LogAnomaly(
                    id = "content_${log.id}",
                    message = log.message,
                    anomalyScore = 0.8,
                    type = AnomalyType.CONTENT,
                    timestamp = log.timestamp
                ))
            }
        }
        
        return anomalies
    }
    
    fun generateInsights(logs: List<LogMessage>, patterns: List<LogPattern>, anomalies: List<LogAnomaly>): List<LogInsight> {
        val insights = mutableListOf<LogInsight>()
        
        // Error rate insights
        val errorRate = logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }.toDouble() / logs.size
        if (errorRate > 0.05) {
            insights.add(LogInsight(
                type = InsightType.ERROR_SPIKE,
                description = "High error rate detected: ${(errorRate * 100).toInt()}%",
                impact = "System reliability may be compromised",
                confidence = 0.9
            ))
        }
        
        // Performance insights
        val timeoutPatterns = patterns.filter { it.pattern.contains("timeout", ignoreCase = true) }
        if (timeoutPatterns.isNotEmpty()) {
            insights.add(LogInsight(
                type = InsightType.PERFORMANCE_DEGRADATION,
                description = "Multiple timeout patterns detected",
                impact = "System performance is degraded",
                confidence = 0.8
            ))
        }
        
        // Security insights
        val securityAnomalies = anomalies.filter { it.message.contains("auth", ignoreCase = true) || 
                                                  it.message.contains("login", ignoreCase = true) }
        if (securityAnomalies.isNotEmpty()) {
            insights.add(LogInsight(
                type = InsightType.SECURITY_ISSUE,
                description = "Authentication-related anomalies detected",
                impact = "Potential security threats",
                confidence = 0.7
            ))
        }
        
        return insights
    }
    
    fun generateRecommendations(insights: List<LogInsight>, errorRate: Double): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (errorRate > 0.1) {
            recommendations.add("Investigate and fix high error rate immediately")
            recommendations.add("Implement better error handling and logging")
        }
        
        insights.forEach { insight ->
            when (insight.type) {
                InsightType.ERROR_SPIKE -> {
                    recommendations.add("Review recent deployments and configurations")
                    recommendations.
sb.appendLine("#### Methods")
                    cls.methods.forEach { method ->
                        documentFunction(sb, method)
                    }
                }
            }
        }
        
        // Document functions
        if (codeContext.functions.isNotEmpty()) {
            sb.appendLine("## Functions")
            sb.appendLine()
            codeContext.functions.forEach { func ->
                documentFunction(sb, func)
            }
        }
        
        // Document dependencies
        if (codeContext.dependencies.isNotEmpty()) {
            sb.appendLine("## Dependencies")
            sb.appendLine()
            codeContext.dependencies.forEach { dep ->
                sb.appendLine("- $dep")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    fun generateApiDocumentation(codeContext: CodeContext): String {
        val sb = StringBuilder()
        
        sb.appendLine("# API Documentation")
        sb.appendLine()
        
        codeContext.functions.forEach { func ->
            sb.appendLine("## ${func.name}")
            sb.appendLine()
            sb.appendLine("**Description:** ${func.description ?: "Function description"}")
            sb.appendLine()
            
            if (func.parameters.isNotEmpty()) {
                sb.appendLine("**Parameters:**")
                func.parameters.forEach { param ->
                    sb.appendLine("- `${param.name}` (${param.type ?: "unknown"}): ${param.description ?: "Parameter description"}")
                }
                sb.appendLine()
            }
            
            sb.appendLine("**Returns:** ${func.returnType ?: "void"}")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    fun generateCodeExamples(codeContext: CodeContext): List<CodeExample> {
        val examples = mutableListOf<CodeExample>()
        
        // Generate basic usage example
        if (codeContext.functions.isNotEmpty()) {
            val mainFunction = codeContext.functions.first()
            val exampleCode = generateExampleUsage(mainFunction, codeContext.language)
            examples.add(CodeExample(
                title = "Basic Usage",
                code = exampleCode,
                description = "Basic example of how to use ${mainFunction.name}"
            ))
        }
        
        // Generate class instantiation example
        if (codeContext.classes.isNotEmpty()) {
            val mainClass = codeContext.classes.first()
            val exampleCode = generateClassExample(mainClass, codeContext.language)
            examples.add(CodeExample(
                title = "Class Usage",
                code = exampleCode,
                description = "Example of how to use ${mainClass.name} class"
            ))
        }
        
        return examples
    }
    
    private fun documentFunction(sb: StringBuilder, func: FunctionInfo) {
        sb.appendLine("#### ${func.name}")
        sb.appendLine(func.description ?: "Function description")
        sb.appendLine()
        
        if (func.parameters.isNotEmpty()) {
            sb.appendLine("**Parameters:**")
            func.parameters.forEach { param ->
                sb.appendLine("- `${param.name}` (${param.type ?: "unknown"}): ${param.description ?: "Parameter description"}")
            }
            sb.appendLine()
        }
        
        sb.appendLine("**Returns:** ${func.returnType ?: "void"}")
        sb.appendLine()
    }
    
    private fun inferPurpose(codeContext: CodeContext): String {
        val fileName = codeContext.filePath.substringAfterLast("/").substringBeforeLast(".")
        return when {
            fileName.contains("service", ignoreCase = true) -> "service operations"
            fileName.contains("controller", ignoreCase = true) -> "request handling"
            fileName.contains("repository", ignoreCase = true) -> "data access"
            fileName.contains("model", ignoreCase = true) -> "data modeling"
            fileName.contains("util", ignoreCase = true) -> "utility functions"
            fileName.contains("config", ignoreCase = true) -> "configuration management"
            else -> "core functionality"
        }
    }
    
    private fun generateExampleUsage(func: FunctionInfo, language: String): String {
        return when (language.lowercase()) {
            "kotlin" -> {
                val params = func.parameters.joinToString(", ") { "${it.name} = ${getDefaultValue(it.type)}" }
                "val result = ${func.name}($params)"
            }
            "java" -> {
                val params = func.parameters.joinToString(", ") { getDefaultValue(it.type) }
                "${func.returnType ?: "void"} result = ${func.name}($params);"
            }
            "python" -> {
                val params = func.parameters.joinToString(", ") { "${it.name}=${getDefaultValue(it.type)}" }
                "result = ${func.name}($params)"
            }
            else -> {
                val params = func.parameters.joinToString(", ") { it.name }
                "${func.name}($params)"
            }
        }
    }
    
    private fun generateClassExample(cls: ClassInfo, language: String): String {
        return when (language.lowercase()) {
            "kotlin" -> "val instance = ${cls.name}()\ninstance.${cls.methods.firstOrNull()?.name ?: "method"}()"
            "java" -> "${cls.name} instance = new ${cls.name}();\ninstance.${cls.methods.firstOrNull()?.name ?: "method"}();"
            "python" -> "instance = ${cls.name}()\ninstance.${cls.methods.firstOrNull()?.name ?: "method"}()"
            else -> "${cls.name} instance = new ${cls.name}()"
        }
    }
    
    private fun getDefaultValue(type: String?): String {
        return when (type?.lowercase()) {
            "string" -> "\"example\""
            "int", "integer" -> "0"
            "double", "float" -> "0.0"
            "boolean", "bool" -> "true"
            "list" -> "emptyList()"
            "map" -> "emptyMap()"
            else -> "null"
        }
    }
}

private class LogAnalysisModel {
    fun extractPatterns(logs: List<LogMessage>): List<LogPattern> {
        val patterns = mutableListOf<LogPattern>()
        
        // Group by message patterns
        val messageGroups = logs.groupBy { extractMessagePattern(it.message) }
        
        messageGroups.forEach { (pattern, messages) ->
            if (messages.size > 1) { // Only consider repeated patterns
                val severity = messages.map { it.level }.maxByOrNull { it.ordinal } ?: LogLevel.INFO
                patterns.add(LogPattern(
                    pattern = pattern,
                    frequency = messages.size,
                    severity = severity,
                    description = "Repeated pattern: $pattern"
                ))
            }
        }
        
        return patterns.sortedByDescending { it.frequency }
    }
    
    fun detectAnomalies(logs: List<LogMessage>): List<LogAnomaly> {
        val anomalies = mutableListOf<LogAnomaly>()
        
        // Detect frequency anomalies
        val hourlyGroups = logs.groupBy { it.timestamp.epochSeconds / 3600 }
        val avgHourlyCount = hourlyGroups.values.map { it.size }.average()
        
        hourlyGroups.forEach { (hour, hourLogs) ->
            val count = hourLogs.size
            if (count > avgHourlyCount * 2) { // Spike detection
                anomalies.add(LogAnomaly(
                    id = "freq_${hour}",
                    message = "High log frequency: $count messages in hour $hour",
                    anomalyScore = count / avgHourlyCount,
                    type = AnomalyType.FREQUENCY,
                    timestamp = Instant.fromEpochSeconds(hour * 3600)
                ))
            }
        }
        
        // Detect content anomalies
        val errorLogs = logs.filter { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }
        errorLogs.forEach { log ->
            if (isUnusualError(log.message)) {
                anomalies.add(LogAnomaly(
                    id = "content_${log.id}",
                    message = log.message,
                    anomalyScore = 0.8,
                    type = AnomalyType.CONTENT,
                    timestamp = log.timestamp
                ))
            }
        }
        
        return anomalies
    }
    
    fun generateInsights(logs: List<LogMessage>, patterns: List<LogPattern>, anomalies: List<LogAnomaly>): List<LogInsight> {
        val insights = mutableListOf<LogInsight>()
        
        // Error rate insights
        val errorRate = logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }.toDouble() / logs.size
        if (errorRate > 0.05) {
            insights.add(LogInsight(
                type = InsightType.ERROR_SPIKE,
                description = "High error rate detected: ${(errorRate * 100).toInt()}%",
                impact = "System reliability may be compromised",
                confidence = 0.9
            ))
        }
        
        // Performance insights
        val timeoutPatterns = patterns.filter { it.pattern.contains("timeout", ignoreCase = true) }
        if (timeoutPatterns.isNotEmpty()) {
            insights.add(LogInsight(
                type = InsightType.PERFORMANCE_DEGRADATION,
                description = "Multiple timeout patterns detected",
                impact = "System performance is degraded",
                confidence = 0.8
            ))
        }
        
        // Security insights
        val securityAnomalies = anomalies.filter { it.message.contains("auth", ignoreCase = true) || 
                                                  it.message.contains("login", ignoreCase = true) }
        if (securityAnomalies.isNotEmpty()) {
            insights.add(LogInsight(
                type = InsightType.SECURITY_ISSUE,
                description = "Authentication-related anomalies detected",
                impact = "Potential security threats",
                confidence = 0.7
            ))
        }
        
        return insights
    }
    
    fun generateRecommendations(insights: List<LogInsight>, errorRate: Double): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (errorRate > 0.1) {
            recommendations.add("Investigate and fix high error rate immediately")
            recommendations.add("Implement better error handling and logging")
        }
        
        insights.forEach { insight ->
            when (insight.type) {
                InsightType.ERROR_SPIKE -> {
                    recommendations.add("Review recent deployments and configurations")
                    recommendations.add("Implement circuit breakers and retry mechanisms")
                }
                InsightType.PERFORMANCE_DEGRADATION -> {
                    recommendations.add("Scale up resources or optimize performance")
                    recommendations.add("Review database queries and API calls")
                }
                InsightType.SECURITY_ISSUE -> {
                    recommendations.add("Review authentication and authorization systems")
                    recommendations.add("Implement additional security monitoring")
                }
                InsightType.SYSTEM_HEALTH -> {
                    recommendations.add("Monitor system health metrics closely")
                    recommendations.add("Consider preventive maintenance")
                }
            }
        }
        
        return recommendations
    }
    
    private fun extractMessagePattern(message: String): String {
        // Extract pattern by replacing specific values with placeholders
        return message
            .replace(Regex("\\d+"), "{NUMBER}")
            .replace(Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"), "{UUID}")
            .replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "{IP}")
            .replace(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"), "{DATE}")
            .replace(Regex("\\b\\d{2}:\\d{2}:\\d{2}\\b"), "{TIME}")
    }
    
    private fun isUnusualError(message: String): Boolean {
        val unusualKeywords = setOf(
            "outofmemory", "stackoverflow", "deadlock", "corruption", "fatal", "panic"
        )
        return unusualKeywords.any { message.lowercase().contains(it) }
    }
}

// Helper data classes
private data class SentimentResult(val score: Double, val confidence: Double)
private data class ThreatResult(val score: Double, val confidence: Double)