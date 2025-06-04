import java.time.LocalDateTime

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
}

group = "com.ataiva.eden"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin and Coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // HTTP Client
    implementation("java.net.http:java.net.http:11")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.20")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.20")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    
    // Test Containers (for integration testing with real databases)
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    
    // Awaitility for async testing
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
    
    // MockK for mocking
    testImplementation("io.mockk:mockk:1.13.8")
    
    // AssertJ for fluent assertions
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
    
    // Test configuration
    maxHeapSize = "2g"
    jvmArgs = listOf(
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=200",
        "-Djunit.jupiter.execution.parallel.enabled=true",
        "-Djunit.jupiter.execution.parallel.mode.default=concurrent"
    )
    
    // Test execution settings
    systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
    systemProperty("junit.jupiter.execution.timeout.default", "10m")
    
    // Environment variables for tests
    environment("EDEN_TEST_MODE", "true")
    environment("EDEN_LOG_LEVEL", "INFO")
    
    // Test reports
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }
    
    // Test filtering
    useJUnitPlatform {
        includeTags("regression", "integration", "performance", "security")
        excludeTags("manual", "slow")
    }
    
    // Fail fast on first failure for quick feedback
    failFast = false
    
    // Test output
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}

// Task for running only regression tests
tasks.register<Test>("regressionTest") {
    description = "Runs comprehensive regression tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("regression")
    }
    
    // Regression tests need more time
    systemProperty("junit.jupiter.execution.timeout.default", "30m")
    
    // Generate detailed reports
    reports {
        html.required.set(true)
        html.outputLocation.set(file("$buildDir/reports/regression-tests"))
        junitXml.required.set(true)
        junitXml.outputLocation.set(file("$buildDir/test-results/regression-tests"))
    }
    
    // Custom test logging for regression tests
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
        showCauses = true
        showExceptions = true
        showStackTraces = true
        minGranularity = 2
    }
    
    // Generate summary report
    doLast {
        println("\n" + "=".repeat(80))
        println("REGRESSION TEST EXECUTION COMPLETED")
        println("=".repeat(80))
        println("Reports available at:")
        println("  HTML: ${reports.html.outputLocation.get()}/index.html")
        println("  XML:  ${reports.junitXml.outputLocation.get()}")
        println("=".repeat(80))
    }
}

// Task for running performance tests
tasks.register<Test>("performanceTest") {
    description = "Runs performance regression tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("performance")
    }
    
    // Performance tests need more memory and time
    maxHeapSize = "4g"
    systemProperty("junit.jupiter.execution.timeout.default", "20m")
    
    reports {
        html.outputLocation.set(file("$buildDir/reports/performance-tests"))
        junitXml.outputLocation.set(file("$buildDir/test-results/performance-tests"))
    }
}

// Task for running security tests
tasks.register<Test>("securityTest") {
    description = "Runs security regression tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("security")
    }
    
    systemProperty("junit.jupiter.execution.timeout.default", "15m")
    
    reports {
        html.outputLocation.set(file("$buildDir/reports/security-tests"))
        junitXml.outputLocation.set(file("$buildDir/test-results/security-tests"))
    }
}

// Task for running cross-service integration tests
tasks.register<Test>("crossServiceTest") {
    description = "Runs cross-service integration tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("cross-service")
    }
    
    systemProperty("junit.jupiter.execution.timeout.default", "25m")
    
    reports {
        html.outputLocation.set(file("$buildDir/reports/cross-service-tests"))
        junitXml.outputLocation.set(file("$buildDir/test-results/cross-service-tests"))
    }
}

// Task to run all regression test categories
tasks.register("allRegressionTests") {
    description = "Runs all regression test categories"
    group = "verification"
    
    dependsOn("regressionTest", "performanceTest", "securityTest", "crossServiceTest")
    
    doLast {
        println("\n" + "🎉".repeat(20))
        println("ALL REGRESSION TESTS COMPLETED SUCCESSFULLY!")
        println("Eden DevOps Suite is validated for production deployment.")
        println("🎉".repeat(20))
    }
}

// Task to generate comprehensive test report
tasks.register("generateTestReport") {
    description = "Generates comprehensive test report"
    group = "reporting"
    
    dependsOn("allRegressionTests")
    
    doLast {
        val reportDir = file("$buildDir/reports/comprehensive")
        reportDir.mkdirs()
        
        val reportFile = file("$reportDir/regression-test-summary.md")
        
        reportFile.writeText("""
# Eden DevOps Suite - Regression Test Summary

## Test Execution Overview

**Execution Date:** ${LocalDateTime.now()}
**Test Suite Version:** $version
**Total Test Categories:** 4

## Test Categories

### 1. Comprehensive Regression Tests
- **Purpose:** Validate all core functionality and cross-service integration
- **Coverage:** All services, API endpoints, business logic
- **Report:** [HTML Report](regression-tests/index.html)

### 2. Performance Regression Tests  
- **Purpose:** Ensure performance standards are maintained
- **Coverage:** Response times, throughput, memory usage, concurrent load
- **Report:** [HTML Report](performance-tests/index.html)

### 3. Security Regression Tests
- **Purpose:** Validate security controls and prevent security regressions
- **Coverage:** Authentication, authorization, input validation, encryption
- **Report:** [HTML Report](security-tests/index.html)

### 4. Cross-Service Integration Tests
- **Purpose:** Validate service-to-service communication and workflows
- **Coverage:** End-to-end workflows, data consistency, event handling
- **Report:** [HTML Report](cross-service-tests/index.html)

## Quality Gates

✅ **All regression tests must pass** (100% success rate required)
✅ **Performance benchmarks must be met** (95% of requests < 200ms)
✅ **Security controls must be validated** (No critical vulnerabilities)
✅ **Cross-service workflows must work** (End-to-end scenarios validated)

## Production Readiness Checklist

- [ ] All regression tests passing
- [ ] Performance benchmarks met
- [ ] Security validation complete
- [ ] Cross-service integration verified
- [ ] Documentation updated
- [ ] Deployment scripts tested

## Next Steps

1. Review any failed tests and address issues
2. Validate performance metrics meet requirements
3. Ensure security controls are properly configured
4. Verify all service dependencies are available
5. Proceed with production deployment

---

*This report was generated automatically by the Eden DevOps Suite regression testing framework.*
        """.trimIndent())
        
        println("Comprehensive test report generated: $reportFile")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
}

// Configure test execution order
tasks.named("crossServiceTest") {
    mustRunAfter("test")
}

tasks.named("performanceTest") {
    mustRunAfter("crossServiceTest")
}

tasks.named("securityTest") {
    mustRunAfter("performanceTest")
}

tasks.named("regressionTest") {
    mustRunAfter("securityTest")
}