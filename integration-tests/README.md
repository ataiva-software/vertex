# Eden DevOps Suite - Comprehensive Regression Testing Framework

This directory contains the comprehensive regression testing framework for the Eden DevOps Suite, designed to validate all critical functionality, performance, and security aspects to prevent regressions and ensure production readiness.

## 🎯 Overview

The regression testing framework provides:

- **Cross-Service Integration Tests** - Validate service-to-service communication and workflows
- **Performance Regression Tests** - Ensure performance standards are maintained
- **Security Regression Tests** - Validate security controls and prevent security vulnerabilities
- **Comprehensive End-to-End Tests** - Complete DevOps workflow validation
- **Automated Test Orchestration** - Automated service startup, test execution, and reporting

## 📁 Test Structure

```
integration-tests/
├── src/test/kotlin/com/ataiva/eden/integration/
│   ├── CrossServiceRegressionTest.kt          # Cross-service integration tests
│   ├── PerformanceRegressionTest.kt           # Performance validation tests
│   ├── SecurityRegressionTest.kt              # Security control tests
│   ├── RegressionTestSuite.kt                 # Comprehensive test orchestrator
│   └── hub/
│       └── HubServiceIntegrationTest.kt       # Hub service specific tests
├── build.gradle.kts                           # Test build configuration
└── README.md                                  # This file
```

## 🚀 Quick Start

### Prerequisites

- JDK 17 or higher
- Docker and Docker Compose
- All Eden services built and ready to run
- PostgreSQL and Redis (can be started via Docker)

### Running All Regression Tests

```bash
# From project root
./scripts/run-regression-tests.sh
```

### Running Specific Test Categories

```bash
# From integration-tests directory
./gradlew regressionTest          # Comprehensive regression tests
./gradlew performanceTest         # Performance tests only
./gradlew securityTest           # Security tests only
./gradlew crossServiceTest       # Cross-service integration tests
./gradlew allRegressionTests     # All test categories
```

## 📊 Test Categories

### 1. Cross-Service Integration Tests

**File:** [`CrossServiceRegressionTest.kt`](src/test/kotlin/com/ataiva/eden/integration/CrossServiceRegressionTest.kt)

**Purpose:** Validate that all Eden services work together correctly and prevent integration regressions.

**Test Scenarios:**
- **Vault → Flow Integration** - Secret retrieval in workflows
- **Hub → Sync Integration** - Webhook-triggered data synchronization
- **Insight Service Data Flow** - Analytics across all service data
- **Monitor → Hub Notifications** - Alert-triggered notifications
- **CLI → API Gateway Routing** - Command-line interface integration
- **End-to-End DevOps Pipeline** - Complete deployment workflow

**Success Criteria:**
- All service interactions work correctly
- Data flows properly between services
- Workflows complete successfully
- No integration points are broken

### 2. Performance Regression Tests

**File:** [`PerformanceRegressionTest.kt`](src/test/kotlin/com/ataiva/eden/integration/PerformanceRegressionTest.kt)

**Purpose:** Ensure the platform maintains performance standards and prevent performance regressions.

**Test Scenarios:**
- **API Gateway Performance** - 50 concurrent users, 10 requests each
- **Vault Encryption Performance** - Encryption/decryption under load
- **Hub Webhook Performance** - High-volume webhook delivery
- **Flow Execution Performance** - Workflow execution times
- **Insight Query Performance** - Analytics query response times
- **Database Performance** - Connection pooling and query optimization
- **Memory Stability** - Memory usage under extended load

**Performance Thresholds:**
- 95% of requests under 200ms response time
- No request should exceed 1000ms
- Memory increase should not exceed 300MB during load tests
- Webhook delivery success rate >= 95%
- Database operations under 300ms (95th percentile)

### 3. Security Regression Tests

**File:** [`SecurityRegressionTest.kt`](src/test/kotlin/com/ataiva/eden/integration/SecurityRegressionTest.kt)

**Purpose:** Validate all security controls remain intact and prevent security regressions.

**Test Scenarios:**
- **Authentication Requirements** - All endpoints require proper auth
- **JWT Token Validation** - Token validation and expiration handling
- **Role-Based Access Control** - User permissions and restrictions
- **Input Validation** - SQL injection and XSS prevention
- **Data Encryption** - Sensitive data protection validation
- **Webhook Security** - Signature verification and secure delivery
- **Rate Limiting** - DoS protection mechanisms
- **Security Headers** - Proper HTTP security headers
- **Audit Logging** - Security event logging

