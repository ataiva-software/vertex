plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    js(IR) {
        nodejs()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:core"))
                implementation(project(":shared:auth"))
                implementation(project(":shared:crypto"))
                implementation(project(":shared:database"))
                implementation(project(":shared:events"))
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:2.3.5")
                implementation("ch.qos.logback:logback-classic:1.4.11")
                implementation("org.fusesource.jansi:jansi:2.4.0") // For colored console output
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:2.3.5")
            }
        }
    }
}

application {
    mainClass.set("com.ataiva.eden.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    args = project.findProperty("args")?.toString()?.split(" ") ?: emptyList()
}

// Create executable JAR
tasks.register<Jar>("executableJar") {
    dependsOn("jvmMainClasses")
    archiveClassifier.set("executable")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "com.ataiva.eden.cli.MainKt"
    }
    
    from(kotlin.jvm().compilations["main"].output.allOutputs)
    from(configurations.getByName("jvmRuntimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
}

// Create native executable using GraalVM (optional)
tasks.register<Exec>("nativeCompile") {
    dependsOn("executableJar")
    
    val jarFile = tasks.getByName<Jar>("executableJar").archiveFile.get().asFile
    val outputFile = file("${buildDir}/native/eden")
    
    commandLine(
        "native-image",
        "--no-fallback",
        "--initialize-at-build-time",
        "--report-unsupported-elements-at-runtime",
        "-jar", jarFile.absolutePath,
        outputFile.absolutePath
    )
    
    doFirst {
        outputFile.parentFile.mkdirs()
    }
}

// Distribution tasks
tasks.named<Zip>("distZip") {
    dependsOn("executableJar")
    
    archiveBaseName.set("eden-cli")
    archiveVersion.set(project.version.toString())
    
    from(tasks.getByName<Jar>("executableJar").archiveFile) {
        rename { "eden.jar" }
    }
    
    from("src/main/scripts") {
        into("bin")
        fileMode = 0b111101101 // 755
    }
    
    from("README.md", "LICENSE")
}

tasks.named<Tar>("distTar") {
    dependsOn("executableJar")
    
    archiveBaseName.set("eden-cli")
    archiveVersion.set(project.version.toString())
    compression = Compression.GZIP
    
    from(tasks.getByName<Jar>("executableJar").archiveFile) {
        rename { "eden.jar" }
    }
    
    from("src/main/scripts") {
        into("bin")
        fileMode = 0b111101101 // 755
    }
    
    from("README.md", "LICENSE")
}