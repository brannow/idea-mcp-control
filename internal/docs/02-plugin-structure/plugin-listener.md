# Listeners﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_listeners.md) Last modified: 19 March 2025

Listeners allow plugins to subscribe to events delivered through the message bus (see [Messaging infrastructure](https://plugins.jetbrains.com/docs/intellij/messaging-infrastructure.html) for details).

Listeners are defined at the application (global) or [project](https://plugins.jetbrains.com/docs/intellij/project.html) level.

> ### tip
>
> Locating Listeners/Topics
>
> All available listeners/topics are listed on [IntelliJ Platform Extension Point and Listener List](https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html) and [IntelliJ Platform Plugins Extension Point and Listener List](https://plugins.jetbrains.com/docs/intellij/intellij-community-plugins-extension-point-list.html) under Listeners sections.
>
> Browse usages inside existing implementations of open-source IntelliJ Platform plugins via [IntelliJ Platform Explorer](https://jb.gg/ipe).

Listener implementations must be stateless and may not implement life-cycle (e.g., `Disposable`). Use inspection Plugin DevKit \| Code \| Listener implementation implements 'Disposable' to verify (2023.3).

Declarative registration of listeners allows achieving better performance than registering listeners from code. The advantage is because listener instances get created lazily — the first time an event is sent to the topic — and not during application startup or project opening.

## Defining Application-Level Listeners﻿

To define an application-level listener, add the [`<applicationListeners>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__applicationListeners) section to [plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html):

```markup
<idea-plugin>
  <applicationListeners>
    <listener
        class="myPlugin.MyListenerClass"
        topic="BaseListenerInterface"/>
  </applicationListeners>
</idea-plugin>
```

The `topic` attribute specifies the listener interface corresponding to the type of events to receive. Usually, this is the interface used as the type parameter of the [`Topic`](../../reference-Repository/intellij-community/platform/extensions/src/com/intellij/util/messages/Topic.java) instance for the type of events. The `class` attribute specifies the class in the plugin that implements the listener interface and receives the events.

As a specific example, to receive events about all [Virtual File System](https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html) changes, implement the `BulkFileListener` interface, corresponding to the topic `VirtualFileManager.VFS_CHANGES`. To subscribe to this topic from code, use something like the following snippet:

```java
messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES,
    new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        // handle the events
      }
});
```

To use declarative registration, it's no longer required to reference the `Topic` instance. Instead, refer directly to the listener interface class:

```markup
<applicationListeners>
  <listener
      class="myPlugin.MyVfsListener"
      topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
</applicationListeners>
```

Then provide the listener implementation:

```java
package myPlugin;

final class MyVfsListener implements BulkFileListener {
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    // handle the events
  }
}
```

## Defining Project-Level Listeners﻿

[Project](https://plugins.jetbrains.com/docs/intellij/project.html)-level listeners are registered in the same way, except that the top-level tag is [`<projectListeners>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__projectListeners). They can be used to listen to project-level events, for example, [tool window](https://plugins.jetbrains.com/docs/intellij/tool-windows.html) operations:

```markup
<idea-plugin>
  <projectListeners>
    <listener
        class="myPlugin.MyToolWindowListener"
        topic="com.intellij.openapi.wm.ex.ToolWindowManagerListener"/>
  </projectListeners>
</idea-plugin>
```

The class implementing the listener interface can define a one-argument constructor accepting a [`Project`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/project/Project.java), and it will receive the instance of the project for which the listener is created:

```java
package myPlugin;

final class MyToolWindowListener implements ToolWindowManagerListener {
  private final Project project;

  MyToolWindowListener(Project project) {
    this.project = project;
  }

  @Override
  public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
    // handle the state change
  }
}
```

## Additional Attributes﻿

Registration of listeners can be restricted using the following attributes.

`os`

Allows restricting listener to a given OS, e.g., `os="windows"` for Windows only.

`activeInTestMode`

Set to `false` to disable listener if [`Application.isUnitTestMode()`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/application/Application.java) returns `true`

`activeInHeadlessMode`

Set to `false` to disable the listener if [`Application.isHeadlessEnvironment()`](../../reference-Repository/intellij-community/platform/core-api/src/com/intellij/openapi/application/Application.java) returns `true`. Also covers `activeInTestMode` as test mode implies headless mode.

> ### note
>
> If declared listener topics are intended to be used by other plugins depending on your plugin, consider [bundling their sources](https://plugins.jetbrains.com/docs/intellij/bundling-plugin-openapi-sources.html) in the plugin distribution.
