import io.gatling.gradle.GatlingPlugin

plugins {
    scala
    id("io.gatling.gradle") version "3.9.3.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.10")
    
    // Gatling dependencies
    gatling("io.gatling:gatling-app:3.9.3")
    gatling("io.gatling:gatling-http:3.9.3")
    gatling("io.gatling:gatling-core:3.9.3")
    gatling("io.gatling:gatling-charts:3.9.3")
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.9.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.7")
}

gatling {
    // Gatling version
    toolVersion = "3.9.3"
    
    // Simulations directory
    simulations {
        include("**/*LoadTest.scala")
    }
    
    // Log level
    logLevel = "WARN"
    
    // Enable JVM arguments
    jvmArgs = listOf(
        "-server",
        "-Xms1g",
        "-Xmx2g",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=30",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:+ParallelRefProcEnabled",
        "-XX:+UseStringDeduplication"
    )
}

// Task to run all load tests
tasks.register<io.gatling.gradle.GatlingRunTask>("runLoadTests") {
    group = "Load Testing"
    description = "Run all load tests"
    
    simulations = listOf("com.ataiva.eden.performance.InsightServiceLoadTest".toString())
}

// Task to run load tests with specific parameters
tasks.register<io.gatling.gradle.GatlingRunTask>("runCustomLoadTest") {
    group = "Load Testing"
    description = "Run load tests with custom parameters"
    
    simulations = listOf("com.ataiva.eden.performance.InsightServiceLoadTest".toString())
    
    // These can be overridden with -P command line arguments
    val baseUrl = project.findProperty("baseUrl") ?: "http://localhost:8080"
    val duration = project.findProperty("duration") ?: "5"
    val rampUp = project.findProperty("rampUp") ?: "1"
    val users = project.findProperty("users") ?: "1000"
    val constantUsers = project.findProperty("constantUsers") ?: "100"
    
    systemProperties = mapOf(
        "baseUrl" to baseUrl,
        "duration" to duration,
        "rampUp" to rampUp,
        "users" to users,
        "constantUsers" to constantUsers
    )
}