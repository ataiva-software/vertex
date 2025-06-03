plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    js(IR) {
        nodejs()
        browser()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:core"))
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                
                // Metrics and monitoring
                implementation("io.micrometer:micrometer-core:1.11.5")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation(project(":shared:testing"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                // JVM-specific monitoring libraries
                implementation("io.micrometer:micrometer-registry-prometheus:1.11.5")
                implementation("io.micrometer:micrometer-registry-jmx:1.11.5")
                implementation("ch.qos.logback:logback-classic:1.4.11")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
                implementation("org.testcontainers:junit-jupiter:1.19.1")
                implementation("org.testcontainers:postgresql:1.19.1")
            }
        }
        
        val jsMain by getting {
            dependencies {
                // JS-specific monitoring (browser APIs, etc.)
            }
        }
    }
}