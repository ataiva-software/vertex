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
                
                // HTTP client for cloud APIs
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
                // AWS SDK
                implementation("aws.sdk.kotlin:aws-core:1.0.30")
                implementation("aws.sdk.kotlin:ec2:1.0.30")
                implementation("aws.sdk.kotlin:s3:1.0.30")
                implementation("aws.sdk.kotlin:cloudwatch:1.0.30")
                implementation("aws.sdk.kotlin:costexplorer:1.0.30")
                
                // Google Cloud SDK
                implementation("com.google.cloud:google-cloud-compute:1.45.0")
                implementation("com.google.cloud:google-cloud-storage:2.29.1")
                implementation("com.google.cloud:google-cloud-monitoring:3.32.0")
                implementation("com.google.cloud:google-cloud-billing:2.30.0")
                
                // Azure SDK
                implementation("com.azure:azure-core:1.45.1")
                implementation("com.azure:azure-identity:1.11.1")
                implementation("com.azure.resourcemanager:azure-resourcemanager:2.33.0")
                implementation("com.azure.resourcemanager:azure-resourcemanager-compute:2.33.0")
                implementation("com.azure.resourcemanager:azure-resourcemanager-storage:2.33.0")
                implementation("com.azure.resourcemanager:azure-resourcemanager-monitor:2.33.0")
                
                // Kubernetes client
                implementation("io.fabric8:kubernetes-client:6.9.2")
                
                // Docker client
                implementation("com.github.docker-java:docker-java:3.3.4")
                implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")
                
                // HTTP client
                implementation("io.ktor:ktor-client-cio:2.3.5")
                
                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.11")
                
                // JSON processing
                implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
                implementation("org.testcontainers:junit-jupiter:1.19.1")
                implementation("org.testcontainers:localstack:1.19.1")
                implementation("io.mockk:mockk:1.13.8")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:2.3.5")
            }
        }
    }
}