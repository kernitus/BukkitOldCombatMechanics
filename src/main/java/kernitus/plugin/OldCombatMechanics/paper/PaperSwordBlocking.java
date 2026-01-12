package kernitus.plugin.OldCombatMechanics.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

/**
 * Paper-only helper: NMS reflection-based to avoid linking Paper API at compile time.
 *
 * - Patch the underlying NMS ItemStack (DataComponents.CONSUMABLE) so the item is actually usable and the server
 *   can drive the "active item"/hand-raised state.
 *
 * Performance: all reflective lookups are done once in the constructor; hot calls only use cached MethodHandles.
 * We deliberately swallow failures here: if Paper changes internals, we prefer "legacy fallback" over hard failing.
 */
public class PaperSwordBlocking {

    private final MethodHandle nmsApplyComponents;
    private final Object addConsumablePatch;
    private final Object removeConsumablePatch;
    private volatile Field craftItemStackHandleField;

    private final MethodHandle craftPlayerGetHandle;
    private final MethodHandle nmsGetUseItem;
    private final MethodHandle nmsItemStackIs;
    private final Object nmsSwordTag;

    public PaperSwordBlocking() throws Exception {
        // Build the NMS consumable component: Consumable.builder().consumeSeconds(MAX).animation(BLOCK).build()
        final Class<?> nmsConsumable = Class.forName("net.minecraft.world.item.component.Consumable");
        final Object nmsConsumableBuilder = nmsConsumable.getMethod("builder").invoke(null);
        final Class<?> nmsUseAnim = Class.forName("net.minecraft.world.item.ItemUseAnimation");
        final Object nmsBlockAnim = nmsUseAnim.getField("BLOCK").get(null);
        final Object nmsConsumableValue = nmsConsumableBuilder.getClass().getMethod("consumeSeconds", float.class)
            .invoke(nmsConsumableBuilder, Float.MAX_VALUE);
        final Object nmsConsumableValue2 = nmsConsumableValue.getClass().getMethod("animation", nmsUseAnim)
            .invoke(nmsConsumableValue, nmsBlockAnim);
        final Object consumableComponent = nmsConsumableValue2.getClass().getMethod("build").invoke(nmsConsumableValue2);

        // Build patches once:
        // DataComponentPatch.builder().set(DataComponents.CONSUMABLE, consumableComponent).build()
        // DataComponentPatch.builder().remove(DataComponents.CONSUMABLE).build()
        final Class<?> nmsDataComponents = Class.forName("net.minecraft.core.component.DataComponents");
        final Object consumableType = nmsDataComponents.getField("CONSUMABLE").get(null);
        final Class<?> nmsPatch = Class.forName("net.minecraft.core.component.DataComponentPatch");
        final Object patchBuilderAdd = nmsPatch.getMethod("builder").invoke(null);
        final Method setMethod = findPatchSetMethod(patchBuilderAdd.getClass());
        setMethod.invoke(patchBuilderAdd, consumableType, consumableComponent);
        addConsumablePatch = patchBuilderAdd.getClass().getMethod("build").invoke(patchBuilderAdd);

        final Object patchBuilderRemove = nmsPatch.getMethod("builder").invoke(null);
        final Method removeMethod = findPatchRemoveMethod(patchBuilderRemove.getClass());
        removeMethod.invoke(patchBuilderRemove, consumableType);
        removeConsumablePatch = patchBuilderRemove.getClass().getMethod("build").invoke(patchBuilderRemove);

        // NMS ItemStack#applyComponents(DataComponentPatch)
        final Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");
        final Method apply = nmsItemStack.getMethod("applyComponents", nmsPatch);
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        nmsApplyComponents = lookup.unreflect(apply);

        // Detect sword blocking via active item use:
        // player.getUseItem().is(ItemTags.SWORDS)
        // (Bukkit's Player#isBlocking and #isHandRaised are shield-biased and do not reliably track the
        // consumable-based sword use animation across server/client combinations.)
        final Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        craftPlayerGetHandle = lookup.unreflect(craftPlayer.getMethod("getHandle"));

        final Class<?> nmsPlayer = Class.forName("net.minecraft.world.entity.player.Player");
        nmsGetUseItem = lookup.unreflect(nmsPlayer.getMethod("getUseItem"));

        final Object swordTag = Class.forName("net.minecraft.tags.ItemTags").getField("SWORDS").get(null);
        nmsSwordTag = swordTag;
        nmsItemStackIs = lookup.unreflect(findItemStackIsMethod(nmsItemStack, swordTag));
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

    private Method findPatchRemoveMethod(Class<?> builderClass) throws NoSuchMethodException {
        for (Method m : builderClass.getMethods()) {
            if (!m.getName().equals("remove")) continue;
            if (m.getParameterCount() != 1) continue;
            return m;
        }
        throw new NoSuchMethodException("DataComponentPatch.Builder#remove(type) not found");
    }

    private Method findItemStackIsMethod(Class<?> nmsItemStackClass, Object tagInstance) throws NoSuchMethodException {
        for (Method m : nmsItemStackClass.getMethods()) {
            if (!m.getName().equals("is")) continue;
            if (m.getParameterCount() != 1) continue;
            if (m.getReturnType() != boolean.class) continue;
            final Class<?> param = m.getParameterTypes()[0];
            // NMS has multiple "is(...)" overloads. We specifically need the tag overload used by
            // ItemStack#is(ItemTags.SWORDS). Pick an overload whose parameter can accept the tag object.
            if (tagInstance != null && param.isInstance(tagInstance)) {
                return m;
            }
            if (tagInstance != null && param.isAssignableFrom(tagInstance.getClass())) {
                return m;
            }
            // Heuristic fallback: TagKey / HolderSet based overloads tend to include "TagKey" in their type name.
            if (param.getName().contains("TagKey")) {
                return m;
            }
        }
        throw new NoSuchMethodException("ItemStack#is(tag) not found");
    }

    public void applyComponents(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !isSword(stack.getType())) return;
        try {
            final Field handleField = resolveCraftItemStackHandleField(stack);
            final Object nms = handleField.get(stack);
            if (nms != null) {
                nmsApplyComponents.invoke(nms, addConsumablePatch);
            }
        } catch (Throwable ignored) {
        }
    }

    public void clearComponents(ItemStack stack) {
        if (stack == null) return;
        try {
            final Field handleField = resolveCraftItemStackHandleField(stack);
            final Object nms = handleField.get(stack);
            if (nms != null) {
                nmsApplyComponents.invoke(nms, removeConsumablePatch);
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isBlockingSword(Player player) {
        if (player == null) return false;
        try {
            final Object nmsPlayer = craftPlayerGetHandle.invoke(player);
            if (nmsPlayer == null) return false;
            final Object useItem = nmsGetUseItem.invoke(nmsPlayer);
            if (useItem == null) return false;
            return (boolean) nmsItemStackIs.invoke(useItem, nmsSwordTag);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSword(Material mat) {
        return mat != null && mat.name().endsWith("_SWORD");
    }
}
