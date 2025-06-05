# Eden Insight Service - Database Repositories

## Overview

The Database Repositories system is a core component of the Eden Insight Service that provides production-ready data access and persistence capabilities. This implementation replaces all previously mocked repositories with fully functional, enterprise-grade database access layers that ensure data integrity, performance, and reliability.

## Architecture

The Database Repositories follow a clean architecture pattern with clear separation of concerns:

```
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│  Repository         │────▶│  Database Tables    │────▶│  Database Config    │
│  Interfaces         │     │  Definitions        │     │                     │
└─────────────────────┘     └─────────────────────┘     └─────────────────────┘
          │                           │                           │
          ▼                           ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│  Repository         │────▶│  Domain Models      │────▶│  Connection Pool    │
│  Implementations    │     │                     │     │                     │
└─────────────────────┘     └─────────────────────┘     └─────────────────────┘
          │                           │                           │
          └───────────────────────────┴───────────────────────────┘
                                      │
                                      ▼
                           ┌─────────────────────┐
                           │  Transaction        │
                           │  Management         │
                           └─────────────────────┘
```

## Key Components

### Repository Interfaces

The repository interfaces define the contract for data access operations:

- Clear method signatures for CRUD operations
- Domain-specific query methods
- Consistent return types and error handling
- Suspension functions for coroutine support

Example:

```kotlin
interface AnalyticsQueryRepository {
    suspend fun findById(id: String): AnalyticsQuery?
    suspend fun findAll(): List<AnalyticsQuery>
    suspend fun save(entity: AnalyticsQuery): AnalyticsQuery
    suspend fun update(entity: AnalyticsQuery): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByName(name: String): AnalyticsQuery?
    suspend fun findByCreatedBy(createdBy: String): List<AnalyticsQuery>
    suspend fun findByQueryType(queryType: QueryType): List<AnalyticsQuery>
    suspend fun findByTags(tags: List<String>): List<AnalyticsQuery>
    suspend fun findActive(): List<AnalyticsQuery>
    suspend fun findByCreatedByAndActive(createdBy: String, isActive: Boolean): List<AnalyticsQuery>
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    suspend fun search(namePattern: String): List<AnalyticsQuery>
}
```

### Database Table Definitions

The table definitions use Exposed DSL to define the database schema:

- Type-safe column definitions
- Proper data types and constraints
- Index definitions for query optimization
- Foreign key relationships
- Consistent naming conventions

Example:

```kotlin
object AnalyticsQueries : Table("analytics_queries") {
    val id = varchar("id", 36).primaryKey()
    val name = varchar("name", 255)
    val description = text("description")
    val queryText = text("query_text")
    val queryType = varchar("query_type", 50)
    val parameters = text("parameters")
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val lastModified = timestamp("last_modified")
    val isActive = bool("is_active")
    val tags = text("tags")
    
    init {
        index(isUnique = true, name = "idx_analytics_queries_name", columns = arrayOf(name))
        index(isUnique = false, name = "idx_analytics_queries_created_by", columns = arrayOf(createdBy))
        index(isUnique = false, name = "idx_analytics_queries_query_type", columns = arrayOf(queryType))
        index(isUnique = false, name = "idx_analytics_queries_is_active", columns = arrayOf(isActive))
    }
}
```

### Repository Implementations

The repository implementations provide concrete data access logic:

- Transaction management for data consistency
- Efficient query execution
- Proper error handling and logging
- Mapping between database rows and domain models
- Optimized database operations

Example:

```kotlin
class AnalyticsQueryRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AnalyticsQueryRepository {

    override suspend fun findById(id: String): AnalyticsQuery? = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.id eq id }
            .singleOrNull()
            ?.toAnalyticsQuery()
    }
    
    override suspend fun save(entity: AnalyticsQuery): AnalyticsQuery = transaction(database) {
        AnalyticsQueries.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[queryText] = entity.queryText
            it[queryType] = entity.queryType.name
            it[parameters] = json.encodeToString(entity.parameters)
            it[createdBy] = entity.createdBy
            it[createdAt] = Instant.ofEpochMilli(entity.createdAt)
            it[lastModified] = Instant.ofEpochMilli(entity.lastModified)
            it[isActive] = entity.isActive
            it[tags] = json.encodeToString(entity.tags)
        }
        entity
    }
    
    // Additional method implementations...
    
    private fun ResultRow.toAnalyticsQuery(): AnalyticsQuery {
        return AnalyticsQuery(
            id = this[AnalyticsQueries.id],
            name = this[AnalyticsQueries.name],
            description = this[AnalyticsQueries.description],
            queryText = this[AnalyticsQueries.queryText],
            queryType = QueryType.valueOf(this[AnalyticsQueries.queryType]),
            parameters = json.decodeFromString(this[AnalyticsQueries.parameters]),
            createdBy = this[AnalyticsQueries.createdBy],
            createdAt = this[AnalyticsQueries.createdAt].toEpochMilli(),
            lastModified = this[AnalyticsQueries.lastModified].toEpochMilli(),
            isActive = this[AnalyticsQueries.isActive],
            tags = json.decodeFromString(this[AnalyticsQueries.tags])
        )
    }
}
```

