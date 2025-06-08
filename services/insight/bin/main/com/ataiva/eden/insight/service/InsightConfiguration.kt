package com.ataiva.eden.insight.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Configuration class for the Insight Service.
 * Contains all configurable parameters for the service.
 */
data class InsightConfiguration(
    // Report generation settings
    val reportOutputPath: String,
    
    // Cache settings
    val cacheEnabled: Boolean,
    val cacheMaxSize: Int,
    val cacheTtlMinutes: Int,
    
    // Query settings
    val queryTimeoutSeconds: Int,
    val maxResultRows: Int,
    
    // Performance optimization settings
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    
    // Connection pool settings
    val maxConcurrentQueries: Int = 20,
    val queryQueueSize: Int = 100,
    
    // Batch processing settings
    val batchSize: Int = 1000,
    val parallelProcessingEnabled: Boolean = true,
    val maxParallelTasks: Int = Runtime.getRuntime().availableProcessors() * 2
)