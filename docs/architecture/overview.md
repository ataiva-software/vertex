# Architecture Overview

Eden DevOps Suite is built using a microservices architecture with shared libraries, designed to scale from simple Docker Compose deployments to enterprise Kubernetes clusters.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Frontend Layer                             │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Eden Web UI       │    │        Eden CLI                 │ │
│  │ (Kotlin/JS+Compose) │    │    (Kotlin Native)              │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     API Gateway Layer                          │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Authentication • Rate Limiting • Load Balancing • Routing │ │
│  └─────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Service Layer                               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  Vault  │ │  Flow   │ │  Task   │ │ Monitor │ │  Sync   │   │
│  │ Service │ │ Service │ │ Service │ │ Service │ │ Service │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────┐ ┌─────────┐                                       │
│  │ Insight │ │   Hub   │                                       │
│  │ Service │ │ Service │                                       │
│  └─────────┘ └─────────┘                                       │
├─────────────────────────────────────────────────────────────────┤
│                   Shared Infrastructure                        │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Shared Core       │    │    Message Bus & Events        │ │
│  │     Library         │    │   (Redis Streams/NATS)         │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     Data Layer                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │         PostgreSQL + Extensions + Redis Cache              │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Key Architectural Decisions

### 1. Microservices with Shared Kernel
- **Independent Services**: Each Eden component runs as a separate service
- **Shared Libraries**: Common functionality in shared Kotlin Multiplatform modules
- **Loose Coupling**: Services communicate via well-defined APIs and events
- **Technology Consistency**: All services use Kotlin and Ktor framework

### 2. PostgreSQL-Centric Data Strategy
- **Single Database**: All services share a PostgreSQL database with schema separation
- **Extensions**: TimescaleDB for time-series data, PostGIS for geospatial features
- **ACID Compliance**: Strong consistency for critical operations
- **Redis Cache**: High-performance caching and session storage

### 3. Event-Driven Communication
- **Asynchronous Events**: Services communicate via domain events
- **Redis Streams**: Reliable event streaming with persistence
- **Event Sourcing**: Critical operations recorded as immutable events
- **CQRS Pattern**: Separate read/write models for complex operations

### 4. Hybrid Encryption Approach
- **Zero-Knowledge Secrets**: Client-side encryption for sensitive data
- **Standard Encryption**: TLS and database encryption for other data
- **Progressive Security**: Start simple, enhance security over time
- **Key Management**: Secure key derivation and rotation

## Technology Stack

### Backend Services
- **Language**: Kotlin JVM
- **Framework**: Ktor (async, lightweight)
- **Database**: PostgreSQL 15+ with extensions
- **Cache/Events**: Redis 7+
- **Build**: Gradle with Kotlin DSL

### Frontend Applications
- **Web UI**: Kotlin/JS with Compose for Web
- **CLI**: Kotlin Native (cross-platform)
- **Mobile**: Kotlin Multiplatform Mobile (future)

### Infrastructure
- **Containerization**: Docker and Docker Compose
- **Orchestration**: Kubernetes (production)
- **Monitoring**: Prometheus + Grafana
- **Logging**: Structured JSON logging

## Component Architecture

### API Gateway
```
┌─────────────────────────────────────────┐
│              API Gateway                │
├─────────────────────────────────────────┤
│  Authentication Middleware              │
│  ├─ JWT Token Validation               │
│  ├─ User Session Management            │
│  └─ Role-Based Access Control          │
├─────────────────────────────────────────┤
│  Traffic Management                     │
│  ├─ Rate Limiting                      │
│  ├─ Load Balancing                     │
│  └─ Circuit Breaker                    │
├─────────────────────────────────────────┤
│  Request Routing                        │
│  ├─ Service Discovery                  │
│  ├─ Path-Based Routing                 │
│  └─ Health Check Aggregation           │
└─────────────────────────────────────────┘
```

### Service Structure (Example: Eden Vault)
```
┌─────────────────────────────────────────┐
│            Eden Vault Service           │
├─────────────────────────────────────────┤
│  API Layer                              │
│  ├─ REST Endpoints                     │
│  ├─ GraphQL Schema (future)            │
│  └─ WebSocket Events                   │
├─────────────────────────────────────────┤
│  Business Logic                         │
│  ├─ Secret Management                  │
│  ├─ Access Control                     │
│  └─ Audit Logging                      │
├─────────────────────────────────────────┤
│  Data Access                            │
│  ├─ Repository Pattern                 │
│  ├─ Database Queries                   │
│  └─ Cache Management                   │
├─────────────────────────────────────────┤
│  Shared Dependencies                    │
│  ├─ Core Models                        │
│  ├─ Authentication                     │
│  ├─ Cryptography                       │
│  └─ Events                             │
└─────────────────────────────────────────┘
```

## Data Architecture