### Database Configuration

The database configuration handles connection setup and management:

- Connection pool configuration
- Database migration setup
- Dialect-specific settings
- Performance tuning parameters
- Environment-specific configuration

Example:

```kotlin
class InsightDatabaseConfig(
    private val config: Config
) {
    val database: Database by lazy {
        val dbConfig = config.getConfig("insight.database")
        
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = dbConfig.getString("url")
            driverClassName = dbConfig.getString("driver")
            username = dbConfig.getString("user")
            password = dbConfig.getString("password")
            maximumPoolSize = dbConfig.getInt("maxPoolSize")
            minimumIdle = dbConfig.getInt("minIdle")
            idleTimeout = dbConfig.getLong("idleTimeout")
            connectionTimeout = dbConfig.getLong("connectionTimeout")
            maxLifetime = dbConfig.getLong("maxLifetime")
            leakDetectionThreshold = dbConfig.getLong("leakDetectionThreshold")
            
            // Additional connection pool properties
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        }
        
        Database.connect(HikariDataSource(hikariConfig))
    }
    
    fun runMigrations() {
        val dbConfig = config.getConfig("insight.database")
        
        Flyway.configure()
            .dataSource(
                dbConfig.getString("url"),
                dbConfig.getString("user"),
                dbConfig.getString("password")
            )
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
    }
}
```

## Production-Ready Features

### Transaction Management

The repositories implement comprehensive transaction management:

- **ACID Compliance**: All operations maintain ACID properties
- **Transaction Isolation**: Proper isolation levels for concurrent access
- **Nested Transactions**: Support for nested transactions with proper rollback
- **Transaction Propagation**: Configurable transaction propagation behavior
- **Deadlock Detection**: Automatic deadlock detection and retry

Example:

```kotlin
override suspend fun updateWithRelatedEntities(entity: Dashboard): Boolean = transaction(database) {
    try {
        // Update the dashboard
        val updatedRows = Dashboards.update({ Dashboards.id eq entity.id }) {
            // Update dashboard fields
        }
        
        // Update related widgets in the same transaction
        entity.widgets.forEach { widget ->
            if (widget.id.isNotEmpty()) {
                DashboardWidgets.update({ DashboardWidgets.id eq widget.id }) {
                    // Update widget fields
                }
            } else {
                DashboardWidgets.insert {
                    // Insert new widget
                }
            }
        }
        
        // Delete removed widgets
        val widgetIds = entity.widgets.map { it.id }.filter { it.isNotEmpty() }
        DashboardWidgets.deleteWhere { 
            (DashboardWidgets.dashboardId eq entity.id) and (DashboardWidgets.id notInList widgetIds) 
        }
        
        updatedRows > 0
    } catch (e: Exception) {
        logger.error("Failed to update dashboard with related entities", e)
        rollback()
        throw e
    }
}
```

### Error Handling

The repositories implement comprehensive error handling:

- **Exception Translation**: Database-specific exceptions are translated to domain exceptions
- **Detailed Logging**: Errors are logged with context information
- **Graceful Degradation**: Fallback mechanisms for non-critical failures
- **Retry Logic**: Automatic retry for transient errors
- **Validation**: Input validation before database operations

Example:

```kotlin
override suspend fun save(entity: Report): Report = try {
    transaction(database) {
        // Validation
        if (entity.name.isBlank()) {
            throw ValidationException("Report name cannot be empty")
        }
        
        // Check for duplicates
        val existing = Reports.select { Reports.name eq entity.name }
            .singleOrNull()
        
        if (existing != null && existing[Reports.id] != entity.id) {
            throw DuplicateEntityException("Report with name ${entity.name} already exists")
        }
        
        // Insert operation
        Reports.insert {
            // Insert fields
        }
        
        entity
    }
} catch (e: SQLException) {
    logger.error("Database error while saving report: ${e.message}", e)
    when {
        e.message?.contains("unique constraint") == true -> 
            throw DuplicateEntityException("Report with the same name already exists", e)
        e.message?.contains("foreign key constraint") == true ->
            throw ReferenceConstraintException("Referenced entity does not exist", e)
        else -> throw RepositoryException("Failed to save report", e)
    }
} catch (e: Exception) {
    logger.error("Error while saving report: ${e.message}", e)
    throw RepositoryException("Failed to save report", e)
}
```

