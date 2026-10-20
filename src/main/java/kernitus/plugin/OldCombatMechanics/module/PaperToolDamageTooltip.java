/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/** Paper's per-modifier display text changes the tooltip without changing combat attributes. */
final class PaperToolDamageTooltip {
    private static final String OWNER = "ocm:old-tool-damage-tooltip";
    private static final PaperToolDamageTooltip INSTANCE = create();

    private final Object type;
    private final Method getData;
    private final Method isDataOverridden;
    private final Method setData;
    private final Method resetData;
    private final Method unsetData;
    private final Method defaultData;
    private final Method modifiers;
    private final Method builder;
    private final Method addModifier;
    private final Method build;
    private final Method attribute;
    private final Method modifier;
    private final Method group;
    private final Method display;
    private final Method override;
    private final Method resetDisplay;
    private final Method overrideText;
    private final Class<?> overrideTextType;

    private PaperToolDamageTooltip() throws ReflectiveOperationException {
        Class<?> dataType = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
        Class<?> valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");
        Class<?> attributes = Class.forName("io.papermc.paper.datacomponent.item.ItemAttributeModifiers");
        Class<?> attributesBuilder = Class.forName("io.papermc.paper.datacomponent.item.ItemAttributeModifiers$Builder");
        Class<?> entry = Class.forName("io.papermc.paper.datacomponent.item.ItemAttributeModifiers$Entry");
        Class<?> displayType = Class.forName("io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay");
        overrideTextType = Class.forName("io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay$OverrideText");
        Field field = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes").getField("ATTRIBUTE_MODIFIERS");
        type = field.get(null);
        getData = ItemStack.class.getMethod("getData", valued);
        isDataOverridden = ItemStack.class.getMethod("isDataOverridden", dataType);
        setData = ItemStack.class.getMethod("setData", valued, Object.class);
        resetData = ItemStack.class.getMethod("resetData", dataType);
        unsetData = ItemStack.class.getMethod("unsetData", dataType);
        defaultData = Material.class.getMethod("getDefaultData", valued);
        modifiers = attributes.getMethod("modifiers");
        builder = attributes.getMethod("itemAttributes");
        addModifier = attributesBuilder.getMethod("addModifier", Attribute.class, AttributeModifier.class,
                EquipmentSlotGroup.class, displayType);
        build = attributesBuilder.getMethod("build");
        attribute = entry.getMethod("attribute");
        modifier = entry.getMethod("modifier");
        group = entry.getMethod("getGroup");
        display = entry.getMethod("display");
        override = displayType.getMethod("override", net.kyori.adventure.text.ComponentLike.class);
        resetDisplay = displayType.getMethod("reset");
        overrideText = overrideTextType.getMethod("text");
    }

    static boolean apply(ItemStack item, double damage) {
        return INSTANCE != null && INSTANCE.update(item, damage);
    }

    static void strip(ItemStack item) {
        if (INSTANCE != null) INSTANCE.restore(item);
    }

    static void setLoreMeta(ItemStack item, org.bukkit.inventory.meta.ItemMeta meta) {
        if (INSTANCE == null) {
            item.setItemMeta(meta);
            return;
        }
        // Bukkit ItemMeta does not retain Paper's per-entry display settings.
        Object attributes = Reflector.invokeMethod(INSTANCE.getData, item, INSTANCE.type);
        boolean explicit = Boolean.TRUE.equals(Reflector.invokeMethod(INSTANCE.isDataOverridden, item, INSTANCE.type));
        item.setItemMeta(meta);
        if (explicit && attributes != null) {
            Reflector.invokeMethod(INSTANCE.setData, item, INSTANCE.type, attributes);
        } else if (explicit) {
            Reflector.invokeMethod(INSTANCE.unsetData, item, INSTANCE.type);
        } else {
            Reflector.invokeMethod(INSTANCE.resetData, item, INSTANCE.type);
        }
    }

    private static PaperToolDamageTooltip create() {
        try {
            return new PaperToolDamageTooltip();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null; // Spigot and older Paper retain the existing lore tooltip.
        }
    }

    private List<?> entries(Object component) {
        return Reflector.invokeMethod(modifiers, component);
    }

