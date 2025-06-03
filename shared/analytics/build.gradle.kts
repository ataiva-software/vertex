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
                implementation(project(":shared:monitoring"))
                implementation(project(":shared:deployment"))
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                
                // Math and statistics libraries
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
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
                // Machine learning and analytics libraries
                implementation("org.apache.commons:commons-math3:3.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-statistics-jvm:0.2.1")
                
                // Time series analysis
                implementation("com.github.signaflo:timeseries:0.4")
                
                // Data processing
                implementation("org.jetbrains.kotlinx:dataframe:0.12.1")
                
                // HTTP client for external ML services
                implementation("io.ktor:ktor-client-cio:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
                
                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.11")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
                implementation("io.mockk:mockk:1.13.8")
            }
        }
        
        val jsMain by getting {
            dependencies {
                // JS-specific analytics (browser APIs, charting libraries)
                implementation("io.ktor:ktor-client-js:2.3.5")
            }
        }
    }
}