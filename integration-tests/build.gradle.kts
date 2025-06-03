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
    implementation(project(":shared:monitoring"))
    implementation(project(":shared:deployment"))
    implementation(project(":shared:analytics"))
    implementation(project(":shared:cloud"))
    
    // Service dependencies for integration testing
    implementation(project(":services:api-gateway"))
    
    // Database integration
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)
    
    // Redis integration
    implementation(libs.jedis)
    
    // Testcontainers for integration testing
    implementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    // Redis testing will use regular testcontainers with generic container
    
    // Ktor testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    
    // Testing frameworks
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test.junit)
    
    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
}

tasks.test {
    useJUnitPlatform()
    
    // Set system properties for integration tests
    systemProperty("testcontainers.reuse.enable", "true")
    systemProperty("testcontainers.ryuk.disabled", "false")
    
    // Configure test execution
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    
    // Increase timeout for integration tests
    timeout.set(Duration.ofMinutes(10))
    
    // Run integration tests in parallel
    maxParallelForks = Runtime.getRuntime().availableProcessors()
}

// Custom task for running only database integration tests
tasks.register<Test>("testDatabase") {
    description = "Run database integration tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("database")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Custom task for running only event system integration tests
tasks.register<Test>("testEvents") {
    description = "Run event system integration tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("events")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Custom task for running service communication tests
tasks.register<Test>("testServiceCommunication") {
    description = "Run service-to-service communication tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("service-communication")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}