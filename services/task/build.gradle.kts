plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("com.ataiva.eden.task.ApplicationKt")
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:crypto"))
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
    
    // Email
    implementation("com.sun.mail:javax.mail:1.6.2")
    implementation("javax.activation:activation:1.1.1")
    
    // Logging
    implementation(libs.logback.classic)
    
    // Testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)
}