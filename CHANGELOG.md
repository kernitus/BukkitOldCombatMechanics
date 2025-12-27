# Changelog

## [2.3.0](https://github.com/kernitus/BukkitOldCombatMechanics/compare/v2.2.0...v2.3.0) (2025-12-27)


### Features

* copper tools support [#822](https://github.com/kernitus/BukkitOldCombatMechanics/issues/822) [#823](https://github.com/kernitus/BukkitOldCombatMechanics/issues/823) ([47ffa96](https://github.com/kernitus/BukkitOldCombatMechanics/commit/47ffa963ecc8619f064b98054d35df81f89a682d))
* kotlin integration tests ([c63c940](https://github.com/kernitus/BukkitOldCombatMechanics/commit/c63c940fcc292d4db3a27185a613a01e7c5c04f0))


### Tests

* 1.12 integration tests ([3e4b6ce](https://github.com/kernitus/BukkitOldCombatMechanics/commit/3e4b6ce9e171530b9fd9254c26c11fb65842b7c1))
* downgrade kotest to 5.x to use java 8 ([977f118](https://github.com/kernitus/BukkitOldCombatMechanics/commit/977f118475f596ecd4689fccf4c0d48d17f0fd0d))
* fix double add & remove players ([46d8858](https://github.com/kernitus/BukkitOldCombatMechanics/commit/46d88581d87ac0a1e6d88399f04f66f9920f3188))
* gapple & potions & knockback & durability & sweep ([a46d54b](https://github.com/kernitus/BukkitOldCombatMechanics/commit/a46d54b41ac0c91a2c7f1b29efbead0fbfc4d843))
* paper version matrix ([d1527d3](https://github.com/kernitus/BukkitOldCombatMechanics/commit/d1527d3dc036696b95c85d8467feeb04c0480549))
* skip old version sleep ([b8f0d29](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b8f0d2900f4c3d128f4fd0ad2919f24c55f02508))
* suppress silly warnings ([63447dc](https://github.com/kernitus/BukkitOldCombatMechanics/commit/63447dc044c0e0f8947c62f9ee9b7f17f0038152))
* undeprecate ([edbfef4](https://github.com/kernitus/BukkitOldCombatMechanics/commit/edbfef4e6601fc48a76a8aaf34fb0aec25e49e68))

## [2.2.0](https://github.com/kernitus/BukkitOldCombatMechanics/compare/v2.1.0...v2.2.0) (2025-10-14)


### Features

* add fallback sound reflection logic ([1592dc2](https://github.com/kernitus/BukkitOldCombatMechanics/commit/1592dc249870ad3113b924cdebd41cbc36b68ad5))


### Bug Fixes

* ocm mode permissions ([e2f0369](https://github.com/kernitus/BukkitOldCombatMechanics/commit/e2f0369f294e250e8cfc474bbd1121498ecf09fe)), closes [#818](https://github.com/kernitus/BukkitOldCombatMechanics/issues/818)
* update checker versioning logic ([8d88a49](https://github.com/kernitus/BukkitOldCombatMechanics/commit/8d88a49d0fa1e8c7dfa7c48cf66c8399c766d6e0))
* use reflection for inventory view ([462e536](https://github.com/kernitus/BukkitOldCombatMechanics/commit/462e536628f3df544c9cf0fe42705eff46b7d4f6)), closes [#812](https://github.com/kernitus/BukkitOldCombatMechanics/issues/812)


### Documentation

* update issue templates ([5927729](https://github.com/kernitus/BukkitOldCombatMechanics/commit/5927729ea5fcca44a6218b10f40cec3f8ce3d4a3))
* update issue templates ([09fd3c5](https://github.com/kernitus/BukkitOldCombatMechanics/commit/09fd3c556ad02d25caa875e7dfe5854888a920cf))
* use yaml issue forms ([fc51924](https://github.com/kernitus/BukkitOldCombatMechanics/commit/fc51924504e3718b9829f4a7deca7a8701000f9f))

## [2.1.0](https://github.com/kernitus/BukkitOldCombatMechanics/compare/v2.0.4...v2.1.0) (2025-08-21)


### Features

* compat with 1.21.8 enums ([68c51ab](https://github.com/kernitus/BukkitOldCombatMechanics/commit/68c51ab8803da56f477660af247c37e5171bc581))
* make config upgrader remove deprecated keys ([e728374](https://github.com/kernitus/BukkitOldCombatMechanics/commit/e72837462fb8c512c9971c1e7d9376c82f37e741))
* remove deprecated modules ([da3d5f0](https://github.com/kernitus/BukkitOldCombatMechanics/commit/da3d5f0b28990d8f654b8557e68f54f41e8b5a60))


### Bug Fixes

* check fishing knockback module enabled when reeling in ([31e3877](https://github.com/kernitus/BukkitOldCombatMechanics/commit/31e3877330cdd820173a5d89e021919b153c6988)), closes [#803](https://github.com/kernitus/BukkitOldCombatMechanics/issues/803)
* disable attack sounds not working in &gt;1.21 ([b355322](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b355322f9b3f5e1c2b1e889684d6242f08ceee92)), closes [#794](https://github.com/kernitus/BukkitOldCombatMechanics/issues/794)
* ExceptionInInitialiserError in enchantment compat ([b2379cc](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b2379cc17e309b035c2acb396392723f15ed3ee2)), closes [#782](https://github.com/kernitus/BukkitOldCombatMechanics/issues/782)
* improve sound packet compatibility ([4ef28fb](https://github.com/kernitus/BukkitOldCombatMechanics/commit/4ef28fb7bd63631fa4b6f0366d23dd1e159aa115)), closes [#780](https://github.com/kernitus/BukkitOldCombatMechanics/issues/780)
* null pointer in potion compat ([b30e27d](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b30e27ddb32d210371152feee8d337f27ea8f495)), closes [#791](https://github.com/kernitus/BukkitOldCombatMechanics/issues/791)
* unmentioned worlds modesets not allowed [#792](https://github.com/kernitus/BukkitOldCombatMechanics/issues/792) ([95c9446](https://github.com/kernitus/BukkitOldCombatMechanics/commit/95c9446edbd0fe56bce0864798cf8d1c70865f8b))


### Refactoring

* improve fishing knockback cross-version compat ([d119c9f](https://github.com/kernitus/BukkitOldCombatMechanics/commit/d119c9f35e1a89be8fb8d03573041cfc2ae2d418))

## [2.0.4](https://github.com/kernitus/BukkitOldCombatMechanics/compare/2.0.3...v2.0.4) (2024-10-28)


### Bug Fixes

* avoid ghost items after sword block restore ([822fb1f](https://github.com/kernitus/BukkitOldCombatMechanics/commit/822fb1fa147fc49266cb9f0668869959e341982e)), closes [#749](https://github.com/kernitus/BukkitOldCombatMechanics/issues/749)
* don't prevent moving shield to chests in disable offhand module ([b299df2](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b299df2d21ace1c7e88b1ee8fafb297e2a9347e8))
* error when right clicking air while holding block in &lt;1.13 ([cbc0c4b](https://github.com/kernitus/BukkitOldCombatMechanics/commit/cbc0c4bc8bf0afd56005699ce70f86ec9b637646)), closes [#754](https://github.com/kernitus/BukkitOldCombatMechanics/issues/754)
* listen to dynamically loaded worlds for modesets ([f5b59d7](https://github.com/kernitus/BukkitOldCombatMechanics/commit/f5b59d7537d410fac35fbb4e0181a61a485ae1a5)), closes [#747](https://github.com/kernitus/BukkitOldCombatMechanics/issues/747)
* resolve elytras always unequipped by removing out-of-scope module ([07106e6](https://github.com/kernitus/BukkitOldCombatMechanics/commit/07106e61a220ec4137a3de200a393cf6aaa50be7)), closes [#725](https://github.com/kernitus/BukkitOldCombatMechanics/issues/725)
* fix sword blocking shield ending up in inventory on world change ([8aa3fa3](https://github.com/kernitus/BukkitOldCombatMechanics/commit/8aa3fa33081c1e1b1a48baa484fd6946b275362b)), closes [#753](https://github.com/kernitus/BukkitOldCombatMechanics/issues/753)
