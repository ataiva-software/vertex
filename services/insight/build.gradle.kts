import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    application
}

group = "com.ataiva.eden"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

val exposedVersion = "0.41.1"
val ktorVersion = "2.3.0"
val kotlinxSerializationVersion = "1.5.0"
val hikariCpVersion = "5.0.1"
val postgresqlVersion = "42.6.0"
val flywayVersion = "9.16.0"
val logbackVersion = "1.4.7"
val junitVersion = "5.9.3"
val h2Version = "2.1.214"
val mockkVersion = "1.13.5"
val lettuceVersion = "6.2.3.RELEASE"
val caffeineVersion = "3.1.5"
val prometheusVersion = "0.16.0"

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    
    // Exposed (SQL framework)
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    
    // Database
    implementation("com.zaxxer:HikariCP:$hikariCpVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    
    // Ktor (HTTP server)
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    
    // Configuration
    implementation("com.typesafe:config:1.4.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.slf4j:slf4j-api:2.0.7")
    
    // Report Generation
    implementation("com.itextpdf:itext7-core:7.2.5")
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
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.h2database:h2:$h2Version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.0")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
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