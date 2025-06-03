package com.ataiva.eden.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * PostgreSQL database implementation using Exposed and HikariCP
 */
class PostgreSQLDatabaseImpl(
    private val config: DatabaseConfig
) : DatabaseConnection {
    
    private val dataSource: DataSource by lazy {
        createDataSource()
    }
    
    private val database: Database by lazy {
        Database.connect(dataSource)
    }

    override suspend fun <T> query(
        sql: String,
        parameters: Map<String, Any?>,
        mapper: (ResultRow) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        newSuspendedTransaction(db = database) {
            val results = mutableListOf<T>()
            exec(sql, parameters.values.toList()) { resultSet ->
                while (resultSet.next()) {
                    val row = ExposedResultRow(resultSet)
                    results.add(mapper(row))
                }
            }
            results
        }
    }

    override suspend fun <T> queryOne(
        sql: String,
        parameters: Map<String, Any?>,
        mapper: (ResultRow) -> T
    ): T? = withContext(Dispatchers.IO) {
        newSuspendedTransaction(db = database) {
            var result: T? = null
            exec(sql, parameters.values.toList()) { resultSet ->
                if (resultSet.next()) {
                    val row = ExposedResultRow(resultSet)
                    result = mapper(row)
                }
            }
            result
        }
    }

    override suspend fun execute(sql: String, parameters: Map<String, Any?>): Int = 
        withContext(Dispatchers.IO) {
            newSuspendedTransaction(db = database) {
                exec(sql, parameters.values.toList())
                1 // Return affected rows count - simplified for now
            }
        }

    override suspend fun <T> transaction(block: suspend (DatabaseConnection) -> T): T =
        withContext(Dispatchers.IO) {
            newSuspendedTransaction(db = database) {
                block(this@PostgreSQLDatabaseImpl)
            }
        }

    override suspend fun close() {
        if (dataSource is HikariDataSource) {
            dataSource.close()
        }
    }

    private fun createDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            driverClassName = config.driverClassName
            maximumPoolSize = config.maxPoolSize
            minimumIdle = config.minIdleConnections
            connectionTimeout = config.connectionTimeout
            idleTimeout = config.idleTimeout
            maxLifetime = config.maxLifetime
            isAutoCommit = config.autoCommit
            
            // PostgreSQL specific optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")
        }
        
        return HikariDataSource(hikariConfig)
    }
}

/**
 * Exposed-based ResultRow implementation
 */
class ExposedResultRow(private val resultSet: ResultSet) : ResultRow {
    
    override fun getString(columnName: String): String? = 
        resultSet.getString(columnName)?.takeIf { !resultSet.wasNull() }
    
    override fun getInt(columnName: String): Int? = 
        resultSet.getInt(columnName).takeIf { !resultSet.wasNull() }
    
    override fun getLong(columnName: String): Long? = 
        resultSet.getLong(columnName).takeIf { !resultSet.wasNull() }
    
    override fun getBoolean(columnName: String): Boolean? = 
        resultSet.getBoolean(columnName).takeIf { !resultSet.wasNull() }
    
    override fun getDouble(columnName: String): Double? = 
        resultSet.getDouble(columnName).takeIf { !resultSet.wasNull() }
    
    override fun getBytes(columnName: String): ByteArray? = 
        resultSet.getBytes(columnName)?.takeIf { !resultSet.wasNull() }
    
    override fun getTimestamp(columnName: String): Instant? = 
        resultSet.getTimestamp(columnName)?.toInstant()?.toKotlinInstant()
    
    override fun isNull(columnName: String): Boolean {
        resultSet.getObject(columnName)
        return resultSet.wasNull()
    }
}

/**
 * PostgreSQL connection pool implementation
 */
class PostgreSQLConnectionPool(
    private val config: DatabaseConfig
) : ConnectionPool {
    
    private val dataSource: HikariDataSource by lazy {
        createDataSource()
    }
    
    override suspend fun getConnection(): DatabaseConnection {
        return PostgreSQLDatabaseImpl(config)
    }
    
    override suspend fun returnConnection(connection: DatabaseConnection) {
        // HikariCP handles connection pooling automatically
        // No explicit return needed
    }
    
    override suspend fun close() {
        dataSource.close()
    }
    
    override fun getStats(): PoolStats {
        return PoolStats(
            totalConnections = dataSource.hikariPoolMXBean?.totalConnections ?: 0,
            activeConnections = dataSource.hikariPoolMXBean?.activeConnections ?: 0,
            idleConnections = dataSource.hikariPoolMXBean?.idleConnections ?: 0,
            waitingForConnection = dataSource.hikariPoolMXBean?.threadsAwaitingConnection ?: 0
        )
    }
    
    private fun createDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            driverClassName = config.driverClassName
            maximumPoolSize = config.maxPoolSize
            minimumIdle = config.minIdleConnections
            connectionTimeout = config.connectionTimeout
            idleTimeout = config.idleTimeout
            maxLifetime = config.maxLifetime
            isAutoCommit = config.autoCommit
            
            // Connection pool name for monitoring
            poolName = "EdenPostgreSQLPool"
            
            // Health check query
            connectionTestQuery = "SELECT 1"
            
            // PostgreSQL specific optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
        }
        
        return HikariDataSource(hikariConfig)
    }
}

/**
 * SQL Query Builder implementation
 */
