# Developing a Plugin﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/getting_started/plugin/developing_plugins.md) Last modified: 04 November 2025

IntelliJ Platform plugins can be developed by using [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) as your IDE. It is highly recommended to always use the latest available version, as the plugin development tooling support from Plugin DevKit continues supporting new features.

Before starting with the actual development, make sure to understand all requirements to achieve best [Plugin User Experience (UX)](https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html).

> ### note
>
> Plugin Alternatives
>
> In some cases, implementing an actual IntelliJ Platform plugin might not be necessary, as [alternative solutions](https://plugins.jetbrains.com/docs/intellij/plugin-alternatives.html) exist.

## Gradle Plugin﻿

The recommended solution for building IntelliJ Platform plugins is using [Gradle](https://www.gradle.org/) with a dedicated Gradle plugin: [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) or [Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html) (obsolete).

> ### warning
>
> Gradle Plugin
>
> The Gradle plugin must be chosen depending on the target [platform version](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#platformVersions).
>
> 2024.2+
>
> Requires [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
>
> 2022.3+
>
> Recommended [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
>
> Requires [Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html)(Obsolete) 1.10.1+

The IntelliJ IDEA provides the necessary plugins to support Gradle-based plugin development: Gradle and Plugin DevKit. To verify these plugins are installed and enabled, see the help section about [Managing Plugins](https://www.jetbrains.com/help/idea/managing-plugins.html).

> ### warning
>
> Installing Plugin DevKit plugin
>
> Plugin DevKit plugin must be installed from JetBrains Marketplace ( [Plugin Homepage](https://plugins.jetbrains.com/plugin/22851-plugin-devkit)) as it is not bundled since version 2023.3.

The Gradle plugin manages the dependencies of a plugin project – both the base IDE and other [plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html). It provides tasks to run the IDE with your plugin and to package and [publish](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#publishing-plugin-with-gradle) your plugin to the [JetBrains Marketplace](https://plugins.jetbrains.com/). To make sure that a plugin is not affected by [API changes](https://plugins.jetbrains.com/docs/intellij/api-changes-list.html), which may happen between major releases of the platform, you can quickly verify your plugin against other IDEs and releases.

There are two main ways of creating a new Gradle-based IntelliJ Platform plugin project:

- dedicated generator available in the [New Project Wizard](https://www.jetbrains.com/help/idea/new-project-wizard.html) – it creates a minimal plugin project with all the required files

- [IntelliJ Platform Plugin Template](https://plugins.jetbrains.com/docs/intellij/plugin-github-template.html) available on GitHub – in addition to the required project files, it includes configuration of the GitHub Actions CI workflows


This documentation section describes the plugin structure generated with the New Project wizard, but the project generated with IntelliJ Platform Plugin Template covers all the described files and directories. See [IntelliJ Platform Plugin Template](https://plugins.jetbrains.com/docs/intellij/plugin-github-template.html) for more information about the advantages of this approach and instructions on how to use it.

### Alternatives﻿

The old DevKit project model and workflow are still supported in existing projects and are recommended for [creating theme plugins](https://plugins.jetbrains.com/docs/intellij/developing-themes.html). See how to [migrate a DevKit plugin to Gradle](https://plugins.jetbrains.com/docs/intellij/migrating-plugin-devkit-to-gradle.html).

A dedicated [SBT plugin](https://github.com/JetBrains/sbt-idea-plugin) is available for plugins implemented in Scala.

## Plugin Development Workflow﻿

- [Creating a Plugin Gradle Project](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html)

  - [IntelliJ Platform Plugin Template](https://plugins.jetbrains.com/docs/intellij/plugin-github-template.html)
- [Configuring IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/configuring-gradle.html)

  - [Configuring Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/configuring-plugin-project.html)(Obsolete)
- [Configuring Kotlin Support](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)

- [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)

- [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
