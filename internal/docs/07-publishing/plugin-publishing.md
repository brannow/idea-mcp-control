# Publishing a Plugin

When a plugin is ready, it can be published to the [JetBrains Marketplace](https://plugins.jetbrains.com/) plugin repository so that other users can install it in their IDE.

The first plugin publication must always be [uploaded manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#uploading-a-plugin-to-jetbrains-marketplace).

### Before Publishing Checklist

Before publishing a plugin, make sure it:

- follows all recommendations from [Plugin User Experience (UX)](https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html)

- follows all requirements from [Plugin Overview page](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html)


The webinar Busy Plugin Developers. Episode 2 discusses [5 tips for optimizing JetBrains Marketplace plugin page](https://youtu.be/oB1GA9JeeiY?t=52) in more detail.

See also [Marketing](https://plugins.jetbrains.com/docs/intellij/marketing.html) about widgets and badges.

## Uploading a Plugin to JetBrains Marketplace

Before publishing a plugin, make sure it is signed. For more details on generating a proper certificate and configuring the signing process, check the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) article.

### Creating a JetBrains Account

To upload a plugin to the [JetBrains Marketplace](https://plugins.jetbrains.com/), log in with your personal JetBrains Account.

1. Open the [JetBrains Account Center](https://account.jetbrains.com/) and click Create Account.

2. Fill in all fields in the Create JetBrains Account form that opens and click Register.


### Uploading plugin

To upload a plugin to [JetBrains Marketplace](https://plugins.jetbrains.com/):

1. [Log in to JetBrains Marketplace](https://plugins.jetbrains.com/author/me) with your personal JetBrains account.

2. On the Profile page that opens, click Add new plugin.

3. Fill in the Add new plugin form that opens and click the Add the plugin button to upload the plugin.


See also [Marketplace Docs](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html).

### Uploading a New Version

New versions can be uploaded manually on the plugin's detail page, see [Marketplace Docs](https://plugins.jetbrains.com/docs/marketplace/plugin-updates.html) for details. See [Deploying a Plugin with Gradle](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#deploying-a-plugin-with-gradle) on how to publish new versions using Gradle.

## Publishing Plugin With Gradle

Once [Gradle support](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html) has been configured, and the plugin has been [uploaded manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#uploading-a-plugin-to-jetbrains-marketplace) to the plugin repository at least once, it can be built and deployed to the [JetBrains Marketplace](https://plugins.jetbrains.com/) automatically using dedicated Gradle tasks.

### Building Distribution

For the initial upload, manual distribution, or local installation, invoke the `buildPlugin` Gradle task (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#buildPlugin), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-buildplugin)) to create the plugin distribution. If the project is configured to rely on [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html), use the `signPlugin` task instead (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#signPlugin), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-signplugin)).

The resulting ZIP file is located in build/distributions and can then be installed in the IDE via [Install Plugin from Disk...](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk) action or uploaded to a [Custom Plugin Repository](https://plugins.jetbrains.com/docs/intellij/custom-plugin-repository.html).

### Providing Your Personal Access Token to Gradle

To deploy a plugin to the JetBrains Marketplace, supply the Personal Access Token, which can be found on your profile page in the [My Tokens](https://plugins.jetbrains.com/author/me/tokens) section.

To create a new token, provide its name and click the Generate Token button. A new token will be created and displayed right below.

This section describes two options to supply the Personal Access Token via Gradle using:

- Environment variables,

- Parameters to the Gradle task.


#### Using Environment Variables

Start by defining an environment variable such as:

export ORG\_GRADLE\_PROJECT\_intellijPlatformPublishingToken='YOUR\_TOKEN'

Now provide the environment variable in the run configuration for running the `publishPlugin` task locally (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#publishPlugin), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-publishplugin)). To do so, create a Gradle run configuration (if not already done), select the Gradle project, specify the `publishPlugin` task, and then add the environment variable.

intellijPlatform {
publishing {
token = providers.gradleProperty("intellijPlatformPublishingToken")
}
}

tasks {
publishPlugin {
token = providers.gradleProperty("intellijPlatformPublishingToken")
}
}

Note that it's still required to put some default values (can be empty) in the Gradle properties. Otherwise, there can be a compilation error.

#### Using Parameters for the Gradle Task

Like using environment variables, the token can also be passed as a parameter to the Gradle task. For example, provide the parameter on the command line or by putting it in the arguments of the Gradle run configuration.

-PintellijPlatformPublishingToken=YOUR\_TOKEN

Note that in this case also, it's still required to put some default values (can be empty) in the Gradle properties

### Deploying a Plugin with Gradle

The first step when deploying a plugin is to confirm that it works correctly. Verify this by [installing the plugin from disk](https://www.jetbrains.com/help/idea/managing-plugins.html) in a fresh instance of the target IDE(s).

#### Signing a Plugin

The Marketplace signing is designed to ensure that plugins are not modified over the course of the publishing and delivery pipeline. The `signPlugin` Gradle task (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#signPlugin), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-signplugin)), will be executed automatically right before the `publishPlugin` task (Reference: [2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#publishPlugin), [1.x](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-publishplugin)).

For more details on generating a proper certificate and configuring the `signPlugin` task, see [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

#### Publishing a Plugin

Once the plugin works as intended, make sure the plugin version is updated, as the JetBrains Marketplace won't accept multiple artifacts with the same version.

To deploy a new version of the plugin to the JetBrains Marketplace, invoke the `publishPlugin` Gradle task.

Now check the most recent version of the plugin on the [JetBrains Marketplace](https://plugins.jetbrains.com/). If successfully deployed, any users who currently have this plugin installed on an available version of the IntelliJ Platform are notified of a new update available as soon as the update has been verified.

### Specifying a Release Channel

It's possible to deploy plugins to a chosen release channel by configuring the [`intellijPlatform.publishing.channels`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-publishing-channels) extension property.

intellijPlatform {
publishing {
channels = listOf("beta")
}
}

intellijPlatform {
publishing {
channels = \['beta'\]
}
}

It's possible to deploy plugins to a chosen release channel by configuring the [`publishPlugin.channels`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-publishplugin-channels) task property.

tasks {
publishPlugin {
channels = listOf("beta")
}
}

tasks {
publishPlugin {
channels = \['beta'\]
}
}

When empty, this uses the default plugin repository, available to all [JetBrains Marketplace](https://plugins.jetbrains.com/) users. However, it's possible to publish it to an arbitrarily named channel. These non-default release channels are treated as separate repositories.

When using a non-default release channel, users need to configure a new [custom plugin repository](https://www.jetbrains.com/help/idea/managing-plugins.html#repos) in their IDE to install the plugin.

For example, when specifying `'canary'` as channel name, users will need to add the `https://plugins.jetbrains.com/plugins/canary/list` repository to install the plugin and receive updates.

Popular channel names include:

- `alpha`: https://plugins.jetbrains.com/plugins/alpha/list

- `beta`: https://plugins.jetbrains.com/plugins/beta/list

- `eap`: https://plugins.jetbrains.com/plugins/eap/list


08 April 2025

[Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) [Plugin Structure](https://plugins.jetbrains.com/docs/intellij/plugin-structure.html)
