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
                implementation("com.auth0:java-jwt:4.4.0")
                implementation("org.mindrot:jbcrypt:0.4")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation(npm("bcryptjs", "2.4.3"))
                implementation(npm("jsonwebtoken", "9.0.2"))
            }
        }
    }
}