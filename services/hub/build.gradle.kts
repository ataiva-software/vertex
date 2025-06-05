plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("com.ataiva.eden.hub.ApplicationKt")
    
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
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    
    // Database
    implementation(libs.bundles.database)
    
    // Redis
    implementation(libs.jedis)
    
    // Logging
    implementation(libs.bundles.logging)
    
    // Configuration
    implementation(libs.typesafe.config)
    
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