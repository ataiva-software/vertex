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
                // Additional core dependencies
                implementation(libs.uuid)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
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
                implementation("com.auth0:java-jwt:4.4.0")
                implementation("org.mindrot:jbcrypt:0.4")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation(npm("crypto-js", "4.2.0"))
                implementation(npm("bcryptjs", "2.4.3"))
            }
        }
    }
}