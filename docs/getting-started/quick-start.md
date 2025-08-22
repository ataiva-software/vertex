# Quick Start Guide

Get Vertex up and running in 5 minutes with this step-by-step guide.

## Prerequisites

- **Docker** and **Docker Compose** for dependencies
- **Internet connection** for downloading Vertex

## Step 1: Install Vertex

### Automatic Installation (Recommended)

```bash
curl -fsSL https://raw.githubusercontent.com/ataiva-software/vertex/main/install.sh | bash
```

### Manual Installation

**macOS (Apple Silicon)**
```bash
curl -L -o vertex https://github.com/ataiva-software/vertex/releases/latest/download/vertex-darwin-arm64
chmod +x vertex
sudo mv vertex /usr/local/bin/
```

**Linux (x64)**
```bash
curl -L -o vertex https://github.com/ataiva-software/vertex/releases/latest/download/vertex-linux-amd64
chmod +x vertex
sudo mv vertex /usr/local/bin/
```

### Verify Installation

```bash
vertex --version
```

## Step 2: Start Dependencies

Vertex requires PostgreSQL and Redis. Start them with Docker:

```bash
# PostgreSQL
docker run -d --name postgres \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_USER=vertex \
  -e POSTGRES_DB=vertex \
  -p 5432:5432 postgres:15

# Redis
docker run -d --name redis \
  -p 6379:6379 redis:7
```

## Step 3: Set Master Password

```bash
export VERTEX_MASTER_PASSWORD="your-secure-password"
```

## Step 4: Start All Services

```bash
vertex server
```

You should see all services starting:
```
🚀 Starting Vertex DevOps Suite - All Services
✅ API Gateway service started on port 8000
✅ Vault service started on port 8080
✅ Flow service started on port 8081
✅ Task service started on port 8082
✅ Monitor service started on port 8083
✅ Sync service started on port 8084
✅ Insight service started on port 8085
✅ Hub service started on port 8086
```

## Step 5: Access the Web Portal

Open your browser to: **http://localhost:8000**

The web portal provides a complete interface for all services:

- 🔐 **Vault**: Manage secrets securely
- 🔄 **Flow**: Create and manage workflows  
- ⚡ **Task**: Orchestrate tasks
- 📊 **Monitor**: System health and metrics
- 🔄 **Sync**: Multi-cloud synchronization
- 📈 **Insight**: Analytics and reports
- 🔗 **Hub**: Service integrations

## Step 6: Try the CLI

```bash
# Check system status
vertex status

# Store a secret
vertex vault store my-secret "hello world"

# List secrets (JSON format)
vertex vault list --format json

# List secrets (YAML format)
vertex vault list --format yaml

# Get a secret
vertex vault get my-secret
```

## What's Next?

### Explore the Web Portal

1. **Store Your First Secret**: Use the Vault tab to securely store API keys
2. **Create a Workflow**: Use the Flow tab to automate tasks
3. **Monitor Services**: Check the Monitor tab for system health
4. **View Analytics**: Use the Insight tab for system metrics

### Learn More

- [Web Portal Guide](../user-guide/web-portal.md) - Complete web interface documentation
- [CLI Reference](../user-guide/cli-reference.md) - All CLI commands and options
- [Service Guides](../services/) - Individual service documentation
- [Deployment Guide](../deployment/) - Production deployment options

## Troubleshooting

### Services Won't Start

```bash
# Check if dependencies are running
docker ps | grep -E "(postgres|redis)"

# Check if ports are available
lsof -i :8000-8086

# Restart dependencies if needed
docker restart postgres redis
```

### Web Portal Not Loading

```bash
# Test API Gateway health
curl http://localhost:8000/health

# Check if master password is set
echo $VERTEX_MASTER_PASSWORD

# Restart server
vertex server
```

### CLI Commands Fail

```bash
# Check service status
vertex status

# Test individual service
curl http://localhost:8080/health
```

## Alternative Setup Methods

### Docker Compose

```bash
# Download docker-compose.yml
curl -L -o docker-compose.yml https://raw.githubusercontent.com/ataiva-software/vertex/main/docker-compose.yml

# Start everything
docker-compose up -d

# Access web portal
open http://localhost:8000
```

### Individual Services

```bash
# Run only specific services
vertex service vault --port 8080
vertex service flow --port 8081

# Use CLI with specific services
vertex vault list
vertex flow list
```

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
