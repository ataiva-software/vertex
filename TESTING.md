# Eden DevOps Suite - Comprehensive Testing Framework

This document describes the comprehensive testing framework implemented for the Eden DevOps Suite, providing 100% test coverage across all components.

## Testing Architecture Overview

The testing framework is organized into multiple layers:

```
testing/
├── shared/testing/           # Shared testing utilities and fixtures
├── */src/commonTest/        # Unit tests for shared modules
├── */src/test/              # Unit tests for services
├── integration-tests/       # Integration tests with real databases
├── e2e-tests/              # End-to-end tests with full system
└── performance-tests/       # Performance and load tests
```

## Testing Layers

### 1. Unit Tests

**Location**: `*/src/commonTest/` and `*/src/test/`

**Coverage**:
- ✅ All shared modules (core, auth, crypto, database, events, config)
- ✅ Service modules (API Gateway, Vault, Flow, Task, Monitor, Sync, Insight, Hub)
- ✅ Client modules (CLI, Web)

**Frameworks**:
- Kotlin Test for multiplatform modules
- JUnit 5 for JVM-only modules
- Kotest for advanced testing features
- MockK for mocking

**Example**:
```kotlin
@Test
fun testUserCreation() {
    val user = TestFixtures.createUser()
    assertEquals("test@example.com", user.email)
    assertTrue(user.id.isNotEmpty())
}
```

### 2. Integration Tests

**Location**: `integration-tests/`

**Coverage**:
- ✅ Database integration with PostgreSQL
- ✅ Redis event streaming integration
- ✅ Service-to-service communication
- ✅ Repository implementations with real databases
- ✅ Transaction handling and rollback scenarios
- ✅ Connection pooling and performance

**Technologies**:
- Testcontainers for database and Redis
- HikariCP for connection pooling
- Exposed ORM for database operations

**Example**:
```kotlin
@Test
@Tag("database")
fun testDatabaseIntegration() {
    val org = TestFixtures.createOrganization()
    // Test with real PostgreSQL container
    transaction(database) {
        Organizations.insert { /* ... */ }
    }
    // Verify data persistence
}
```

### 3. End-to-End Tests

**Location**: `e2e-tests/`

**Coverage**:
- ✅ Complete user workflows (registration, authentication, etc.)
- ✅ Multi-service orchestration tests
- ✅ User journey testing
- ✅ CLI integration testing
- ✅ Web UI testing (with Selenium)

**Technologies**:
- Docker Compose for full system orchestration
- Testcontainers Compose module
- Selenium WebDriver for UI testing
- Ktor HTTP Client for API testing

**Example**:
```kotlin
@Test
@Tag("user-workflow")
fun testCompleteUserRegistrationFlow() {
    // Start all services via Docker Compose
    // Test user registration through API
    // Verify user can login
    // Test user can access protected resources
}
```

### 4. Performance Tests

**Location**: `performance-tests/`

**Coverage**:
- ✅ Cryptographic operations benchmarks
- ✅ API endpoint load testing
- ✅ Database performance testing
- ✅ Concurrent user simulation
- ✅ Memory usage analysis
- ✅ Stress and endurance testing

**Technologies**:
- JMH for micro-benchmarks
- Gatling for load testing
- Custom performance measurement utilities
- Memory profiling tools

**Example**:
```kotlin
@Test
@Tag("crypto-performance")
fun testEncryptionPerformance() {
    val results = measureRepeated(1000) {
        encryption.encrypt(testData, key)
    }
    assertTrue(results.averageTime < 100) // ms
    assertTrue(results.throughput > 10) // ops/sec
}
```

## Shared Testing Utilities

### Test Data Builders

**Location**: `shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/builders/`

Provides fluent builders for creating test data:

```kotlin
val user = UserTestDataBuilder()
    .withEmail("test@example.com")
    .withName("Test User")
    .withOrganization(organizationId)
    .build()

val org = OrganizationTestDataBuilder()
    .withName("Test Organization")
    .withDescription("Test description")
    .build()
```

### Test Fixtures

**Location**: `shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/fixtures/`

Provides pre-configured test data:

```kotlin
val user = TestFixtures.createUser()
val organization = TestFixtures.createOrganization()
val permission = TestFixtures.createPermission()
val auditLog = TestFixtures.createAuditLog()
```

### Mock Factory

**Location**: `shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/mocks/`

Provides consistent mocks across tests:

