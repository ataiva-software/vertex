# Installation Guide

## Quick Install (Recommended)

### Automatic Installation Script

The fastest way to install Vertex is using our installation script:

```bash
curl -fsSL https://raw.githubusercontent.com/ataiva-software/vertex/main/install.sh | bash
```

This script will:
- ✅ Detect your operating system and architecture automatically
- ✅ Download the latest release binary
- ✅ Install to `/usr/local/bin/vertex`
- ✅ Verify the installation
- ✅ Show you next steps

## Manual Installation

### Download Latest Release

Choose your platform and download the appropriate binary:

**macOS (Apple Silicon)**
```bash
curl -L -o vertex https://github.com/ataiva-software/vertex/releases/latest/download/vertex-darwin-arm64
chmod +x vertex
sudo mv vertex /usr/local/bin/
```

**macOS (Intel)**
```bash
curl -L -o vertex https://github.com/ataiva-software/vertex/releases/latest/download/vertex-darwin-amd64
chmod +x vertex
sudo mv vertex /usr/local/bin/
```

**Linux (x64)**
```bash
curl -L -o vertex https://github.com/ataiva-software/vertex/releases/latest/download/vertex-linux-amd64
chmod +x vertex
sudo mv vertex /usr/local/bin/
```

**Linux (ARM64)**
```bash
curl -L -o vertex https://github.com/ataiva-software/vertex/releases/latest/download/vertex-linux-arm64
chmod +x vertex
sudo mv vertex /usr/local/bin/
```

**Windows**
1. Download `vertex-windows-amd64.exe` from the [releases page](https://github.com/ataiva-software/vertex/releases/latest)
2. Rename to `vertex.exe`
3. Add to your PATH

### Verify Installation

```bash
vertex --version
```

You should see output like:
```
Vertex DevOps Suite v1.0.0
```

## Prerequisites

Vertex requires these dependencies to run:

### Required Dependencies

**PostgreSQL Database**
```bash
# Using Docker (recommended)
docker run -d --name postgres \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_USER=vertex \
  -e POSTGRES_DB=vertex \
  -p 5432:5432 postgres:15

# Or install locally
# macOS: brew install postgresql
# Ubuntu: sudo apt install postgresql
# CentOS: sudo yum install postgresql-server
```

**Redis Cache**
```bash
# Using Docker (recommended)
docker run -d --name redis \
  -p 6379:6379 redis:7

# Or install locally
# macOS: brew install redis
# Ubuntu: sudo apt install redis-server
# CentOS: sudo yum install redis
```

### Environment Variables

**Master Password (Required)**
```bash
export VERTEX_MASTER_PASSWORD="your-secure-password"
```

**Database Configuration (Optional)**
```bash
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="vertex"
export DB_USER="vertex"
export DB_PASSWORD="secret"
export DB_SSL_MODE="disable"
```

## Quick Start

Once installed, start Vertex:

```bash
# 1. Set master password
export VERTEX_MASTER_PASSWORD="your-secure-password"

# 2. Start all services
vertex server
```

You should see:
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

## Access Vertex

### Web Portal
Open your browser to: **http://localhost:8000**

### CLI Commands
```bash
# Check system status
vertex status

# Store a secret
vertex vault store my-secret "hello world"

# List secrets
vertex vault list --format yaml

# Get help
vertex --help
```

## Docker Installation (Alternative)

If you prefer using Docker:

```bash
# Pull the image
docker pull vertex:latest

# Run with dependencies
docker-compose up -d
```

## Troubleshooting

### Common Issues

**"vertex: command not found"**
- Ensure `/usr/local/bin` is in your PATH
- Try running `./vertex` from the download directory

**"VERTEX_MASTER_PASSWORD environment variable is required"**
- Set the master password: `export VERTEX_MASTER_PASSWORD="your-password"`

**"Failed to connect to database"**
- Ensure PostgreSQL is running on port 5432
- Check database credentials and connection

**"Failed to connect to Redis"**
- Ensure Redis is running on port 6379
- Check Redis connection

### Getting Help

- **Documentation**: [Complete guides](https://github.com/ataiva-software/vertex/tree/main/docs)
- **Issues**: [GitHub Issues](https://github.com/ataiva-software/vertex/issues)
- **Support**: [support@ataiva.com](mailto:support@ataiva.com)

## Next Steps

- [Quick Start Guide](quick-start.md) - Get up and running in 5 minutes
- [Web Portal Guide](../user-guide/web-portal.md) - Complete web interface documentation
- [CLI Reference](../user-guide/cli-reference.md) - All CLI commands
- [Configuration Guide](configuration.md) - Advanced configuration options

```bash
# Build all components
./gradlew build

# Build specific components
./gradlew :services:api-gateway:build
./gradlew :clients:cli:build
```

### 4. Verify Installation

```bash
# Check API Gateway
curl http://localhost:8080/health

# Test CLI (after building)
./clients/cli/build/bin/linuxX64/releaseExecutable/vertex --help
```

## Service Endpoints

After successful installation, the following services will be available:

- **API Gateway**: http://localhost:8080
- **Web UI**: http://localhost:3000 *(planned)*
- **Database**: localhost:5432 (vertex/dev_password)
- **Redis**: localhost:6379

## Platform-Specific Instructions

### macOS

```bash
# Install Java 17 via Homebrew
brew install openjdk@17

# Install Docker Desktop
brew install --cask docker
```

### Linux (Ubuntu/Debian)

```bash
# Install Java 17
sudo apt update
sudo apt install openjdk-17-jdk

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
```

### Windows

1. Install Java 17 from [Oracle](https://www.oracle.com/java/technologies/downloads/)
2. Install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop)
3. Use Git Bash or WSL2 for command execution

## Troubleshooting

### Common Issues

**Port Conflicts**
```bash
# Check if ports are in use
netstat -tulpn | grep :8080
netstat -tulpn | grep :5432

# Stop conflicting services or modify docker-compose.yml
```

**Permission Issues (Linux/macOS)**
```bash
# Make gradlew executable
chmod +x gradlew

# Fix Docker permissions
sudo usermod -aG docker $USER
# Log out and back in
```

**Build Failures**
```bash
# Clean and rebuild
./gradlew clean build

# Check Java version
java -version
```

### Getting Help

- Check the [Development Guide](development.md) for detailed setup
- Review [Project Status](../development/project-status.md) for current limitations
- Open an issue on [GitHub](https://github.com/your-org/vertex/issues)