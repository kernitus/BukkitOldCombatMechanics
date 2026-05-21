/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Runtime API capability checks shared by compatibility-sensitive call sites.
 */
public final class CompatibilityCapabilities {
    private static final Method PLAYER_ATTACK_METHOD = findMethod(Player.class, "attack", Entity.class);
    private static final Method YAML_PARSE_COMMENTS_METHOD = findYamlParseCommentsMethod();
    private static volatile boolean blockIsPassableAvailable = true;

    private CompatibilityCapabilities() {
    }

    public static boolean isBlockPassable(Block block) {
        if (!blockIsPassableAvailable) {
            return legacyBlockPassable(block);
        }

        try {
            // Chorus-fruit safe-location checks may call passability several times per teleport attempt;
            // direct calls with a cached linkage fallback avoid repeated reflective invocation on supported runtimes.
            return block.isPassable();
        } catch (IncompatibleClassChangeError ignored) {
            blockIsPassableAvailable = false;
            return legacyBlockPassable(block);
        }
    }

    private static boolean legacyBlockPassable(Block block) {
        return !block.getType().isSolid();
    }

    public static boolean canPreserveYamlComments() {
        return YAML_PARSE_COMMENTS_METHOD != null;
    }

    public static boolean isMaterialAvailable(String materialName) {
        return Material.matchMaterial(materialName) != null;
    }

    public static boolean isBukkitClassAvailable(String className) {
        try {
            Class.forName(className, false, CompatibilityCapabilities.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isPlayerAttackApiAvailable(Class<?> playerClass) {
        return PLAYER_ATTACK_METHOD != null && findMethod(playerClass, "attack", Entity.class) != null;
    }

    public static boolean isKnockbackResistanceAvailable() {
        final Attribute knockbackResistanceAttribute = XAttribute.KNOCKBACK_RESISTANCE.get();
        return isMaterialAvailable("NETHERITE_BOOTS") && knockbackResistanceAttribute != null;
    }

    public static YamlConfiguration loadCommentPreservingYaml(File file) {
        final YamlConfiguration configuration = new YamlConfiguration();
        if (YAML_PARSE_COMMENTS_METHOD != null) {
            try {
                YAML_PARSE_COMMENTS_METHOD.invoke(configuration.options(), true);
                configuration.load(file);
                return configuration;
            } catch (IllegalAccessException | InvocationTargetException | IOException | InvalidConfigurationException ignored) {
                // Fall through to Bukkit's standard loader; the caller can still migrate values safely.
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private static Method findMethod(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findYamlParseCommentsMethod() {
        final Method method = findMethod(YamlConfiguration.class, "options");
        if (method == null) {
            return null;
        }
        try {
            return method.getReturnType().getMethod("parseComments", boolean.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
