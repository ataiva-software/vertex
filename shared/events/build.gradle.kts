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
                implementation("redis.clients:jedis:5.0.2")
                implementation("io.nats:jnats:2.16.14")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation(npm("redis", "4.6.10"))
            }
        }
    }
}