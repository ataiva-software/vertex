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
        // Tests are now enabled
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
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
                implementation("org.bouncycastle:bcpkix-jdk18on:1.76")
                implementation("de.mkammerer:argon2-jvm:2.11")
                implementation("org.mindrot:jbcrypt:0.4")
                implementation("commons-codec:commons-codec:1.15")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
                
                // Add Ktor dependencies for mTLS support
                implementation("io.ktor:ktor-server-core:2.3.7")
                implementation("io.ktor:ktor-server-netty:2.3.7")
                implementation("io.ktor:ktor-network-tls-certificates:2.3.7")
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-cio:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-client-logging:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
            }
            
            // Exclude problematic files from compilation
            kotlin.srcDir("src/jvmMain/kotlin")
            kotlin.exclude("**/mtls/**")
        }
        
        val jsMain by getting {
            dependencies {
                implementation(npm("crypto-js", "4.2.0"))
                implementation(npm("node-forge", "1.3.1"))
            }
        }
        
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(project(":shared:testing"))
                // Removed kotlin("test-junit") to avoid JUnit 4 vs JUnit 5 conflict
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.runner.junit5)
                implementation(libs.testcontainers.junit.jupiter)
                implementation(libs.mockk)
            }
        }
    }
}