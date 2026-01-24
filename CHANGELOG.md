# Changelog

## [2.3.0](https://github.com/kernitus/BukkitOldCombatMechanics/compare/v2.2.0...v2.3.0) (2026-01-24)


### Features

* 1.8 hitbox ([5cbd93d](https://github.com/kernitus/BukkitOldCombatMechanics/commit/5cbd93d89dde101dd7e750d4ee13e733b6dfd4a2)), closes [#69](https://github.com/kernitus/BukkitOldCombatMechanics/issues/69)
* always & disabled modules lists ([5f7bf12](https://github.com/kernitus/BukkitOldCombatMechanics/commit/5f7bf127412e2675294d27703ac6562a4c8e0c9d))
* always & disabled modules lists ([18dd6e1](https://github.com/kernitus/BukkitOldCombatMechanics/commit/18dd6e17aeb5e391fbb5704c0726d05e0b676971))
* copper tools support [#822](https://github.com/kernitus/BukkitOldCombatMechanics/issues/822) [#823](https://github.com/kernitus/BukkitOldCombatMechanics/issues/823) ([47ffa96](https://github.com/kernitus/BukkitOldCombatMechanics/commit/47ffa963ecc8619f064b98054d35df81f89a682d))
* custom trident & mace damage ([be70c5b](https://github.com/kernitus/BukkitOldCombatMechanics/commit/be70c5bb7756699fe4e3cb5bced450df3308f195)), closes [#757](https://github.com/kernitus/BukkitOldCombatMechanics/issues/757)
* item damage lore ([0ca855a](https://github.com/kernitus/BukkitOldCombatMechanics/commit/0ca855a21634c933ab0626770a74f756e57849fe)), closes [#775](https://github.com/kernitus/BukkitOldCombatMechanics/issues/775)
* kotlin integration tests ([c63c940](https://github.com/kernitus/BukkitOldCombatMechanics/commit/c63c940fcc292d4db3a27185a613a01e7c5c04f0))
* switch from protocollib to packetevents ([44afce1](https://github.com/kernitus/BukkitOldCombatMechanics/commit/44afce1a0f01a0ad54b39662e3597a8e371c5454)), closes [#790](https://github.com/kernitus/BukkitOldCombatMechanics/issues/790)
* sword blocking animation [#769](https://github.com/kernitus/BukkitOldCombatMechanics/issues/769) ([8596c9d](https://github.com/kernitus/BukkitOldCombatMechanics/commit/8596c9da58dc2167d05ac878d4a7809014ff02d8))
* warn on unknown effects, enchants, etc ([fa828d3](https://github.com/kernitus/BukkitOldCombatMechanics/commit/fa828d3469ca18b51ffd0871bd8e510f0c831d1c))


### Bug Fixes

* 'disable-offhand' module working even if disabled ([ecca0b5](https://github.com/kernitus/BukkitOldCombatMechanics/commit/ecca0b56d6f8c1d9b2e96d190ae2098dfeb10fc4))
* `disable-offhand` handling on modeset change ([54aaf0c](https://github.com/kernitus/BukkitOldCombatMechanics/commit/54aaf0c1a0a812c89c39ae0c49fe6300760a8ecc))
* apply old tool damage to all mobs ([91b121f](https://github.com/kernitus/BukkitOldCombatMechanics/commit/91b121ff009971586c877778e6a75309088ba667)), closes [#735](https://github.com/kernitus/BukkitOldCombatMechanics/issues/735)
* chorus fruit tp into blocks ([bba1ecb](https://github.com/kernitus/BukkitOldCombatMechanics/commit/bba1ecb62ec9faf1526189f308798d3b25f43cf9)), closes [#748](https://github.com/kernitus/BukkitOldCombatMechanics/issues/748)
* clear modules list on reload ([ebdfd10](https://github.com/kernitus/BukkitOldCombatMechanics/commit/ebdfd101ae1393dbf97c431726b09ea797cc267d))
* double strength effect when old-potion-effects disabled ([4cecb64](https://github.com/kernitus/BukkitOldCombatMechanics/commit/4cecb649c8e03c0c2fd593b907da778e3f7dc453)), closes [#781](https://github.com/kernitus/BukkitOldCombatMechanics/issues/781)
* fire damage overwriting lastDamage ([9af8a0f](https://github.com/kernitus/BukkitOldCombatMechanics/commit/9af8a0fa796513ab88588612f5551c5bf582db32)), closes [#707](https://github.com/kernitus/BukkitOldCombatMechanics/issues/707)
* legacy (pre-1.11) sweep detection ([2825b35](https://github.com/kernitus/BukkitOldCombatMechanics/commit/2825b35b4c6b0b879389da170e6d85b9441d9799))
* negative last damage ([1321717](https://github.com/kernitus/BukkitOldCombatMechanics/commit/1321717288ec9624065d86b00aa441f9a1404b52)), closes [#765](https://github.com/kernitus/BukkitOldCombatMechanics/issues/765)
* only strip consumable on swords ([e01faea](https://github.com/kernitus/BukkitOldCombatMechanics/commit/e01faea183d8387371dbd62e89fb847deb2fc38e)), closes [#841](https://github.com/kernitus/BukkitOldCombatMechanics/issues/841)
* reflection error on weapon enchant ([10ff4ce](https://github.com/kernitus/BukkitOldCombatMechanics/commit/10ff4ceead5147b87c13ffea1401ef716596b80c)), closes [#840](https://github.com/kernitus/BukkitOldCombatMechanics/issues/840)
* skip unknown sound packets ([cfa1e58](https://github.com/kernitus/BukkitOldCombatMechanics/commit/cfa1e58b5b5e481e1427458d22a98e487b32a621))
* unknown particles. ([1408720](https://github.com/kernitus/BukkitOldCombatMechanics/commit/140872008929ecc49918bdf0cc8e40d226957054)), closes [#825](https://github.com/kernitus/BukkitOldCombatMechanics/issues/825)
* weakness calc on &gt;=1.20 ([f502adb](https://github.com/kernitus/BukkitOldCombatMechanics/commit/f502adbbece03875e491bd1f39e4a45d1f498802))
* weakness calculations for amplifier &gt; 1 ([d866015](https://github.com/kernitus/BukkitOldCombatMechanics/commit/d86601504eb6d16481e18946cf07e7432039cd92))


### Performance

* **attack-cooldown-tracker:** gate sampler by API presence and use HashMap for stable caching ([b605501](https://github.com/kernitus/BukkitOldCombatMechanics/commit/b605501b40d350457d5227ce24c9c9a9522ed1f9))
* **disable-enderpearl-cooldown:** use HashMap and lazily drop expired cooldown entries ([83ff726](https://github.com/kernitus/BukkitOldCombatMechanics/commit/83ff726c5a9a2c2e6cc16571ce6e2d37123341ab))
* **fishing-rod-velocity:** replace per-hook gravity tasks with single shared tick runner ([5856bed](https://github.com/kernitus/BukkitOldCombatMechanics/commit/5856bed51830c51b0b6c205cb010fcb8dd9be0ac))
* **old-armour-durability:** replace per-explosion suppression task with shared 1-tick expiry cleaner ([c7932d6](https://github.com/kernitus/BukkitOldCombatMechanics/commit/c7932d6f0b32966ad38cdb81f8c83b14ca438478))
* **old-player-regen:** switch to tick-based interval tracking with shared counter task ([4f8df3c](https://github.com/kernitus/BukkitOldCombatMechanics/commit/4f8df3c5d0781240a46a4ed7e1ce10043aafb8d1))
* **player-knockback:** replace per-hit cleanup tasks with shared 1-tick expiry cleaner ([9667ef5](https://github.com/kernitus/BukkitOldCombatMechanics/commit/9667ef539e033ede2b97fadf309cd805df6900c5))
* **shield-damage-reduction:** replace per-hit fully-blocked cleanup tasks with shared 1-tick expiry cleaner ([a18b1ef](https://github.com/kernitus/BukkitOldCombatMechanics/commit/a18b1efec039c35b12f2c7c53dcc2be875545b29))
* **sword-block:** reduce amount of recurring tasks ([a08b5b4](https://github.com/kernitus/BukkitOldCombatMechanics/commit/a08b5b47bc5c1645db885ffca794e3ac7d9592ba))
* use one task to clear EDBEE map ([11ec51b](https://github.com/kernitus/BukkitOldCombatMechanics/commit/11ec51bd609339b8e41d74ef2d8697c3083a7d5f))

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
