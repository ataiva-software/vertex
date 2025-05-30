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
    
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-cio-jvm")
    
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-dao")
    implementation("org.jetbrains.exposed:exposed-jdbc")
    implementation("redis.clients:jedis")
    implementation("ch.qos.logback:logback-classic")
    
    // Cloud provider SDKs
    implementation("software.amazon.awssdk:ec2:2.21.29")
    implementation("com.google.cloud:google-cloud-compute:1.44.0")
    implementation("com.azure:azure-resourcemanager:2.32.0")
    
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}