**Security Standards:**
- All protected endpoints return 401/403 without valid auth
- Malicious input is properly rejected or sanitized
- Sensitive data is encrypted at rest and in transit
- Security headers are present in responses
- Failed authentication attempts are logged

### 4. Comprehensive Test Suite

**File:** [`RegressionTestSuite.kt`](src/test/kotlin/com/ataiva/eden/integration/RegressionTestSuite.kt)

**Purpose:** Orchestrate all regression tests and provide comprehensive validation.

**Test Phases:**
1. **Infrastructure Health** - Service availability and database connectivity
2. **Cross-Service Integration** - All integration scenarios
3. **Performance Validation** - All performance benchmarks
4. **Security Validation** - All security controls
5. **Data Consistency** - Cross-service data integrity
6. **Error Handling** - Failure recovery and resilience

**Quality Gates:**
- Minimum 90% test success rate required
- All critical integration tests must pass
- Performance thresholds must be met
- Security controls must be validated
- No critical errors or failures

## 🔧 Configuration

### Test Environment Variables

```bash
# Test execution settings
EDEN_TEST_MODE=true
EDEN_LOG_LEVEL=INFO
JUNIT_JUPITER_EXECUTION_TIMEOUT_DEFAULT=10m

# Service endpoints (automatically configured)
VAULT_SERVICE_URL=http://localhost:8081
TASK_SERVICE_URL=http://localhost:8083
FLOW_SERVICE_URL=http://localhost:8082
HUB_SERVICE_URL=http://localhost:8080
SYNC_SERVICE_URL=http://localhost:8084
INSIGHT_SERVICE_URL=http://localhost:8085
API_GATEWAY_URL=http://localhost:8000
```

### Performance Thresholds

```kotlin
// Configurable in test files
private val responseTimeThreshold = 200L // 95% under 200ms
private val maxResponseTime = 1000L      // Max 1 second
private val concurrentUsers = 50         // Load test users
private val requestsPerUser = 10         // Requests per user
```

### Security Test Configuration

```kotlin
// Test user credentials and roles
private val testUserId = "security-test-user"
private val adminUserId = "admin-user"
private val readOnlyUserId = "readonly-user"

// Malicious payloads for testing
private val sqlInjectionPayloads = listOf(
    "'; DROP TABLE secrets; --",
    "' OR '1'='1",
    // ... more payloads
)
```

## 📈 Test Reports

### HTML Reports

After test execution, detailed HTML reports are available:

```
integration-tests/build/reports/
├── tests/                    # Basic integration tests
├── regression-tests/         # Comprehensive regression tests
├── performance-tests/        # Performance test results
├── security-tests/          # Security validation results
├── cross-service-tests/     # Cross-service integration results
└── comprehensive/           # Combined test summary
```

### Test Metrics

The framework tracks and reports:

- **Test Execution Time** - Individual and total test durations
- **Success Rate** - Percentage of passing tests
- **Performance Metrics** - Response times, throughput, memory usage
- **Security Validation** - Security control verification status
- **Integration Status** - Service-to-service communication health

### CI/CD Integration

JSON summary reports are generated for CI/CD integration:

```json
{
    "timestamp": "2025-01-06T15:30:00Z",
    "duration_minutes": 25,
    "services_tested": 8,
    "services_healthy": 8,
    "test_categories": 5,
    "overall_status": "PASSED",
    "report_location": "/path/to/detailed/report.md"
}
```

## 🛠️ Development and Maintenance

### Adding New Tests

1. **Cross-Service Tests** - Add to `CrossServiceRegressionTest.kt`
2. **Performance Tests** - Add to `PerformanceRegressionTest.kt`
3. **Security Tests** - Add to `SecurityRegressionTest.kt`
4. **Service-Specific Tests** - Create new files in appropriate subdirectories

### Test Naming Conventions

```kotlin
@Test
fun `should validate specific functionality under test conditions`() = runTest {
    // Test implementation
}
```

### Helper Methods

Common helper methods are available in each test class:

```kotlin
// HTTP request helpers
private fun makeRequest(method: String, url: String, body: Map<String, Any>? = null)
private fun makeAuthenticatedRequest(method: String, url: String, body: Map<String, Any>? = null)

// Service-specific helpers
private fun createVaultSecret(request: Map<String, Any>)
private fun createHubWebhook(request: Map<String, Any>)
private fun executeFlowWorkflow(id: String, params: Map<String, Any>)
```

