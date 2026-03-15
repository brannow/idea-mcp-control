# Extension Points﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_extension_points.md) Last modified: 25 March 2025

> ### note
>
> See [Plugin Extensions](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html) for using extension points in your plugin.

By defining extension points in your plugin, you can allow other plugins to extend your plugin's functionality. There are two types of extension points:

- Interface extension points allow other plugins to extend your plugins with code. When defining an interface extension point, specify an interface, and other plugins will provide classes implementing that interface. The providing plugin can then invoke methods on this interface. In most cases, the interface can be annotated with `@ApiStatus.OverrideOnly` (see [Override-Only API](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html#override-only-api)).

- Bean extension points allow other plugins to extend a plugin with data. Specify the fully qualified name of an extension class, and other plugins will provide data that will be turned into instances of that class.


### Declaring Extension Point﻿

1. Add an [`<extensionPoints>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensionPoints) section to plugin.xml, if it's not yet present there.

2. Add a child element [`<extensionPoint>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensionPoints__extensionPoint).

3. Specify the extension point name with the `name` or `qualifiedName` attribute \*.

4. Depending on the extension point type, specify the `interface` or `beanClass` attribute \*.

5. If required, specify the `area` attribute \*.


See the [Example](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html#example).

\*see the Attributes section for [`<extensionPoint>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensionPoints__extensionPoint) for details

The plugin that contributes to the extension point will read the specified properties from the plugin.xml file.

If extension implementations are filtered according to [dumb mode](https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html#dumb-mode), the base class should be marked with [`PossiblyDumbAware`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/project/PossiblyDumbAware.java) to highlight this. Use [`DumbService.getDumbAwareExtensions()`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/project/DumbService.kt) to retrieve dumb-aware implementations.

Base classes for extensions requiring a key:

- [`LanguageExtension`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/lang/LanguageExtension.java)

- [`FileTypeExtension`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/fileTypes/FileTypeExtension.java)

- [`ClassExtension`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/util/ClassExtension.java)

- [`KeyedExtensionCollector`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/util/KeyedExtensionCollector.java)


> ### note
>
> See the [Bundling Plugin API Sources](https://plugins.jetbrains.com/docs/intellij/bundling-plugin-openapi-sources.html) section explaining how to expose extension points sources to other plugins.

## Example﻿

Consider example extension points declarations:

myPlugin/META-INF/plugin.xml

```markup
<idea-plugin>
  <id>my.plugin</id>

  <extensionPoints>
    <extensionPoint
        name="myExtensionPoint1"
        interface="com.example.MyInterface"/>

    <extensionPoint
        name="myExtensionPoint2"
        beanClass="com.example.MyBeanClass"/>
  </extensionPoints>

</idea-plugin>
```

The `com.example.MyBeanClass` bean class used in the above `plugin.xml` file is implemented as follows:

myPlugin/src/com/myplugin/MyBeanClass.java

```java
public final class MyBeanClass extends AbstractExtensionPointBean {

  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  public String getKey() {
    return key;
  }

  public String getClass() {
    return implementationClass;
  }

}
```

> ### tip
>
> See [Extension properties code insight](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html#extension-properties-code-insight) on how to provide smart completion/validation.

For the above extension points, their usage in anotherPlugin would look like this (see also [Declaring Extensions](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html#declaring-extensions)):

anotherPlugin/META-INF/plugin.xml

```markup
<idea-plugin>
  <id>another.plugin</id>

  <!-- Declare dependency on plugin defining extension point: -->
  <depends>my.plugin</depends>

  <!-- Use "my.plugin" namespace: -->
  <extensions defaultExtensionNs="my.plugin">
    <myExtensionPoint1
            key="someKey"
            implementationClass="com.example.MyImplementation"/>

    <myExtensionPoint2
            implementation="another.MyInterfaceImpl"/>
  </extension>

</idea-plugin>
```

## Using Extension Points﻿

To refer to all registered extension instances at runtime, declare an [`ExtensionPointName`](../../reference-Repository/intellij-community/platform/extensions/src/com/intellij/openapi/extensions/ExtensionPointName.kt) with private visibility passing in the fully qualified name matching its [declaration in plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html#declaring-extension-points). If needed, provide a public method to query registered extensions (Sample: [`TestSourcesFilter.isTestSources()`](../../reference-Repository/intellij-community/platform/projectModel-api/src/com/intellij/openapi/roots/TestSourcesFilter.java)).

myPlugin/src/com/myplugin/MyExtensionUsingService.java

```java
@Service
public final class MyExtensionUsingService {

  private static final ExtensionPointName<MyBeanClass> EP_NAME =
      ExtensionPointName.create("my.plugin.myExtensionPoint1");

  public void useRegisteredExtensions() {
    for (MyBeanClass extension : EP_NAME.getExtensionList()) {
      String key = extension.getKey();
      String clazz = extension.getClass();
      // ...
    }
  }

}
```

A gutter icon for the `ExtensionPointName` declaration allows navigating to the corresponding [`<extensionPoint>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensionPoints__extensionPoint) declaration in plugin.xml. Code insight is available for the extension point name String literal (2022.3).

### Error Handling﻿

When processing extension implementations or registrations, there might be errors, compatibility and configuration issues. Use [`PluginException`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/diagnostic/PluginException.java) to log and correctly attribute the causing plugin for [builtin error reporting](https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#error-reporting).

To report use of a deprecated API, use `PluginException.reportDeprecatedUsage()` methods.

Examples:

- [`CompositeFoldingBuilder.assertSameFile()`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/lang/folding/CompositeFoldingBuilder.java)

- [`InspectionProfileEntry.getDisplayName()`](../../reference-Repository/intellij-community/platform/analysis-api/src/com/intellij/codeInspection/InspectionProfileEntry.java)


2020.1+

## Dynamic Extension Points﻿

To support [Dynamic Plugins](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html), an extension point must adhere to specific usage rules:

- extensions are enumerated on every use, and extension instances are not stored anywhere

- alternatively, an [`ExtensionPointListener`](../../reference-Repository/intellij-community/platform/extensions/src/com/intellij/openapi/extensions/ExtensionPointListener.kt) can perform any necessary updates of data structures (register via `ExtensionPointName.addExtensionPointListener()`)


Extension points matching these conditions can then be marked as dynamic by adding `dynamic="true"` in their declaration:

```markup
<extensionPoints>
  <extensionPoint
          name="myDynamicExtensionPoint"
          beanClass="com.example.MyBeanClass"
          dynamic="true"/>
</extensionPoints>
```

All non-dynamic extension points are highlighted via Plugin DevKit \| Plugin descriptor \| Plugin.xml dynamic plugin verification inspection.
