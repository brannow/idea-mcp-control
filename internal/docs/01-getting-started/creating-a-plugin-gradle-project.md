# Creating a Plugin Gradle Project﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/tutorials/build_system/creating_plugin_project.md) Last modified: 09 January 2026

This documentation page describes a Gradle-based plugin project generated with the [New Project Wizard](https://www.jetbrains.com/help/idea/new-project-wizard.html) in IntelliJ IDEA, but the project generated with [IntelliJ Platform Plugin Template](https://plugins.jetbrains.com/docs/intellij/plugin-github-template.html) covers all the described files and directories.

## Creating a Plugin with New Project Wizard﻿

To enable the IDE Plugin wizard, make sure Gradle and Plugin DevKit plugins are installed and enabled.

> ### warning
>
> Installing Plugin DevKit plugin
>
> Plugin DevKit plugin must be installed from JetBrains Marketplace ( [Plugin Homepage](https://plugins.jetbrains.com/plugin/22851-plugin-devkit)) as it is not bundled since version 2023.3.

### Create IDE Plugin﻿

Launch the New Project wizard via the File \| New \| Project... action and follow these steps:

1. Select the IDE Plugin type from the list on the left.

2. Specify the project Name and Location.

3. Choose the Plugin option in the project Type.

4. Only in IntelliJ IDEA older than 2023.1:

Choose the Language the plugin will use for implementation. For this example select the Kotlin option. See also [Kotlin for Plugin Developers](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html) for more information.


> ### note
>
> Using Kotlin and Java sources
>
> Projects generated with IntelliJ IDEA 2023.1 or newer support both Kotlin and Java sources out of the box. The wizard automatically creates the $PLUGIN\_DIR$/src/main/kotlin sources directory. To add Java sources, add the $PLUGIN\_DIR$/src/main/java directory manually.

5. Provide the Group which is typically an inverted company domain (e.g. `com.example.mycompany`). It is used for the Gradle property `project.group` value in the project's Gradle build script.

6. Provide the Artifact which is the default name of the build project artifact (without the version). It is also used for the Gradle property `rootProject.name` value in the project's settings.gradle.kts file. For this example, enter `my_plugin`.

7. Select a JDK matching the required Java version. It will be the default JRE used to run Gradle, and the JDK used to compile the plugin sources.


> ### note
>
> IDE and Java Versions
>
> Java version must be set depending on the target [platform version](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#platformVersions).
>
> 2024.2+
>
> Java 21
>
> 2022.3+
>
> Java 17

8. Click the Create button to generate the project.


### Components of a Wizard-Generated Gradle IntelliJ Platform Plugin﻿

For the `my_plugin` example created with the steps described above, the following directory content is created:

my\_plugin.runRun IDE with Plugin.run.xmlgradlewrappergradle-wrapper.jargradle-wrapper.propertiessrcmainkotlinresourcesMETA-INFplugin.xmlpluginIcon.svg.gitignorebuild.gradle.ktsgradle.propertiesgradlewgradlew.batsettings.gradle.kts

- The default IntelliJ Platform build.gradle.kts file (see [below](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html#build-gradle-kts)).

- The gradle.properties file, containing properties used by Gradle build script.

- The settings.gradle.kts file, containing a definition of the `rootProject.name` and required repositories.

- The Gradle Wrapper files in the gradle directory. The gradle-wrapper.properties file specifies the version of Gradle to be used to build the plugin. If needed, the IDE downloads the version of Gradle specified in this file automatically.

- The META-INF directory under the default `main` [source set](https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_project_layout) contains the [plugin configuration file](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) and [plugin logo](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html).

- The Run IDE with Plugin [run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html).


#### `build.gradle.kts` Gradle Build File﻿

The generated `my_plugin` project build.gradle.kts file depends on the IDE version used to generate the project:

- 2025.1+: [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) variant

- earlier IDE versions: [Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html) variant (deprecated)


IntelliJ Platform Gradle Plugin (2.x)

Gradle IntelliJ Plugin (1.x)

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1.7")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
      Initial version
    """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}
```

- Three Gradle plugins are explicitly declared:

  - [Gradle Java](https://docs.gradle.org/current/userguide/java_plugin.html) plugin (`java`)

  - [Kotlin Gradle](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin) plugin (`org.jetbrains.kotlin.jvm`)

  - [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) (`org.jetbrains.intellij.platform`)
- The Group from the [New Project](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html#create-ide-plugin) wizard is the `project.group` value

- `repositories`: setup required repositories ( [Repositories Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html))

- `dependencies`:

  - define target IDE type (`IC`) and version (`2025.1.7`) ( [Target Versions](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#target-versions))

  - add dependency on the platform testing framework ( [Testing](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#testing))
- `pluginConfiguration`: [`since-build`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration-ideaVersion) and initial [change notes](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration-changeNotes)

- `sourceCompatibility` enforces using a 21 JDK


```kotlin
plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.9.21"
  id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  version.set("2022.2.5")
  type.set("IC") // Target IDE Platform

  plugins.set(listOf(/* Plugin Dependencies */))
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
  }

  patchPluginXml {
    sinceBuild.set("222")
    untilBuild.set("232.*")
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}
```

- Three Gradle plugins are explicitly declared:

  - The [Gradle Java](https://docs.gradle.org/current/userguide/java_plugin.html) plugin (`java`).

  - The [Kotlin Gradle](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin) plugin (`org.jetbrains.kotlin.jvm`).

  - The [Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html) (`org.jetbrains.intellij`).
- The Group from the [New Project](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html#create-ide-plugin) wizard is the `project.group` value.

- The `sourceCompatibility` line is injected to enforce using Java 17 JDK to compile Java sources.

- The values of the [`intellij.version`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-version) and [`intellij.type`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-type) properties specify the version and type of the IntelliJ Platform to be used to build the plugin.

- The empty placeholder list for [plugin dependencies](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-plugins).

- The values of the [`patchPluginXml.sinceBuild`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-patchpluginxml-sincebuild) and [`patchPluginXml.untilBuild`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-patchpluginxml-untilbuild) properties specifying the minimum and maximum versions of the IDE build the plugin is compatible with.

- The initial [`signPlugin`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-signplugin) and [`publishPlugin`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-publishplugin) tasks configuration. See the [Publishing Plugin With Gradle](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#publishing-plugin-with-gradle) section for more information.


#### Plugin Gradle Properties and Plugin Configuration File Elements﻿

The Gradle properties `rootProject.name` and `project.group` will not, in general, match the respective [plugin configuration file](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) plugin.xml elements [`<name>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__name) and [`<id>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id). There is no IntelliJ Platform-related reason they should as they serve different functions.

The `<name>` element (used as the plugin's display name) is often the same as `rootProject.name`, but it can be more explanatory.

The `<id>` value must be a unique identifier over all plugins, typically a concatenation of the specified Group and Artifact. Please note that it is impossible to change the `<id>` of a published plugin without losing automatic updates for existing installations.

## Running a Plugin With the `runIde` Gradle task﻿

Gradle projects are run from the IDE's Gradle tool window.

### Adding Code to the Project﻿

Before running [`my_plugin`](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html#components-of-a-wizard-generated-gradle-intellij-platform-plugin), some code can be added to provide basic functionality. See the [Creating Actions](https://plugins.jetbrains.com/docs/intellij/creating-actions-tutorial.html) tutorial for step-by-step instructions for adding a menu action.

### Executing the Plugin﻿

The generated project contains the Run IDE with Plugin run configuration that can be executed via the Run \| Run... action or can be found in the Gradle tool window under the Run Configurations node.

To execute the Gradle `runIde` task directly, open the Gradle tool window and search for the runIde task under the Tasks node. If it's not on the list, click the Sync All Gradle Projects button on the [toolbar](https://www.jetbrains.com/help/idea/jetgradle-tool-window.html#gradle_toolbar) at the top of the Gradle tool window. Then double-click it to execute.

To debug your plugin in a standalone IDE instance, please see [How to Debug Your Own IntelliJ IDEA Instance](https://medium.com/agorapulse-stories/how-to-debug-your-own-intellij-idea-instance-7d7df185a48d) blog post.

> ### tip
>
> For more information about how to work with Gradle-based projects see the [Working with Gradle in IntelliJ IDEA](https://www.youtube.com/watch?v=6V6G3RyxEMk) screencast and working with [Gradle tasks](https://www.jetbrains.com/help/idea/work-with-gradle-tasks.html) in the IntelliJ IDEA help.
