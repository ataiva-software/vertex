package com.ataiva.eden.config

import com.ataiva.eden.database.DatabaseConfig
import java.io.File
import java.util.Properties
import java.util.logging.Logger

/**
 * Database configuration loader
 *
 * This class provides functionality to load database configuration from various sources:
 * - Configuration files (HOCON, properties, JSON)
 * - Environment variables
 * - System properties
 * - Default values
 *
 * It supports multiple environments (dev, test, prod) and can be extended
 * to support custom configuration sources.
 *
 * @author Eden Configuration Team
 * @version 1.0.0
 */
class DatabaseConfigLoader {
    private val logger = Logger.getLogger(DatabaseConfigLoader::class.java.name)
    
    /**
     * Load database configuration from a file
     *
     * @param configPath Path to the configuration file
     * @param environment Environment name (dev, test, prod)
     * @return DatabaseConfig object
     */
    fun loadFromFile(configPath: String, environment: String = "dev"): DatabaseConfig {
        logger.info("Loading database configuration from $configPath for environment $environment")
        
        try {
            val file = File(configPath)
            if (!file.exists()) {
                logger.warning("Configuration file not found: $configPath")
                return loadFromEnvironment(environment)
            }
            
            // Check file extension
            return when {
                configPath.endsWith(".properties") -> loadFromProperties(configPath, environment)
                configPath.endsWith(".json") -> {
                    logger.warning("JSON configuration not implemented yet")
                    loadFromEnvironment(environment)
                }
                else -> {
                    logger.warning("Unsupported file format: $configPath")
                    loadFromEnvironment(environment)
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to load configuration from file: ${e.message}")
            return loadFromEnvironment(environment)
        }
    }
    
    /**
     * Load database configuration from properties file
     *
     * @param propertiesPath Path to the properties file
     * @param environment Environment name (dev, test, prod)
     * @return DatabaseConfig object
     */
    fun loadFromProperties(propertiesPath: String, environment: String = "dev"): DatabaseConfig {
        logger.info("Loading database configuration from properties file $propertiesPath for environment $environment")
        
        try {
            val file = File(propertiesPath)
            if (!file.exists()) {
                logger.warning("Properties file not found: $propertiesPath")
                return loadFromEnvironment(environment)
            }
            
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            
            val prefix = "database.$environment."
            val defaultPrefix = "database."
            
            fun getProperty(key: String): String? {
                return properties.getProperty("$prefix$key") ?: properties.getProperty("$defaultPrefix$key")
            }
            
            val url = getProperty("url")
            val username = getProperty("username")
            val password = getProperty("password")
            val driverClassName = getProperty("driver-class-name")
            
            if (url == null || username == null || password == null || driverClassName == null) {
                logger.warning("Missing required database configuration properties")
                return loadFromEnvironment(environment)
            }
            
            val dbProperties = mutableMapOf<String, String>()
            properties.stringPropertyNames().forEach { propName ->
                if (propName.startsWith("$prefix.properties.")) {
                    val key = propName.substring("$prefix.properties.".length)
                    dbProperties[key] = properties.getProperty(propName)
                } else if (propName.startsWith("$defaultPrefix.properties.")) {
                    val key = propName.substring("$defaultPrefix.properties.".length)
                    if (!dbProperties.containsKey(key)) {
                        dbProperties[key] = properties.getProperty(propName)
                    }
                }
            }
            
            return DatabaseConfig(
                url = url,
                username = username,
                password = password,
                driverClassName = driverClassName,
                maxPoolSize = getProperty("max-pool-size")?.toIntOrNull() ?: 10,
                minIdle = getProperty("min-idle")?.toIntOrNull() ?: 5,
                idleTimeout = getProperty("idle-timeout")?.toLongOrNull() ?: 600000,
                connectionTimeout = getProperty("connection-timeout")?.toLongOrNull() ?: 30000,
                validationTimeout = getProperty("validation-timeout")?.toLongOrNull() ?: 5000,
                maxLifetime = getProperty("max-lifetime")?.toLongOrNull() ?: 1800000,
                autoCommit = getProperty("auto-commit")?.toBoolean() ?: false,
                schema = getProperty("schema"),
                properties = dbProperties
            )
        } catch (e: Exception) {
            logger.warning("Failed to load configuration from properties file: ${e.message}")
            return loadFromEnvironment(environment)
        }
    }
    
    /**
     * Load database configuration from environment variables
     *
     * @param environment Environment name (dev, test, prod)
     * @return DatabaseConfig object
     */
    fun loadFromEnvironment(environment: String = "dev"): DatabaseConfig {
        logger.info("Loading database configuration from environment variables for environment $environment")
        
        val envPrefix = "DATABASE_${environment.uppercase()}_"
        val defaultEnvPrefix = "DATABASE_"
        
        fun getEnv(key: String): String? {
            return System.getenv("$envPrefix$key") ?: System.getenv("$defaultEnvPrefix$key")
        }
        
        val url = getEnv("URL") ?: getDefaultUrl(environment)
        val username = getEnv("USERNAME") ?: getDefaultUsername(environment)
        val password = getEnv("PASSWORD") ?: getDefaultPassword(environment)
        val driverClassName = getEnv("DRIVER_CLASS_NAME") ?: "org.postgresql.Driver"
        
        val properties = mutableMapOf<String, String>()
        System.getenv().forEach { (key, value) ->
            if (key.startsWith("${envPrefix}PROPERTIES_")) {
                val propKey = key.substring("${envPrefix}PROPERTIES_".length).lowercase().replace('_', '-')
                properties[propKey] = value
            } else if (key.startsWith("${defaultEnvPrefix}PROPERTIES_")) {
                val propKey = key.substring("${defaultEnvPrefix}PROPERTIES_".length).lowercase().replace('_', '-')
                if (!properties.containsKey(propKey)) {
                    properties[propKey] = value
                }
            }
        }
        
        return DatabaseConfig(
            url = url,
            username = username,
            password = password,
            driverClassName = driverClassName,
            maxPoolSize = getEnv("MAX_POOL_SIZE")?.toIntOrNull() ?: 10,
            minIdle = getEnv("MIN_IDLE")?.toIntOrNull() ?: 5,
            idleTimeout = getEnv("IDLE_TIMEOUT")?.toLongOrNull() ?: 600000,
            connectionTimeout = getEnv("CONNECTION_TIMEOUT")?.toLongOrNull() ?: 30000,
            validationTimeout = getEnv("VALIDATION_TIMEOUT")?.toLongOrNull() ?: 5000,
            maxLifetime = getEnv("MAX_LIFETIME")?.toLongOrNull() ?: 1800000,
            autoCommit = getEnv("AUTO_COMMIT")?.toBoolean() ?: false,
            schema = getEnv("SCHEMA"),
            properties = properties
        )
    }
    
    /**
     * Get default database URL for the given environment
     */
    private fun getDefaultUrl(environment: String): String {
        return when (environment) {
            "prod" -> "jdbc:postgresql://db:5432/eden_prod"
            "test" -> "jdbc:postgresql://localhost:5432/eden_test"
            else -> "jdbc:postgresql://localhost:5432/eden_dev"
        }
    }
    
    /**
     * Get default database username for the given environment
     */
    private fun getDefaultUsername(environment: String): String {
        return when (environment) {
            "prod" -> "eden_prod"
            "test" -> "eden_test"
            else -> "eden_dev"
        }
    }
    
    /**
     * Get default database password for the given environment
     */
    private fun getDefaultPassword(environment: String): String {
        return when (environment) {
            "prod" -> "eden_prod_password"
            "test" -> "eden_test_password"
            else -> "eden_dev_password"
        }
    }
    
    companion object {
        /**
         * Create a database configuration loader and load configuration from the default location
         */
        fun load(environment: String = "dev"): DatabaseConfig {
            val loader = DatabaseConfigLoader()
            
            // Try to load from application.properties
            val propertiesFile = System.getProperty("config.properties") ?: "application.properties"
            if (File(propertiesFile).exists()) {
                return loader.loadFromProperties(propertiesFile, environment)
            }
            
            // Fall back to environment variables
            return loader.loadFromEnvironment(environment)
        }
    }
}