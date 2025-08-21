# Vertex DevOps Suite - Database Schema Implementation

This directory contains the complete database schema implementation for the Vertex DevOps Suite, designed to support the core business logic for Phase 1b development.

## Overview

The database schema implements a comprehensive data model supporting:

- **User Management**: Authentication, sessions, and user profiles
- **Secrets Management**: Encrypted storage with audit trails (Vertex Vault)
- **Workflow Management**: YAML-based workflow definitions and executions (Vertex Flow)
- **Task Management**: Scheduled and on-demand task execution (Vertex Task)
- **System Monitoring**: Events, audit logs, and system health tracking
- **Multi-tenancy**: Organization-based resource isolation (future-ready)

## 🗄️ Schema Architecture

### Core Tables

#### Users & Authentication
```sql
users                 -- User accounts and profiles
user_sessions         -- Active user sessions and tokens
organizations         -- Multi-tenant organization support
```

#### Secrets Management (Vertex Vault)
```sql
secrets               -- Encrypted secrets with versioning
secret_access_logs    -- Detailed access audit trail
```

#### Workflow Management (Vertex Flow)
```sql
workflows             -- YAML workflow definitions
workflow_executions   -- Workflow execution instances
workflow_steps        -- Individual step execution tracking
```

#### Task Management (Vertex Task)
```sql
tasks                 -- Task definitions with scheduling
task_executions       -- Task execution queue and history
```

#### System Monitoring
```sql
system_events         -- Application events and monitoring
audit_logs            -- Comprehensive audit trail
```

## 📁 File Structure

```
infrastructure/database/
├── README.md                           # This documentation
├── init/
│   ├── 01-init-database.sql          # Legacy initialization (deprecated)
│   ├── V2__core_business_schema.sql   # Core business schema (Phase 1b)
│   └── V3__sample_data.sql           # Development sample data
└── migrations/                        # Future migration files
```

## Migration Files

### V2__core_business_schema.sql
**Purpose**: Implements the complete Phase 1b database schema
**Features**:
- All core business tables with proper relationships
- Performance-optimized indexes
- Full-text search capabilities
- JSONB columns for flexible data storage
- Comprehensive foreign key constraints
- Automatic timestamp triggers

### V3__sample_data.sql
**Purpose**: Provides realistic sample data for development and testing
**Includes**:
- Test users with different roles and verification states
- Sample secrets for various use cases
- Complex workflow definitions with multi-step processes
- Scheduled and on-demand tasks
- System events and audit trail examples

## Database Configuration

### Environment Variables
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/vertex_dev
DATABASE_USERNAME=vertex
DATABASE_PASSWORD=dev_password
DATABASE_MAX_POOL_SIZE=10
DATABASE_MIN_IDLE=2
```

### Connection Pool Settings
- **Maximum Pool Size**: 10 connections
- **Minimum Idle**: 2 connections
- **Connection Timeout**: 30 seconds
- **Idle Timeout**: 10 minutes
- **Max Lifetime**: 30 minutes

## Schema Details

### Users Table
```sql
users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    is_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
)
```

### Secrets Table
```sql
secrets (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    encrypted_value TEXT NOT NULL,
    encryption_key_id VARCHAR(255) NOT NULL,
    secret_type VARCHAR(100) DEFAULT 'generic',
    description TEXT,
    user_id UUID REFERENCES users(id),
    organization_id UUID REFERENCES organizations(id),
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(name, user_id, version)
)
```

### Workflows Table
```sql
workflows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    definition JSONB NOT NULL,  -- YAML converted to JSON
    user_id UUID REFERENCES users(id),
    status VARCHAR(50) DEFAULT 'active',
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(name, user_id)
)
```

### Tasks Table
```sql
tasks (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    task_type VARCHAR(100) NOT NULL,
    configuration JSONB NOT NULL,
    schedule_cron VARCHAR(255),  -- Cron expression
    user_id UUID REFERENCES users(id),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
)
```

## 🔍 Indexes and Performance

### Critical Indexes
- **Users**: `idx_users_email`, `idx_users_active`
- **Secrets**: `idx_secrets_user_id`, `idx_secrets_name`, `idx_secrets_active`
- **Workflows**: `idx_workflows_user_id`, `idx_workflows_status`
- **Tasks**: `idx_tasks_user_id`, `idx_tasks_type`, `idx_tasks_active`
- **Executions**: `idx_workflow_executions_status`, `idx_task_executions_status`
- **Audit**: `idx_audit_logs_timestamp`, `idx_system_events_created_at`

### Full-Text Search
```sql
-- Search indexes for better UX
idx_secrets_search     -- Search secrets by name and description
idx_workflows_search   -- Search workflows by name and description
idx_tasks_search       -- Search tasks by name and description
```

## Testing Support

### Test Data Builders
Located in `shared/testing/src/commonMain/kotlin/com/ataiva/vertex/testing/builders/`:
- `SecretTestDataBuilder.kt` - Creates test secrets and access logs
- `WorkflowTestDataBuilder.kt` - Creates test workflows and executions
- `TaskTestDataBuilder.kt` - Creates test tasks and executions

### Test Fixtures
Located in `shared/testing/src/commonMain/kotlin/com/ataiva/vertex/testing/fixtures/`:
- `DatabaseTestFixtures.kt` - Comprehensive test data sets
- `TestFixtures.kt` - Extended with new schema entities

### Integration Tests
Located in `integration-tests/src/test/kotlin/com/ataiva/vertex/integration/database/`:
- `NewSchemaIntegrationTest.kt` - Comprehensive schema validation tests

## Development Workflow

### 1. Database Setup
```bash
# Start PostgreSQL (via Docker Compose)
docker-compose up -d postgres

