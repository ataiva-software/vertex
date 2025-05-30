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
                implementation("com.typesafe:config:1.4.3")
                implementation("org.yaml:snakeyaml:2.2")
            }
        }
        
        val jsMain by getting {
            dependencies {
                // For web-based configuration
            }
        }
    }
}