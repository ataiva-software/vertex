# Development Guide

This guide helps you set up a development environment for Vertex and understand the codebase structure.

## Prerequisites

### Required Software
- **Go 1.21 or higher** - Primary development language
- **Docker** - For running dependencies (PostgreSQL, Redis)
- **Docker Compose** - For orchestrating development services
- **Git** - Version control
- **Make** - Build automation

### Recommended Tools
- **VS Code** - With Go extension for development
- **PostgreSQL Client** - For database management
- **Redis CLI** - For cache and queue inspection

## Quick Setup

### 1. Clone Repository

```bash
git clone https://github.com/ataiva-software/vertex.git
cd vertex
```

### 2. Start Dependencies

```bash
# Start PostgreSQL and Redis
docker-compose up -d
```

### 3. Install Go Dependencies

```bash
# Download and install dependencies
go mod tidy
```

### 4. Build Vertex

```bash
# Build single binary
make build
```

### 5. Run Vertex

```bash
# Start all services
./bin/vertex server

# Or run specific service
./bin/vertex service vault --port 8080
```

## Project Structure

```
vertex/
├── cmd/
│   └── vertex/                 # Main application entry point
├── internal/                 # Internal service implementations
│   ├── api-gateway/          # API Gateway service
│   ├── vault/                # Vault service
│   ├── flow/                 # Flow service
│   ├── task/                 # Task service
│   ├── monitor/              # Monitor service
│   ├── sync/                 # Sync service
│   ├── insight/              # Insight service
│   └── hub/                  # Hub service
├── pkg/                      # Shared packages
│   ├── core/                 # Core utilities
│   ├── crypto/               # Cryptographic operations
│   ├── auth/                 # Authentication
│   ├── database/             # Database abstractions
│   ├── events/               # Event system
│   ├── config/               # Configuration
│   └── monitoring/           # Monitoring utilities
├── docs/                     # Documentation
├── web/                      # Web dashboard (future)
└── bin/                      # Built binaries
```

## Development Workflow

### 1. Service Development

Each service follows a consistent structure:

```
internal/service-name/
├── main.go                   # Service entry point
├── handler.go                # HTTP handlers
├── service.go                # Business logic
├── models.go                 # Data models
├── repository.go             # Data access
└── service_test.go           # Tests
```

### 2. Shared Package Development

Shared packages provide common functionality:

```
pkg/package-name/
├── package.go                # Main package file
├── types.go                  # Type definitions
├── utils.go                  # Utility functions
└── package_test.go           # Tests
```

### 3. Testing

Run tests for specific packages:

```bash
# Test specific service
go test ./internal/vault/...

# Test shared package
go test ./pkg/crypto/...

# Test everything
go test ./...

# Test with coverage
go test -cover ./...
```

### 4. Building

Build the single binary:

```bash
# Build for current platform
make build

# Build for specific platform
GOOS=linux GOARCH=amd64 go build -o bin/vertex-linux ./cmd/vertex/

# Build Docker image
make docker-build
```

## Configuration

### Environment Variables

```bash
# Database configuration
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=vertex
export DB_USER=vertex
export DB_PASSWORD=secret

# Redis configuration
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Service configuration
export BASE_PORT=8000
export LOG_LEVEL=info
```

### Configuration Files

Create `config/local.yaml` for local development:

```yaml
database:
  host: localhost
  port: 5432
  name: vertex
  user: vertex
  password: secret

redis:
  host: localhost
  port: 6379

services:
  base_port: 8000
  log_level: debug
```

## IDE Setup

### VS Code Configuration

Install recommended extensions:
- Go (by Google)
- Docker
- YAML
- GitLens

Create `.vscode/settings.json`:

```json
{
  "go.useLanguageServer": true,
  "go.formatTool": "goimports",
  "go.lintTool": "golangci-lint",
  "go.testFlags": ["-v"],
  "go.coverOnSave": true
}
```

### Debugging

Create `.vscode/launch.json` for debugging:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "Launch Vertex Server",
      "type": "go",
      "request": "launch",
      "mode": "auto",
      "program": "${workspaceFolder}/cmd/vertex",
      "args": ["server"],
      "env": {
        "DB_HOST": "localhost",
        "DB_PASSWORD": "secret"
      }
    }
  ]
}
```

## Development Commands

### Common Make Targets

```bash
# Build binary
make build

# Run tests
make test

# Run tests with coverage
make test-coverage

# Start development environment
make dev

# Clean build artifacts
make clean

# Build Docker image
make docker-build

# Run linting
make lint
```

### Go Commands

```bash
# Run specific service
go run cmd/vertex/main.go service vault

# Test with verbose output
go test -v ./internal/vault/...

# Generate test coverage report
go test -coverprofile=coverage.out ./...
go tool cover -html=coverage.out

# Format code
go fmt ./...

# Check for issues
go vet ./...
```

## Database Development

### Migrations

Database migrations are handled automatically on startup. To create new migrations:

```bash
# Create migration file
touch pkg/database/migrations/001_create_users.sql
```

### Database Access

Use the database package for consistent access:

```go
import "github.com/ataiva-software/vertex/pkg/database"

// Get database connection
db, err := database.Connect(config.Database)
if err != nil {
    log.Fatal(err)
}

// Use GORM for queries
var users []User
db.Find(&users)
```

## Testing Guidelines

### Test Structure

Follow Go testing conventions:

```go
func TestServiceFunction(t *testing.T) {
    // Arrange
    service := NewService()
    
    // Act
    result, err := service.DoSomething()
    
    // Assert
    assert.NoError(t, err)
    assert.Equal(t, expected, result)
}
```

### Integration Tests

Use build tags for integration tests:

```go
//go:build integration

func TestDatabaseIntegration(t *testing.T) {
    // Integration test code
}
```

Run integration tests:

```bash
go test -tags=integration ./...
```

## Code Style

### Go Conventions
- Follow standard Go formatting (use `gofmt`)
- Use meaningful variable and function names
- Write comprehensive comments for public APIs
- Handle errors explicitly
- Use interfaces for testability

### Project Conventions
- Services should be stateless where possible
- Use dependency injection for testability
- Follow the repository pattern for data access
- Use events for inter-service communication
- Implement comprehensive error handling

## Debugging

### Local Debugging

```bash
# Run with debug logging
LOG_LEVEL=debug ./bin/vertex server

# Run specific service with debugging
./bin/vertex service vault --port 8080 --debug
```

### Docker Debugging

```bash
# View service logs
docker-compose logs -f vertex

# Access container shell
docker-compose exec vertex sh
```

## Contributing

### Before Submitting

1. **Run tests**: Ensure all tests pass
2. **Check formatting**: Run `go fmt ./...`
3. **Lint code**: Run `golangci-lint run`
4. **Update documentation**: Keep docs current
5. **Test locally**: Verify changes work locally

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

For more details, see [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Getting Help

- **Documentation**: Check the docs/ directory
- **Issues**: Report bugs on GitHub
- **Discussions**: Join community discussions
- **Support**: Contact support@ataiva.com
