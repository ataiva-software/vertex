# Contributing to Eden DevOps Suite

Thank you for your interest in contributing to Eden! This document provides guidelines and information for contributors.

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Git
- Basic knowledge of Kotlin and DevOps concepts

### Development Setup

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/ataivadev/eden.git
   cd eden
   ```

2. **Set up the development environment**
   ```bash
   ./scripts/setup-dev.sh
   ```

3. **Verify the setup**
   ```bash
   ./gradlew build
   docker-compose ps
   ```

## 📋 Development Workflow

### Branch Strategy

- `main` - Production-ready code
- `develop` - Integration branch for features
- `feature/*` - Feature development branches
- `bugfix/*` - Bug fix branches
- `hotfix/*` - Critical production fixes

### Making Changes

1. **Create a feature branch**
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Follow the coding standards (see below)
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew integrationTest
   ```

4. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add new feature description"
   ```

5. **Push and create a pull request**
   ```bash
   git push origin feature/your-feature-name
   ```

### Commit Message Format

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `chore`: Changes to the build process or auxiliary tools

**Examples:**
```
feat(vault): add zero-knowledge encryption for secrets
fix(api-gateway): resolve CORS configuration issue
docs: update installation instructions
test(flow): add unit tests for workflow execution
```

## 🏗️ Architecture Guidelines

### Project Structure

```
eden/
├── shared/                    # Shared libraries (core, auth, crypto, etc.)
├── services/                  # Microservices (vault, flow, task, etc.)
├── clients/                   # Client applications (web, cli, mobile)
├── infrastructure/            # Infrastructure as code
└── docs/                      # Documentation
    ├── getting-started/       # Installation and setup guides
    ├── user-guide/            # User documentation and CLI reference
    ├── architecture/          # Technical architecture documentation
    └── development/           # Development guides and project status
```

### Coding Standards

#### Kotlin Style Guide

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs

#### Example:

```kotlin
/**
 * Encrypts data using zero-knowledge encryption
 * 
 * @param data The data to encrypt
 * @param userPassword The user's password for key derivation
 * @return Encrypted data with metadata
 */
suspend fun encryptZeroKnowledge(
    data: String,
    userPassword: String
): ZeroKnowledgeResult {
    // Implementation
}
```

#### Database Guidelines

- Use meaningful table and column names
- Always include created_at and updated_at timestamps
- Use UUIDs for primary keys
- Add proper indexes for performance
- Include database migrations for schema changes

#### API Design

- Follow RESTful principles
- Use consistent URL patterns: `/api/v1/organizations/{id}/resources`
- Include proper HTTP status codes
- Implement comprehensive error handling
- Add OpenAPI/Swagger documentation

## 🧪 Testing

### Test Structure

```
src/
├── main/kotlin/
└── test/kotlin/
    ├── unit/           # Unit tests
    ├── integration/    # Integration tests
    └── fixtures/       # Test data and utilities
```

### Testing Guidelines

- Write tests for all new functionality
- Aim for high test coverage (>80%)
- Use descriptive test names
- Follow the AAA pattern (Arrange, Act, Assert)
- Use test containers for integration tests

#### Example Test:

```kotlin
class SecretServiceTest {
    @Test
    fun `should encrypt secret with zero-knowledge encryption`() {
        // Arrange
        val secretService = SecretService()
        val plaintext = "my-secret-value"
        val password = "user-password"
        
        // Act
        val result = secretService.encryptSecret(plaintext, password)
        
        // Assert
        assertThat(result.isSuccess).isTrue()
        assertThat(result.encryptedData).isNotEmpty()
    }
}
```

## 📚 Documentation

### Code Documentation

- Add KDoc comments for all public APIs
- Include usage examples in documentation
- Document complex algorithms and business logic
- Keep documentation up to date with code changes

### API Documentation

- Use OpenAPI/Swagger for REST APIs
- Include request/response examples
- Document error codes and responses
- Provide authentication examples

## 🔒 Security Guidelines

### Security Best Practices

- Never commit secrets or credentials
- Use environment variables for configuration
- Implement proper input validation
- Follow the principle of least privilege
- Add security tests for authentication and authorization

### Zero-Knowledge Encryption

- Client-side key derivation only
- Never store or transmit user passwords
- Use strong encryption algorithms (AES-256-GCM)
- Implement proper key management

## 🐛 Bug Reports

### Before Submitting

1. Check if the issue already exists
2. Verify it's reproducible
3. Test with the latest version

### Bug Report Template

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. See error

**Expected behavior**
What you expected to happen.

**Environment**
- OS: [e.g. macOS, Linux, Windows]
- Java version: [e.g. 17]
- Eden version: [e.g. 1.0.0]

**Additional context**
Any other context about the problem.
```

## 💡 Feature Requests

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
A clear description of what the problem is.

**Describe the solution you'd like**
A clear description of what you want to happen.

**Describe alternatives you've considered**
Other solutions you've considered.

**Additional context**
Any other context about the feature request.
```

## 📝 Pull Request Process

### Before Submitting

- [ ] Code follows the style guidelines
- [ ] Self-review of the code
- [ ] Tests added for new functionality
- [ ] Tests pass locally
- [ ] Documentation updated
- [ ] No merge conflicts

### Pull Request Template

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests pass
```

## 🏷️ Release Process

### Versioning

We use [Semantic Versioning](https://semver.org/):
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes (backward compatible)

### Release Checklist

- [ ] All tests pass
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version bumped
- [ ] Release notes prepared
- [ ] Security review completed

## 🤝 Community

### Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

### Getting Help

- **Documentation**: Check the [docs/](docs/) directory
- **Getting Started**: [Installation](docs/getting-started/installation.md) and [Quick Start](docs/getting-started/quick-start.md)
- **Development**: [Development Guide](docs/getting-started/development.md)
- **Project Status**: [Current Implementation Status](docs/development/project-status.md)
- **Issues**: Search existing [GitHub Issues](https://github.com/your-org/eden/issues)
- **Discussions**: Join [GitHub Discussions](https://github.com/your-org/eden/discussions)

### Recognition

Contributors will be recognized in:
- CONTRIBUTORS.md file
- Release notes
- Project documentation

## 📄 License

By contributing to Eden, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to Eden DevOps Suite! 🌱