# IDE Development Instance﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/ide_development_instance.md) Last modified: 29 April 2025

A plugin project can be run or debugged from within the development instance of IntelliJ IDEA. Selecting the `runIde` task for a Gradle-based project (or [Run](https://plugins.jetbrains.com/docs/intellij/running-and-debugging-a-theme.html) menu for a Plugin DevKit-based project) will launch a Development Instance of the target IDE with the current development version of the plugin enabled.

This page describes how to control some settings for the Development Instance.

> ### tip
>
> See also `runIde` task (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#runIde), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-runide)) properties and [Advanced Configuration](https://www.jetbrains.com/help/idea/tuning-the-ide.html) for general VM options and properties.

## Using a JetBrains Runtime for the Development Instance﻿

> ### tip
>
> IntelliJ Platform Gradle Plugin (2.x)
>
> See [JetBrains Runtime (JBR)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-jetbrains-runtime.html) when using [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

An everyday use case is to develop (build) a plugin project against a JDK, e.g., Java 17, and then run or debug the plugin in a Development Instance of the IDE. In such a situation, Development Instance must use a [JetBrains Runtime (JBR)](https://www.jetbrains.com/jetbrains-runtime) rather than the JDK used to build the plugin project.

The JetBrains Runtime is an environment for running IntelliJ Platform-based IDEs on Windows, macOS, and Linux. It has some modifications by JetBrains, such as fixes for native crashes not present in official JDK builds. A version of the JetBrains Runtime is bundled with all IntelliJ Platform-based IDEs. To produce accurate results while running or debugging a plugin project in a Development Instance, follow the procedures below to ensure the Development Instance uses a JetBrains Runtime.

### Using JetBrains Runtime﻿

Gradle IntelliJ Plugin (1.x)

Plugin DevKit

By default, the Gradle plugin will fetch and use the version of the JetBrains Runtime for the Development Instance corresponding to the version of the IntelliJ Platform used for building the plugin project. If required, an alternative version can be specified using the [`runIde.jbrVersion`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-runide-jbrversion) task property.

The [Run Configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html) for a DevKit-based plugin project controls the JDK used to run and debug a plugin project in a Development Instance. The default Run Configuration uses the same JDK for building the plugin project and running the plugin in a Development Instance.

To change the runtime for the Development Instance, set the JRE field in the Run Configuration edit dialog to download a JetBrains Runtime.

### Determining a JetBrains Runtime Version﻿

The JetBrains Runtime is determined by the JDK version used to build the plugin project, regardless of whether it is built on macOS, Windows, or Linux.

### Determine an Example JetBrains Runtime Version﻿

If a plugin is developed against the Java 8 SE Development Kit 8 for macOS (jdk-8u212-macosx-x64.dmg) to acquire the compatible JetBrains Runtime:

1. Go to the [GitHub JetBrains Runtime Releases](https://github.com/JetBrains/JetBrainsRuntime) for general information and the latest build.

2. Open the [Releases](https://github.com/JetBrains/JetBrainsRuntime/releases) page to access all releases.

3. Select the package name corresponding to the platform and SDK version. In this case, the package name is `jbrsdk8-osx-x64` for JetBrains Runtime SDK version 8, macOS x64 hardware.

4. In the list of files, find the name that satisfies:

   - The version and build number match the JDK used to build the plugin project. For example, `jbrx-8u252-osx-x64` matches the Java 8 JDK, build 252: `jdk-8u252-macosx-x64`.

   - Pick the highest JetBrains Runtime build number available. For example, the file is jbrx-8u252-osx-x64-b1649.2.tar.gz, meaning build 1649.2 for this JetBrains Runtime matching Java 8 JDK build 252.

### JetBrains Runtime Variants﻿

The JetBrains Runtime is delivered in various variants used for different purposes, like debugging, running for development purposes, or bundling with the IDE.

Available JBR variants are:

- `jcef` \- the release bundles with the [JCEF](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html) browser engine

- `sdk` \- JBR SDK bundle used for development purposes

- `fd` \- the fastdebug bundle which also includes the `jcef` module

- `dcevm` \- bundles DCEVM (Dynamic Code Evolution Virtual Machine)

- `nomod` – the release bundled without any additional modules


> ### note
>
> For `JBR 17`, `dcevm` is bundled by default. As a consequence, separated `dcevm` and `nomod` variants are no longer available.

2020.1+

## Enabling Auto-Reload﻿

Auto-Reload is available for compatible [dynamic plugins](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html). This allows a much faster development cycle by avoiding a full restart of the development instance after detecting code changes (when JARs are modified).

Please note that any unloading problems in a production environment will ask the user to restart the IDE.

> ### warning
>
> Debugging
>
> Auto-Reload does not work when the sandbox IDE instance is running under a debugger.

### IntelliJ Platform Gradle Plugin (2.x)﻿

Auto-Reload is enabled by default.

Set property [`intellijPlatform.autoReload`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-autoReload) to `false` to disable it explicitly, see [How to disable the automatic reload of dynamic plugins?](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-disable-the-automatic-reload-of-dynamic-plugins)

After starting the sandbox IDE instance, run the [`buildPlugin`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#buildPlugin) task after modifications in the plugin project and switch back focus to the sandbox instance to trigger reload.

> ### warning
>
> [`buildSearchableOptions`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#buildSearchableOptions) task must currently be [disabled explicitly](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-disable-building-the-searchable-options) to work around Only one instance of IDEA can be run at a time problem.

Obsolete

### Gradle IntelliJ Plugin (1.x)﻿

> ### warning
>
> Obsolescence Notice
>
> Gradle IntelliJ Plugin (1.x) is no longer under active development.
>
> Whenever possible, use [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) instead.

Auto-Reload is enabled by default when targeting 2020.2 or later.

Set the property [`runIde.autoReloadPlugins`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-runide-autoreloadplugins) to `true` for enabling it in earlier platform versions or `false` to disable it explicitly, see [How to disable automatic reload of dynamic plugins?](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-automatic-reload-of-dynamic-plugins)

After starting the sandbox IDE instance, run the [`buildPlugin`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-buildplugin) task after modifications in the plugin project and switch focus back to the sandbox instance to trigger reload.

> ### warning
>
> [`buildSearchableOptions`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-buildsearchableoptions) task must currently be [disabled explicitly](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options) to work around Only one instance of IDEA can be run at a time problem.

### Plugin DevKit﻿

Add system property `idea.auto.reload.plugins` in the Plugin DevKit [run configuration](https://plugins.jetbrains.com/docs/intellij/running-and-debugging-a-theme.html).

To disable auto-reload, set `idea.auto.reload.plugins` to `false` explicitly.

## The Development Instance Sandbox Directory﻿

The Sandbox Home directory contains the [settings, caches, logs, and plugins](https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html#development-instance-settings-caches-logs-and-plugins) for a Development Instance of the IDE. This information is stored in a different location than for the [installed IDE itself](https://intellij-support.jetbrains.com/hc/en-us/articles/206544519-Directories-used-by-the-IDE-to-store-settings-caches-plugins-and-logs).

### IntelliJ Platform Gradle Plugin (2.x)﻿

The default Sandbox Home location in a [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) plugin project is:

- Windows: $PROJECT\_DIRECTORY$\\build\\$TARGET\_IDE$\\idea-sandbox

- Linux/macOS: $PROJECT\_DIRECTORY$/build/$TARGET\_IDE$/idea-sandbox


The Sandbox Home location can be configured with the [`intellijPlatform.sandboxContainer`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-sandboxContainer) property.

Obsolete

### Gradle IntelliJ Plugin (1.x)﻿

> ### warning
>
> Obsolescence Notice
>
> Gradle IntelliJ Plugin (1.x) is no longer under active development.
>
> Whenever possible, use [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) instead.

The default Sandbox Home location in a [Gradle IntelliJ Plugin (1.x)](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html) plugin project is:

- Windows: $PROJECT\_DIRECTORY$\\build\\idea-sandbox

- Linux/macOS: $PROJECT\_DIRECTORY$/build/idea-sandbox


The Sandbox Home location can be configured with the [`intellij.sandboxDir`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-sandboxdir) property.

### Plugin DevKit﻿

For Plugin DevKit-based plugins, the default Sandbox Home location is defined in the IntelliJ Platform Plugin SDK. See the [Setting Up a Theme Development Environment](https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-intellij-platform-plugin-sdk) for information about how to set up Sandbox Home in IntelliJ Platform SDK.

The default Sandbox Home directory location is:

- Windows: $USER\_HOME$\\.$PRODUCT\_SYSTEM\_NAME$$PRODUCT\_VERSION$\\system\\plugins-sandbox\

- Linux: ~/.$PRODUCT\_SYSTEM\_NAME$$PRODUCT\_VERSION$/system/plugins-sandbox/

- macOS: ~/Library/Caches/$PRODUCT\_SYSTEM\_NAME$$PRODUCT\_VERSION$/plugins-sandbox/


### Development Instance Settings, Caches, Logs, and Plugins﻿

Within the Sandbox Home directory are subdirectories of the Development Instance:

- config contains settings for the IDE instance.

- plugins contains folders for each plugin being run in the IDE instance.

- system/caches or system\\caches holds the IDE instance data.

- system/log or system\\log contains the idea.log file for the IDE instance.


Each of these Sandbox Home subdirectories can be manually cleared to reset the IDE Development Instance. At the next launch of a Development Instance, the subdirectories will be repopulated with the appropriate information.
