package com.ataiva.eden.insight.config

import com.ataiva.eden.insight.repository.*
import com.ataiva.eden.insight.repository.impl.*
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

/**
 * Database configuration for the Insight Service.
 * Manages database connections, migrations, and provides repository instances.
 */
class InsightDatabaseConfig(
    private val configPath: String = "database",
    private val migrateOnStartup: Boolean = true
) {
    private val config = ConfigFactory.load().getConfig(configPath)
    private val dataSource: DataSource
    private val database: Database
    
    // Repository instances
    val analyticsQueryRepository: AnalyticsQueryRepository
    val queryExecutionRepository: QueryExecutionRepository
    val reportRepository: ReportRepository
    val reportTemplateRepository: ReportTemplateRepository
    val reportExecutionRepository: ReportExecutionRepository
    val dashboardRepository: DashboardRepository
    val metricRepository: MetricRepository
    val metricValueRepository: MetricValueRepository
    val kpiRepository: KPIRepository
    
    init {
        // Initialize data source
        dataSource = createDataSource()
        
        // Run migrations if enabled
        if (migrateOnStartup && config.getBoolean("migration.enabled")) {
            runMigrations()
        }
        
        // Connect to database
        database = Database.connect(dataSource)
        
        // Create tables if they don't exist
        createTablesIfNeeded()
        
        // Initialize repositories
        analyticsQueryRepository = AnalyticsQueryRepositoryImpl(database)
        queryExecutionRepository = QueryExecutionRepositoryImpl(database)
        reportRepository = ReportRepositoryImpl(database)
        reportTemplateRepository = ReportTemplateRepositoryImpl(database)
        reportExecutionRepository = ReportExecutionRepositoryImpl(database)
        dashboardRepository = DashboardRepositoryImpl(database)
        metricRepository = MetricRepositoryImpl(database)
        metricValueRepository = MetricValueRepositoryImpl(database)
        kpiRepository = KPIRepositoryImpl(database)
    }
    
    /**
     * Create HikariCP data source with configuration from application.conf
     */
    private fun createDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            driverClassName = config.getString("driver")
            jdbcUrl = config.getString("url")
            username = config.getString("user")
            password = config.getString("password")
            
            // Connection pool settings
            maximumPoolSize = config.getInt("pool.max-size")
            minimumIdle = config.getInt("pool.min-idle")
            idleTimeout = config.getLong("pool.idle-timeout")
            maxLifetime = config.getLong("pool.max-lifetime")
            connectionTimeout = config.getLong("pool.connection-timeout")
            validationTimeout = config.getLong("pool.validation-timeout")
            
            // Additional properties
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        
        return HikariDataSource(hikariConfig)
    }
    
    /**
     * Run database migrations using Flyway
     */
    private fun runMigrations() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(config.getString("migration.location"))
            .baselineOnMigrate(config.getBoolean("migration.baseline-on-migrate"))
            .load()
            
        flyway.migrate()
    }
    
    /**
     * Create database tables if they don't exist
     */
    private fun createTablesIfNeeded() {
        transaction(database) {
            // Create all tables defined in InsightTables.kt
            SchemaUtils.createMissingTablesAndColumns(
                AnalyticsQueriesTable,
                QueryExecutionsTable,
                ReportTemplatesTable,
                ReportsTable,
                ReportExecutionsTable,
                DashboardsTable,
                MetricsTable,
                MetricValuesTable,
                KPIsTable
            )
        }
    }
    
    /**
     * Get the data source for metrics collection
     */
    fun getDataSource(): HikariDataSource {
        return if (dataSource is HikariDataSource) {
            dataSource as HikariDataSource
        } else {
            throw IllegalStateException("DataSource is not a HikariDataSource")
        }
    }
    
    /**
     * Close database connections
     */
    fun close() {
        if (dataSource is HikariDataSource) {
            dataSource.close()
        }
    }
}