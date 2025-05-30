# Quick Start Guide

Get Eden DevOps Suite running in 5 minutes with this step-by-step tutorial.

## 5-Minute Setup

### Step 1: Clone and Start (2 minutes)

```bash
# Clone the repository
git clone https://github.com/ataivadev/eden.git
cd eden

# Start the development environment
docker-compose up -d
```

### Step 2: Build and Test (2 minutes)

```bash
# Build the project
./gradlew build

# Verify API Gateway is running
curl http://localhost:8080/health
```

### Step 3: Try the CLI (1 minute)

```bash
# Build the CLI
./gradlew :clients:cli:linkReleaseExecutableLinuxX64

# Run Eden CLI
./clients/cli/build/bin/linuxX64/releaseExecutable/eden --help
```

## What You Just Set Up

```
┌─────────────────────────────────────────┐
│              Eden DevOps Suite          │
├─────────────────────────────────────────┤
│  ✅ API Gateway (Port 8080)             │
│  ✅ PostgreSQL Database (Port 5432)     │
│  ✅ Redis Cache (Port 6379)             │
│  ✅ CLI Tool                            │
│  🚧 Web UI (Coming Soon)                │
└─────────────────────────────────────────┘
```

## Current Capabilities

### ✅ What Works Now
- **Project Structure**: Complete Kotlin Multiplatform setup
- **API Gateway**: Basic Ktor server with health endpoints
- **Database**: PostgreSQL with initialization scripts
- **CLI Framework**: Command structure for all Eden components
- **Development Environment**: Docker Compose setup

### 🚧 In Development
- Authentication system
- Secrets management (Eden Vault)
- Workflow automation (Eden Flow)
- Web UI interface

### 📋 Planned Features
- Task orchestration (Eden Task)
- Monitoring system (Eden Monitor)
- Multi-cloud sync (Eden Sync)
- Analytics dashboard (Eden Insight)
- Service discovery (Eden Hub)

## Next Steps

### For Developers
1. **Explore the Code**: Check out the [Project Status](../development/project-status.md)
2. **Set Up Development**: Follow the [Development Guide](development.md)
3. **Contribute**: Read the [Contributing Guide](../../CONTRIBUTING.md)

### For Users
1. **Learn Concepts**: Understand [Eden Philosophy](../user-guide/concepts.md)
2. **Check Roadmap**: See what's coming in the [Roadmap](../development/roadmap.md)
3. **Stay Updated**: Watch the repository for updates

## Troubleshooting Quick Fixes

### Services Won't Start
```bash
# Check Docker is running
docker --version

# Check port availability
netstat -tulpn | grep :8080
```

### Build Fails
```bash
# Check Java version (needs 17+)
java -version

# Clean and retry
./gradlew clean build
```

### CLI Not Working
```bash
# Make sure gradlew is executable
chmod +x gradlew

# Try building again
./gradlew :clients:cli:build
```

## Getting Help

- **Installation Issues**: See [Installation Guide](installation.md)
- **Development Setup**: Check [Development Guide](development.md)
- **Bug Reports**: Open an [Issue](https://github.com/your-org/eden/issues)
- **Questions**: Start a [Discussion](https://github.com/your-org/eden/discussions)

---

**🎉 Congratulations!** You now have Eden DevOps Suite running locally. The foundation is solid, and we're actively building the core features.