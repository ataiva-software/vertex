plugins {
    kotlin("multiplatform") version "1.9.20" apply false
    kotlin("plugin.serialization") version "1.9.20" apply false
    kotlin("jvm") version "1.9.20" apply false
    id("org.jetbrains.compose") version "1.5.4" apply false
    id("io.ktor.plugin") version "2.3.5" apply false
}

allprojects {
    group = "com.ataiva.eden"
    version = "1.0.0-SNAPSHOT"
    
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers")
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }
    
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// Common dependencies for all projects
configure(subprojects.filter { it.name != "mobile" }) {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    
    kotlin {
        jvm {
            compilations.all {
                kotlinOptions.jvmTarget = "17"
            }
            withJava()
            testRuns["test"].executionTask.configure {
                useJUnitPlatform()
            }
        }
        
        js(IR) {
            browser {
                commonWebpackConfig {
                    cssSupport {
                        enabled.set(true)
                    }
                }
            }
            nodejs()
        }
        
        sourceSets {
            val commonMain by getting {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                    implementation("io.ktor:ktor-client-core:2.3.5")
                    implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
                    implementation("io.github.oshai:kotlin-logging:5.1.0")
                }
            }
            
            val commonTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                    implementation("io.kotest:kotest-framework-engine:5.7.2")
                    implementation("io.kotest:kotest-assertions-core:5.7.2")
                }
            }
            
            val jvmMain by getting {
                dependencies {
                    implementation("io.ktor:ktor-server-core:2.3.5")
                    implementation("io.ktor:ktor-server-netty:2.3.5")
                    implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
                    implementation("io.ktor:ktor-server-auth:2.3.5")
                    implementation("io.ktor:ktor-server-auth-jwt:2.3.5")
                    implementation("io.ktor:ktor-server-cors:2.3.5")
                    implementation("io.ktor:ktor-server-call-logging:2.3.5")
                    implementation("io.ktor:ktor-server-status-pages:2.3.5")
                    implementation("io.ktor:ktor-client-cio:2.3.5")
                    implementation("org.postgresql:postgresql:42.6.0")
                    implementation("com.zaxxer:HikariCP:5.0.1")
                    implementation("redis.clients:jedis:5.0.2")
                    implementation("ch.qos.logback:logback-classic:1.4.11")
                    implementation("org.flywaydb:flyway-core:9.22.3")
                    implementation("org.flywaydb:flyway-database-postgresql:9.22.3")
                }
            }
            
            val jvmTest by getting {
                dependencies {
                    implementation("io.ktor:ktor-server-tests:2.3.5")
                    implementation("io.ktor:ktor-client-mock:2.3.5")
                    implementation("org.testcontainers:testcontainers:1.19.1")
                    implementation("org.testcontainers:postgresql:1.19.1")
                    implementation("org.testcontainers:junit-jupiter:1.19.1")
                    implementation("io.mockk:mockk:1.13.8")
                }
            }
            
            val jsMain by getting {
                dependencies {
                    implementation("io.ktor:ktor-client-js:2.3.5")
                    implementation("org.jetbrains.kotlin-wrappers:kotlin-react:18.2.0-pre.467")
                    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:18.2.0-pre.467")
                    implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:11.11.1-pre.467")
                }
            }
        }
    }
}

// Configure CLI-specific native targets
configure(subprojects.filter { it.name == "cli" }) {
    kotlin {
        // Native targets for CLI
        linuxX64()
        macosX64()
        macosArm64()
        mingwX64()
        
        sourceSets {
            val nativeMain by creating {
                dependsOn(sourceSets["commonMain"])
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
                    implementation("com.squareup.okio:okio:3.6.0")
                }
            }
            
            val linuxX64Main by getting { dependsOn(nativeMain) }
            val macosX64Main by getting { dependsOn(nativeMain) }
            val macosArm64Main by getting { dependsOn(nativeMain) }
            val mingwX64Main by getting { dependsOn(nativeMain) }
        }
    }
}

tasks.register("setupDev") {
    description = "Set up development environment"
    group = "setup"
    
    doLast {
        println("Setting up Eden development environment...")
        println("1. Building shared libraries...")
        println("2. Setting up database...")
        println("3. Starting services...")
        println("Run 'docker-compose up -d' to start the development environment")
    }
}

tasks.register("cleanAll") {
    description = "Clean all projects"
    group = "build"
    
    dependsOn(subprojects.map { "${it.path}:clean" })
}

tasks.register("buildAll") {
    description = "Build all projects"
    group = "build"
    
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("testAll") {
    description = "Test all projects"
    group = "verification"
    
    dependsOn(subprojects.map { "${it.path}:test" })
}