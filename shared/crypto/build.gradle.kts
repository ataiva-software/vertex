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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(project(":shared:testing"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
                implementation("org.bouncycastle:bcpkix-jdk18on:1.76")
                implementation("de.mkammerer:argon2-jvm:2.11")
                // kotlinx-coroutines-core already provides JVM support
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation(npm("crypto-js", "4.2.0"))
                implementation(npm("node-forge", "1.3.1"))
            }
        }
    }
}