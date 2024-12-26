# Changelog

## [3.0.0](https://github.com/kernitus/BukkitOldCombatMechanics/compare/v2.0.4...v3.0.0) (2024-12-26)


### ⚠ BREAKING CHANGES

* Remove no lapis enchantments module
* Remove disable projectile randomness & disable bow boost modules

### Features

* remove showMessage from enderpearl & crafting modules, relying on empty to disable ([a57bfab](https://github.com/kernitus/BukkitOldCombatMechanics/commit/a57bfabd6b28770764b34dd119e3adabf3799b1f))


### Documentation

* grammar ([02ec0c4](https://github.com/kernitus/BukkitOldCombatMechanics/commit/02ec0c470ea687581f9319e631c89ebf27aec6e4))
* update list of modules in readme ([3f6c0b4](https://github.com/kernitus/BukkitOldCombatMechanics/commit/3f6c0b48c5277737c760daa2b699ca41c906de8c))


### Refactoring

* convert codebase to kotlin ([0ee22a5](https://github.com/kernitus/BukkitOldCombatMechanics/commit/0ee22a52bef970d4fdc50ef5029ee98a1cfbc2d8))
* convert updated & commands to kotlin ([6c338fd](https://github.com/kernitus/BukkitOldCombatMechanics/commit/6c338fd3d76f1afd86cd5933fdc7971e59194f97))
* fix onPLayerQuit return type warnings ([9bef3ef](https://github.com/kernitus/BukkitOldCombatMechanics/commit/9bef3ef129dbe4697b73d03ecd69e351fa472b73))
* more idiomatic kotlin & null safety ([53a4e42](https://github.com/kernitus/BukkitOldCombatMechanics/commit/53a4e42ab808fea9cf60fcd042b3a412e790a217))

## [2.0.4](https://github.com/kernitus/BukkitOldCombatMechanics/compare/2.0.3...v2.0.4) (2024-10-28)


### Bug Fixes

* avoid ghost items after sword block restore ([822fb1f](https://github.com/kernitus/BukkitOldCombatMechanics/commit/822fb1fa147fc49266cb9f0668869959e341982e)), closes [#749](https://github.com/kernitus/BukkitOldCombatMechanics/issues/749)
* don't prevent moving shield to chests in disable offhand module ([b299df2](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b299df2d21ace1c7e88b1ee8fafb297e2a9347e8))
* error when right clicking air while holding block in &lt;1.13 ([cbc0c4b](https://github.com/kernitus/BukkitOldCombatMechanics/commit/cbc0c4bc8bf0afd56005699ce70f86ec9b637646)), closes [#754](https://github.com/kernitus/BukkitOldCombatMechanics/issues/754)
* listen to dynamically loaded worlds for modesets ([f5b59d7](https://github.com/kernitus/BukkitOldCombatMechanics/commit/f5b59d7537d410fac35fbb4e0181a61a485ae1a5)), closes [#747](https://github.com/kernitus/BukkitOldCombatMechanics/issues/747)
* resolve elytras always unequipped by removing out-of-scope module ([07106e6](https://github.com/kernitus/BukkitOldCombatMechanics/commit/07106e61a220ec4137a3de200a393cf6aaa50be7)), closes [#725](https://github.com/kernitus/BukkitOldCombatMechanics/issues/725)
* fix sword blocking shield ending up in inventory on world change ([8aa3fa3](https://github.com/kernitus/BukkitOldCombatMechanics/commit/8aa3fa33081c1e1b1a48baa484fd6946b275362b)), closes [#753](https://github.com/kernitus/BukkitOldCombatMechanics/issues/753)
