/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Runtime API capability checks shared by compatibility-sensitive call sites.
 */
public final class CompatibilityCapabilities {
    private static final Method BLOCK_IS_PASSABLE_METHOD = findMethod(Block.class, "isPassable");
    private static final Method YAML_PARSE_COMMENTS_METHOD = findYamlParseCommentsMethod();

    private CompatibilityCapabilities() {
    }

    public static boolean isBlockPassable(Block block) {
        if (BLOCK_IS_PASSABLE_METHOD == null) {
            return !block.getType().isSolid();
        }

        try {
            return Boolean.TRUE.equals(BLOCK_IS_PASSABLE_METHOD.invoke(block));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodError ignored) {
            return !block.getType().isSolid();
        }
    }

    public static boolean canPreserveYamlComments() {
        return YAML_PARSE_COMMENTS_METHOD != null;
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