### Database Schema Organization
```
PostgreSQL Database: eden_dev
├─ Schema: core
│  ├─ users
│  ├─ organizations  
│  ├─ permissions
│  └─ audit_logs
├─ Schema: vault
│  ├─ secrets
│  ├─ secret_versions
│  └─ access_logs
├─ Schema: flow
│  ├─ workflows
│  ├─ workflow_runs
│  └─ workflow_steps
├─ Schema: task
│  ├─ jobs
│  ├─ job_runs
│  └─ job_logs
└─ Schema: monitor
   ├─ checks
   ├─ check_results
   └─ incidents
```

### Event Streaming Architecture
```
Redis Streams
├─ Stream: eden.auth.events
│  ├─ user.created
│  ├─ user.login
│  └─ permission.changed
├─ Stream: eden.vault.events
│  ├─ secret.created
│  ├─ secret.accessed
│  └─ secret.deleted
├─ Stream: eden.flow.events
│  ├─ workflow.started
│  ├─ workflow.completed
│  └─ workflow.failed
└─ Stream: eden.system.events
   ├─ service.started
   ├─ service.health
   └─ service.error
```

## Security Architecture

### Zero-Knowledge Encryption Flow
```
Client Device                 Eden Platform              Database
┌─────────────┐              ┌─────────────┐            ┌─────────────┐
│             │              │             │            │             │
│ 1. Password │─────────────▶│             │            │             │
│ 2. Derive   │              │             │            │             │
│    Key      │              │             │            │             │
│ 3. Encrypt  │              │             │            │             │
│    Secret   │              │             │            │             │
│             │              │             │            │             │
│ 4. Send     │─────────────▶│ 5. Store    │───────────▶│ 6. Encrypted│
│    Encrypted│              │    Encrypted│            │    Data Only│
│    Data     │              │    Data     │            │             │
│             │              │             │            │             │
│ 8. Decrypt  │◀─────────────│ 7. Retrieve │◀───────────│             │
│    Secret   │              │    Encrypted│            │             │
│             │              │    Data     │            │             │
└─────────────┘              └─────────────┘            └─────────────┘
```

### Authentication & Authorization
- **JWT Tokens**: Stateless authentication with refresh tokens
- **Role-Based Access Control**: Granular permissions system
- **Multi-Factor Authentication**: TOTP and WebAuthn support
- **API Keys**: Service-to-service authentication
- **Audit Logging**: Complete audit trail for compliance

## Deployment Architecture

### Development (Docker Compose)
```
┌─────────────────────────────────────────┐
│           Docker Compose                │
├─────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │   API   │ │  Vault  │ │  Flow   │   │
│  │ Gateway │ │ Service │ │ Service │   │
│  └─────────┘ └─────────┘ └─────────┘   │
├─────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐               │
│  │ PostgreSQL │ │  Redis  │               │
│  │ Database│ │  Cache  │               │
│  └─────────┘ └─────────┘               │
└─────────────────────────────────────────┘
```

### Production (Kubernetes)
```
┌─────────────────────────────────────────┐
│            Kubernetes Cluster           │
├─────────────────────────────────────────┤
│  Ingress Controller                     │
│  ├─ TLS Termination                    │
│  ├─ Load Balancing                     │
│  └─ Path Routing                       │
├─────────────────────────────────────────┤
│  Application Pods                       │
│  ├─ API Gateway (3 replicas)          │
│  ├─ Vault Service (2 replicas)        │
│  ├─ Flow Service (2 replicas)         │
│  └─ Other Services...                  │
├─────────────────────────────────────────┤
│  Data Layer                             │
│  ├─ PostgreSQL (HA Cluster)           │
│  ├─ Redis (Sentinel Setup)            │
│  └─ Persistent Volumes                 │
├─────────────────────────────────────────┤
│  Monitoring & Logging                   │
│  ├─ Prometheus                         │
│  ├─ Grafana                            │
│  └─ Fluentd                            │
└─────────────────────────────────────────┘
```

## Scalability Considerations

### Horizontal Scaling
- **Stateless Services**: All services designed for horizontal scaling
- **Database Sharding**: Future support for database partitioning
- **Caching Strategy**: Multi-level caching with Redis and CDN
- **Load Balancing**: Intelligent routing based on service health

### Performance Optimization
- **Connection Pooling**: Efficient database connection management
- **Async Processing**: Non-blocking I/O throughout the stack
- **Event Streaming**: Asynchronous inter-service communication
- **Resource Management**: Kubernetes resource limits and requests

## Integration Patterns

### API Design
- **RESTful APIs**: Standard HTTP methods and status codes
- **GraphQL**: Future support for flexible data querying
- **WebSocket**: Real-time updates and notifications
- **OpenAPI**: Comprehensive API documentation

### External Integrations
- **Webhooks**: Outbound event notifications
- **OAuth 2.0**: Third-party authentication
- **SAML/LDAP**: Enterprise identity providers
- **Cloud APIs**: AWS, GCP, Azure integrations

---

This architecture provides a solid foundation for the Eden DevOps Suite while maintaining flexibility for future enhancements and scaling requirements.