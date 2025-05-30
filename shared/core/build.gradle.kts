plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Additional core dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-uuid:0.0.22")
                implementation("com.benasher44:uuid:0.8.2")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.bouncycastle:bcprov-jdk18on:1.76")
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