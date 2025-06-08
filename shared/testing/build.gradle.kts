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
                api(libs.bundles.testing.jvm)
                
                // Crypto testing utilities
                api(libs.bouncycastle)
            }
        }
        
        // Remove jsMain sourceSet as this module is now JVM-only
    }
}