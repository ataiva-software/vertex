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
                implementation("org.bouncycastle:bcprov-jdk18on:1.76")
                implementation("org.bouncycastle:bcpkix-jdk18on:1.76")
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