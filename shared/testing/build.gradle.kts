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
                api(project(":shared:auth"))
                api(project(":shared:crypto"))
                api(project(":shared:database"))
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
                api(libs.bundles.testing.jvm)
                
                // Crypto testing utilities
                api(libs.bouncycastle)
            }
        }
        
        val jsMain by getting {
            dependencies {
                api(libs.kotest.framework.engine.js)
            }
        }
    }
}