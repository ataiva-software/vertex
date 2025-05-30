plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
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
                implementation("org.flywaydb:flyway-core:9.22.3")
                implementation("org.flywaydb:flyway-database-postgresql:9.22.3")
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