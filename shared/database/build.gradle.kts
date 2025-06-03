plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

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
    
    // Native targets disabled for Docker builds
    // linuxX64()
    // macosX64()
    // macosArm64()
    // mingwX64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":shared:core"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.postgresql:postgresql:42.6.0")
                implementation("com.zaxxer:HikariCP:5.0.1")
                implementation("org.flywaydb:flyway-core:10.22.0")
                implementation("org.flywaydb:flyway-database-postgresql:10.22.0")
                implementation("org.jetbrains.exposed:exposed-core:0.44.1")
                implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
                implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
                implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.44.1")
                implementation("org.jetbrains.exposed:exposed-json:0.44.1")
            }
        }
        
        val jsMain by getting {
            dependencies {
                // For future web-based database operations
            }
        }
    }
}