import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version libs.versions.ktor.get()
    application
}

group = "com.ataiva.eden"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

val flywayVersion = "9.16.0"
val h2Version = "2.1.214"
val lettuceVersion = "6.2.3.RELEASE"
val caffeineVersion = "3.1.5"
val prometheusVersion = "0.16.0"

dependencies {
    // Shared libraries
    implementation(project(":shared:core"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:crypto"))
    implementation(project(":shared:database"))
    implementation(project(":shared:events"))
    implementation(project(":shared:config"))
    
    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Exposed (SQL framework)
    implementation(libs.bundles.database)
    implementation("org.jetbrains.exposed:exposed-java-time:${libs.versions.exposed.get()}")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    
    // Ktor (HTTP server)
    implementation(libs.bundles.ktor.server)
    
    // Additional Ktor server plugins
    implementation("io.ktor:ktor-server-call-logging:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-default-headers:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-compression:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-forwarded-header:${libs.versions.ktor.get()}")
    
    // Configuration
    implementation(libs.typesafe.config)
    
    // Logging
    implementation(libs.bundles.logging)
    
    // Report Generation
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.itextpdf:layout:7.2.5")
    implementation("com.itextpdf:kernel:7.2.5")
    implementation("com.itextpdf:io:7.2.5")
    implementation("com.itextpdf:commons:7.2.5")
    implementation("com.itextpdf:styled-xml-parser:7.2.5")
    implementation("com.itextpdf:svg:7.2.5")
    implementation("com.itextpdf:forms:7.2.5")
    implementation("com.itextpdf:pdfa:7.2.5")
    implementation("com.itextpdf:sign:7.2.5")
    implementation("com.itextpdf:barcodes:7.2.5")
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("org.jfree:jfreechart:1.5.4")
    implementation("com.cronutils:cron-utils:9.2.0")
    implementation("com.sun.mail:javax.mail:1.6.2")
    
    // Caching
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")
    
    // Metrics and monitoring
    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_httpserver:$prometheusVersion")
    implementation("io.prometheus:simpleclient_pushgateway:$prometheusVersion")
    
    // Testing
    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.testing.jvm)
    testImplementation("com.h2database:h2:$h2Version")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.tests)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("com.ataiva.eden.insight.InsightApplicationKt")
}

// Task to run database migrations
tasks.register("migrateDatabase") {
    group = "database"
    description = "Runs database migrations"
    
    doLast {
        // This would be implemented to run Flyway migrations
        println("Running database migrations...")
    }
}

// Task to generate sample data
tasks.register("generateSampleData") {
    group = "database"
    description = "Generates sample data for development"
    
    doLast {
        println("Generating sample data...")
    }
}

// Configure JAR task
tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.ataiva.eden.insight.InsightApplicationKt"
    }
    
    // Include all dependencies in the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}