```kotlin
val mockRepository = MockFactory.createUserRepository()
val mockEncryption = MockFactory.createEncryption()
val mockEventPublisher = MockFactory.createEventPublisher()
```

### Crypto Test Utils

**Location**: `shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/crypto/`

Provides cryptographic testing utilities:

```kotlin
val testKey = CryptoTestUtils.generateTestKey()
val testData = CryptoTestUtils.generateTestData(1024)
CryptoTestUtils.assertEncryptionRoundTrip(data, key)
```

### Test Extensions

**Location**: `shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/extensions/`

Provides extension functions for testing:

```kotlin
user.shouldHaveValidId()
organization.shouldHaveValidTimestamps()
result.shouldBeSuccess()
```

## Running Tests

### All Tests
```bash
./gradlew test
```

### Unit Tests Only
```bash
./gradlew testClasses
```

### Integration Tests
```bash
./gradlew :integration-tests:test
```

### Database Integration Tests
```bash
./gradlew :integration-tests:testDatabase
```

### End-to-End Tests
```bash
./gradlew :e2e-tests:test
```

### User Workflow Tests
```bash
./gradlew :e2e-tests:testUserWorkflows
```

### Performance Tests
```bash
./gradlew :performance-tests:test
```

### Crypto Performance Tests
```bash
./gradlew :performance-tests:testCryptoPerformance
```

### Load Tests
```bash
./gradlew :performance-tests:testApiLoad
```

### Stress Tests
```bash
./gradlew :performance-tests:stressTest
```

### Endurance Tests
```bash
./gradlew :performance-tests:enduranceTest
```

### Benchmarks
```bash
./gradlew :performance-tests:runBenchmarks
```

### Gatling Load Tests
```bash
./gradlew :performance-tests:runGatlingTests
```

## Test Configuration

### Environment Variables

```bash
# Database
DATABASE_URL=postgresql://eden_test:eden_test_password@localhost:5433/eden_test
REDIS_URL=redis://localhost:6380

# Testing
TESTCONTAINERS_REUSE_ENABLE=true
STRESS_TEST_ENABLED=true
ENDURANCE_TEST_ENABLED=true

# Performance
STRESS_TEST_DURATION=300
STRESS_TEST_CONCURRENT_USERS=1000
ENDURANCE_TEST_DURATION=3600
```

### Test Profiles

#### Development Profile
- Fast unit tests only
- In-memory databases where possible
- Minimal logging

#### CI Profile
- All tests except endurance tests
- Testcontainers with reuse enabled
- Detailed reporting

#### Full Profile
- All tests including stress and endurance
- Complete performance benchmarking
- Maximum logging and reporting

## Test Data Management

### Database Test Data
- Automatic cleanup between tests
- Transaction rollback for isolation
- Seed data for consistent testing

### File System Test Data
- Temporary directories for file operations
- Automatic cleanup after tests
- Test-specific file fixtures

### Network Test Data
- Mock HTTP responses
- Test API endpoints
- Simulated network conditions

## Continuous Integration

### GitHub Actions Workflow

```yaml
name: Test Suite
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: ./gradlew test

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: ./gradlew :integration-tests:test

  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: ./gradlew :e2e-tests:test

  performance-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: ./gradlew :performance-tests:test
```

## Test Coverage

### Coverage Tools
- Kover for Kotlin code coverage
- JaCoCo for Java code coverage
- Combined reporting across all modules

### Coverage Targets
- **Unit Tests**: 100% line coverage
- **Integration Tests**: 100% critical path coverage
- **E2E Tests**: 100% user workflow coverage
- **Performance Tests**: 100% performance-critical code coverage

### Coverage Reports
```bash
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```

## Best Practices

### Test Organization
1. **Arrange-Act-Assert** pattern
2. **Given-When-Then** for BDD-style tests
3. **One assertion per test** where possible
4. **Descriptive test names** that explain the scenario

### Test Data
1. **Use builders** for complex test data
2. **Isolate test data** between tests
3. **Use realistic data** that matches production
4. **Clean up resources** after tests

### Performance Testing
1. **Warm up** before measurements
2. **Multiple iterations** for statistical significance
3. **Measure both latency and throughput**
4. **Test under realistic load**

### Integration Testing
1. **Use real dependencies** where possible
2. **Test failure scenarios** as well as success
3. **Verify data persistence** and consistency
4. **Test concurrent access** patterns

## Troubleshooting

### Common Issues