### Connection Pooling

The repositories use HikariCP for efficient connection pooling:

- **Pool Sizing**: Optimal pool size based on workload
- **Connection Lifecycle**: Proper connection lifecycle management
- **Leak Detection**: Detection and handling of connection leaks
- **Health Checking**: Regular validation of connections
- **Metrics**: Comprehensive metrics for pool usage

### Query Optimization

The repositories implement various query optimization techniques:

- **Indexing**: Strategic indexes for common query patterns
- **Query Planning**: Optimized query execution plans
- **Pagination**: Efficient pagination for large result sets
- **Batch Operations**: Batched inserts and updates
- **Lazy Loading**: Deferred loading of related entities

Example:

```kotlin
override suspend fun findAllPaginated(
    page: Int,
    pageSize: Int,
    sortField: String,
    sortDirection: SortDirection
): Page<Dashboard> = transaction(database) {
    // Count total records
    val totalCount = Dashboards.selectAll().count()
    
    // Determine sort column and direction
    val sortColumn = when (sortField) {
        "name" -> Dashboards.name
        "createdAt" -> Dashboards.createdAt
        "lastModified" -> Dashboards.lastModified
        else -> Dashboards.lastModified
    }
    
    val order = when (sortDirection) {
        SortDirection.ASC -> SortOrder.ASC
        SortDirection.DESC -> SortOrder.DESC
    }
    
    // Execute paginated query
    val items = Dashboards
        .selectAll()
        .orderBy(sortColumn, order)
        .limit(pageSize, offset = (page - 1) * pageSize)
        .map { it.toDashboard() }
    
    Page(
        items = items,
        page = page,
        pageSize = pageSize,
        totalCount = totalCount,
        totalPages = (totalCount + pageSize - 1) / pageSize
    )
}
```

### Caching

The repositories implement caching strategies for improved performance:

- **Query Cache**: Caching of frequently executed queries
- **Result Cache**: Caching of query results
- **Cache Invalidation**: Proper invalidation on data changes
- **TTL Management**: Time-to-live settings for cached data
- **Cache Statistics**: Metrics for cache hit/miss rates

Example:

```kotlin
override suspend fun findById(id: String): KPI? {
    // Check cache first
    val cachedKpi = kpiCache.get(id)
    if (cachedKpi != null) {
        return cachedKpi
    }
    
    // If not in cache, query database
    return transaction(database) {
        KPIs.select { KPIs.id eq id }
            .singleOrNull()
            ?.toKPI()
            ?.also { kpi ->
                // Store in cache
                kpiCache.put(id, kpi)
            }
    }
}

override suspend fun update(entity: KPI): Boolean {
    return transaction(database) {
        val updatedRows = KPIs.update({ KPIs.id eq entity.id }) {
            // Update fields
        }
        
        if (updatedRows > 0) {
            // Invalidate cache on successful update
            kpiCache.invalidate(entity.id)
            true
        } else {
            false
        }
    }
}
```

## Repository Implementations

The Insight Service includes the following production-ready repository implementations:

### AnalyticsQueryRepository

Manages analytics query definitions and execution history:

- **Query Management**: CRUD operations for analytics queries
- **Query Execution**: Tracking of query executions and results
- **Parameter Management**: Handling of query parameters
- **Query Types**: Support for different query types (SQL, NoSQL, etc.)
- **Tagging**: Categorization of queries with tags

### DashboardRepository

Handles dashboard definitions and components:

- **Dashboard Management**: CRUD operations for dashboards
- **Widget Management**: Handling of dashboard widgets
- **Layout Management**: Dashboard layout and configuration
- **Sharing**: Dashboard sharing and permissions
- **Versioning**: Dashboard version history

### KPIRepository

Manages key performance indicators and their values:

- **KPI Definition**: CRUD operations for KPI definitions
- **Value Tracking**: Historical KPI values and trends
- **Thresholds**: KPI thresholds and alerts
- **Aggregation**: Aggregation of KPI values over time
- **Targets**: KPI targets and progress tracking

### MetricRepository

Stores and retrieves system and business metrics:

- **Metric Collection**: Storage of collected metrics
- **Time Series**: Time-series data for metrics
- **Aggregation**: Aggregation functions for metrics
- **Tagging**: Metric categorization with tags
- **Retention**: Configurable retention policies

### ReportRepository

Manages report definitions, templates, and execution history:

- **Report Management**: CRUD operations for report definitions
- **Template Management**: Report templates and parameters
- **Scheduling**: Report scheduling and delivery
- **Execution History**: Tracking of report executions
- **Output Management**: Storage of report outputs

