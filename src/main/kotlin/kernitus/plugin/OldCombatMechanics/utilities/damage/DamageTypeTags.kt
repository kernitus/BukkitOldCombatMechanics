/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.event.entity.EntityDamageEvent
import java.lang.reflect.Method

/** Reads live damage tags without linking legacy servers to modern Bukkit types. */
class DamageTypeTags private constructor(
    private val damageType: Any?,
    val key: String?,
) {
    /** Null means the server cannot answer; false means the tag exists but excludes this type. */
    fun contains(tag: String): Boolean? {
        val api = access ?: return null
        val type = damageType ?: return null
        return try {
            val tagKey = api.keys[tag] ?: return null
            // Resolve tags afresh so datapack reloads cannot leave cached membership behind.
            val liveTag = api.getTag.invoke(null, api.registry, tagKey, api.damageTypeClass) ?: return null
            api.isTagged.invoke(liveTag, type) as? Boolean
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    fun matches(
        tag: String,
        fallback: Boolean,
    ): Boolean = contains(tag) ?: fallback

    companion object {
        private val names =
            listOf(
                "bypasses_armor",
                "bypasses_resistance",
                "bypasses_effects",
                "bypasses_enchantments",
                "is_fire",
                "is_explosion",
                "is_projectile",
                "is_fall",
            )
        private val access: Access? = createAccess()
        private val unavailable = DamageTypeTags(null, null)

        @JvmStatic
        fun from(event: EntityDamageEvent): DamageTypeTags {
            val api = access ?: return unavailable
            return try {
                val source = api.getSource.invoke(event) ?: return unavailable
                val type = api.getType.invoke(source) ?: return unavailable
                val key = api.getKey.invoke(type).toString()
                // Deprecated event constructors supply GENERIC irrespective of their supplied cause.
                if (key == "minecraft:generic" && event.cause != EntityDamageEvent.DamageCause.CUSTOM) {
                    unavailable
                } else {
                    DamageTypeTags(type, key)
                }
            } catch (_: ReflectiveOperationException) {
                unavailable
            } catch (_: LinkageError) {
                unavailable
            }
        }

        private fun createAccess(): Access? =
            try {
                val type = Class.forName("org.bukkit.damage.DamageType")
                val source = Class.forName("org.bukkit.damage.DamageSource")
                val key = Class.forName("org.bukkit.NamespacedKey")
                val getTag = Bukkit::class.java.getMethod("getTag", String::class.java, key, Class::class.java)
                val minecraft = key.getMethod("minecraft", String::class.java)
                val keys = names.associateWith { minecraft.invoke(null, it) }
                val registry =
                    listOf("damage_type", "damage_types").firstOrNull {
                        try {
                            getTag.invoke(null, it, keys.getValue("bypasses_armor"), type) != null
                        } catch (
                            _: ReflectiveOperationException,
                        ) {
                            false
                        }
                    }
                if (registry == null) {
                    null
                } else {
                    Access(
                        EntityDamageEvent::class.java.getMethod("getDamageSource"),
                        source.getMethod("getDamageType"),
                        type.getMethod("getKey"),
                        getTag,
                        Reflector.getMethod(Class.forName("org.bukkit.Tag"), "isTagged", 1),
                        type,
                        registry,
                        keys,
                    )
                }
            } catch (_: ReflectiveOperationException) {
                null
            } catch (_: LinkageError) {
                null
            }
    }

    private class Access(
        val getSource: Method,
        val getType: Method,
        val getKey: Method,
        val getTag: Method,
        val isTagged: Method,
        val damageTypeClass: Class<*>,
        val registry: String,
        val keys: Map<String, Any>,
    )
}