# Run migrations
./gradlew flywayMigrate

# Validate schema
./scripts/validate-database-schema.sh
```

### 2. Running Tests
```bash
# Run database integration tests
./gradlew :integration-tests:test --tests "*NewSchemaIntegrationTest*"

# Run all integration tests
./gradlew :integration-tests:test
```

### 3. Schema Validation
```bash
# Validate complete schema implementation
./scripts/validate-database-schema.sh

# Check migration status
./gradlew flywayInfo
```

## Migration Strategy

### Development
1. **V2__core_business_schema.sql** - Core schema implementation
2. **V3__sample_data.sql** - Development sample data
3. Future migrations will be numbered sequentially (V4, V5, etc.)

### Production
- Migrations are applied automatically via Flyway
- All migrations are transactional and reversible
- Schema changes are backward compatible where possible
- Critical data migrations include rollback procedures

## Security Considerations

### Data Protection
- All sensitive data is encrypted at rest
- Secrets use application-level encryption with key rotation
- Audit logs capture all data access and modifications
- User sessions have configurable expiration

### Access Control
- Row-level security ready for multi-tenant deployment
- Foreign key constraints prevent orphaned records
- Soft deletes preserve audit trail integrity
- IP address and user agent tracking for security monitoring

## 🚦 Validation and Health Checks

### Schema Validation Script
The `validate-database-schema.sh` script performs comprehensive checks:
- Table existence and structure
- Index presence and performance
- Foreign key constraint validation
- Data type verification (JSONB, UUID, etc.)
- Basic CRUD operation testing
- Sample data verification

### Health Monitoring
```sql
-- Check database health
SELECT 
    schemaname,
    tablename,
    n_tup_ins as inserts,
    n_tup_upd as updates,
    n_tup_del as deletes
FROM pg_stat_user_tables 
WHERE schemaname = 'public'
ORDER BY n_tup_ins DESC;

-- Monitor index usage
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes 
WHERE schemaname = 'public'
ORDER BY idx_tup_read DESC;
```

## Future Enhancements

### Phase 2 Additions
- Advanced analytics tables for metrics and reporting
- Multi-cloud resource tracking tables
- Enhanced monitoring and alerting schema
- Time-series data optimization with TimescaleDB

### Phase 3 Additions
- AI/ML model metadata and training data tables
- Advanced workflow orchestration with dependencies
- Real-time collaboration and notification schema
- Advanced security and compliance tracking

## References

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Flyway Migration Guide](https://flywaydb.org/documentation/)
- [Vertex Architecture Implementation Plan](../../docs/architecture/implementation-plan.md)
- [Database Integration Tests](../../integration-tests/src/test/kotlin/com/ataiva/vertex/integration/database/)

---

**Status**: **COMPLETE** - Ready for Phase 1b business logic implementation

**Last Updated**: December 2024  
**Schema Version**: V3 (Core Business Schema + Sample Data)