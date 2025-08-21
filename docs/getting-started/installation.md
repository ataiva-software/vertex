# Installation Guide

## Prerequisites

Before installing Vertex DevOps Suite, ensure you have the following:

- **Java 17 or higher** - Required for running services
- **Docker and Docker Compose** - For containerized deployment
- **Git** - For version control and repository management

## Quick Installation

### 1. Clone the Repository

```bash
git clone https://github.com/ataiva-software/vertex.git
cd vertex
```

### 2. Start Development Environment

```bash
# Start all services with Docker Compose
docker-compose up -d

# Verify services are running
docker-compose ps
```

### 3. Build the Project

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