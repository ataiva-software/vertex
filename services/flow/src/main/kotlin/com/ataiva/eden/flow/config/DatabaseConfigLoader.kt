package com.ataiva.eden.flow.config

import com.typesafe.config.ConfigFactory
import java.io.File

object DatabaseConfigLoader {
    fun load(): DatabaseConfig {
        val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.conf"
        val configFile = File(configPath)
        
        val config = if (configFile.exists()) {
            ConfigFactory.parseFile(configFile)
        } else {
            ConfigFactory.load()
        }
        
        return try {
            val dbConfig = config.getConfig("database")
            DatabaseConfig(
                url = dbConfig.getString("url"),
                username = dbConfig.getString("username"),
                password = dbConfig.getString("password"),
                poolSize = if (dbConfig.hasPath("poolSize")) dbConfig.getInt("poolSize") else 10,
                connectionTimeout = if (dbConfig.hasPath("connectionTimeout")) dbConfig.getLong("connectionTimeout") else 30000,
                idleTimeout = if (dbConfig.hasPath("idleTimeout")) dbConfig.getLong("idleTimeout") else 600000,
                maxLifetime = if (dbConfig.hasPath("maxLifetime")) dbConfig.getLong("maxLifetime") else 1800000
            )
        } catch (e: Exception) {
            // Fallback to default configuration
            DatabaseConfig()
        }
    }
}

data class DatabaseConfig(
    val url: String = "jdbc:postgresql://localhost:5432/eden",
    val username: String = "eden_user",
    val password: String = "eden_password",
    val poolSize: Int = 10,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000
)