    private boolean owned(Object entry) {
        Object value = Reflector.invokeMethod(display, entry);
        return overrideTextType.isInstance(value)
                && OWNER.equals(((Component) Reflector.invokeMethod(overrideText, value)).insertion());
    }

    private boolean damageEntry(Object entry) {
        Attribute key = Reflector.invokeMethod(attribute, entry);
        AttributeModifier value = Reflector.invokeMethod(modifier, entry);
        return "ATTACK_DAMAGE".equals(key.getKey().getKey().toUpperCase(java.util.Locale.ROOT))
                && "minecraft:base_attack_damage".equals(value.getKey().toString());
    }

    private Object rewritten(List<?> entries, Object replacement, boolean restoring) {
        Object result = Reflector.invokeMethod(builder, null);
        for (Object entry : entries) {
            Object shown = Reflector.invokeMethod(display, entry);
            if (restoring ? owned(entry) : damageEntry(entry)) shown = replacement;
            Reflector.invokeMethod(addModifier, result,
                    Reflector.invokeMethod(attribute, entry), Reflector.invokeMethod(modifier, entry),
                    Reflector.invokeMethod(group, entry), shown);
        }
        return Reflector.invokeMethod(build, result);
    }

    private boolean update(ItemStack item, double damage) {
        Object current = Reflector.invokeMethod(getData, item, type);
        if (current == null) return false;
        List<?> entries = entries(current);
        if (entries.stream().noneMatch(this::damageEntry)) return false;
        // Only default-derived components are ours to change. Explicit foreign components,
        // including vanilla-equivalent ones, retain their original display and patch state.
        if (entries.stream().noneMatch(this::owned)
                && Boolean.TRUE.equals(Reflector.invokeMethod(isDataOverridden, item, type))) return false;
        Object nativeDisplay = Reflector.invokeMethod(resetDisplay, null);
        if (entries.stream().anyMatch(entry -> damageEntry(entry) && !owned(entry)
                && !sameDisplay(Reflector.invokeMethod(display, entry), nativeDisplay))) return false;

        String amount = ModuleOldToolDamage.formatDamage(damage);
        Component text = Component.text(" ").append(Component.translatable("attribute.modifier.equals.0",
                Component.text(amount), Component.translatable("attribute.name.attack_damage")))
                .color(NamedTextColor.DARK_GREEN).insertion(OWNER);
        Object shown = Reflector.invokeMethod(override, null, text);
        if (entries.stream().filter(this::damageEntry)
                .allMatch(entry -> sameDisplay(Reflector.invokeMethod(display, entry), shown))) return true;
        Reflector.invokeMethod(setData, item, type, rewritten(entries, shown, false));
        return true;
    }

    private void restore(ItemStack item) {
        Object current = Reflector.invokeMethod(getData, item, type);
        if (current == null || entries(current).stream().noneMatch(this::owned)) return;
        Object nativeDisplay = Reflector.invokeMethod(resetDisplay, null);
        Object restored = rewritten(entries(current), nativeDisplay, true);
        Object defaults = Reflector.invokeMethod(defaultData, item.getType(), type);
        if (defaults != null && sameEntries(entries(restored), entries(defaults))) {
            Reflector.invokeMethod(resetData, item, type);
        } else {
            Reflector.invokeMethod(setData, item, type, restored);
        }
    }

    private boolean sameEntries(List<?> left, List<?> right) {
        if (!sameModifiers(left, right)) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!sameDisplay(Reflector.invokeMethod(display, left.get(i)), Reflector.invokeMethod(display, right.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameModifiers(List<?> left, List<?> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            Object a = left.get(i);
            Object b = right.get(i);
            if (!Objects.equals(Reflector.invokeMethod(attribute, a), Reflector.invokeMethod(attribute, b))
                    || !Objects.equals(Reflector.invokeMethod(modifier, a), Reflector.invokeMethod(modifier, b))
                    || !Objects.equals(Reflector.invokeMethod(group, a), Reflector.invokeMethod(group, b))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameDisplay(Object left, Object right) {
        if (!left.getClass().equals(right.getClass())) return false;
        return !overrideTextType.isInstance(left)
                || Objects.equals(Reflector.invokeMethod(overrideText, left), Reflector.invokeMethod(overrideText, right));
    }
}
