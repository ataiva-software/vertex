plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    application
}

group = "com.ataiva.eden"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.6")
    implementation("io.ktor:ktor-server-netty:2.3.6")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.6")
    implementation("io.ktor:ktor-server-cors:2.3.6")
    implementation("io.ktor:ktor-server-call-logging:2.3.6")
    implementation("io.ktor:ktor-server-status-pages:2.3.6")
    implementation("io.ktor:ktor-server-auth:2.3.6")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.6")
    
    // Ktor Client (for external API calls)
    implementation("io.ktor:ktor-client-core:2.3.6")
    implementation("io.ktor:ktor-client-cio:2.3.6")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-client-logging:2.3.6")
    implementation("io.ktor:ktor-client-auth:2.3.6")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.44.1")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.postgresql:postgresql:42.6.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    
    // Configuration
    implementation("com.typesafe:config:1.4.3")
    
    // DateTime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    
    // Encryption/Security
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("com.auth0:java-jwt:4.4.0")
    
    // HTTP Client for webhooks
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Email
    implementation("org.simplejavamail:simple-java-mail:8.3.1")
    
    // AWS SDK (for AWS connector)
    implementation("aws.sdk.kotlin:aws-core:0.33.1-beta")
    implementation("aws.sdk.kotlin:ec2:0.33.1-beta")
    implementation("aws.sdk.kotlin:s3:0.33.1-beta")
    implementation("aws.sdk.kotlin:lambda:0.33.1-beta")
    
    // Testing
    testImplementation("io.ktor:ktor-server-tests:2.3.6")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.20")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("com.h2database:h2:2.2.224")
}

application {
    mainClass.set("com.ataiva.eden.hub.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
}

// Task to run integration tests
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("integration")
    }
    
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    
    shouldRunAfter("test")
}

// Task to run all tests
tasks.register("testAll") {
    description = "Runs all tests including integration tests"
    group = "verification"
    
    dependsOn("test", "integrationTest")
}