plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val ktorVersion = "3.2.3"
val mcpSdkVersion = "0.9.0"

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get()
        )
        bundledPlugin("com.jetbrains.php")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
    }

    // MCP Kotlin SDK (server)
    implementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")

    // Ktor server (CIO = lightweight, pure Kotlin engine)
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // Testing
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup").get()
        name = providers.gradleProperty("pluginName").get()
        version = providers.gradleProperty("pluginVersion").get()
        // This is the 2025.3 maintenance line. It exists because the 0.7.0 runtime stack
        // (ktor 3.5 / MCP SDK 0.13 / Kotlin 2.3, built against 2026.1.3) fails to start the
        // embedded server on build 253 — see the 253 notes in CLAUDE.md. Keep the ceiling at
        // 253.* so Marketplace never serves this build to a 2026.1+ IDE, which gets 0.7.1+.
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.0"
    }
    test {
        useJUnitPlatform()
    }
}
