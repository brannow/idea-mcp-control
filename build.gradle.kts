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
    // Held at 2.1 from when 2025.3 was the floor. With sinceBuild now 261 (bundled Kotlin 2.3)
    // this cap is no longer required, only conservative — nothing here needs a newer stdlib.
    // Kept deliberately so a lower cap never becomes the thing that breaks a backport to the
    // maintenance/2025.3 branch. Raise it only when there is an actual API you want.
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
        // 0.7.0 shipped with sinceBuild 253 and did not work there: the embedded MCP server
        // never bound on build 253 because the bundled runtime stack (ktor 3.5, MCP SDK 0.13,
        // kotlinx-coroutines 1.11, Kotlin stdlib 2.3) is a 2026.1 stack. verifyPlugin reported
        // "Compatible" because it only checks OUR bytecode against the platform API — it never
        // loads a class or exercises the bundled libraries, so this whole class of break is
        // invisible to it. 261 is therefore the honest floor: it matches what we build against.
        // 2025.3 users are served the 0.6.x maintenance line (branch maintenance/2025.3).
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "262.*"
        }
    }

    // Verify against the floor we actually claim. Note the limitation above: a green result here
    // says our platform API usage is fine, NOT that the plugin runs — only launching the IDE does.
    pluginVerification {
        ides {
            // Plugin 2.16 replaced ide(type, version) with select { }.
            select {
                types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm)
                channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                sinceBuild = "261"
                untilBuild = "261.*"
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
