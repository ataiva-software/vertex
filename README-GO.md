# Eden DevOps Suite - Go Implementation

This is a complete rewrite of the Eden DevOps Suite from Kotlin to Go, following Test-Driven Development (TDD) principles.

## Architecture

The Go implementation maintains the same microservices architecture as the original Kotlin version:

### Services
- **api-gateway** - API Gateway with authentication and routing
- **vault** - Zero-knowledge secrets management
- **flow** - Workflow automation engine
- **task** - Distributed task orchestration
- **monitor** - Real-time monitoring and alerting
- **sync** - Multi-cloud data synchronization
- **insight** - Analytics and reporting engine
- **hub** - Service discovery and integration hub

### Shared Packages
- **pkg/core** - Core utilities and types
- **pkg/crypto** - Cryptographic operations
- **pkg/auth** - Authentication and authorization
- **pkg/database** - Database abstractions
- **pkg/events** - Event system
- **pkg/config** - Configuration management
- **pkg/testing** - Testing utilities
- **pkg/ai** - AI/ML capabilities
- **pkg/cloud** - Multi-cloud abstractions
- **pkg/analytics** - Analytics engine
- **pkg/monitoring** - Monitoring utilities
- **pkg/deployment** - Deployment automation

### Clients
- **cmd/cli** - Command-line interface
- **web** - Web dashboard (React/TypeScript)

## Technology Stack

- **Go 1.21+** - Primary language
- **Gin** - HTTP web framework
- **GORM** - ORM for database operations
- **Redis** - Caching and message broker
- **PostgreSQL** - Primary database
- **Cobra** - CLI framework
- **Testify** - Testing framework
- **Docker** - Containerization
- **Kubernetes** - Orchestration

## Development Approach

This rewrite follows strict Test-Driven Development (TDD):

1. **Red** - Write failing tests first
2. **Green** - Write minimal code to pass tests
3. **Refactor** - Improve code while keeping tests green

## Getting Started

```bash
# Initialize Go modules
go mod tidy

# Run tests
go test ./...

# Build all services
make build

# Start development environment
docker-compose up -d

# Run a specific service
go run cmd/vault/main.go
```

## Project Status

🚧 **In Development** - Currently rewriting from Kotlin to Go

### Completed
- [ ] Project structure
- [ ] Core packages
- [ ] Database layer
- [ ] Authentication system
- [ ] Vault service
- [ ] API Gateway
- [ ] CLI framework

### In Progress
- [x] Project initialization
- [ ] Core package implementation
- [ ] Database abstractions
- [ ] Crypto package
- [ ] Auth package

### Planned
- [ ] All microservices
- [ ] Web dashboard
- [ ] Kubernetes deployment
- [ ] CI/CD pipeline
- [ ] Documentation

## Testing

All code is developed using TDD with comprehensive test coverage:

```bash
# Run all tests
go test ./...

# Run tests with coverage
go test -cover ./...

# Run integration tests
go test -tags=integration ./...

# Run benchmarks
go test -bench=. ./...
```

## Contributing

1. Follow TDD principles - tests first!
2. Maintain high test coverage (>90%)
3. Use Go best practices and idioms
4. Document all public APIs
5. Follow the existing project structure
