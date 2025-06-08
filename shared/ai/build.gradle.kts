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
    
    // Remove JavaScript target as this module has JVM-specific dependencies
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:core"))
                implementation(project(":shared:events"))
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.6")
                
                // Math and statistics
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
                // JVM-only dependencies
                implementation(project(":shared:monitoring"))
                implementation(project(":shared:analytics"))
                implementation(project(":shared:cloud"))
                
                // Deep Learning and Neural Networks
                implementation("org.deeplearning4j:deeplearning4j-core:1.0.0-M2.1")
                implementation("org.nd4j:nd4j-native-platform:1.0.0-M2.1")
                implementation("org.deeplearning4j:deeplearning4j-nn:1.0.0-M2.1")
                implementation("org.deeplearning4j:deeplearning4j-nlp:1.0.0-M2.1")
                
                // Machine Learning
                implementation("org.apache.commons:commons-math3:3.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-statistics-jvm:0.2.1")
                implementation("smile:smile-core:3.0.2")
                implementation("smile:smile-nlp:3.0.2")
                implementation("smile:smile-plot:3.0.2")
                
                // Computer Vision
                implementation("org.openpnp:opencv:4.6.0-0")
                
                // Natural Language Processing
                implementation("edu.stanford.nlp:stanford-corenlp:4.5.1")
                implementation("edu.stanford.nlp:stanford-corenlp:4.5.1:models")
                
                // Reinforcement Learning
                implementation("org.apache.commons:commons-math3:3.6.1")
                
                // Time series and forecasting
                implementation("com.github.signaflo:timeseries:0.4")
                
                // Data processing and manipulation
                implementation("org.jetbrains.kotlinx:dataframe:0.12.1")
                
                // HTTP client for external AI services
                implementation("io.ktor:ktor-client-cio:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
                
                // Model serialization and persistence
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.0")
                
                // Distributed computing
                implementation("org.apache.spark:spark-core_2.13:3.5.0")
                implementation("org.apache.spark:spark-mllib_2.13:3.5.0")
                
                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.11")
                implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
                implementation("io.mockk:mockk:1.13.8")
                implementation("org.testcontainers:testcontainers:1.19.1")
                implementation("org.testcontainers:junit-jupiter:1.19.1")
            }
        }
        
        // Remove jsMain sourceSet as this module is now JVM-only
    }
}