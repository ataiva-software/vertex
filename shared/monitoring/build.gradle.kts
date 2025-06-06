plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:config"))
    
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    
    // OpenTelemetry
    val openTelemetryVersion = "1.31.0"
    val openTelemetryAlphaVersion = "1.31.0-alpha"
    
    // OpenTelemetry API and SDK
    api("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$openTelemetryVersion")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:$openTelemetryVersion")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:$openTelemetryAlphaVersion")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:$openTelemetryAlphaVersion")
    
    // OpenTelemetry exporters
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$openTelemetryVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-prometheus:$openTelemetryAlphaVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-logging:$openTelemetryVersion")
    
    // OpenTelemetry instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:$openTelemetryAlphaVersion")
    
    // Ktor instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:$openTelemetryAlphaVersion")
    
    // JDBC instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:$openTelemetryAlphaVersion")
    
    // Redis instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-jedis-3.0:$openTelemetryAlphaVersion")
    
    // Micrometer bridge for compatibility with Spring Boot apps
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.5")
    implementation("io.opentelemetry.instrumentation:opentelemetry-micrometer-1.0:$openTelemetryAlphaVersion")
    
    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}