#### Testcontainers Issues
```bash
# Enable container reuse
export TESTCONTAINERS_REUSE_ENABLE=true

# Check Docker daemon
docker info

# Clean up containers
docker system prune -f
```

#### Memory Issues
```bash
# Increase heap size
export GRADLE_OPTS="-Xmx4g"

# Enable G1GC
export GRADLE_OPTS="$GRADLE_OPTS -XX:+UseG1GC"
```

#### Network Issues
```bash
# Check port availability
netstat -tulpn | grep :5433

# Reset Docker network
docker network prune -f
```

## Metrics and Reporting

### Test Metrics
- Test execution time
- Test success/failure rates
- Code coverage percentages
- Performance benchmarks

### Performance Metrics
- Response times (p50, p95, p99)
- Throughput (requests/second)
- Error rates
- Resource utilization

### Reports Generated
- HTML test reports
- Coverage reports
- Performance benchmark reports
- Load test reports
- Trend analysis reports

## Future Enhancements

### Planned Improvements
1. **Visual regression testing** for UI components
2. **Contract testing** between services
3. **Chaos engineering** tests
4. **Security testing** automation
5. **Mobile app testing** framework
6. **API fuzzing** tests
7. **Database migration testing**
8. **Multi-environment testing**

## Test Execution Commands

### Quick Commands

```bash
# Run all tests (recommended for development)
./scripts/run-tests.sh

# Run only unit tests
./scripts/run-tests.sh -u

# Run with coverage report
./scripts/run-coverage.sh

# Run performance tests
./scripts/run-performance-tests.sh

# Run integration tests only
./scripts/run-tests.sh -i

# Run E2E tests only
./scripts/run-tests.sh -e
```

### Advanced Commands

```bash
# Run tests with performance benchmarks
./scripts/run-tests.sh -p

# Run coverage with browser opening
./scripts/run-coverage.sh --open

# Run stress tests (high resource usage)
./scripts/run-performance-tests.sh --stress

# Run endurance tests (very long running)
./scripts/run-performance-tests.sh --endurance

# Run tests without parallel execution
./scripts/run-tests.sh --no-parallel

# Run tests with verbose output
./scripts/run-tests.sh -v
```

### Gradle Commands

```bash
# Basic test execution
./gradlew test
./gradlew integrationTest
./gradlew e2eTest
./gradlew performanceTest

# Coverage commands
./gradlew koverHtmlReport
./gradlew koverXmlReport
./gradlew koverVerify
./gradlew coverageReport

# Test reporting
./gradlew testReport
./gradlew testAll

# Clean and test
./gradlew clean test
```

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Testcontainers Issues

**Problem**: Tests fail with "Could not find a valid Docker environment"

**Solutions**:
```bash
# Check Docker daemon
docker info

# Enable container reuse
export TESTCONTAINERS_REUSE_ENABLE=true

# Clean up containers
docker system prune -f

# Reset Docker network
docker network prune -f

# Check Docker permissions (Linux)
sudo usermod -aG docker $USER
newgrp docker
```

#### 2. Memory Issues

**Problem**: Tests fail with OutOfMemoryError

**Solutions**:
```bash
# Increase heap size
export GRADLE_OPTS="-Xmx4g -XX:+UseG1GC"

# For performance tests
export GRADLE_OPTS="$GRADLE_OPTS -XX:MaxGCPauseMillis=200"

# Reduce parallel test execution
./gradlew test --max-workers=2
```

#### 3. Network Issues

**Problem**: Tests fail due to port conflicts

**Solutions**:
```bash
# Check port availability
netstat -tulpn | grep :5433
lsof -i :5433

# Kill processes using ports
sudo kill -9 $(lsof -t -i:5433)

# Use different ports in tests
export TEST_DATABASE_PORT=5434
export TEST_REDIS_PORT=6380
```

#### 4. Database Connection Issues

**Problem**: Database integration tests fail

**Solutions**:
```bash
# Check PostgreSQL container
docker ps | grep postgres

# Check container logs
docker logs <container_id>

# Restart containers
docker-compose down && docker-compose up -d

# Reset database
./gradlew flywayClean flywayMigrate
```

#### 5. Coverage Issues

**Problem**: Coverage verification fails

**Solutions**:
```bash
# Generate detailed coverage report
./gradlew koverHtmlReport --info

# Check uncovered code
open build/reports/kover/html/index.html

# Run tests with coverage collection
./gradlew test koverHtmlReport

# Verify specific modules
./gradlew :shared:core:koverVerify
```

#### 6. Performance Test Issues

