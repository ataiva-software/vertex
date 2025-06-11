plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("com.ataiva.eden.gateway.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared libraries
    implementation(project(":shared:core"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:crypto"))
    implementation(project(":shared:database"))
    implementation(project(":shared:events"))
    implementation(project(":shared:config"))
    
    // Ktor server and client
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    
    // Additional Ktor server plugins
    implementation("io.ktor:ktor-server-forwarded-header-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-http-redirect-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-hsts-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-compression-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-default-headers-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-call-id-jvm:${libs.versions.ktor.get()}")
    
    // Micrometer and Prometheus for metrics
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:${libs.versions.ktor.get()}")
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.5")
    
    // Database
    implementation(libs.bundles.database)
    
    // Redis
    implementation(libs.jedis)
    
    // Logging
    implementation(libs.bundles.logging)
    
    // Configuration
    implementation(libs.typesafe.config)
    
    // Testing
    testImplementation(project(":shared:testing"))
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation(libs.kotlinx.coroutines.test)
}