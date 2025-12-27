/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldArmourDurability
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class OldArmourDurabilityIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleOldArmourDurability>()
        .firstOrNull() ?: error("ModuleOldArmourDurability not registered")

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
                action()
                null
            }).get()
        }
    }

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val reduction = ocm.config.getInt("old-armour-durability.reduction")
        try {
            block()
        } finally {
            ocm.config.set("old-armour-durability.reduction", reduction)
            module.reload()
            ModuleLoader.toggleModules()
        }
    }

    fun setModeset(modeset: String) {
        val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, playerData)
    }

    fun createItemDamageEvent(item: ItemStack, damage: Int): PlayerItemDamageEvent {
        val ctor = PlayerItemDamageEvent::class.java.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 4 &&
                Player::class.java.isAssignableFrom(params[0]) &&
                ItemStack::class.java.isAssignableFrom(params[1]) &&
                params[2] == Int::class.javaPrimitiveType &&
                params[3] == Int::class.javaPrimitiveType
        }
        return if (ctor != null) {
            ctor.newInstance(player, item, damage, damage) as PlayerItemDamageEvent
        } else {
            PlayerItemDamageEvent(player, item, damage)
        }
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = Bukkit.getServer().getWorld("world")
            val location = Location(world, 0.0, 100.0, 0.0)
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(location)
            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
            player.isOp = true
            setModeset("old")
        }
    }

    afterSpec {
        runSync {
            fakePlayer.removePlayer()
        }
    }

    beforeTest {
        runSync {
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            setModeset("old")
            module.reload()
        }
    }

    context("Armour durability reduction") {
        test("worn armour takes reduced durability") {
            withConfig {
                ocm.config.set("old-armour-durability.reduction", 2)
                module.reload()

                val helmet = ItemStack(Material.DIAMOND_HELMET)
                player.inventory.helmet = helmet

                val event = createItemDamageEvent(helmet, 5)
                Bukkit.getPluginManager().callEvent(event)

                event.damage shouldBe 2
            }
        }

        test("non-armour items are ignored") {
            withConfig {
                ocm.config.set("old-armour-durability.reduction", 2)
                module.reload()

                val sword = ItemStack(Material.DIAMOND_SWORD)
                player.inventory.setItemInMainHand(sword)

                val event = createItemDamageEvent(sword, 5)
                Bukkit.getPluginManager().callEvent(event)

                event.damage shouldBe 5
            }
        }

        test("elytra is ignored") {
            withConfig {
                ocm.config.set("old-armour-durability.reduction", 2)
                module.reload()

                val elytra = ItemStack(Material.ELYTRA)
                player.inventory.chestplate = elytra

                val event = createItemDamageEvent(elytra, 5)
                Bukkit.getPluginManager().callEvent(event)

                event.damage shouldBe 5
            }
        }
    }

    context("Explosion handling") {
        test("explosion damage bypasses durability reduction") {
            withConfig {
                ocm.config.set("old-armour-durability.reduction", 2)
                module.reload()

                val helmet = ItemStack(Material.DIAMOND_HELMET)
                player.inventory.helmet = helmet

                val explosion = EntityDamageEvent(player, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 6.0)
                Bukkit.getPluginManager().callEvent(explosion)

                val event = createItemDamageEvent(helmet, 5)
                Bukkit.getPluginManager().callEvent(event)

                event.damage shouldBe 5
            }
        }
    }
})
