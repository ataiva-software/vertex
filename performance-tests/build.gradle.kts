import java.time.Duration

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Shared libraries
    implementation(project(":shared:core"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:crypto"))
    implementation(project(":shared:database"))
    implementation(project(":shared:events"))
    implementation(project(":shared:config"))
    implementation(project(":shared:testing"))
    
    // Services for performance testing
    implementation(project(":services:api-gateway"))
    implementation(project(":services:vault"))
    implementation(project(":services:flow"))
    implementation(project(":services:task"))
    implementation(project(":services:monitor"))
    implementation(project(":services:sync"))
    implementation(project(":services:insight"))
    implementation(project(":services:hub"))
    
    // Gatling for load testing
    testImplementation("io.gatling.highcharts:gatling-charts-highcharts:3.9.5")
    testImplementation("io.gatling:gatling-test-framework:3.9.5")
    
    // JMH for micro-benchmarks
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    
    // HTTP client for API performance testing
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.auth)
    
    // Database performance testing
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    // Redis testing will use regular testcontainers with generic container
    testImplementation(libs.testcontainers.junit.jupiter)
    
    // Metrics and monitoring
    testImplementation("io.micrometer:micrometer-core:1.12.0")
    testImplementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
    // Testing frameworks
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test.junit)
    
    // Coroutines for concurrent testing
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    
    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    
    // JSON processing
    implementation(libs.kotlinx.serialization.json)
}

tasks.test {
    useJUnitPlatform()
    
    // Set system properties for performance tests
    systemProperty("testcontainers.reuse.enable", "true")
    systemProperty("testcontainers.ryuk.disabled", "false")
    
    // Configure test execution for performance
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    
    // Increase timeout for performance tests
    timeout.set(Duration.ofMinutes(30))
    
    // Run performance tests sequentially
    maxParallelForks = 1
    
    // Allocate more memory for performance tests
    minHeapSize = "1g"
    maxHeapSize = "4g"
    
    // JVM options for performance testing
    jvmArgs = listOf(
        "-XX:+UseG1GC",
        "-XX:+UseStringDeduplication",
        "-XX:MaxGCPauseMillis=200",
        "-Djava.awt.headless=true"
    )
}

// Custom task for running crypto performance tests
tasks.register<Test>("testCryptoPerformance") {
    description = "Run cryptographic operations performance tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("crypto-performance")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Custom task for running API load tests
tasks.register<Test>("testApiLoad") {
    description = "Run API endpoint load tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("api-load")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Custom task for running database performance tests
tasks.register<Test>("testDatabasePerformance") {
    description = "Run database performance tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("database-performance")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Custom task for running concurrent user simulation
tasks.register<Test>("testConcurrentUsers") {
    description = "Run concurrent user simulation tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("concurrent-users")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Task for running JMH benchmarks
tasks.register<JavaExec>("runBenchmarks") {
    description = "Run JMH micro-benchmarks"
    group = "verification"
    
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    
    args = listOf(
        "-rf", "json",
        "-rff", "build/reports/benchmarks/results.json"
    )
}

// Task for generating performance reports
tasks.register("generatePerformanceReport") {
    description = "Generate performance test report"
    group = "verification"
    
    dependsOn("test", "runBenchmarks")
    
    doLast {
        println("📊 Performance test report generated in build/reports/")
        println("📈 Benchmark results available in build/reports/benchmarks/")
    }
}

// Task for running Gatling load tests
tasks.register<JavaExec>("runGatlingTests") {
    description = "Run Gatling load tests"
    group = "verification"
    
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.gatling.app.Gatling")
    
    args = listOf(
        "--simulations-folder", "src/test/kotlin/gatling",
        "--resources-folder", "src/test/resources",
        "--results-folder", "build/reports/gatling"
    )
}

// Task for stress testing
tasks.register<Test>("stressTest") {
    description = "Run stress tests with high load"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("stress-test")
    }
    
    // Increase resources for stress testing
    minHeapSize = "2g"
    maxHeapSize = "8g"
    
    systemProperty("stress.test.enabled", "true")
    systemProperty("stress.test.duration", "300") // 5 minutes
    systemProperty("stress.test.concurrent.users", "1000")
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Task for endurance testing
tasks.register<Test>("enduranceTest") {
    description = "Run endurance tests for extended periods"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("endurance-test")
    }
    
    // Very long timeout for endurance tests
    timeout.set(Duration.ofHours(2))
    
    systemProperty("endurance.test.enabled", "true")
    systemProperty("endurance.test.duration", "3600") // 1 hour
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}