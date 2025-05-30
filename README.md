# Eden DevOps Suite

![Phase](https://img.shields.io/badge/Phase-1%20Foundation-yellow)
![Status](https://img.shields.io/badge/Status-Early%20Development-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

A comprehensive, privacy-first DevOps platform built with Kotlin Multiplatform, designed to unify your development workflow through seven integrated components.

## 🌟 What is Eden?

Eden creates a _perfect, pristine_ environment for developers and operations teams by combining secrets management, workflow automation, task orchestration, monitoring, and analytics into a single, integrated suite.

**Current Status**: Early Phase 1 development - foundation and core infrastructure are being built.

### Core Components (Planned)

- **🔐 Eden Vault** - Zero-knowledge secrets management
- **🔄 Eden Flow** - Secure workflow automation  
- **⚡ Eden Task** - Distributed task orchestration
- **📊 Eden Monitor** - Global uptime/performance monitoring
- **☁️ Eden Sync** - Multi-cloud cost optimization
- **📈 Eden Insight** - Privacy-first analytics dashboards
- **🎯 Eden Hub** - Service discovery and configuration management

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Git

### 5-Minute Setup

```bash
# 1. Clone and start
git clone https://github.com/ataivadev/eden.git
cd eden
docker-compose up -d

# 2. Build the project
./gradlew build

# 3. Try the CLI
./gradlew :clients:cli:linkReleaseExecutableLinuxX64
./clients/cli/build/bin/linuxX64/releaseExecutable/eden --help
```

**What works now**: Basic project structure, API Gateway skeleton, CLI framework, and development environment.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Frontend Layer                             │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Eden Web UI       │    │        Eden CLI                 │ │
│  │ (Kotlin/JS+Compose) │    │    (Kotlin Native)              │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     API Gateway Layer                          │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Authentication • Rate Limiting • Load Balancing • Routing │ │
│  └─────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Service Layer                               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  Vault  │ │  Flow   │ │  Task   │ │ Monitor │ │  Sync   │   │
│  │ Service │ │ Service │ │ Service │ │ Service │ │ Service │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────┐ ┌─────────┐                                       │
│  │ Insight │ │   Hub   │                                       │
│  │ Service │ │ Service │                                       │
│  └─────────┘ └─────────┘                                       │
├─────────────────────────────────────────────────────────────────┤
│                   Shared Infrastructure                        │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Shared Core       │    │    Message Bus & Events        │ │
│  │     Library         │    │   (Redis Streams/NATS)         │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     Data Layer                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │         PostgreSQL + Extensions + Redis Cache              │ │
│  └─────────────────────────────────────────────────────────────┘ │
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
- **[Coding Standards](docs/development/coding-standards.md)** - Development guidelines

## 🤝 Contributing

We welcome contributions! Eden is in early development, making it a great time to get involved.

### Quick Contribution Guide
1. Check [Project Status](docs/development/project-status.md) for current priorities
2. Look for "good first issue" labels in [Issues](https://github.com/your-org/eden/issues)
3. Read the [Contributing Guide](CONTRIBUTING.md)
4. Set up your [Development Environment](docs/getting-started/development.md)

### Development Commands

```bash
# Build all projects
./gradlew build

# Run tests
./gradlew test

# Start development environment
docker-compose up -d

# Build CLI for your platform
./gradlew :clients:cli:build
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
