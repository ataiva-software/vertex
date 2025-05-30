package com.ataiva.eden.database

/**
 * Generic repository interface for CRUD operations
 */
interface Repository<T, ID> {
    /**
     * Find entity by ID
     */
    suspend fun findById(id: ID): T?
    
    /**
     * Find all entities
     */
    suspend fun findAll(): List<T>
    
    /**
     * Find entities with pagination
     */
    suspend fun findAll(offset: Int, limit: Int): Page<T>
    
    /**
     * Save entity (create or update)
     */
    suspend fun save(entity: T): T
    
    /**
     * Save multiple entities
     */
    suspend fun saveAll(entities: List<T>): List<T>
    
    /**
     * Delete entity by ID
     */
    suspend fun deleteById(id: ID): Boolean
    
    /**
     * Delete entity
     */
    suspend fun delete(entity: T): Boolean
    
    /**
     * Check if entity exists by ID
     */
    suspend fun existsById(id: ID): Boolean
    
    /**
     * Count all entities
     */
    suspend fun count(): Long
}

/**
 * Paginated result
 */
data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * Database connection interface
 */
interface DatabaseConnection {
    /**
     * Execute query and return results
     */
    suspend fun <T> query(sql: String, parameters: Map<String, Any?> = emptyMap(), mapper: (ResultRow) -> T): List<T>
    
    /**
     * Execute query and return single result
     */
    suspend fun <T> queryOne(sql: String, parameters: Map<String, Any?> = emptyMap(), mapper: (ResultRow) -> T): T?
    
    /**
     * Execute update/insert/delete query
     */
    suspend fun execute(sql: String, parameters: Map<String, Any?> = emptyMap()): Int
    
    /**
     * Execute query in transaction
     */
    suspend fun <T> transaction(block: suspend (DatabaseConnection) -> T): T
    
    /**
     * Close connection
     */
    suspend fun close()
}

/**
 * Result row interface for database queries
 */
interface ResultRow {
    /**
     * Get string value by column name
     */
    fun getString(columnName: String): String?
    
    /**
     * Get int value by column name
     */
    fun getInt(columnName: String): Int?
    
    /**
     * Get long value by column name
     */
    fun getLong(columnName: String): Long?
    
    /**
     * Get boolean value by column name
     */
    fun getBoolean(columnName: String): Boolean?
    
    /**
     * Get double value by column name
     */
    fun getDouble(columnName: String): Double?
    
    /**
     * Get bytes value by column name
     */
    fun getBytes(columnName: String): ByteArray?
    
    /**
     * Get timestamp value by column name
     */
    fun getTimestamp(columnName: String): kotlinx.datetime.Instant?
    
    /**
     * Check if column is null
     */
    fun isNull(columnName: String): Boolean
}

/**
 * Query builder interface
 */
interface QueryBuilder {
    /**
     * SELECT clause
     */
    fun select(vararg columns: String): QueryBuilder
    
    /**
     * FROM clause
     */
    fun from(table: String): QueryBuilder
    
    /**
     * WHERE clause
     */
    fun where(condition: String, vararg parameters: Any?): QueryBuilder
    
    /**
     * AND condition
     */
    fun and(condition: String, vararg parameters: Any?): QueryBuilder
    
    /**
     * OR condition
     */
    fun or(condition: String, vararg parameters: Any?): QueryBuilder
    
    /**
     * ORDER BY clause
     */
    fun orderBy(column: String, direction: SortDirection = SortDirection.ASC): QueryBuilder
    
    /**
     * LIMIT clause
     */
    fun limit(count: Int): QueryBuilder
    
    /**
     * OFFSET clause
     */
    fun offset(count: Int): QueryBuilder
    
    /**
     * JOIN clause
     */
    fun join(table: String, condition: String): QueryBuilder
    
    /**
     * LEFT JOIN clause
     */
    fun leftJoin(table: String, condition: String): QueryBuilder
    
    /**
     * Build SQL query
     */
    fun build(): String
    
    /**
     * Get query parameters
     */
    fun getParameters(): Map<String, Any?>
}

/**
 * Sort direction enum
 */
enum class SortDirection {
    ASC, DESC
}

/**
 * Migration interface
 */
interface Migration {
    /**
     * Migration version
     */
    val version: String
    
    /**
     * Migration description
     */
    val description: String
    
    /**
     * Execute migration
     */
    suspend fun up(connection: DatabaseConnection)
    
    /**
     * Rollback migration
     */
    suspend fun down(connection: DatabaseConnection)
}

/**
 * Migration manager interface
 */
interface MigrationManager {
    /**
     * Run all pending migrations
     */
    suspend fun migrate(): List<String>
    
    /**
     * Rollback to specific version
     */
    suspend fun rollback(version: String): List<String>
    
    /**
     * Get migration status
     */
    suspend fun getStatus(): List<MigrationStatus>
    
    /**
     * Validate migrations
     */
    suspend fun validate(): Boolean
}

/**
 * Migration status
 */
data class MigrationStatus(
    val version: String,
    val description: String,
    val applied: Boolean,
    val appliedAt: kotlinx.datetime.Instant?
)

/**
 * Database configuration
 */
data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val driverClassName: String = "org.postgresql.Driver",
    val maxPoolSize: Int = 10,
    val minIdleConnections: Int = 2,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000,
    val autoCommit: Boolean = true,
    val schema: String? = null
)

/**
 * Database factory interface
 */
interface DatabaseFactory {
    /**
     * Create database connection
     */
    suspend fun createConnection(config: DatabaseConfig): DatabaseConnection
    
    /**
     * Create connection pool
     */
    suspend fun createConnectionPool(config: DatabaseConfig): ConnectionPool
    
    /**
     * Create migration manager
     */
    fun createMigrationManager(config: DatabaseConfig): MigrationManager
}

/**
 * Connection pool interface
 */
interface ConnectionPool {
    /**
     * Get connection from pool
     */
    suspend fun getConnection(): DatabaseConnection
    
    /**
     * Return connection to pool
     */
    suspend fun returnConnection(connection: DatabaseConnection)
    
    /**
     * Close all connections in pool
     */
    suspend fun close()
    
    /**
     * Get pool statistics
     */
    fun getStats(): PoolStats
}

/**
 * Connection pool statistics
 */
data class PoolStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val idleConnections: Int,
    val waitingForConnection: Int
)