plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("com.ataiva.eden.flow.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared libraries
    implementation(project(":shared:core"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:crypto"))
    implementation(project(":shared:database"))
    implementation(project(":shared:events"))
    implementation(project(":shared:config"))
    
    // Ktor
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    
    // Database
    implementation(libs.bundles.database)
    
    // Redis
    implementation(libs.jedis)
    
    // Workflow engine
    implementation("org.yaml:snakeyaml:2.2")
    
    // Email
    implementation("com.sun.mail:javax.mail:1.6.2")
    implementation("javax.activation:activation:1.1.1")
    
    // Expression evaluation
    implementation("org.graalvm.js:js:22.3.0")
    implementation("org.graalvm.js:js-scriptengine:22.3.0")
    
    // Logging
    implementation(libs.bundles.logging)
    
    // Testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
}