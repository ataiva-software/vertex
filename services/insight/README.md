# Insight Service

The Insight Service is a comprehensive analytics and reporting service that provides data insights, dashboards, and metrics for the Eden platform. This implementation uses production-ready PostgreSQL database repositories with full transaction support, connection pooling, and proper error handling.

## Features

- Analytics queries management
- Report generation and scheduling
- Interactive dashboards
- Metrics collection and analysis
- KPI tracking and visualization
- Anomaly detection and trend analysis

## Architecture

The Insight Service follows a clean architecture pattern with the following components:

- **Models**: Data classes representing domain entities
- **Repositories**: Database access layer using the Repository pattern
- **Services**: Business logic layer
- **API**: HTTP endpoints for client interaction
- **Configuration**: Application and database configuration

## Database Implementation

The service uses PostgreSQL as the database backend with the following production-ready components:

- **Exposed ORM**: For type-safe SQL operations with full transaction support
- **HikariCP**: For connection pooling with optimal performance configuration
- **Flyway**: For database migrations with versioning and rollback support
- **Repository Pattern**: For data access abstraction with comprehensive error handling

### Database Tables

- `analytics_queries`: Stores analytics query definitions
- `query_executions`: Tracks query execution history
- `report_templates`: Stores report templates
- `reports`: Stores report definitions
- `report_executions`: Tracks report generation history
- `dashboards`: Stores dashboard definitions
- `metrics`: Stores metric definitions
- `metric_values`: Stores collected metric values
- `kpis`: Stores KPI definitions and current values

## Getting Started

### Prerequisites

- JDK 11 or higher
- PostgreSQL 12 or higher
- Gradle 7.0 or higher

### Configuration

The application is configured using the `application.conf` file located in `src/main/resources`. Key configuration sections include:

- **Database**: Connection settings, pool configuration, and migration options
- **Insight**: Service-specific settings like report output path, cache settings, and metrics collection

### Running the Application

1. Ensure PostgreSQL is running and accessible
2. Update the database connection settings in `application.conf` if needed
3. Build the application:
   ```
   ./gradlew build
   ```
4. Run the application:
   ```
   ./gradlew run
   ```

The service will start on port 8080 by default.

### API Endpoints

The service exposes the following API endpoints:

- **Queries**: `/api/v1/queries`
- **Reports**: `/api/v1/reports`
- **Dashboards**: `/api/v1/dashboards`
- **Metrics**: `/api/v1/metrics`
- **KPIs**: `/api/v1/kpis`
- **Analytics**: `/api/v1/analytics`

## Development

### Project Structure

```
services/insight/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/ataiva/eden/insight/
│   │   │       ├── config/
│   │   │       │   └── InsightDatabaseConfig.kt
│   │   │       ├── engine/
│   │   │       │   └── AnalyticsEngine.kt
│   │   │       ├── model/
│   │   │       │   └── [Domain models]
│   │   │       ├── repository/
│   │   │       │   ├── InsightTables.kt
│   │   │       │   ├── InsightRepositories.kt
│   │   │       │   └── impl/
│   │   │       │       └── [Repository implementations]
│   │   │       ├── service/
│   │   │       │   ├── InsightService.kt
│   │   │       │   └── InsightConfiguration.kt
│   │   │       └── InsightApplication.kt
│   │   └── resources/
│   │       ├── application.conf
│   │       └── db/migration/
│   │           └── V1__create_insight_tables.sql
│   └── test/
│       └── kotlin/
│           └── com/ataiva/eden/insight/
│               ├── repository/
│               │   └── impl/
│               │       └── [Repository tests]
│               └── service/
│                   └── [Service tests]
└── build.gradle.kts
```

### Adding New Features

1. Define the domain model in the `model` package
2. Create a table definition in `InsightTables.kt`
3. Define a repository interface in `InsightRepositories.kt`
4. Implement the repository in the `repository/impl` package
5. Add the necessary methods to the `InsightService` class
6. Add API endpoints in the `InsightApplication` class

### Running Tests

```
./gradlew test
```

## Background Tasks

The service runs the following background tasks:

- **Scheduled Report Generation**: Periodically checks for and generates scheduled reports
- **Metrics Collection**: Collects system metrics and updates KPIs at regular intervals

## Transactions and Error Handling

The service uses Exposed's transaction management to ensure data consistency. Error handling is implemented at multiple levels:

- Repository level: Database-specific errors are caught and translated
- Service level: Business logic errors are handled and appropriate responses are returned
- Application level: Global error handling for HTTP requests

## Production-Ready Features

### Database Repositories

The Insight Service implements fully production-ready database repositories:

- **Complete CRUD Operations**: All repositories implement create, read, update, and delete operations
- **Transaction Management**: All database operations are wrapped in transactions for data consistency
- **Error Handling**: Comprehensive error handling with proper exception translation
- **Connection Pooling**: Optimized connection pool settings for high throughput
- **Query Optimization**: Efficient SQL queries with proper indexing
- **JSON Serialization**: Type-safe JSON serialization for complex data structures
- **Pagination Support**: Efficient data retrieval with pagination for large datasets
- **Audit Trails**: Automatic tracking of creation and modification timestamps

### Repository Implementations

- **AnalyticsQueryRepository**: Manages analytics query definitions and execution history
- **DashboardRepository**: Handles dashboard definitions and components
- **KPIRepository**: Manages key performance indicators and their values
- **MetricRepository**: Stores and retrieves system and business metrics
- **ReportRepository**: Manages report definitions, templates, and execution history

## Future Improvements

- Add caching for frequently accessed data
- Implement more sophisticated analytics algorithms
- Add support for real-time data processing
- Enhance the dashboard visualization capabilities
- Implement user-specific data access controls