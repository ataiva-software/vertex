plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:config"))
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Define versions
    val ktorVersion = "2.3.5"
    
    // OpenTelemetry API only - we'll use stubs for the rest
    api("io.opentelemetry:opentelemetry-api:1.31.0")
    
    // Ktor dependencies
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    
    // Date/Time libraries
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    
    // Micrometer bridge for compatibility with Spring Boot apps
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.5")
    
    // Additional dependencies for monitoring
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("io.mockk:mockk:1.13.8")
}