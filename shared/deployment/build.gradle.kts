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
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:core"))
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                
                // HTTP client for API calls
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
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
                // JVM-only dependencies
                // Temporarily disabled due to build issues
                // implementation(project(":shared:monitoring"))
                
                // Kubernetes client
                implementation("io.fabric8:kubernetes-client:6.9.2")
                implementation("io.fabric8:kubernetes-server-mock:6.9.2")
                
                // Docker client
                implementation("com.github.docker-java:docker-java:3.3.4")
                implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")
                
                // HTTP client
                implementation("io.ktor:ktor-client-cio:2.3.5")
                
                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.11")
                
                // YAML processing for Kubernetes manifests
                implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
                implementation("org.testcontainers:junit-jupiter:1.19.1")
                implementation("org.testcontainers:k3s:1.19.1")
                implementation("io.mockk:mockk:1.13.8")
            }
        }
        
    }
}