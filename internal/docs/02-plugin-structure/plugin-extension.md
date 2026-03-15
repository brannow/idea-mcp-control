# Extensions﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_extensions.md) Last modified: 31 October 2025

Extensions are the most common way for a plugin to extend the IntelliJ-based IDE's functionality. They are implementations of specific interfaces or classes that are [registered](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html#declaring-extensions) in the plugin descriptor. Provided extension implementations are called by the platform or other plugins to customize and extend the IDE's functionality.

## Common Extension Use Cases﻿

The following are some of the most common tasks achieved using extensions:

- The [`com.intellij.toolWindow`](https://jb.gg/ipe?extensions=com.intellij.toolWindow) extension point allows plugins to add [tool windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html) (panels displayed at the sides of the IDE user interface);

- The [`com.intellij.applicationConfigurable`](https://jb.gg/ipe?extensions=com.intellij.applicationConfigurable) extension point and [`com.intellij.projectConfigurable`](https://jb.gg/ipe?extensions=com.intellij.projectConfigurable) extension point allow plugins to add pages to the [Settings dialog](https://plugins.jetbrains.com/docs/intellij/settings.html);

- [Custom language plugins](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html) use many extension points to extend various language support features in the IDE.


There are more than 1700 extension points available in the platform and the bundled plugins, allowing customizing different parts of the IDE behavior.

## Exploring Available Extensions﻿

### Documentation﻿

- [IntelliJ Platform Extension Point and Listener List](https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html)

- [IntelliJ Platform Plugins Extension Point and Listener List](https://plugins.jetbrains.com/docs/intellij/intellij-community-plugins-extension-point-list.html) (bundled plugins in IntelliJ IDEA)

- [Open Source Plugins Extension Point and Listener List](https://plugins.jetbrains.com/docs/intellij/oss-plugins-extension-point-list.html)


Lists for other IDEs are available under Product Specific (for example, [PhpStorm](https://plugins.jetbrains.com/docs/intellij/php-extension-point-list.html)).

### IntelliJ Platform Explorer﻿

Browse usages inside existing implementations of open-source IntelliJ Platform plugins via [IntelliJ Platform Explorer](https://jb.gg/ipe).

### Code Insight﻿

Alternatively (or when using 3rd party extension points), all available extension points for the specified namespace (`defaultExtensionNs`) can be listed using auto-completion inside the [`<extensions>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensions) block in [plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html). Use View \| Quick Documentation in the lookup list to access more information about the extension point and implementation (if applicable).

See [Explore the IntelliJ Platform API](https://plugins.jetbrains.com/docs/intellij/explore-api.html) for more information and strategies.

## Declaring Extensions﻿

> ### tip
>
> Auto-completion, Quick Documentation, and other code insight features are available on extension point tags and attributes in plugin.xml.

### Declaring Extension﻿

1. Add an [`<extensions>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensions) element to plugin.xml if it's not yet present there. Set the `defaultExtensionNs` attribute to one of the following values:

   - `com.intellij` if the plugin extends the IntelliJ Platform core functionality.

   - `{ID of a plugin}` if the plugin extends the functionality of another plugin (must configure [plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)).
2. Add a new child element to the `<extensions>` element. The child element's name must match the name of the used [extension point](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html).

3. Depending on the type of the extension point, do one of the following:


   - If the extension point was declared using the `interface` attribute, set the `implementation` attribute to the name of the class that implements the specified interface.

   - If the extension point was declared using the `beanClass` attribute, set all properties annotated with the [`@Attribute`](../../reference-Repository/intellij-community/platform/util/src/com/intellij/util/xmlb/annotations/Attribute.java) and [`Tag`](../../reference-Repository/intellij-community/platform/util/src/com/intellij/util/xmlb/annotations/Tag.java) annotations in the specified bean class.


See the [Declaring Extension Point](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html#declaring-extension-points) section for details.

4. In addition to attributes defined by the extension point, the extension element can specify basic attributes (see the attributes list in [An Extension](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensions__-) section).

5. Implement the extension API as required (see [Implementing Extension](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html#implementing-extension)).


To clarify this procedure, consider the following sample section of the plugin.xml file that defines two extensions designed to access the [`com.intellij.appStarter`](https://jb.gg/ipe?extensions=com.intellij.appStarter) and [`com.intellij.projectTemplatesFactory`](https://jb.gg/ipe?extensions=com.intellij.projectTemplatesFactory) extension points in the IntelliJ Platform, and one extension to access the `another.plugin.myExtensionPoint` extension point in another plugin `another.plugin`:

```markup
<!--
  Declare extensions to access extension points in the IntelliJ Platform.
  These extension points have been declared using "interface".
 -->
<extensions defaultExtensionNs="com.intellij">
  <appStarter
      implementation="com.example.MyAppStarter"/>
  <projectTemplatesFactory
      implementation="com.example.MyProjectTemplatesFactory"/>
</extensions>

<!--
  Declare extensions to access extension points in a custom plugin "another.plugin".
  The "myExtensionPoint" extension point has been declared using "beanClass"
  and exposes custom properties "key" and "implementationClass".
-->
<extensions defaultExtensionNs="another.plugin">
  <myExtensionPoint
      key="keyValue"
      implementationClass="com.example.MyExtensionPointImpl"/>
</extensions>
```

### Implementing Extension﻿

Please note the following important points:

- Extension implementation must be stateless. Use explicit [services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html) for managing (runtime) data.

- Avoid any initialization in the constructor, see also notes for [services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html#ctor).

- Do not perform any static initialization. Use inspection Plugin DevKit \| Code \| Static initialization in extension point implementations (2023.3).

- An extension implementation must not be registered as a [service](https://plugins.jetbrains.com/docs/intellij/plugin-services.html) additionally. Use inspection Plugin DevKit \| Code \| Extension registered as service/component (2023.3).

- If an extension instance needs to "opt out" in certain scenarios, it can throw [`ExtensionNotApplicableException`](../../reference-Repository/intellij-community/platform/extensions/src/com/intellij/openapi/extensions/ExtensionNotApplicableException.java) in its constructor.


When using [Kotlin](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html):

- Do not use `object` but `class` for implementation. [More details](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#object-vs-class)

- Do not use `companion object` to avoid excessive classloading/initialization when the extension class is loaded. Use top-level declarations or objects instead. [More details](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#companion-object-extensions)


### Extension Properties Code Insight﻿

Several tooling features are available to help configure bean class extension points in plugin.xml.

#### Required Properties﻿

Properties annotated with [`RequiredElement`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/extensions/RequiredElement.java) are inserted automatically and validated.

If the given property is allowed to have an explicit empty value, set `allowEmpty` to `true`.

#### Class names﻿

Property names matching the following list will resolve to a fully qualified class name:

- `implementation`

- `className`

- ending with `Class` (case-sensitive)

- `serviceInterface`/`serviceImplementation`


A required parent type can be specified in the [extension point declaration](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html) via [`<with>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__extensionPoints__extensionPoint__with):

```markup
<extensionPoint name="myExtension" beanClass="MyExtensionBean">
  <with
      attribute="psiElementClass"
      implements="com.intellij.psi.PsiElement"/>
</extensionPoint>
```

#### Custom resolve﻿

Property name `language` (or ending in `*Language`) resolves to all present [`Language`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/lang/Language.java) IDs.

Similarly, `action` and `actionId` (2024.3+) resolve to all registered [`<action>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__actions__action) IDs.

#### Deprecation/ApiStatus﻿

Properties marked as `@Deprecated` or annotated with any of [`ApiStatus`](https://github.com/JetBrains/java-annotations/tree/24.0.0/common/src/main/java/org/jetbrains/annotations/ApiStatus.java)`@Internal`, `@Experimental`, `@ScheduledForRemoval`, or `@Obsolete` will be highlighted accordingly.

#### Enum properties﻿

`Enum` attributes support code insight with lowerCamelCased notation. Note: The `Enum` implementation must not override `toString()`.

#### I18n﻿

Annotating with [`@Nls`](https://github.com/JetBrains/java-annotations/tree/24.0.0/common/src/main/java/org/jetbrains/annotations/Nls.java) validates a UI `String` capitalization according to the text property `Capitalization` enum value.
