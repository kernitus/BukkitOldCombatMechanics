package kernitus.plugin.OldCombatMechanics.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Native component fallback for builds predating Paper's public component operations. */
final class LegacySwordComponents implements PaperSwordBlocking.Components {

    private final MethodHandle nmsSetComponent;
    private final MethodHandle nmsGetComponentsPatch;
    private final MethodHandle nmsPatchGet;
    private final MethodHandle nmsPatchEntrySet;
    private final Object nmsConsumableType;
    private final Object nmsConsumableComponent;
    private volatile Field craftItemStackHandleField;
    private final MethodHandle nmsGetComponent;
    private final MethodHandle nmsGetComponents;
    private final MethodHandle restorePatch;
    private final Class<?> patchClass;
    private final org.bukkit.NamespacedKey marker = new org.bukkit.NamespacedKey("oldcombatmechanics", "sword_consumable");

    public LegacySwordComponents() throws Exception {
        // Build the NMS consumable component: Consumable.builder().consumeSeconds(MAX).animation(BLOCK).build()
        final Class<?> nmsConsumable = Class.forName("net.minecraft.world.item.component.Consumable");
        final Object nmsConsumableBuilder = nmsConsumable.getMethod("builder").invoke(null);
        final Class<?> nmsUseAnim = Class.forName("net.minecraft.world.item.ItemUseAnimation");
        final Object nmsBlockAnim = nmsUseAnim.getField("BLOCK").get(null);
        final Object nmsConsumableValue = nmsConsumableBuilder.getClass().getMethod("consumeSeconds", float.class)
            .invoke(nmsConsumableBuilder, 2_592_000.0f); // needs to be less than 2_147_483_647
        final Object nmsConsumableValue2 = nmsConsumableValue.getClass().getMethod("animation", nmsUseAnim)
            .invoke(nmsConsumableValue, nmsBlockAnim);
        final Object consumableComponent = nmsConsumableValue2.getClass().getMethod("build").invoke(nmsConsumableValue2);
        nmsConsumableComponent = consumableComponent;

        final Class<?> nmsDataComponents = Class.forName("net.minecraft.core.component.DataComponents");
        nmsConsumableType = nmsDataComponents.getField("CONSUMABLE").get(null);
        final Class<?> nmsPatch = Class.forName("net.minecraft.core.component.DataComponentPatch");
        patchClass = nmsPatch;
        final Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        final Class<?> nmsComponentType = Class.forName("net.minecraft.core.component.DataComponentType");
        nmsSetComponent = lookup.unreflect(findItemStackSetMethod(nmsItemStack, nmsComponentType));
        nmsGetComponent = lookup.unreflect(nmsItemStack.getMethod("get", nmsComponentType));
        nmsGetComponents = lookup.unreflect(nmsItemStack.getMethod("getComponents"));
        final Class<?> patchedMap = Class.forName("net.minecraft.core.component.PatchedDataComponentMap");
        restorePatch = lookup.unreflect(patchedMap.getMethod("restorePatch", nmsPatch));

        nmsGetComponentsPatch = lookup.unreflect(nmsItemStack.getMethod("getComponentsPatch"));
        MethodHandle patchGet = null;
        try {
            patchGet = lookup.unreflect(nmsPatch.getMethod("get", nmsComponentType));
        } catch (NoSuchMethodException ignored) {
        }
        nmsPatchGet = patchGet;
        nmsPatchEntrySet = lookup.unreflect(nmsPatch.getMethod("entrySet"));

    }

