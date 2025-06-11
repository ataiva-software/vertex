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
        // Enable compilation (removed disable flags)
        compilations.all {
            // Compilation is now enabled
        }
    }
    
    // Remove JavaScript target as this module has JVM-specific code
    
    // Native targets disabled for Docker builds
    // linuxX64()
    // macosX64()
    // macosArm64()
    // mingwX64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":shared:core"))
                api(project(":shared:auth"))
                api(project(":shared:crypto"))
                api(project(":shared:events"))
                api(project(":shared:config"))

                // Testing framework dependencies
                api(libs.bundles.testing.common)

                // Test data generation
                api(libs.uuid)
                api(libs.kotlinx.datetime)
            }
        }
        
        val jvmMain by getting {
            dependencies {
                api(project(":shared:database"))
                
                // Use explicit dependencies instead of bundle to avoid conflicts
                api("io.kotest:kotest-runner-junit5:5.7.2")
                api("io.kotest:kotest-assertions-core:5.7.2")
                api("io.kotest:kotest-assertions-core-jvm:5.7.2")
                api("io.kotest:kotest-property:5.7.2")
                api("io.mockk:mockk:1.13.8")
                api("org.testcontainers:testcontainers:1.19.3")
                api("org.testcontainers:postgresql:1.19.3")
                api("org.testcontainers:junit-jupiter:1.19.3")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                
                // Crypto testing utilities
                api(libs.bouncycastle)
            }
        }
        
        // Remove jsMain sourceSet as this module is now JVM-only
    }
}