## Database Schema

The Insight Service uses a well-designed database schema:

### Core Tables

- **analytics_queries**: Stores analytics query definitions
- **query_executions**: Tracks query execution history
- **report_templates**: Stores report templates
- **reports**: Stores report definitions
- **report_executions**: Tracks report generation history
- **dashboards**: Stores dashboard definitions
- **dashboard_widgets**: Stores dashboard widgets
- **metrics**: Stores metric definitions
- **metric_values**: Stores collected metric values
- **kpis**: Stores KPI definitions and current values

### Schema Features

- **Proper Indexing**: Indexes on frequently queried columns
- **Foreign Keys**: Referential integrity constraints
- **Check Constraints**: Data validation constraints
- **Default Values**: Sensible defaults for columns
- **Timestamps**: Creation and modification timestamps

## Usage Examples

### Working with Analytics Queries

```kotlin
// Create a new analytics query
val query = AnalyticsQuery(
    id = UUID.randomUUID().toString(),
    name = "Monthly Active Users",
    description = "Tracks monthly active users across the platform",
    queryText = """
        SELECT DATE_TRUNC('month', activity_date) as month,
               COUNT(DISTINCT user_id) as active_users
        FROM user_activities
        WHERE activity_date >= :startDate AND activity_date <= :endDate
        GROUP BY DATE_TRUNC('month', activity_date)
        ORDER BY month
    """,
    queryType = QueryType.SQL,
    parameters = mapOf(
        "startDate" to "date",
        "endDate" to "date"
    ),
    createdBy = "admin",
    createdAt = System.currentTimeMillis(),
    lastModified = System.currentTimeMillis(),
    isActive = true,
    tags = listOf("users", "activity", "monthly")
)

// Save the query
val savedQuery = analyticsQueryRepository.save(query)

// Find queries by tag
val userQueries = analyticsQueryRepository.findByTags(listOf("users"))

// Update a query
val updatedQuery = savedQuery.copy(
    description = "Updated description",
    lastModified = System.currentTimeMillis()
)
analyticsQueryRepository.update(updatedQuery)

// Deactivate a query
analyticsQueryRepository.updateStatus(savedQuery.id, false)
```

### Working with Dashboards

```kotlin
// Create a dashboard with widgets
val dashboard = Dashboard(
    id = UUID.randomUUID().toString(),
    name = "Operations Overview",
    description = "Key operational metrics and KPIs",
    layout = "grid",
    widgets = listOf(
        DashboardWidget(
            id = UUID.randomUUID().toString(),
            dashboardId = "",  // Will be set during save
            title = "Active Users",
            type = WidgetType.CHART,
            chartType = "line",
            dataSource = "monthly-active-users",
            position = mapOf("x" to 0, "y" to 0, "w" to 6, "h" to 4),
            configuration = mapOf(
                "xAxis" to "month",
                "yAxis" to "active_users",
                "showLegend" to true
            )
        ),
        DashboardWidget(
            id = UUID.randomUUID().toString(),
            dashboardId = "",  // Will be set during save
            title = "System Health",
            type = WidgetType.STATUS,
            dataSource = "system-health",
            position = mapOf("x" to 6, "y" to 0, "w" to 6, "h" to 4),
            configuration = mapOf(
                "showLabels" to true,
                "refreshInterval" to 60
            )
        )
    ),
    createdBy = "admin",
    createdAt = System.currentTimeMillis(),
    lastModified = System.currentTimeMillis(),
    isPublic = true,
    tags = listOf("operations", "overview")
)

// Save the dashboard with widgets
val savedDashboard = dashboardRepository.saveWithWidgets(dashboard)

// Find dashboards by creator
val adminDashboards = dashboardRepository.findByCreatedBy("admin")

// Find public dashboards
val publicDashboards = dashboardRepository.findByIsPublic(true)
```

## Best Practices

The repository implementations follow these best practices:

- **Clean Architecture**: Clear separation of concerns
- **Domain-Driven Design**: Focus on domain concepts
- **Immutable Models**: Immutable domain models for thread safety
- **Consistent Naming**: Consistent naming conventions
- **Comprehensive Testing**: Thorough unit and integration testing
- **Documentation**: Clear documentation of repository behavior
- **Performance Awareness**: Attention to performance implications
- **Security Considerations**: Proper handling of sensitive data

## Conclusion

The Database Repositories system in the Eden Insight Service provides a comprehensive, production-ready solution for data access and persistence. It replaces all previously mocked implementations with fully functional, enterprise-grade repositories that ensure data integrity, performance, and reliability.