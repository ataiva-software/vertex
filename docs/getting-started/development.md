# Development Guide

This guide helps you set up a complete development environment for contributing to Eden DevOps Suite.

## Prerequisites

### Required Software
- **Java 17 or higher** - For running Kotlin/JVM services
- **Docker Desktop** - For containerized development environment
- **Git** - For version control
- **IDE** - IntelliJ IDEA (recommended) or VS Code with Kotlin plugin

### Recommended Tools
- **Postman/Insomnia** - For API testing
- **pgAdmin** - For database management
- **Redis CLI** - For cache inspection

## Development Environment Setup

### 1. Clone and Initialize

```bash
# Clone the repository
git clone https://github.com/ataivadev/eden.git
cd eden

# Make scripts executable
chmod +x gradlew
chmod +x scripts/setup-dev.sh

# Run development setup script
./scripts/setup-dev.sh
```

### 2. Start Infrastructure Services

```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Verify services are running
docker-compose ps
```

### 3. Build the Project

```bash
# Build all modules
./gradlew build

# Build specific modules
./gradlew :shared:core:build
./gradlew :services:api-gateway:build
./gradlew :clients:cli:build
```

### 4. Initialize Database

```bash
# Run database initialization
docker-compose exec postgres psql -U eden -d eden_dev -f /docker-entrypoint-initdb.d/01-init-database.sql

# Verify database setup
docker-compose exec postgres psql -U eden -d eden_dev -c "\dt"
```

## Development Workflow

### Project Structure

```
eden/
├── shared/                   # Shared Kotlin Multiplatform libraries
│   ├── core/                 # Core models and utilities
│   ├── auth/                 # Authentication framework
│   ├── crypto/               # Cryptography utilities
│   ├── database/             # Database abstraction
│   ├── events/               # Event system
│   └── config/               # Configuration management
├── services/                 # Microservices (Kotlin/JVM)
│   ├── api-gateway/          # API Gateway service
│   ├── vault/                # Vault service (planned)
│   ├── flow/                 # Flow service (planned)
│   └── ...                   # Other services
├── clients/                  # Client applications
│   ├── web/                  # Web UI (Kotlin/JS)
│   ├── cli/                  # CLI (Kotlin Native)
│   └── mobile/               # Mobile app (future)
├── infrastructure/           # Infrastructure as code
│   ├── docker/               # Docker configurations
│   ├── kubernetes/           # Kubernetes manifests
│   └── database/             # Database scripts
└── docs/                     # Documentation
```

### Working with Shared Libraries

Shared libraries use Kotlin Multiplatform and are consumed by services and clients:

```bash
# Build shared libraries
./gradlew :shared:build

# Test shared libraries
./gradlew :shared:test

# Publish to local repository
./gradlew :shared:publishToMavenLocal
```

### Working with Services

Services are Kotlin/JVM applications using Ktor:

```bash
# Run API Gateway locally
./gradlew :services:api-gateway:run

# Run with specific profile
./gradlew :services:api-gateway:run --args="--config=dev"

# Build Docker image
./gradlew :services:api-gateway:buildImage
```

### Working with CLI

The CLI is built with Kotlin Native for cross-platform support:

```bash
# Build for your platform
./gradlew :clients:cli:build

# Build for Linux
./gradlew :clients:cli:linkReleaseExecutableLinuxX64

# Build for macOS
./gradlew :clients:cli:linkReleaseExecutableMacosX64

# Build for Windows
./gradlew :clients:cli:linkReleaseExecutableMingwX64

# Run the CLI
./clients/cli/build/bin/linuxX64/releaseExecutable/eden --help
```

### Working with Web UI

The Web UI uses Kotlin/JS with Compose for Web:

```bash
# Build web UI
./gradlew :clients:web:build

# Run development server
./gradlew :clients:web:jsBrowserDevelopmentRun

# Build for production
./gradlew :clients:web:jsBrowserProductionWebpack
```

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :shared:core:test
./gradlew :services:api-gateway:test

# Run integration tests
./gradlew integrationTest

# Run tests with coverage
./gradlew test jacocoTestReport
```

### Test Structure

```
src/
├── main/kotlin/              # Production code
└── test/kotlin/              # Test code
    ├── unit/                 # Unit tests
    ├── integration/          # Integration tests
    └── fixtures/             # Test data and utilities