    private Field resolveCraftItemStackHandleField(ItemStack stack) throws NoSuchFieldException {
        final Field cached = craftItemStackHandleField;
        if (cached != null) return cached;

        Class<?> c = stack.getClass();
        while (c != null && c != Object.class) {
            try {
                final Field f = c.getDeclaredField("handle");
                f.setAccessible(true);
                craftItemStackHandleField = f;
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("No 'handle' field found on " + stack.getClass().getName());
    }

    private Method findPatchSetMethod(Class<?> builderClass) throws NoSuchMethodException {
        for (Method m : builderClass.getMethods()) {
            if (!m.getName().equals("set")) continue;
            if (m.getParameterCount() != 2) continue;
            return m;
        }
        throw new NoSuchMethodException("DataComponentPatch.Builder#set(type, value) not found");
    }

    private Object nativeStack(ItemStack stack) throws IllegalAccessException, NoSuchFieldException {
        return resolveCraftItemStackHandleField(stack).get(stack);
    }

    public void applyComponents(ItemStack stack) {
        if (stack == null || !isSword(stack.getType())) return;
        try {
            final Object nms = nativeStack(stack);
            if (nms == null || nmsGetComponent.invoke(nms, nmsConsumableType) != null) return;
            final Object patch = nmsGetComponentsPatch.invoke(nms);
            for (Object value : (Set<?>) nmsPatchEntrySet.invoke(patch)) {
                if (((Map.Entry<?, ?>) value).getKey() == nmsConsumableType) return;
            }
            final org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(marker, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
            nmsSetComponent.invoke(nativeStack(stack), nmsConsumableType, nmsConsumableComponent);
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot apply legacy sword component", failure);
        }
    }

    public boolean clearComponents(ItemStack stack) {
        if (stack == null || !isSword(stack.getType())) return false;
        try {
            final org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (!meta.getPersistentDataContainer().has(marker, org.bukkit.persistence.PersistentDataType.BYTE)) return false;
            final boolean owned = nmsConsumableComponent.equals(nmsGetComponent.invoke(nativeStack(stack), nmsConsumableType));
            meta.getPersistentDataContainer().remove(marker);
            stack.setItemMeta(meta);
            if (!owned) return true;
            final Object nms = nativeStack(stack);
            final Object patch = nmsGetComponentsPatch.invoke(nms);
            final Object builder = patchClass.getMethod("builder").invoke(null);
            final Method set = findPatchSetMethod(builder.getClass());
            final Method remove = builder.getClass().getMethod("remove", Class.forName("net.minecraft.core.component.DataComponentType"));
            for (Object value : (Set<?>) nmsPatchEntrySet.invoke(patch)) {
                final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                if (entry.getKey() == nmsConsumableType) continue;
                final Optional<?> component = (Optional<?>) entry.getValue();
                if (component.isPresent()) set.invoke(builder, entry.getKey(), component.get());
                else remove.invoke(builder, entry.getKey());
            }
            restorePatch.invoke(nmsGetComponents.invoke(nms), builder.getClass().getMethod("build").invoke(builder));
            return true;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot clear legacy sword component", failure);
        }
    }

    private Method findItemStackSetMethod(Class<?> nmsItemStackClass, Class<?> nmsComponentTypeClass) throws NoSuchMethodException {
        for (Method m : nmsItemStackClass.getMethods()) {
            if (!m.getName().equals("set")) continue;
            if (m.getParameterCount() != 2) continue;
            if (!m.getParameterTypes()[0].isAssignableFrom(nmsComponentTypeClass)) continue;
            return m;
        }
        throw new NoSuchMethodException("ItemStack#set(component, value) not found");
    }

    public boolean hasConsumableComponent(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !isSword(stack.getType())) return false;
        try {
            final Field handleField = resolveCraftItemStackHandleField(stack);
            final Object nms = handleField.get(stack);
            if (nms == null) return false;
            final Object patch = nmsGetComponentsPatch.invoke(nms);
            if (patch == null) return false;
            if (nmsPatchGet != null) {
                final Object entry = nmsPatchGet.invoke(patch, nmsConsumableType);
                if (!(entry instanceof Optional)) return false;
                return ((Optional<?>) entry).isPresent();
            }
            final Object entries = nmsPatchEntrySet.invoke(patch);
            if (!(entries instanceof Set)) return false;
            for (final Object entryObject : (Set<?>) entries) {
                if (!(entryObject instanceof Map.Entry)) continue;
                final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                if (entry.getKey() != nmsConsumableType) continue;
                final Object value = entry.getValue();
                return value instanceof Optional && ((Optional<?>) value).isPresent();
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSword(Material mat) {
        return mat != null && mat.name().endsWith("_SWORD");
    }
}
