# Plugin Content﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_content.md) Last modified: 25 March 2025

Plugin distribution is built using the dedicated Gradle `buildPlugin` task (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#buildPlugin), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-buildplugin)) or [Plugin DevKit](https://plugins.jetbrains.com/docs/intellij/deploying-theme.html).

The plugin distribution .jar file contains:

- configuration file (META-INF/plugin.xml) ( [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html))

- classes implementing the plugin functionality

- recommended: plugin logo file(s) (META-INF/pluginIcon\*.svg) ( [Plugin Logo](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html))


> ### tip
>
> See [Distribution Size](https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#distribution-size) for important steps to optimize the plugin distribution file.

Targeting a plugin distribution to a specific OS is not possible ( [issue](https://youtrack.jetbrains.com/issue/MP-1896)).

## Plugin Without Dependencies﻿

A plugin consisting of a single .jar file is placed in the /plugins directory.

<IDE directory>pluginssample.jar(Plugin distribution)comcompanySample.class(Classcom.company.Sample)...META-INFplugin.xml(Plugin Configuration File)pluginIcon.svg(Plugin Logo)pluginIcon\_dark.svg(Plugin Logo, dark variant)

## Plugin With Dependencies﻿

The plugin .jar file is placed in the /lib folder under the plugin's "root" folder, together with all required bundled libraries.

All JARs from the /lib folder are automatically added to the classpath (see also [Plugin Class Loaders](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html)).

> ### warning
>
> Do Not Repackage Libraries
>
> Do not repackage libraries into the main plugin JAR file. Otherwise, [Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html) will yield false positives for unresolved classes and methods.

<IDE directory>pluginssampleliblib\_foo.jar(Required bundled library #1)lib\_bar.jar(Required bundled library #2)...sample.jar(Plugin distribution)comcompanySample.class(Classcom.company.Sample)...META-INFplugin.xml(Plugin Configuration File)pluginIcon.svg(Plugin Logo)pluginIcon\_dark.svg(Plugin Logo, dark variant)
