# Class Loaders﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_class_loaders.md) Last modified: 18 June 2025

Each plugin has a dedicated class loader, which is used to load the plugin's classes. This allows each plugin to use a different library version, even if the same library is used by the IDE itself or by another plugin.

## Overriding IDE Dependencies﻿

Gradle 7 introduced the `implementation` scope that replaced the `compile` scope. For this setup, to use a library dependency declared by a plugin instead of the version bundled in the IDE, add the following snippet to the Gradle build script:

```kotlin
configurations.all {
  resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
}
```

> ### tip
>
> Bundled Libraries
>
> [Third-Party Software and Licenses](https://www.jetbrains.com/legal/third-party-software/) list all bundled libraries and their versions for each IDE.

## Loading Classes from Plugin Dependencies﻿

By default, the main IDE class loader loads classes that are not found in the plugin class loader. However, in the [plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) file, you may use the [`<depends>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__depends) element to specify that a [plugin depends](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html) on one or more other plugins. In this case, the class loaders of those plugins will be used for classes not found in the current plugin. This allows a plugin to reference classes from other plugins.

## Using `ServiceLoader`﻿

Some libraries use [`ServiceLoader`](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/util/ServiceLoader.html) to detect and load implementations. To make it work in a plugin, the context class loader must be set to the plugin's classloader and restored afterward with the original one around initialization code:

Kotlin

Java

```kotlin
val currentThread = Thread.currentThread()
val originalClassLoader = currentThread.contextClassLoader
val pluginClassLoader = this.javaClass.classLoader
try {
  currentThread.contextClassLoader = pluginClassLoader
  // code working with ServiceLoader here
} finally {
  currentThread.contextClassLoader = originalClassLoader
}
```

```java
Thread currentThread = Thread.currentThread();
ClassLoader originalClassLoader = currentThread.getContextClassLoader();
ClassLoader pluginClassLoader = this.getClass().getClassLoader();
try {
  currentThread.setContextClassLoader(pluginClassLoader);
  // code working with ServiceLoader here
} finally {
  currentThread.setContextClassLoader(originalClassLoader);
}
```