### Performance Test Guidelines

1. **Use Realistic Load** - Test with production-like concurrent users
2. **Measure Key Metrics** - Response time, throughput, memory usage
3. **Set Clear Thresholds** - Define acceptable performance limits
4. **Test Under Stress** - Include high-load scenarios
5. **Monitor Resources** - Track CPU, memory, database connections

### Security Test Guidelines

1. **Test All Endpoints** - Ensure comprehensive coverage
2. **Use Real Payloads** - Test with actual malicious inputs
3. **Validate Responses** - Check both success and failure cases
4. **Test Edge Cases** - Include boundary conditions
5. **Verify Logging** - Ensure security events are logged

## 🚨 Troubleshooting

### Common Issues

**Services Not Starting**
```bash
# Check service logs
tail -f logs/service-name-startup.log

# Verify ports are available
netstat -tulpn | grep :8080

# Check Docker containers
docker ps -a
```

**Test Failures**
```bash
# Run specific test for debugging
./gradlew test --tests "CrossServiceRegressionTest.should validate specific test"

# Enable debug logging
export EDEN_LOG_LEVEL=DEBUG

# Check test reports
open integration-tests/build/reports/tests/test/index.html
```

**Performance Issues**
```bash
# Increase JVM memory for tests
export GRADLE_OPTS="-Xmx4g"

# Run performance tests in isolation
./gradlew performanceTest --max-workers=1

# Monitor system resources
top -p $(pgrep -f "gradle|java")
```

**Database Connection Issues**
```bash
# Check database containers
docker logs eden-postgres-test

# Verify database connectivity
psql -h localhost -U eden_user -d eden_test

# Reset test database
docker restart eden-postgres-test
```

### Debug Mode

Enable debug mode for detailed test execution information:

```bash
# Set debug environment variables
export EDEN_TEST_DEBUG=true
export EDEN_LOG_LEVEL=DEBUG

# Run tests with debug output
./gradlew test --debug --info
```

### Test Data Cleanup

Tests automatically clean up created data, but manual cleanup may be needed:

```bash
# Clean up test databases
docker exec eden-postgres-test psql -U eden_user -d eden_test -c "TRUNCATE TABLE secrets, tasks, workflows CASCADE;"

# Reset Redis cache
docker exec eden-redis-test redis-cli FLUSHALL

# Clean up test files
rm -rf test-reports/* logs/*
```

## 📋 Best Practices

### Test Design

1. **Independent Tests** - Each test should be self-contained
2. **Deterministic Results** - Tests should produce consistent results
3. **Clear Assertions** - Use descriptive assertion messages
4. **Proper Cleanup** - Always clean up test data
5. **Realistic Scenarios** - Test real-world usage patterns

### Performance Testing

1. **Baseline Measurements** - Establish performance baselines
2. **Gradual Load Increase** - Ramp up load gradually
3. **Resource Monitoring** - Monitor system resources during tests
4. **Consistent Environment** - Use consistent test environments
5. **Regular Execution** - Run performance tests regularly

### Security Testing

1. **Comprehensive Coverage** - Test all attack vectors
2. **Real Payloads** - Use actual malicious inputs
3. **Positive and Negative Tests** - Test both success and failure cases
4. **Regular Updates** - Keep security tests updated with new threats
5. **Compliance Validation** - Ensure compliance with security standards

## 🎉 Success Criteria

The regression testing framework validates that the Eden DevOps Suite:

✅ **Functionality** - All services and integrations work correctly
✅ **Performance** - Meets or exceeds performance benchmarks
✅ **Security** - All security controls are properly implemented
✅ **Reliability** - Handles errors gracefully and recovers properly
✅ **Scalability** - Performs well under concurrent load
✅ **Maintainability** - Code quality and test coverage standards met

## 📞 Support

For questions or issues with the regression testing framework:

1. **Check Test Reports** - Review detailed HTML reports for failures
2. **Review Logs** - Check service and test execution logs
3. **Run Debug Mode** - Enable debug logging for detailed information
4. **Consult Documentation** - Review service-specific documentation
5. **Create Issues** - Report bugs or feature requests in the project repository

---

**The Eden DevOps Suite regression testing framework ensures production readiness through comprehensive validation of all critical system aspects.**