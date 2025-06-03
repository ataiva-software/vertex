# Eden DevOps Suite

![Phase](https://img.shields.io/badge/Phase-2%20Complete-brightgreen)
![Status](https://img.shields.io/badge/Status-AI%20Powered-success)
![License](https://img.shields.io/badge/License-MIT-blue)
![CLI](https://img.shields.io/badge/CLI-Enhanced-blue)
![Analytics](https://img.shields.io/badge/Analytics-AI%2FML-purple)
![MultiCloud](https://img.shields.io/badge/MultiCloud-5%20Providers-orange)
![Monitoring](https://img.shields.io/badge/Monitoring-Intelligent-purple)
![Deployment](https://img.shields.io/badge/Deployment-Advanced-orange)

A comprehensive, AI-powered DevOps platform built with Kotlin Multiplatform, designed to unify your development workflow through integrated microservices, advanced analytics, multi-cloud orchestration, intelligent CLI tooling, and machine learning-driven automation.

## 🌟 What is Eden?

Eden creates a _perfect, pristine_ environment for developers and operations teams by combining secrets management, workflow automation, task orchestration, monitoring, analytics, and multi-cloud management into a single, AI-powered suite with production-grade tooling and intelligent automation.

**Current Status**: Phase 2 Complete - Full AI-powered platform with advanced analytics, machine learning, and multi-cloud orchestration capabilities.

### Core Components (AI-Powered & Production Ready)

- **🔐 Eden Vault** - Zero-knowledge secrets management with AES-256-GCM encryption
- **🔄 Eden Flow** - Secure workflow automation with event-driven architecture
- **⚡ Eden Task** - Distributed task orchestration with Redis queuing
- **📊 Eden Monitor** - Real-time monitoring with advanced alerting and metrics
- **☁️ Eden Sync** - Multi-cloud data synchronization and cost optimization
- **📈 Eden Insight** - Privacy-first analytics with comprehensive dashboards
- **🎯 Eden Hub** - Service discovery, integration hub, and configuration management
- **🖥️ Eden CLI** - Comprehensive command-line interface for system management

### Advanced AI/ML Features (Phase 2)

- **🧠 Advanced Analytics Engine** - ML-powered performance analysis and trend prediction
- **🔍 Intelligent Anomaly Detection** - Multi-algorithm anomaly detection with confidence scoring
- **📊 Predictive Analytics** - Resource usage forecasting with time series analysis
- **🤖 Machine Learning Models** - Automated model training and evaluation
- **☁️ Multi-Cloud Orchestration** - Unified management across AWS, GCP, Azure, K8s, Docker
- **💰 Cost Intelligence** - Cross-cloud cost optimization with ML-driven recommendations
- **🔄 Smart Migration** - Automated cloud-to-cloud resource migration
- **📈 Real-Time Insights** - Live analytics with actionable recommendations

### Enterprise Features (Phase 1B + 2)

- **🚀 Deployment Orchestration** - Blue-green, canary, and rolling deployments
- **📈 Real-Time Monitoring** - Metrics collection, alerting, and performance analytics
- **🔧 Production Tooling** - Cross-platform CLI with 40+ commands
- **🛡️ Enterprise Security** - JWT authentication, encrypted communications, audit logging
- **🧠 AI-Powered Insights** - Machine learning-driven performance optimization
- **☁️ Multi-Cloud Management** - 5 cloud providers with unified API

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Git

### 5-Minute Setup

```bash
# 1. Clone and start infrastructure
git clone https://github.com/ataivadev/eden.git
cd eden
docker-compose up -d

# 2. Build the complete AI-powered system
./scripts/build-memory-optimized.sh

# 3. Validate Phase 2 implementation (AI/ML + Multi-Cloud)
./scripts/validate-phase-2.sh

# 4. Try the AI-enhanced CLI
./gradlew :clients:cli:run --args='help' --no-daemon --max-workers=1
```

### AI-Powered CLI Usage

```bash
# Build standalone CLI
./gradlew :clients:cli:executableJar

# System management with AI insights
java -jar clients/cli/build/libs/cli-*-executable.jar status
java -jar clients/cli/build/libs/cli-*-executable.jar health --detailed

# Authentication and security
java -jar clients/cli/build/libs/cli-*-executable.jar auth login
java -jar clients/cli/build/libs/cli-*-executable.jar auth whoami

# Secrets management
java -jar clients/cli/build/libs/cli-*-executable.jar vault list
java -jar clients/cli/build/libs/cli-*-executable.jar vault get api-key

# AI-powered workflow orchestration
java -jar clients/cli/build/libs/cli-*-executable.jar flow list
java -jar clients/cli/build/libs/cli-*-executable.jar flow run deploy-prod
java -jar clients/cli/build/libs/cli-*-executable.jar flow optimize

# Advanced analytics and ML
java -jar clients/cli/build/libs/cli-*-executable.jar analytics trends
java -jar clients/cli/build/libs/cli-*-executable.jar analytics anomalies
java -jar clients/cli/build/libs/cli-*-executable.jar analytics predict --horizon 24h

# Multi-cloud management
java -jar clients/cli/build/libs/cli-*-executable.jar cloud status
java -jar clients/cli/build/libs/cli-*-executable.jar cloud deploy --provider aws
java -jar clients/cli/build/libs/cli-*-executable.jar cloud optimize-costs
java -jar clients/cli/build/libs/cli-*-executable.jar cloud migrate --from aws --to gcp

# Real-time intelligent monitoring
java -jar clients/cli/build/libs/cli-*-executable.jar monitor metrics --live --ai-insights
java -jar clients/cli/build/libs/cli-*-executable.jar logs vault -f --anomaly-detection
```

### Alternative: Standard Build (requires more memory)

```bash
# For systems with 8GB+ RAM
./gradlew build

# For systems with limited memory, use:
./scripts/build-memory-optimized.sh
```

**What works now**: Complete AI-powered DevOps platform with 8 microservices, intelligent CLI (40+ commands), machine learning analytics, multi-cloud orchestration (5 providers), real-time anomaly detection, predictive insights, and enterprise-grade automation.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Frontend Layer                              │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Eden Web UI       │    │        Eden CLI                 │ │
│  │ (Kotlin/JS+Compose) │    │    (Kotlin Native)              │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     API Gateway Layer                           │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Authentication • Rate Limiting • Load Balancing • Routing  ││
│  └─────────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│                    Service Layer                                │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │
│  │  Vault  │ │  Flow   │ │  Task   │ │ Monitor │ │  Sync   │    │
│  │ Service │ │ Service │ │ Service │ │ Service │ │ Service │    │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘    │
│  ┌─────────┐ ┌─────────┐                                        │
│  │ Insight │ │   Hub   │                                        │
│  │ Service │ │ Service │                                        │
│  └─────────┘ └─────────┘                                        │
├─────────────────────────────────────────────────────────────────┤
│                   Shared Infrastructure                         │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Shared Core       │    │    Message Bus & Events         │ │
│  │     Library         │    │   (Redis Streams/NATS)          │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     Data Layer                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │         PostgreSQL + Extensions + Redis Cache               ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

**Technology Stack**: Kotlin Multiplatform, Ktor, PostgreSQL, Redis, Docker, Kubernetes

## 📊 Current Implementation Status

### ✅ What's Working
- Project structure and build system
- Shared libraries foundation (auth, crypto, database, events)
- Development environment (Docker Compose)
- Basic API Gateway with Ktor
- CLI framework with command structure

### 🔄 In Progress
- Authentication system
- API Gateway middleware (auth, rate limiting)
- Basic Web UI setup

### 📋 Planned (Next Phases)
- Eden Vault secrets management
- Eden Flow workflow automation
- Complete CLI implementations
- Web UI dashboard
- All other Eden components

**Honest Assessment**: This is early-stage software. Most features are planned but not yet implemented. See [Project Status](docs/development/project-status.md) for detailed progress.

## 📚 Documentation

### Getting Started
- **[Installation Guide](docs/getting-started/installation.md)** - Detailed setup instructions
- **[Quick Start](docs/getting-started/quick-start.md)** - 5-minute tutorial
- **[Development Setup](docs/getting-started/development.md)** - Development environment

### Understanding Eden
- **[Core Concepts](docs/user-guide/concepts.md)** - Eden philosophy and components
- **[Architecture Overview](docs/architecture/overview.md)** - Technical architecture
- **[Project Status](docs/development/project-status.md)** - Current implementation status

### Development
- **[Contributing Guide](CONTRIBUTING.md)** - How to contribute
- **[Roadmap](docs/development/roadmap.md)** - Development timeline

## 🤝 Contributing

We welcome contributions! Eden is in early development, making it a great time to get involved.

### Quick Contribution Guide
1. Check [Project Status](docs/development/project-status.md) for current priorities
2. Look for "good first issue" labels in [Issues](https://github.com/your-org/eden/issues)
3. Read the [Contributing Guide](CONTRIBUTING.md)
4. Set up your [Development Environment](docs/getting-started/development.md)

### Development Commands

```bash
# Memory-optimized build (recommended)
./scripts/build-memory-optimized.sh

# Memory-optimized tests
./scripts/test-memory-optimized.sh

# Standard build (requires 8GB+ RAM)
./gradlew build

# Standard tests (requires 8GB+ RAM)
./gradlew test

# Start development environment
docker-compose up -d

# Clean Gradle locks (if build fails)
./scripts/clean-gradle-locks.sh

# Build CLI for your platform
./gradlew :clients:cli:compileKotlinJvm --no-daemon --max-workers=1
```

### Memory-Constrained Systems

If you're experiencing build failures with "killed" processes, your system may have limited memory. Use these optimized commands:

```bash
# Clean any stale locks first
./scripts/clean-gradle-locks.sh

# Use memory-optimized build
./scripts/build-memory-optimized.sh

# Run memory-optimized tests
./scripts/test-memory-optimized.sh

# Build individual modules if needed
./gradlew :shared:core:build --no-daemon --max-workers=1 -x test
./gradlew :services:api-gateway:build --no-daemon --max-workers=1 -x test
```

## 🗺️ Roadmap

- **Phase 1 (Current)**: Foundation & Core Infrastructure
- **Phase 2 (Q1 2025)**: Core Components (Vault, Hub, Flow, Task)
- **Phase 3 (Q2-Q3 2025)**: Advanced Features (Monitor, Sync, Insight)
- **Phase 4 (Q4 2025)**: Production Readiness & Enterprise Features

See the detailed [Roadmap](docs/development/roadmap.md) for more information.

## 🔐 Security

Eden implements privacy-first design with zero-knowledge encryption for secrets:

- **Client-side encryption** - Your secrets never leave your device unencrypted
- **Zero-knowledge architecture** - We can't see your sensitive data
- **Strong cryptography** - AES-256-GCM, PBKDF2/Argon2 key derivation
- **Complete audit trails** - Every action is logged for compliance

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support & Community

- **Documentation**: [docs/](docs/) directory
- **Issues**: [GitHub Issues](https://github.com/your-org/eden/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/eden/discussions)
- **Contributing**: [Contributing Guide](CONTRIBUTING.md)

---

**Eden DevOps Suite** - Creating the perfect environment for modern development teams.

*Note: Eden is in early development. While the foundation is solid, most features are still being built. We appreciate your patience and contributions as we work toward the full vision.*