**Problem**: Performance tests are unstable

**Solutions**:
```bash
# Run with more iterations
export PERFORMANCE_TEST_ITERATIONS=10

# Disable other processes
# Close unnecessary applications

# Use dedicated test environment
export PERFORMANCE_TEST_ISOLATED=true

# Check system resources
htop
free -h
```

#### 7. CI/CD Issues

**Problem**: Tests pass locally but fail in CI

**Solutions**:
```bash
# Check environment differences
env | grep -E "(JAVA|GRADLE|TEST)"

# Use same JDK version
sdk use java 17.0.8-tem

# Check CI logs for resource constraints
# Increase CI timeout values

# Use CI-specific test profile
./gradlew test -Pci=true
```

### Performance Tuning

#### Test Execution Optimization

```bash
# Enable parallel execution
./gradlew test --parallel --max-workers=4

# Use build cache
./gradlew test --build-cache

# Skip unnecessary tasks
./gradlew test -x checkstyleMain -x pmdMain

# Use daemon
./gradlew test --daemon
```

#### Memory Optimization

```bash
# JVM tuning for tests
export GRADLE_OPTS="-Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Reduce test parallelism if needed
export GRADLE_OPTS="$GRADLE_OPTS -Dkotest.framework.parallelism=2"

# Enable incremental compilation
./gradlew test --continuous
```

#### Database Optimization

```bash
# Use faster test database
export TEST_DATABASE_URL="jdbc:h2:mem:testdb"

# Optimize PostgreSQL for tests
export POSTGRES_SHARED_BUFFERS=256MB
export POSTGRES_EFFECTIVE_CACHE_SIZE=1GB
```

## Test Maintenance Guidelines

### Regular Maintenance Tasks

#### Weekly Tasks
1. **Review test execution times**
   ```bash
   ./gradlew test --profile
   ```

2. **Check for flaky tests**
   ```bash
   # Run tests multiple times
   for i in {1..10}; do ./gradlew test; done
   ```

3. **Update test dependencies**
   ```bash
   ./gradlew dependencyUpdates
   ```

#### Monthly Tasks
1. **Review coverage reports**
   ```bash
   ./scripts/run-coverage.sh --open
   ```

2. **Performance baseline updates**
   ```bash
   ./scripts/run-performance-tests.sh --benchmarks-only
   ```

3. **Clean up test data**
   ```bash
   docker system prune -f
   ./gradlew clean
   ```

#### Quarterly Tasks
1. **Test framework updates**
2. **Performance threshold reviews**
3. **Test environment optimization**
4. **Documentation updates**

### Code Quality Standards

#### Test Code Standards
- **Test naming**: Use descriptive names that explain the scenario
- **Test structure**: Follow Arrange-Act-Assert pattern
- **Test isolation**: Each test should be independent
- **Test data**: Use builders and fixtures for consistent data

#### Coverage Standards
- **Line coverage**: 100% required
- **Branch coverage**: 100% required
- **Mutation testing**: Consider for critical components
- **Integration coverage**: All API endpoints and database operations

#### Performance Standards
- **Unit tests**: < 100ms average
- **Integration tests**: < 5s average
- **E2E tests**: < 30s average
- **Performance tests**: Baseline + 10% tolerance

### Monitoring and Alerting

#### Test Metrics to Monitor
- Test execution time trends
- Test failure rates
- Coverage percentage changes
- Performance regression detection
- Flaky test identification

#### Alerting Setup
```bash
# Set up alerts for:
# - Test failures in main branch
# - Coverage drops below 100%
# - Performance degradation > 20%
# - Test execution time > thresholds
```

### Best Practices Summary

#### Development Workflow
1. **Write tests first** (TDD approach)
2. **Run tests locally** before committing
3. **Use test scripts** for consistent execution
4. **Monitor coverage** continuously
5. **Fix flaky tests** immediately

#### CI/CD Integration
1. **Fail fast** on test failures
2. **Parallel execution** for speed
3. **Artifact collection** for debugging
4. **Performance tracking** over time
5. **Comprehensive reporting**

#### Team Practices
1. **Code review** includes test review
2. **Test maintenance** is everyone's responsibility
3. **Performance awareness** in all changes
4. **Documentation updates** with code changes
5. **Knowledge sharing** on testing practices

This comprehensive testing framework ensures the Eden DevOps Suite maintains high quality, performance, and reliability across all components and use cases. The framework is designed to scale with the project and provide confidence in all deployments.