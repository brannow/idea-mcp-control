# Plugin Dependencies﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_dependencies.md) Last modified: 07 November 2025

A plugin may depend on API and classes from other plugins, either bundled or third-party.

This document describes the syntax for declaring plugin dependencies and optional plugin dependencies. For more information about dependencies on the IntelliJ Platform modules, see [Plugin Compatibility with IntelliJ Platform Products](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html).

> ### note
>
> For adding dependencies on 3rd party libraries, use regular [Gradle dependency management](https://docs.gradle.org/current/userguide/core_dependency_management.html).

### Required Steps﻿

To express a dependency on classes from other plugins or modules, perform the following three required steps detailed below on this page:

1. Locate Plugin ID

2. Project Setup

3. Declaration in [plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)


> ### tip
>
> Getting java.lang.NoClassDefFoundError
>
> If `java.lang.NoClassDefFoundError` occurs at runtime, most likely Step 3 was omitted.
>
> Otherwise, loading the plugin dependency may have failed, check log files from the [Development Instance](https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html#development-instance-settings-caches-logs-and-plugins).

## 1\. Locating Plugin ID and Preparing Sandbox﻿

A compatible version must be chosen carefully according to the plugin's [compatibility](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html). For non-bundled plugins, it is not possible to specify the minimum/maximum version for the dependent plugin. ( [Issue](https://youtrack.jetbrains.com/issue/IDEABKL-7906))

### JetBrains Marketplace﻿

For plugins published on [JetBrains Marketplace](https://plugins.jetbrains.com/):

1. Open plugin's detail page

2. Scroll down to the bottom section Additional Information

3. Copy Plugin ID


### Bundled and Other Plugins﻿

All IDs of bundled plugins can be gathered using a dedicated Gradle task. See Other tab on how to locate the plugin ID for a plugin distribution file.

IntelliJ Platform Gradle Plugin (2.x)

Gradle IntelliJ Plugin (1.x)

Other

Use [`printBundledPlugins`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#printBundledPlugins) task.

Use [`listBundledPlugins`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-listbundledplugins) task.

Locate the plugin's main JAR file containing the META-INF/plugin.xml descriptor with the [`<id>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id) tag (use [`<name>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__name) if `<id>` is not specified).

Bundled plugins are located in $PRODUCT\_ROOT$/plugins/$PLUGIN\_NAME$/lib/$PLUGIN\_NAME$.jar.

#### IDs of Bundled Plugins﻿

The following table lists some commonly used bundled plugins and their ID. See also [Modules Specific to Functionality](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules-specific-to-functionality).

| Plugin Name | Plugin ID | Related Documentation |
| --- | --- | --- |
| Copyright | `com.intellij.copyright` |  |
| CSS | `com.intellij.css` | [WebStorm Plugin Development](https://plugins.jetbrains.com/docs/intellij/webstorm.html) |
| Database Tools and SQL | `com.intellij.database` | [DataGrip Plugin Development](https://plugins.jetbrains.com/docs/intellij/data-grip.html) |
| Gradle | `com.intellij.gradle` |  |
| Groovy | `org.intellij.groovy` |  |
| IntelliLang | `org.intellij.intelliLang` | [Language Injection](https://plugins.jetbrains.com/docs/intellij/language-injection.html) |
| Java | `com.intellij.java` | [Java Plugin](https://plugins.jetbrains.com/docs/intellij/idea.html#java-plugin) |
| JavaScript and TypeScript | `JavaScript` | [WebStorm Plugin Development](https://plugins.jetbrains.com/docs/intellij/webstorm.html) |
| JSON | `com.intellij.modules.json` | [JSON plugin introduction notes](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2024.html#json-plugin-new-20243) |
| Kotlin | `org.jetbrains.kotlin` | [Kotlin Plugin](https://plugins.jetbrains.com/docs/intellij/idea.html#kotlin-plugin) |
| Markdown | `org.intellij.plugins.markdown` |  |
| Maven | `org.jetbrains.idea.maven` |  |
| Spring | `com.intellij.spring` | [Spring API](https://plugins.jetbrains.com/docs/intellij/spring-api.html) |
| Spring Boot | `com.intellij.spring.boot` | [Spring Boot](https://plugins.jetbrains.com/docs/intellij/spring-api.html#spring-boot) |
| Terminal | `org.jetbrains.plugins.terminal` | [Embedded Terminal](https://plugins.jetbrains.com/docs/intellij/embedded-terminal.html) |
| YAML | `org.jetbrains.plugins.yaml` |  |

### Preparing Sandbox﻿

If the plugin is not bundled with the target IDE, run the (sandbox) [IDE Development Instance](https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html) of your target IDE and install the plugin there.

## 2\. Project Setup﻿

Depending on the chosen development workflow (Gradle or DevKit), one of the following steps is necessary.

### IntelliJ Platform Gradle Plugin (2.x)﻿

Define dependencies on plugins using the provided helper functions in the `dependencies {}` block of the build.gradle.kts file:

```kotlin
dependencies {
  intellijPlatform {
    bundledPlugin("<pluginId>")
    plugin("<nonBundledPluginId>:<version>")
  }
}
```

For bundled plugins, use `bundledPlugin()`. Use `plugin()` for non-bundled plugins (for example, from [JetBrains Marketplace](https://plugins.jetbrains.com/)).

See [Plugins](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#plugins) for full reference and additional options.

Obsolete

### Gradle IntelliJ Plugin (1.x)﻿

> ### warning
>
> Obsolescence Notice
>
> Gradle IntelliJ Plugin (1.x) is no longer under active development.
>
> Whenever possible, use [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) instead.

> ### note
>
> See the [`intellij.plugins`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-plugins) property for acceptable values.

Add the dependency to the [`intellij.plugins`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-plugins) parameter in your build script:

Kotlin

Groovy

```kotlin
intellij {
  plugins.set(listOf("com.example.another-plugin:1.0"))
}
```

```groovy
intellij {
  plugins = ['com.example.another-plugin:1.0']
}
```

> ### note
>
> Transitive dependencies required for tests must currently be [specified explicitly](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/38).

### Plugin DevKit﻿

> ### note
>
> Existing DevKit-based projects can be [converted to use Gradle setup](https://plugins.jetbrains.com/docs/intellij/migrating-plugin-devkit-to-gradle.html) where dependency management is fully automated.

Add the JARs of the plugin on which the project depends to the Classpath of the [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-intellij-platform-plugin-sdk).

> ### warning
>
> Do not add the plugin JARs as a library: this will fail at runtime because the IntelliJ Platform will load two separate copies of the dependency plugin classes.

### Adding a plugin dependency in a DevKit-based plugin﻿

1. Open the Project Structure dialog and go to the Platform Settings \| SDKs section.

2. Select the SDK used in the project.

3. Click the + button in the Classpath tab.

4. Select the plugin JAR depending on whether it is a bundled or non-bundled plugin:

   - For bundled plugins, the plugin JAR files are located in plugins/$PLUGIN\_NAME$ or plugins/$PLUGIN\_NAME$/lib under the main installation directory.

   - For non-bundled plugins, the plugin JAR files are located in OS-specific [plugins directory](https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html#plugins-directory)

## 3\. Dependency Declaration in plugin.xml﻿

Regardless of whether a plugin project uses [Modules Available in All Products](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules-available-in-all-products) or [Modules Specific to Functionality](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules-specific-to-functionality), the correct module must be listed as a dependency in plugin.xml. If a project depends on another plugin, the dependency must be declared like a [module](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules). If only general IntelliJ Platform features (APIs) are used, then a default dependency on `com.intellij.modules.platform` must be declared.

To display a list of available IntelliJ Platform modules, invoke the [code completion](https://www.jetbrains.com/help/idea/auto-completing-code.html#4eac28ba) feature for the [`<depends>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__depends) element contents while editing the plugin project's plugin.xml file.

In the plugin.xml, add a `<depends>` tag with the dependency plugin's ID as its content. Continuing with the example from the [Project Setup](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#project-setup) above, the dependency declaration in plugin.xml would be:

```markup
<depends>com.example.another-plugin</depends>
```

## Optional Plugin Dependencies﻿

A plugin can also specify an optional plugin dependency. In this case, the plugin will load even if the plugin it depends on is not installed or enabled, but part of the plugin's functionality will not be available.

Declare additional `optional="true"` and required `config-file` attribute pointing to the [optional plugin descriptor file](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#additional-plugin-configuration-files):

```markup
<depends
    optional="true"
    config-file="myPluginId-optionalPluginName.xml">dependency.plugin.id</depends>
```

> ### note
>
> Additional plugin descriptor files must follow the naming pattern myPluginId-$NAME$.xml resulting in unique filenames to prevent problems with classloaders in tests ( [Details](https://youtrack.jetbrains.com/issue/IDEA-205964)).

### Sample﻿

The plugin adds additional highlighting for Java and Kotlin files. The main plugin.xml defines a required dependency on the Java plugin (plugin ID `com.intellij.java`) and registers the corresponding `com.intellij.annotator` extension. Additionally, it specifies an optional dependency on the Kotlin plugin (plugin ID `org.jetbrains.kotlin`):

plugin.xml

```markup
<idea-plugin>
   ...
   <depends>com.intellij.java</depends>

   <depends
       optional="true"
       config-file="myPluginId-withKotlin.xml">org.jetbrains.kotlin</depends>

   <extensions defaultExtensionNs="com.intellij">
      <annotator
          language="JAVA"
          implementationClass="com.example.MyJavaAnnotator"/>
   </extensions>
</idea-plugin>
```

The configuration file myPluginId-withKotlin.xml is located in the same directory as the main plugin.xml file. In that file, the annotator extension for Kotlin is defined:

myPluginId-withKotlin.xml

```markup
<idea-plugin>
   <extensions defaultExtensionNs="com.intellij">
      <annotator
          language="kotlin"
          implementationClass="com.example.MyKotlinAnnotator"/>
   </extensions>
</idea-plugin>
```
