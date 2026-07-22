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
        // This is the 2025.x legacy line (PhpStorm 2025.1 - 2025.3, builds 251 - 253).
        //
        // Why it exists: the 0.7.0 runtime stack (ktor 3.5 / MCP SDK 0.13 / Kotlin 2.3, built
        // against 2026.1.3) fails to start the embedded MCP server on build 253. This branch
        // keeps the older stack (ktor 3.2.3 / MCP SDK 0.9 / Kotlin 2.1) that does work there.
        //
        // Why the ceiling is CLOSED here while main's is open: Marketplace serves the highest
        // *version* among builds compatible with the user's IDE. Without a hard 253.* ceiling a
        // 2025.x IDE would also match the newer line and be served the build that cannot start.
        // The ranges must partition; this end is the one that has to be explicit.
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "253.*"
        }
    }

    // We build against 2025.3 (the ceiling) because that is the artifact verified by hand via
    // MCP Inspector — rebuilding against the 251 floor would ship something nobody tested.
    // The floor is enforced here instead: verifyPlugin checks our bytecode against 251 so a
    // 252/253-only platform API cannot silently break 2025.1 at runtime.
    //
    // Limitation, learned the hard way on 0.7.0: this only checks OUR bytecode against the
    // platform API. It never loads a class or exercises the bundled libraries, so a "Compatible"
    // result does NOT mean the plugin starts. Only launching the IDE proves that.
    pluginVerification {
        ides {
            select {
                types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm)
                channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                sinceBuild = "251"
                untilBuild = "251.*"
            }
        }
    }
}

// One launchable IDE per build in our supported range, because verifyPlugin cannot answer the
// question that actually matters. It checks our bytecode against the platform API and reports
// "Compatible" without ever loading a class or touching the bundled ktor/coroutines/MCP-SDK stack
// -- which is exactly how 0.7.0 shipped green and then failed to bind its server on 253.
//
// So: green verifyPlugin is necessary, launching is sufficient. Before widening sinceBuild or
// cutting a release, run each of these and confirm the MCP server actually reaches "Running".
//
// Each task gets its own sandbox (below), so caches/config from one build never mask a problem
// in another -- a shared sandbox can hide a failure that only occurs on a first-run install.
intellijPlatformTesting {
    runIde {
        register("runIde251") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm
            version = "2025.1"
            sandboxDirectory = layout.buildDirectory.dir("idea-sandbox-251")
        }
        register("runIde252") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm
            version = "2025.2"
            sandboxDirectory = layout.buildDirectory.dir("idea-sandbox-252")
        }
        register("runIde253") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm
            version = "2025.3"
            sandboxDirectory = layout.buildDirectory.dir("idea-sandbox-253")
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
