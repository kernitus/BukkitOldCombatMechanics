package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.block.BlockFace
import org.bukkit.inventory.EquipmentSlot
import io.kotest.matchers.shouldBe

@OptIn(ExperimentalKotest::class)
class SwordBlockingIntegrationTest(private val plugin: JavaPlugin) : StringSpec({
    extension(MainThreadDispatcherExtension(plugin))
    concurrency = 1  // Run tests sequentially
}) {
    private lateinit var player: Player
    private lateinit var fakePlayer: FakePlayer

    override suspend fun beforeSpec(spec: Spec) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.logger.info("Running before all")
            preparePlayer()

            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0 // remove spawn invulnerability
            player.isInvulnerable = false
        })
    }

    override suspend fun afterSpec(spec: Spec) {
        plugin.logger.info("Running after all")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            fakePlayer.removePlayer()
        })
    }

    fun preparePlayer() {
        println("Preparing player")
        val world = Bukkit.getServer().getWorld("world")
        val location = Location(world, 0.0, 100.0, 0.0)

        fakePlayer = FakePlayer(plugin)
        fakePlayer.spawn(location)

        player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
    }

    init {
        "test sword blocking" {
            player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))

            // Simulate right-clicking
            val event = PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, player.inventory.itemInMainHand, null, BlockFace.SELF, EquipmentSlot.HAND)
            Bukkit.getPluginManager().callEvent(event)

            // Check if player is blocking and has a shield in off-hand
            player.isBlocking shouldBe true
            player.inventory.itemInOffHand.type shouldBe Material.SHIELD
        }
    }
}

