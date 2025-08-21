# Eden Architecture Overview

Eden is built as a single-binary application with a microservices architecture, providing a unified DevOps platform through integrated services.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Eden Binary (19MB)                          │
├─────────────────────────────────────────────────────────────────┤
│ API Gateway │ Vault │ Flow │ Task │ Monitor │ Sync │ Insight │ Hub │
│   Port 8000 │ 8080  │ 8081 │ 8082 │  8083   │ 8084 │  8085   │8086 │
├─────────────────────────────────────────────────────────────────┤
│                    Shared Infrastructure                         │
│  Database Pool • Event Bus • Crypto • Config • Logging          │
├─────────────────────────────────────────────────────────────────┤
│                       Data Layer                                 │
│              PostgreSQL + Redis + File System                   │
└─────────────────────────────────────────────────────────────────┘
```

## Core Services

### API Gateway (Port 8000)
- **Purpose**: Central entry point for all API requests
- **Features**: Authentication, routing, rate limiting, request/response transformation
- **Technology**: Go with Gin framework

### Vault Service (Port 8080)
- **Purpose**: Zero-knowledge secrets management
- **Features**: AES-256-GCM encryption, secure key storage, audit logging
- **Technology**: Go with cryptographic libraries

### Flow Service (Port 8081)
- **Purpose**: Workflow automation and orchestration
- **Features**: Visual workflow designer, event-driven execution, conditional logic
- **Technology**: Go with event-driven architecture

### Task Service (Port 8082)
- **Purpose**: Distributed task processing and job scheduling
- **Features**: Redis-based queuing, parallel execution, retry mechanisms
- **Technology**: Go with Redis integration

### Monitor Service (Port 8083)
- **Purpose**: Real-time monitoring and alerting
- **Features**: Metrics collection, health checks, AI-powered anomaly detection
- **Technology**: Go with monitoring libraries

### Sync Service (Port 8084)
- **Purpose**: Multi-cloud data synchronization
- **Features**: Cross-cloud replication, conflict resolution, cost optimization
- **Technology**: Go with cloud provider SDKs

### Insight Service (Port 8085)
- **Purpose**: Analytics and business intelligence
- **Features**: Data analysis, predictive modeling, reporting dashboards
- **Technology**: Go with analytics engines

### Hub Service (Port 8086)
- **Purpose**: Service discovery and integration management
- **Features**: Service registry, configuration management, health monitoring
- **Technology**: Go with service discovery patterns

## Shared Infrastructure

### Database Layer
- **Primary Database**: PostgreSQL for persistent data
- **Cache Layer**: Redis for session management and queuing
- **Connection Pooling**: Optimized database connections
- **Migrations**: Automated schema management

### Event System
- **Message Broker**: Redis pub/sub for inter-service communication
- **Event Sourcing**: Audit trail and state reconstruction
- **Async Processing**: Non-blocking event handling

### Security
- **Authentication**: JWT tokens with configurable expiration
- **Authorization**: Role-based access control (RBAC)
- **Encryption**: AES-256-GCM for data at rest and in transit
- **Audit Logging**: Comprehensive security event tracking

### Configuration
- **Environment Variables**: Runtime configuration
- **Configuration Files**: YAML-based service configuration
- **Secret Management**: Secure configuration storage
- **Hot Reload**: Dynamic configuration updates

## Technology Stack

### Core Technologies
- **Language**: Go 1.21+
- **Web Framework**: Gin for HTTP services
- **Database**: PostgreSQL with GORM ORM
- **Cache**: Redis for caching and messaging
- **CLI Framework**: Cobra for command-line interface

### Development Tools
- **Build System**: Go modules and Makefiles
- **Testing**: Go testing framework with Testify
- **Containerization**: Docker with multi-stage builds
- **Orchestration**: Kubernetes with Helm charts

### Deployment
- **Single Binary**: All services compiled into one executable
- **Container Support**: Docker images with health checks
- **Cloud Native**: Kubernetes-ready with service discovery
- **CI/CD**: GitHub Actions for automated testing and deployment

## Design Principles

### Single Binary Architecture
- **Simplicity**: One binary to deploy and manage
- **Efficiency**: Shared resources and optimized memory usage
- **Portability**: Runs anywhere Go is supported
- **Performance**: Minimal overhead and fast startup

### Microservices Benefits
- **Modularity**: Clear separation of concerns
- **Scalability**: Individual service scaling
- **Maintainability**: Independent service development
- **Resilience**: Fault isolation and recovery

### Security First
- **Zero Trust**: Verify all requests and communications
- **Encryption**: End-to-end data protection
- **Audit Trail**: Complete operation logging
- **Least Privilege**: Minimal required permissions

### Cloud Native
- **12-Factor App**: Following cloud-native principles
- **Stateless Services**: Horizontal scaling capability
- **Health Checks**: Kubernetes-compatible health endpoints
- **Observability**: Comprehensive metrics and logging

## Data Flow

### Request Processing
1. **API Gateway** receives and authenticates requests
2. **Service Routing** directs requests to appropriate services
3. **Business Logic** processes requests within services
4. **Data Access** interacts with PostgreSQL/Redis
5. **Response** returns processed data to client

### Event Processing
1. **Event Generation** services publish events to Redis
2. **Event Distribution** Redis pub/sub delivers to subscribers
3. **Event Processing** services handle events asynchronously
4. **State Updates** services update their state based on events

### Data Persistence
1. **Transactional Data** stored in PostgreSQL
2. **Cache Data** stored in Redis for performance
3. **File Storage** for binary data and logs
4. **Backup Strategy** automated backups and recovery

## Scalability Considerations

### Horizontal Scaling
- **Stateless Services**: Multiple instances can run concurrently
- **Load Balancing**: Distribute requests across instances
- **Database Scaling**: Read replicas and connection pooling
- **Cache Scaling**: Redis clustering for high availability

### Performance Optimization
- **Connection Pooling**: Efficient database connections
- **Caching Strategy**: Multi-level caching for performance
- **Async Processing**: Non-blocking operations where possible
- **Resource Management**: Efficient memory and CPU usage

### Monitoring and Observability
- **Health Checks**: Service health monitoring
- **Metrics Collection**: Performance and business metrics
- **Distributed Tracing**: Request flow across services
- **Log Aggregation**: Centralized logging and analysis

## Security Architecture

### Authentication and Authorization
- **JWT Tokens**: Stateless authentication
- **RBAC**: Role-based access control
- **API Keys**: Service-to-service authentication
- **Session Management**: Secure session handling

### Data Protection
- **Encryption at Rest**: Database and file encryption
- **Encryption in Transit**: TLS for all communications
- **Key Management**: Secure key storage and rotation
- **Data Masking**: Sensitive data protection

### Network Security
- **TLS Termination**: Secure communications
- **Rate Limiting**: DDoS protection
- **Input Validation**: Prevent injection attacks
- **CORS Configuration**: Cross-origin request security

This architecture provides a robust, scalable, and secure foundation for the Eden DevOps platform while maintaining simplicity through the single-binary deployment model.