class SQLQueryBuilder : QueryBuilder {
    private val selectColumns = mutableListOf<String>()
    private var fromTable: String? = null
    private val whereConditions = mutableListOf<String>()
    private val orderByColumns = mutableListOf<String>()
    private val joinClauses = mutableListOf<String>()
    private var limitCount: Int? = null
    private var offsetCount: Int? = null
    private val parameters = mutableMapOf<String, Any?>()
    private var parameterIndex = 0

    override fun select(vararg columns: String): QueryBuilder {
        selectColumns.addAll(columns)
        return this
    }

    override fun from(table: String): QueryBuilder {
        fromTable = table
        return this
    }

    override fun where(condition: String, vararg parameters: Any?): QueryBuilder {
        whereConditions.add(condition)
        parameters.forEach { param ->
            this.parameters["param${parameterIndex++}"] = param
        }
        return this
    }

    override fun and(condition: String, vararg parameters: Any?): QueryBuilder {
        if (whereConditions.isNotEmpty()) {
            whereConditions.add("AND $condition")
        } else {
            whereConditions.add(condition)
        }
        parameters.forEach { param ->
            this.parameters["param${parameterIndex++}"] = param
        }
        return this
    }

    override fun or(condition: String, vararg parameters: Any?): QueryBuilder {
        if (whereConditions.isNotEmpty()) {
            whereConditions.add("OR $condition")
        } else {
            whereConditions.add(condition)
        }
        parameters.forEach { param ->
            this.parameters["param${parameterIndex++}"] = param
        }
        return this
    }

    override fun orderBy(column: String, direction: SortDirection): QueryBuilder {
        orderByColumns.add("$column ${direction.name}")
        return this
    }

    override fun limit(count: Int): QueryBuilder {
        limitCount = count
        return this
    }

    override fun offset(count: Int): QueryBuilder {
        offsetCount = count
        return this
    }

    override fun join(table: String, condition: String): QueryBuilder {
        joinClauses.add("JOIN $table ON $condition")
        return this
    }

    override fun leftJoin(table: String, condition: String): QueryBuilder {
        joinClauses.add("LEFT JOIN $table ON $condition")
        return this
    }

    override fun build(): String {
        val sql = StringBuilder()
        
        // SELECT clause
        if (selectColumns.isNotEmpty()) {
            sql.append("SELECT ${selectColumns.joinToString(", ")}")
        } else {
            sql.append("SELECT *")
        }
        
        // FROM clause
        fromTable?.let { table ->
            sql.append(" FROM $table")
        }
        
        // JOIN clauses
        joinClauses.forEach { join ->
            sql.append(" $join")
        }
        
        // WHERE clause
        if (whereConditions.isNotEmpty()) {
            sql.append(" WHERE ${whereConditions.joinToString(" ")}")
        }
        
        // ORDER BY clause
        if (orderByColumns.isNotEmpty()) {
            sql.append(" ORDER BY ${orderByColumns.joinToString(", ")}")
        }
        
        // LIMIT clause
        limitCount?.let { limit ->
            sql.append(" LIMIT $limit")
        }
        
        // OFFSET clause
        offsetCount?.let { offset ->
            sql.append(" OFFSET $offset")
        }
        
        return sql.toString()
    }

    override fun getParameters(): Map<String, Any?> = parameters.toMap()
}

/**
 * PostgreSQL Database Factory
 */
class PostgreSQLDatabaseFactory : DatabaseFactory {
    
    override suspend fun createConnection(config: DatabaseConfig): DatabaseConnection {
        return PostgreSQLDatabaseImpl(config)
    }
    
    override suspend fun createConnectionPool(config: DatabaseConfig): ConnectionPool {
        return PostgreSQLConnectionPool(config)
    }
    
    override fun createMigrationManager(config: DatabaseConfig): MigrationManager {
        return FlywayMigrationManager(config)
    }
}

/**
 * Utility functions for database operations
 */
object DatabaseUtils {
    
    /**
     * Create database configuration from environment variables
     */
    fun createConfigFromEnv(): DatabaseConfig {
        return DatabaseConfig(
            url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/eden_dev",
            username = System.getenv("DATABASE_USERNAME") ?: "eden",
            password = System.getenv("DATABASE_PASSWORD") ?: "dev_password",
            driverClassName = "org.postgresql.Driver",
            maxPoolSize = System.getenv("DATABASE_MAX_POOL_SIZE")?.toIntOrNull() ?: 10,
            minIdleConnections = System.getenv("DATABASE_MIN_IDLE")?.toIntOrNull() ?: 2,
            connectionTimeout = System.getenv("DATABASE_CONNECTION_TIMEOUT")?.toLongOrNull() ?: 30000,
            idleTimeout = System.getenv("DATABASE_IDLE_TIMEOUT")?.toLongOrNull() ?: 600000,
            maxLifetime = System.getenv("DATABASE_MAX_LIFETIME")?.toLongOrNull() ?: 1800000,
            autoCommit = System.getenv("DATABASE_AUTO_COMMIT")?.toBooleanStrictOrNull() ?: true,
            schema = System.getenv("DATABASE_SCHEMA")
        )
    }
    
    /**
     * Test database connectivity
     */
    suspend fun testConnection(config: DatabaseConfig): Boolean {
        return try {
            val connection = PostgreSQLDatabaseImpl(config)
            connection.queryOne("SELECT 1 as test") { row ->
                row.getInt("test")
            }
            connection.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}