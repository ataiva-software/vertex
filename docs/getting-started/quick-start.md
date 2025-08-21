# Quick Start Guide

Get Vertex up and running in 5 minutes with this step-by-step guide.

## Prerequisites

- **Go 1.21+** installed on your system
- **Docker** and **Docker Compose** for dependencies
- **Git** for cloning the repository

## Step 1: Clone Repository

```bash
git clone https://github.com/ataiva-software/vertex.git
cd vertex
```

## Step 2: Start Dependencies

Vertex requires PostgreSQL and Redis. Start them with Docker Compose:

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port 5432
- Redis on port 6379

## Step 3: Build Vertex

Build the single binary containing all services:

```bash
make build
```

This creates `./bin/vertex` - a 19MB binary with all services.

## Step 4: Start Vertex

Start all services with one command:

```bash
./bin/vertex server
```

Vertex will start 8 services on ports 8000-8086:
- API Gateway: http://localhost:8000
- Vault: http://localhost:8080
- Flow: http://localhost:8081
- Task: http://localhost:8082
- Monitor: http://localhost:8083
- Sync: http://localhost:8084
- Insight: http://localhost:8085
- Hub: http://localhost:8086

## Step 5: Verify Installation

Check that all services are running:

```bash
./bin/vertex status
```

You should see all services reporting as healthy.

## Basic Usage

### Secrets Management

Store and retrieve secrets:

```bash
# Store a secret
./bin/vertex vault store my-secret "hello world"

# Retrieve a secret
./bin/vertex vault get my-secret

# List all secrets
./bin/vertex vault list
```

### Task Management

Create and run tasks:

```bash
# Create a task
./bin/vertex task create "backup-database" \
  --command "pg_dump mydb > backup.sql" \
  --schedule "0 2 * * *"

# List tasks
./bin/vertex task list

# Run a task immediately
./bin/vertex task run backup-database
```

### Workflow Automation

Create simple workflows:

```bash
# Create a workflow
./bin/vertex flow create deploy-app \
  --steps "build,test,deploy" \
  --trigger webhook

# List workflows
./bin/vertex flow list

# Execute a workflow
./bin/vertex flow run deploy-app
```

### Monitoring

Check system health and metrics:

```bash
# View system status
./bin/vertex monitor status

# Get metrics
./bin/vertex monitor metrics

# View recent alerts
./bin/vertex monitor alerts
```

## Configuration

### Environment Variables

Configure Vertex with environment variables:

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

# Service ports (optional)
export BASE_PORT=8000
```

### Configuration File

Create `config/vertex.yaml` for persistent configuration:

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
  log_level: info

security:
  jwt_secret: your-secret-key
  token_expiry: 24h
```

## Service Modes

### All Services (Default)

Run all services in one process:

```bash
./bin/vertex server
```

### Individual Services

Run services separately for development:

```bash
# Run only the vault service
./bin/vertex service vault --port 8080

# Run only the task service
./bin/vertex service task --port 8082
```

### CLI Only

Use Vertex as a CLI tool without running services:

```bash
# Direct CLI operations (requires services running elsewhere)
./bin/vertex vault store key value
./bin/vertex task create job-name
```

## Docker Deployment

### Quick Docker Run

```bash
# Build Docker image
make docker-build

# Run with Docker Compose
docker-compose -f docker-compose-single.yml up -d
```

### Manual Docker Run

```bash
docker run -d \
  --name vertex \
  -p 8000-8086:8000-8086 \
  -e DB_HOST=postgres \
  -e DB_PASSWORD=secret \
  --link postgres:postgres \
  --link redis:redis \
  vertex:latest server
```

## Health Checks

### Service Health

Check individual service health:

```bash
# Check all services
curl http://localhost:8000/health

# Check specific service
curl http://localhost:8080/health  # Vault
curl http://localhost:8081/health  # Flow
curl http://localhost:8082/health  # Task
```

### Database Health

Verify database connectivity:

```bash
# Check database connection
./bin/vertex status --database

# Run database migrations
./bin/vertex migrate
```

## Troubleshooting

### Common Issues

**Services won't start**
```bash
# Check if ports are available
netstat -tulpn | grep :8000

# Check logs
./bin/vertex server --log-level debug
```

**Database connection failed**
```bash
# Verify PostgreSQL is running
docker-compose ps

# Check database crvertextials
./bin/vertex status --database
```

**Redis connection failed**
```bash
# Verify Redis is running
docker-compose ps

# Test Redis connection
redis-cli ping
```

### Getting Help

- **Logs**: Check service logs for detailed error messages
- **Status**: Use `./bin/vertex status` for system overview
- **Documentation**: See `docs/` for detailed guides
- **Issues**: Report problems on GitHub

## Next Steps

### Learn More
- [Development Guide](development.md) - Set up development environment
- [Configuration Guide](../user-guide/configuration.md) - Advanced configuration
- [CLI Reference](../user-guide/cli-reference.md) - Complete command reference
- [Architecture Overview](../architecture/overview.md) - System architecture

### Advanced Usage
- [Multi-Cloud Setup](../user-guide/multi-cloud-orchestration.md) - Cloud integration
- [Security Guide](../security/security-guide.md) - Security best practices
- [Monitoring Guide](../monitoring/monitoring.md) - Advanced monitoring

### Contributing
- [Contributing Guide](../../CONTRIBUTING.md) - How to contribute
- [Development Roadmap](../development/roadmap.md) - Future plans

You're now ready to use Vertex! Start with the basic commands above and explore the documentation for advanced features.
