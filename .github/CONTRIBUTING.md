<!--
     This Source Code Form is subject to the terms of the Mozilla Public
     License, v. 2.0. If a copy of the MPL was not distributed with this
     file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

# Contributing
All contributions should be in the form of [pull requests](https://github.com/kernitus/BukkitOldCombatMechanics/pulls), and should follow the formatting guidance below. All pull requests must be fully functional and able to compile, and should be fully tested. Please submit **monofocal** pull requests, i.e.if you're making unrelated changes to two different modules, or decide to also update the version of a dependency, *those should be separate pull requests*.

## Testing expectations
Contributions are expected to include automated test coverage within the existing test framework where practical. For behaviour changes, bug fixes, and new features, prefer adding or updating tests in the existing Kotlin integration-test harness rather than relying on manual testing alone.

## Language expectations
New classes should be written in Kotlin by default. If you are modifying an existing Java class, keep the surrounding file consistent unless there is a clear reason to migrate it as part of the same change.

## Local setup
Install [pre-commit](https://pre-commit.com/) with your preferred Python package manager, for example:

```shell
pipx install pre-commit
```

Then install and test the repository hooks:

```shell
pre-commit install
pre-commit run --all-files
```

Before committing or opening a pull request, run:

```shell
./gradlew spotlessCheck
./gradlew check
```

Also run the relevant integration tests when behaviour, compatibility, or bug-fix work warrants them, such as `./gradlew integrationTest` or a targeted task like `./gradlew integrationTest1_19_2`.

## Module system
OldCombatMechanics uses a modular system to make sure each feature is completely independent from any other, and can be toggled off and have no impact on server performance. This means each new module must extend the `Module` class, and implement a public constructor which takes an instance of the plugin and passes the module name to the superconstructor. The `Module` class also provides an overloadable `reload()` method which is called whenever the plugin is reloaded. If you are using any class-level variables they should probably be updated in here. This should also be used for any initialisation that might need to be done if the config section is changed and `ocm reload` is called. You may then call `reload()` from the constructor.

## Module naming
The name specified in the module constructor must be the same as the one used in the config.yml. The module name must meaningfully describe the purpose of the module, for example `disable-offhand` or `old-burn-delay`. The module classes should thus be respectively named `ModuleDisableOffhand` and `ModuleOldBurnDelay`. As you can see, kebab case is used for the constructor and config name, while pascal case prefixed by `Module` is used for class names.

## Module configuration
All module configuration is done through the Module class in a subsection of the config.yml for each module. To access config variables under the module section, you can use the methods provided by `module()`, such as `module().getBoolean("enableBlue")`. The module config section must contain an `enabled` boolean key which is used by the module system to selectively register/unregister the module, and, if applicable, a `worlds: []` list key to configure which worlds your module will work in. It is the responsibility of the module to use the `module().isEnabled(world)` to make sure this is enforced.

## Code style
There is relative freedom when it codes to coding style, but please adhere to the following guidelines:
* Use `final` for variables whenever possible. This makes it much less likely to accidentally change a variable and improves code readability.
* Use `if !condition return;` pattern to avoid eccessive nested if-statements. That is, to check multiple prerequisite conditions, invert the condition and return, and after returning continue with the code.
* Use internal utilies where possible. There is a `Messenger` class for sending messages to users or console, a `Reflector` class for simple reflection, and `DualVersionedMaterial` for support across minecraft versions where the Bukkit item names have changed. There are many more in the `utilities` package, the usage of which can be found in already existing modules.

## Formatting
The repository root `.editorconfig` is the source of truth for IntelliJ and EditorConfig-aware editors. Use `./gradlew spotlessCheck` to verify formatting and `./gradlew spotlessApply` to apply formatting fixes.

Java is not formatted with Google Java Format. Continue to follow the repository IntelliJ/EditorConfig style for Java changes.