```

### Writing Tests

```kotlin
// Unit test example
class SecretServiceTest {
    @Test
    fun `should encrypt secret with zero-knowledge encryption`() {
        // Arrange
        val service = SecretService()
        val plaintext = "my-secret"
        val password = "user-password"
        
        // Act
        val result = service.encryptSecret(plaintext, password)
        
        // Assert
        assertTrue(result.isSuccess)
        assertNotEquals(plaintext, result.encryptedData)
    }
}

// Integration test example
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiGatewayIntegrationTest {
    private lateinit var testApplication: TestApplication
    
    @BeforeAll
    fun setup() {
        testApplication = TestApplication {
            module()
        }
    }
    
    @Test
    fun `should authenticate user successfully`() = testApplication.test {
        // Test API endpoints
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("user@example.com", "password"))
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

## Database Development

### Database Access

```bash
# Connect to development database
docker-compose exec postgres psql -U eden -d eden_dev

# Connect to test database
docker-compose exec postgres psql -U eden -d eden_test

# Run SQL scripts
docker-compose exec postgres psql -U eden -d eden_dev -f /path/to/script.sql
```

### Schema Management

```bash
# Create new migration
./gradlew flywayInfo
./gradlew flywayMigrate

# Reset database (development only)
./gradlew flywayClean flywayMigrate
```

### Database Schema

Current schema organization:
```sql
-- Core schema
CREATE SCHEMA IF NOT EXISTS core;
-- Tables: users, organizations, permissions, audit_logs

-- Service-specific schemas
CREATE SCHEMA IF NOT EXISTS vault;
-- Tables: secrets, secret_versions, access_logs

CREATE SCHEMA IF NOT EXISTS flow;
-- Tables: workflows, workflow_runs, workflow_steps
```

## Debugging

### IntelliJ IDEA Setup

1. **Import Project**: Open the root `build.gradle.kts` file
2. **Configure JDK**: Set Project SDK to Java 17+
3. **Enable Kotlin**: Ensure Kotlin plugin is installed and enabled
4. **Configure Run Configurations**: 
   - API Gateway: Main class `com.ataiva.eden.gateway.ApplicationKt`
   - CLI: Native binary execution

### Debugging Services

```bash
# Run with debug port
./gradlew :services:api-gateway:run --debug-jvm

# Or set environment variable
export JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
./gradlew :services:api-gateway:run
```

### Logging

Services use structured JSON logging:

```kotlin
// In service code
private val logger = LoggerFactory.getLogger(this::class.java)

logger.info("Processing request", 
    StructuredArguments.kv("userId", userId),
    StructuredArguments.kv("action", "secret.create")
)
```

View logs:
```bash
# Service logs
docker-compose logs api-gateway

# Database logs
docker-compose logs postgres

# All logs
docker-compose logs -f
```

## Code Quality

### Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful names for variables and functions
- Add KDoc comments for public APIs
- Keep functions small and focused

### Pre-commit Checks

```bash
# Format code
./gradlew ktlintFormat

# Check code style
./gradlew ktlintCheck

# Run static analysis
./gradlew detekt

# Run all quality checks
./gradlew check
```

### Git Hooks

Set up pre-commit hooks:
```bash
# Install pre-commit hooks
cp scripts/pre-commit .git/hooks/
chmod +x .git/hooks/pre-commit
```

## Troubleshooting

### Common Issues

**Build Failures**
```bash
# Clean and rebuild
./gradlew clean build

# Check Java version
java -version

# Update Gradle wrapper
./gradlew wrapper --gradle-version=8.5
```

**Database Connection Issues**
```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Reset database
docker-compose down postgres
docker-compose up -d postgres
```

**CLI Build Issues**
```bash
# Check Kotlin Native setup
./gradlew :clients:cli:tasks

# Clean native build
./gradlew :clients:cli:clean
```

**Port Conflicts**
```bash
# Check port usage
netstat -tulpn | grep :8080

# Change ports in docker-compose.yml if needed
```

### Getting Help

- **Documentation**: Check the [docs/](.) directory
- **Issues**: Search [GitHub Issues](https://github.com/your-org/eden/issues)
- **Discussions**: Join [GitHub Discussions](https://github.com/your-org/eden/discussions)
- **Code Review**: Ask for help in pull requests

## Next Steps

1. **Pick a Component**: Choose from [Project Status](../development/project-status.md)
2. **Read Architecture**: Review [Architecture Overview](../architecture/overview.md)
3. **Start Contributing**: Follow [Contributing Guide](../../CONTRIBUTING.md)
4. **Join Community**: Participate in discussions and code reviews

---

Happy coding! 🚀