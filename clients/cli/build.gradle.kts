plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    // Native targets for CLI
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:core"))
                implementation(project(":shared:auth"))
                implementation(project(":shared:crypto"))
                implementation(project(":shared:config"))
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
                implementation("com.squareup.okio:okio:3.6.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-curl:2.3.5")
            }
        }
        
        val linuxX64Main by getting { dependsOn(nativeMain) }
        val macosX64Main by getting { dependsOn(nativeMain) }
        val macosArm64Main by getting { dependsOn(nativeMain) }
        val mingwX64Main by getting { dependsOn(nativeMain) }
    }
}

// Configure native binaries
kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries {
        executable {
            entryPoint = "com.ataiva.eden.cli.main"
            baseName = "eden"
        }
    }
}