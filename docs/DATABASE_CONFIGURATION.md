# Database Configuration Guide

This document provides information about the database configuration system implemented across Eden services.

## Overview

The Eden platform now uses a standardized approach for database configuration across all services:

1. **Production-ready encryption** using BouncyCastle for the Hub service
2. **Real database health checks** across all services
3. **Configuration loading from files** for database connections
4. **Key management system** in the Hub service

## Database Configuration

### Configuration Files

Each service now loads its database configuration from a properties file. The default location is `application.properties` in the service's resources directory. You can specify a different location using the `EDEN_CONFIG_PATH` environment variable.

The configuration file supports environment-specific settings:

```properties
# Development environment
database.dev.url=jdbc:postgresql://localhost:5432/eden_dev
database.dev.username=eden_user
database.dev.password=eden_password
database.dev.driver-class-name=org.postgresql.Driver
database.dev.max-pool-size=10
# ... other properties

# Test environment
database.test.url=jdbc:postgresql://localhost:5432/eden_test
# ... other properties

# Production environment
database.prod.url=jdbc:postgresql://db:5432/eden_prod
# ... other properties
```

The active environment is determined by the `EDEN_ENVIRONMENT` environment variable, which defaults to `dev` if not specified.

### Configuration Properties

The following properties are supported:

| Property | Description | Default |
|----------|-------------|---------|
| url | JDBC URL for the database | - |
| username | Database username | - |
| password | Database password | - |
| driver-class-name | JDBC driver class name | org.postgresql.Driver |
| max-pool-size | Maximum number of connections in the pool | 10 |
| min-idle | Minimum number of idle connections | 5 |
| idle-timeout | Maximum time (ms) a connection can be idle | 600000 |
| connection-timeout | Maximum time (ms) to wait for a connection | 30000 |
| validation-timeout | Maximum time (ms) to validate a connection | 5000 |
| max-lifetime | Maximum lifetime (ms) of a connection | 1800000 |
| auto-commit | Whether to enable auto-commit | false |
| schema | Database schema | public |

Additional properties can be specified using the `properties` prefix:

```properties
database.dev.properties.socketTimeout=30
database.dev.properties.tcpKeepAlive=true
database.dev.properties.ssl=false
```

### Environment Variables

Database configuration can also be provided through environment variables:

```
DATABASE_DEV_URL=jdbc:postgresql://localhost:5432/eden_dev
DATABASE_DEV_USERNAME=eden_user
DATABASE_DEV_PASSWORD=eden_password
DATABASE_DEV_DRIVER_CLASS_NAME=org.postgresql.Driver
DATABASE_DEV_MAX_POOL_SIZE=10
```

Environment variables take precedence over configuration files.

## Database Health Checks

All services now implement real database health checks that provide the following information:

- Connection availability
- Query response time
- Active connections
- Pool utilization statistics
- Migration status

Health checks can be accessed through each service's `/health` endpoint.

Example response:

```json
{
  "status": "success",
  "data": {
    "status": "healthy",
    "timestamp": "2025-06-06T16:30:00Z",
    "uptime": 3600000,
    "service": "hub",
    "database": {
      "connected": true,
      "responseTime": 50,
      "activeConnections": 2
    },
    "encryption": "BouncyCastle AES-GCM"
  }
}
```

## Hub Service Encryption

The Hub service now uses BouncyCastle for encryption, replacing the previous mock implementation. This provides:

- AES-GCM encryption with 256-bit keys
- Proper authentication tags for integrity verification
- Secure random IV generation
- Comprehensive error handling

## Key Management System

The Hub service now includes a key management system that provides:

- Secure key generation and storage
- Key rotation
- Key versioning
- Access control
- Audit logging

### Using the Key Management System

The Key Management System is automatically initialized when the Hub service starts. It can be accessed through the `HubService` class:

```kotlin
// Create a new key
val keyResult = keyManagementSystem.createKey("my-key", userId)

// Use a key for encryption
val encryptionResult = keyManagementSystem.encryptWithManagedKey(data, "my-key", userId)

// Decrypt data
val decryptionResult = keyManagementSystem.decryptWithManagedKey(
    data, "my-key", version, nonce, authTag, userId
)

// Rotate a key
val rotatedKeyResult = keyManagementSystem.rotateKey("my-key", userId)
```

## Implementation Details

### Database Service

The database service implementation has been updated to use HikariCP for connection pooling and provides real-time health monitoring.

Key features:
- Connection pooling with HikariCP
- Real-time health monitoring
- Connection validation
- Performance metrics
- Error handling and recovery

### Configuration Loading

The `DatabaseConfigLoader` class provides functionality to load database configuration from various sources:
- Configuration files (properties)
- Environment variables
- Default values

It supports multiple environments (dev, test, prod) and can be extended to support custom configuration sources.

## Migration Guide

To migrate existing services to use the new database configuration system:

1. Create an `application.properties` file in the service's resources directory
2. Update the service's `Application.kt` file to use `DatabaseConfigLoader` and `PostgreSQLDatabaseServiceImpl`
3. Update health check endpoints to use real database health information

Example:

```kotlin
private fun createDatabaseService(): EdenDatabaseService {
    val environment = System.getenv("EDEN_ENVIRONMENT") ?: "dev"
    val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.properties"
    
    val config = DatabaseConfigLoader().loadFromFile(configPath, environment)
    
    return PostgreSQLDatabaseServiceImpl(config)
}
```

## Best Practices

1. **Use environment-specific configurations**: Define separate configurations for development, testing, and production environments.
2. **Don't hardcode sensitive information**: Use environment variables for passwords and other sensitive information.
3. **Monitor database health**: Regularly check the health status of your database connections.
4. **Rotate encryption keys**: Periodically rotate encryption keys to enhance security.
5. **Configure connection pools appropriately**: Set appropriate values for max-pool-size, min-idle, and other connection pool parameters based on your application's needs.