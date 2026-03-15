# Configuring IntelliJ Platform Gradle Plugin (2.x)

This section presents a short overview of the most important Gradle plugin configuration elements to achieve commonly desired functionality.

For more advanced options and topics, see:

- [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) reference

- [IntelliJ Platform Gradle Plugin – FAQ](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html)

- [Recipes](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-recipes.html)

- [Migrating from Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html)


## Keep Up To Date

IntelliJ Platform Gradle Plugin (2.x) and [Gradle](https://gradle.org/install/) build system are constantly developed, and every new release brings important bug fixes, new features, and improvements that make the development more efficient.

It is strongly recommended to keep updating both Gradle and IntelliJ Platform Gradle Plugin to the latest versions. Newer IDE releases might not be supported fully in older releases of the IntelliJ Platform Gradle Plugin.

## Setup

See [Configuration](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#configuration) for the necessary declaration of repositories.

## Target Platform and Dependencies

If a matching version of the specified IntelliJ Platform is not present on the local machine, the Gradle plugin will download it. IntelliJ IDEA then indexes the build and any associated source code and [JetBrains Java Runtime](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-jetbrains-runtime.html).

To build a plugin for more than one target platform version, see [Targeting Multiple IDE Versions](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#multipleIDEVersions) for important notes.

### IntelliJ Platform Configuration

The target IDE platform is set in the `dependencies {}` block, see [Setting Up IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#setting-up-intellij-platform) for a minimal sample.

[Target Platforms](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#target-platforms) lists all available extension functions for known target IDE platforms.

### Plugin Dependencies

IntelliJ Platform plugin projects may depend on either bundled or third-party plugins defined in the `dependencies {}` block, see [Plugins](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#plugins) for details.

The runtime dependency must be added in the [Plugin Configuration](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) (plugin.xml) file as described in [Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#dependency-declaration-in-pluginxml).

## Run IDE Task

By default, the [`runIde`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#runIde) task will use the same version of the IntelliJ Platform for the IDE Development instance as was used for building the plugin.

## Verifying Plugin

The following tasks allow running integrity and compatibility tests:

- [`verifyPlugin`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#verifyPlugin) \- runs the [Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html#plugin-verifier) tool to check the binary compatibility with the specified IDE builds

- [`verifyPluginStructure`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#verifyPluginStructure) — validates completeness and contents of [plugin.xml descriptors](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) as well as the plugin's archive structure

- [`verifyPluginProjectConfiguration`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#verifyPluginProjectConfiguration) — validates the plugin project configuration


## Publishing Plugin

Review the [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html) page before using the [`publishPlugin`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#publishPlugin) task.

26 March 2025

[Migrating DevKit Plugin to Gradle](https://plugins.jetbrains.com/docs/intellij/migrating-plugin-devkit-to-gradle.html) [Configuring Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/configuring-plugin-project.html)
