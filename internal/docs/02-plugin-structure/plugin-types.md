# Plugin Types﻿

[Edit page](https://github.com/JetBrains/intellij-sdk-docs/edit/main/topics/basics/plugin_types.md) Last modified: 26 February 2024

Products based on the IntelliJ Platform can be modified and adjusted for custom purposes by adding plugins. All downloadable plugins are available from the [JetBrains Marketplace](https://plugins.jetbrains.com/).

The most common types of plugins include:

- Custom language support

- Framework integration

- Tool integration

- User interface add-ons

- Themes


> ### note
>
> Plugin Alternatives
>
> In some cases, implementing an actual IntelliJ Platform plugin might not be necessary, as [alternative solutions](https://plugins.jetbrains.com/docs/intellij/plugin-alternatives.html) exist.

## Custom Language Support﻿

Custom language support provides basic functionality for working with a particular programming language, that includes:

- File type recognition

- Lexical analysis

- Syntax highlighting

- Formatting

- Code insight and code completion

- Inspections and quick fixes

- Intention actions


Plugins can also augment existing (bundled) custom languages, e.g., by providing additional inspections, intentions, or any other features.

Refer to the [Custom Language Support Tutorial](https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html) to learn more about the topic.

## Framework Integration﻿

Framework integration consists of improved code insight features, which are typical for a given framework, as well as the option to use framework-specific functionality directly from the IDE. Sometimes it also includes language support elements for a custom syntax or DSL.

- Specific code insight

- Direct access to framework-specific functionality


Refer to the [IntelliJ-HCL](https://github.com/JetBrains/intellij-plugins/tree/idea/253.31033.145/terraform) as an example of framework integration. More reference plugins can be found on [JetBrains Marketplace](https://plugins.jetbrains.com/search?orderBy=update%20date&shouldHaveSource=true&tags=Framework).

## Tool Integration﻿

Tool integration makes it possible to manipulate third-party tools and components directly from the IDE without switching contexts, that implies:

- Implementation of additional actions

- Related UI components

- Access to external resources


Refer to the [Gerrit integration](https://plugins.jetbrains.com/plugin/7272) plugin as an example.

## User Interface Add-Ons﻿

Plugins in this category apply various changes to the standard user interface of the IDE. Some newly added components are interactive and provide new functionality, while others are limited to visual modifications only. The [Foldable ProjectView](https://plugins.jetbrains.com/plugin/17288-foldable-projectview) plugin may serve as an example.

## Themes﻿

[Themes](https://plugins.jetbrains.com/docs/intellij/themes-getting-started.html) give designers the ability to customize the appearance of built-in IDE UI elements.
