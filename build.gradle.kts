plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
    // We build against the newest platform but keep compatibility down to 2025.3 (sinceBuild 253),
    // whose bundled Kotlin is older. Cap the API/language version to 2.1 so we never call stdlib
    // APIs that are missing at runtime on the oldest supported IDE. Raise this only if we also
    // raise sinceBuild.
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val ktorVersion = "3.5.0"
val mcpSdkVersion = "0.13.0"

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
    testImplementation("io.mockk:mockk:1.14.11")
    // IntelliJ's test-framework base classes (UsefulTestCase, etc.) extend JUnit 4's TestCase, so
    // JUnit 4 must be on the test classpath even though our tests are JUnit 5. Platform 2026.1.3 +
    // Gradle plugin 2.16 no longer pull it transitively, so we declare it explicitly.
    testImplementation("junit:junit:4.13.2")
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
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "261.*"
        }
    }

    // We build against the newest platform but support down to 2025.3. Verify binary compatibility
    // against the floor so a 261-only platform API signature can't silently break 2025.3 at runtime.
    pluginVerification {
        ides {
            // Plugin 2.16 replaced ide(type, version) with select { }. Pick PhpStorm release builds
            // at the 253 floor so we validate binary compatibility against the oldest IDE we claim.
            select {
                types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm)
                channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                sinceBuild = "253"
                untilBuild = "253.*"
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.5.1"
    }
    test {
        useJUnitPlatform()
    }
}
