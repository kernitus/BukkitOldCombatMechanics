/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.projectile

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.ModuleOldPotionThrowing
import kernitus.plugin.OldCombatMechanics.module.ModuleOldProjectileTrajectory
import kernitus.plugin.OldCombatMechanics.module.ModuleProjectileShootOffset
import kernitus.plugin.OldCombatMechanics.module.ModuleRelativeProjectileVelocity
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.Collections
import java.util.Random
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.sin

/** Coordinates independent corrections at the native post-shoot, pre-insertion boundary. */
class ProjectileLaunchCoordinator(
    private val plugin: OCMMain,
    private val trajectory: ModuleOldProjectileTrajectory,
    private val offset: ModuleProjectileShootOffset,
    private val relative: ModuleRelativeProjectileVelocity,
    private val potions: ModuleOldPotionThrowing,
) {
    private val listener = object : Listener {}
    private val identity = NativeLaunchIdentity()
    private val movement = NativeLaunchMovement(plugin)
    private val insertion = ProjectileInsertion(plugin) { _, player -> offset.isEnabled(player) }
    private val corrected = Collections.newSetFromMap(WeakHashMap<Projectile, Boolean>())

    private data class Launch(
        val projectile: Projectile,
        val player: Player,
        val material: Material,
        val force: Double,
        val inherited: Vector?,
        val origin: Location,
    )

    private val launches = WeakHashMap<Event, Launch>()
    private val random = Random()
    private var throwableAttribution = true
    private var reportedAbsence = false
    private var customBows = emptySet<Material>()

    init {
        reloadCustomBows()
        offset.reloadLaunchState = {
            reloadCustomBows()
            insertion.clear()
            corrected.clear()
            launches.clear()
            if (!throwableAttribution && !reportedAbsence &&
                (trajectory.isEnabled || offset.isEnabled || relative.isEnabled)
            ) {
                reportedAbsence = true
                plugin.logger.info(
                    "Generic projectile launch modules preserve native launches: this server has no safe native item attribution hook.",
                )
            }
        }
        registerThrowables()
        plugin.server.pluginManager.registerEvent(
            EntityShootBowEvent::class.java,
            listener,
            EventPriority.LOWEST,
            { _, raw ->
                val event = raw as EntityShootBowEvent
                val player = event.entity as? Player
                val projectile = event.projectile as? Projectile
                val bow = event.bow
                if (player != null && projectile != null && bow != null && (bow.type == Material.BOW || bow.type in customBows) &&
                    !projectile.isValid && projectile.shooter == player &&
                    projectile.type.name in setOf("ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW") &&
                    identity.nativeDispatch(true) && identity.mainHand(player, bow)
                ) {
                    launches[event] = snapshot(projectile, player, Material.BOW, event.force.toDouble())
                }
            },
            plugin,
        )
        plugin.server.pluginManager.registerEvent(
            EntityShootBowEvent::class.java,
            listener,
            EventPriority.HIGHEST,
            { _, raw ->
                val event = raw as EntityShootBowEvent
                // A nested redispatch of this same object must not consume the outer native context.
                if (identity.nativeDispatch(true)) {
                    val launch = launches.remove(event)
                    if (!event.isCancelled && launch != null && event.projectile === launch.projectile) {
                        correct(launch)
                    }
                }
            },
            plugin,
        )
        plugin.addDisableListener {
            HandlerList.unregisterAll(listener)
            corrected.clear()
            launches.clear()
        }
    }

    private fun reloadCustomBows() {
        customBows =
            plugin.config.getStringList("projectile-launch.custom-bows").mapNotNull { name ->
                Material.matchMaterial(name.trim()).also {
                    if (it == null) plugin.logger.warning("Unknown projectile-launch.custom-bows material: $name")
                }
            }.toSet()
    }

    private fun registerThrowables() {
        val type =
            try {
                Class
                    .forName(
                        "com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent",
                    ).asSubclass(Event::class.java)
            } catch (_: ClassNotFoundException) {
                throwableAttribution = false
                return
            }
        val projectileGetter = checkNotNull(Reflector.getMethod(type, "getProjectile", 0))
        val itemGetter = checkNotNull(Reflector.getMethod(type, "getItemStack", 0))
        plugin.server.pluginManager.registerEvent(
            type,
            listener,
            EventPriority.LOWEST,
            { _, event ->
                if (identity.nativeDispatch(false)) {
                    val projectile = projectileGetter.invoke(event) as? Projectile
                    val player = projectile?.shooter as? Player
                    val item = itemGetter.invoke(event) as? ItemStack
                    if (projectile != null && player != null && item != null && identity.mainHand(player, item) &&
                        !projectile.isValid && matches(item.type, projectile.type.name)
                    ) {
                        launches[event] = snapshot(projectile, player, item.type, 1.0)
                    }
                }
            },
            plugin,
        )
        plugin.server.pluginManager.registerEvent(
            type,
            listener,
            EventPriority.HIGHEST,
            { _, event ->
                if (identity.nativeDispatch(false)) {
                    val launch = launches.remove(event)
                    if (!(event as Cancellable).isCancelled && launch != null &&
                        projectileGetter.invoke(event) === launch.projectile
                    ) {
                        correct(launch)
                    }
                }
            },
            plugin,
        )
    }

    private fun snapshot(
        projectile: Projectile,
        player: Player,
        material: Material,
        force: Double,
    ): Launch {
        // Native shootFromRotation has already run. Preserve its movement, ground state and Paper switch
        // at the first attributed callback, before later listeners can change the shooter or world.
        val potionOwned = material == Material.SPLASH_POTION && potions.isEnabled(player)
        val inherited =
            if (!potionOwned && (trajectory.isEnabled(player) || relative.isEnabled(player))) {
                movement.inherited(player)?.clone()
            } else {
                null
            }
        val origin = player.location
        val yaw = Math.toRadians(origin.yaw.toDouble())
        origin.add(-cos(yaw) * 0.16, (if (player.isSneaking) 1.54 else 1.62) - 0.1, -sin(yaw) * 0.16)
        // Changes made before this first observer cannot be distinguished from native launch state.
        return Launch(projectile, player, material, force, inherited, origin)
    }

    private fun matches(
        material: Material,
        entity: String,
    ): Boolean =
        when (material.name) {
            "EGG" -> entity == "EGG"
            "SNOWBALL" -> entity == "SNOWBALL"
            "ENDER_PEARL" -> entity == "ENDER_PEARL"
            "SPLASH_POTION" -> entity == "SPLASH_POTION" || entity == "POTION"
            "EXPERIENCE_BOTTLE", "EXP_BOTTLE" -> entity == "EXPERIENCE_BOTTLE" || entity == "THROWN_EXP_BOTTLE"
            else -> false
        }

    private fun correct(launch: Launch) {
        val (projectile, player, material, force, inherited) = launch
        val splash = material == Material.SPLASH_POTION
        // The existing potion module owns its complete launch, including configured gravity and inheritance.
        if (splash && potions.isEnabled(player)) return
        val changeTrajectory = trajectory.isEnabled(player)
        val changeOffset = offset.isEnabled(player)
        val removeMovement = relative.isEnabled(player)
        if ((!changeTrajectory && !changeOffset && !removeMovement) || !corrected.add(projectile)) return
        if (changeTrajectory || removeMovement) {
            if (inherited != null) {
                projectile.velocity =
                    if (changeTrajectory) {
                        legacyVelocity(launch.origin, material, force).add(if (removeMovement) Vector() else inherited)
                    } else {
                        projectile.velocity.subtract(inherited)
                    }
            }
        }
        if (changeOffset) {
            insertion.position(projectile, launch.origin.clone())
        }
    }

    private fun legacyVelocity(
        eye: Location,
        material: Material,
        force: Double,
    ): Vector {
        val yaw = Math.toRadians(eye.yaw.toDouble())
        val pitch = Math.toRadians(eye.pitch.toDouble())
        val bottle = material.name == "EXPERIENCE_BOTTLE" || material.name == "EXP_BOTTLE"
        val splash = material == Material.SPLASH_POTION
        // Minecraft 1.8.9 EntityThrowable constructor and setThrowableHeading, EntityArrow constructor
        // and setThrowableHeading, EntityPotion.getInaccuracy, EntityExpBottle.getInaccuracy.
        // Pinned MavenMCP-1.8.9 source: 6bc307e9f937728886d3859987c0d3b441d14557.
        val yPitch = Math.toRadians(eye.pitch + if (splash || bottle) -20.0 else 0.0)
        val velocity = Vector(-sin(yaw) * cos(pitch), -sin(yPitch), cos(yaw) * cos(pitch))
        if (velocity.lengthSquared() > 1e-24) velocity.normalize() else velocity.zero()
        velocity.add(
            Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).multiply(0.007499999832361937),
        )
        val speed =
            when {
                material == Material.BOW -> 3.0 * force
                splash -> 0.5
                bottle -> 0.7
                else -> 1.5
            }
        return velocity.multiply(speed)
    }
}
