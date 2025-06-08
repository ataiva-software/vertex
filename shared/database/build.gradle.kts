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
    
    // JavaScript target disabled due to JVM-specific code in commonMain
    // js(IR) {
    //     browser {
    //         commonWebpackConfig {
    //             cssSupport {
    //                 enabled.set(true)
    //             }
    //         }
    //     }
    //     nodejs()
    // }
    
    // Native targets disabled for Docker builds
    // linuxX64()
    // macosX64()
    // macosArm64()
    // mingwX64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":shared:core"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(libs.postgresql)
                implementation(libs.hikaricp)
                implementation("org.flywaydb:flyway-core:10.22.0")
                implementation("org.flywaydb:flyway-database-postgresql:10.22.0")
                implementation(libs.exposed.core)
                implementation(libs.exposed.dao)
                implementation(libs.exposed.jdbc)
                implementation(libs.exposed.kotlin.datetime)
                implementation(libs.exposed.json)
                // kotlinx-coroutines-core already provides JVM support
            }
        }
        
        // JavaScript target disabled
        // val jsMain by getting {
        //     dependencies {
        //         // For future web-based database operations
        //     }
        // }
    }
}