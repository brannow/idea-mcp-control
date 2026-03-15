# Plugin Logo﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_structure/plugin_icon_file.md) Last modified: 24 April 2025

The IntelliJ Platform supports representing a plugin with a logo. A Plugin Logo is intended to be a unique representation of a plugin's functionality, technology, or company.

When opening plugin.xml in editor, inspection Plugin DevKit \| Plugin descriptor \| Plugin Logo check (2024.3+, for earlier versions Plugin DevKit \| Plugin descriptor \| Plugin.xml validity) will highlight a missing plugin icon.

Note: icons and images used within a plugin have different requirements. See [Working with Icons](https://plugins.jetbrains.com/docs/intellij/icons.html) for more information.

## Plugin Logo Usages﻿

Plugin Logos are shown in the [JetBrains Marketplace](https://plugins.jetbrains.com/). They also appear in the Settings [Plugin Manager](https://www.jetbrains.com/help/idea/managing-plugins.html) UI in IntelliJ Platform-based IDEs. Whether online or in the product UI, a Plugin Logo helps users to identify a plugin more quickly in a list, as shown below:

![Example Product Plugin Settings Dialog](https://plugins.jetbrains.com/docs/intellij/images/plugin_prefs.png)

> ### note
>
> When browsing [custom plugin repositories](https://plugins.jetbrains.com/docs/intellij/custom-plugin-repository.html), there is no support for showing logos for plugins hosted there but not yet installed.

## Plugin Logo Requirements﻿

> ### note
>
> Please see also these [important requirements](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-logo) for JetBrains Marketplace.

For a Plugin Logo to be displayed correctly within an IntelliJ Platform-based IDE, it must:

- Follow the best practices design guidelines,

- Be in the correct file format,

- Conform to file name conventions,

- Have the correct size,

- Be in the META-INF folder of the plugin distribution file.


### Plugin Logo Size﻿

The Plugin Logo should be provided in one size: 40px by 40px.

A Plugin Logo is displayed in two sizes, and scales automatically in each context:

- 40px by 40px in the plugins list in the Plugin Manager UI.

- 80px by 80px in the plugin details screen in the Plugin Manager UI and on the plugin's page in JetBrains Marketplace.


Verify that Plugin Logo designs are effective in both sizes and all display contexts.

### Plugin Logo Shape﻿

Plugin Logo designs should leave at least 2px transparent padding around the perimeter, as shown below:

![36px by 36px is the area where the visible part of the Logo should fit](https://plugins.jetbrains.com/docs/intellij/images/icon_size.png)

Make sure Plugin Logos have the same visual weight as the logos in the examples below. The more filled a Plugin Logo design is, the less actual space it needs. See more examples of [visual weight compensation](https://plugins.jetbrains.com/docs/intellij/icons-style.html#basic-shapes) in the UI Guidelines for Icons.

For basic shapes, use the following sizes. Note the different areas of transparent padding used for each shape:

|     |     |
| --- | --- |
| ![Square 32px by 32px](https://plugins.jetbrains.com/docs/intellij/images/square_logo.png) | ![Circle 36px in diameter](https://plugins.jetbrains.com/docs/intellij/images/circle_logo.png) |
| Square logo 32px by 32px | Circular logo 36px in diameter |
| ![Horizontal rectangle 36px by 26px](https://plugins.jetbrains.com/docs/intellij/images/rectangle_horizontal.png) | ![Vertical rectangle 26px by 36px](https://plugins.jetbrains.com/docs/intellij/images/rectangle_vertical.png) |
| Horizontal rectangular logo 36px by 26px | Vertical rectangular logo 26px by 36px |

### Plugin Logo Colors﻿

If the plugin's technology already has a logo, use its colors. Check the license terms before using the logo. If there is no existing logo, or its use is prohibited, create a custom logo based on the [Action Colors Palette](https://plugins.jetbrains.com/docs/intellij/icons-style.html#action-icons) in the UI Guidelines for Icons.

|     |     |
| --- | --- |
| ![The YouTrack Plugin Logo uses the YouTrack product logo ](https://plugins.jetbrains.com/docs/intellij/images/yt_logo.png) | ![The Keymap Plugin Logo uses a color from the Action Colors Palette](https://plugins.jetbrains.com/docs/intellij/images/keymap_logo.png) |
| The YouTrack Plugin Logo uses<br>the YouTrack product logo | The Keymap Plugin Logo uses a color<br>from the Action Colors Palette |

Ensure a Plugin Logo is visible on both light and dark backgrounds. If one Plugin Logo design does not work on both light and dark backgrounds, create separate light and dark versions of the Plugin Logo. The examples below illustrate how a Plugin Logo design may work well for a light background but not for a dark background. Consequently, a separate Plugin Logo for dark backgrounds is needed.

|     |     |     |
| --- | --- | --- |
| ![Plugin Logo on Light Theme](https://plugins.jetbrains.com/docs/intellij/images/light_version.png) | ![Light Plugin Logo on Dark Theme](https://plugins.jetbrains.com/docs/intellij/images/dark_bad.png) | ![Plugin Logo for Dark Theme](https://plugins.jetbrains.com/docs/intellij/images/dark_good.png) |
| The light Plugin Logo design<br>works well on light theme | The light Plugin Logo design does<br>not work well on a dark theme | A separate, dark Plugin Logo design<br>works well on dark theme |

### Plugin Logo File Format﻿

All Plugin Logo images must be in SVG format. This vector image format is required because the Plugin Logo file must be small (ideally less than 2-3kB), and the image must scale without any loss of quality.

> ### warning
>
> Using automatic conversion of bitmap graphics to SVG is highly discouraged, as the resulting files have excessive size (100kB or more).

### Plugin Logo File Naming Convention﻿

Name the Plugin Logo files according to the following conventions:

- pluginIcon.svg is the default Plugin Logo. If a separate Logo file for dark themes exists in the plugin, then this file is used solely for light themes,

- pluginIcon\_dark.svg is an optional, alternative Plugin Logo for use solely with dark IDE themes.


## Adding Plugin Logo Files to a Plugin Project﻿

The Plugin Logo files must be packaged in the META-INF folder of the [plugin main JAR file](https://plugins.jetbrains.com/docs/intellij/plugin-content.html).

To include Plugin Logo files, place them into the plugin project's resources/META-INF folder. For example:

![Plugin Logo Files in META-INF folder](https://plugins.jetbrains.com/docs/intellij/images/resource_directory_structure.png)
