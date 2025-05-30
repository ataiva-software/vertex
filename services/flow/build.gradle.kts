plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("com.ataiva.eden.flow.ApplicationKt")
    
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
    
    // Ktor
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-cio-jvm")
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-dao")
    implementation("org.jetbrains.exposed:exposed-jdbc")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime")
    implementation("org.jetbrains.exposed:exposed-json")
    
    // Redis
    implementation("redis.clients:jedis")
    
    // Workflow engine
    implementation("org.yaml:snakeyaml:2.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic")
    implementation("io.github.oshai:kotlin-logging-jvm")
    
    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("io.mockk:mockk")
}