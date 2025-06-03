plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("com.ataiva.eden.sync.ApplicationKt")
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:database"))
    implementation(project(":shared:events"))
    implementation(project(":shared:config"))
    
    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    
    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    
    // Redis
    implementation(libs.jedis)
    
    // Logging
    implementation(libs.logback.classic)
    
    // Cloud provider SDKs
    implementation("software.amazon.awssdk:ec2:2.21.29")
    implementation("com.google.cloud:google-cloud-compute:1.44.0")
    // TODO: Fix Azure SDK version - temporarily commented out
    // implementation("com.azure:azure-resourcemanager:2.19.0")
    
    // Testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)
}