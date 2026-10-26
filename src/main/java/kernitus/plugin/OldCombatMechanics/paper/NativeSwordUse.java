package kernitus.plugin.OldCombatMechanics.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Loaded only when public active-item inspection is unavailable. */
final class NativeSwordUse {
    private final MethodHandle craftPlayerGetHandle;
    private final MethodHandle nmsGetUseItem;
    private final MethodHandle nmsItemStackIs;
    private final MethodHandle nmsGetItemHolder;
    private final MethodHandle nmsHolderIsTag;
    private final Object nmsSwordTag;

    NativeSwordUse() throws ReflectiveOperationException {
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        final Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");
        // Older Paper can lack public active-item inspection:
        // player.getUseItem().is(ItemTags.SWORDS)
        // (Bukkit's Player#isBlocking and #isHandRaised are shield-biased and do not reliably track the
        // consumable-based sword use animation across server/client combinations.)
        final Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        craftPlayerGetHandle = lookup.unreflect(craftPlayer.getMethod("getHandle"));

        final Class<?> nmsPlayer = Class.forName("net.minecraft.world.entity.player.Player");
        nmsGetUseItem = lookup.unreflect(nmsPlayer.getMethod("getUseItem"));

        final Object swordTag = Class.forName("net.minecraft.tags.ItemTags").getField("SWORDS").get(null);
        nmsSwordTag = swordTag;
        MethodHandle itemStackIsTag = null;
        MethodHandle getItemHolder = null;
        MethodHandle holderIsTag = null;
        try {
            itemStackIsTag = lookup.unreflect(findItemStackIsMethod(nmsItemStack, swordTag));
        } catch (NoSuchMethodException ignored) {
            final Method holderMethod = findItemStackHolderMethod(nmsItemStack);
            getItemHolder = lookup.unreflect(holderMethod);
            holderIsTag = lookup.unreflect(findHolderIsTagMethod(holderMethod.getReturnType(), swordTag));
        }
        nmsItemStackIs = itemStackIsTag;
        nmsGetItemHolder = getItemHolder;
        nmsHolderIsTag = holderIsTag;
    }

    private Method findItemStackIsMethod(Class<?> nmsItemStackClass, Object tagInstance) throws NoSuchMethodException {
        if (tagInstance != null) {
            try {
                return nmsItemStackClass.getMethod("is", tagInstance.getClass());
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : nmsItemStackClass.getMethods()) {
            if (!m.getName().equals("is")) continue;
            if (m.getParameterCount() != 1) continue;
            if (m.getReturnType() != boolean.class) continue;
            final Class<?> param = m.getParameterTypes()[0];
            if (param == Object.class) continue;
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

    private Method findItemStackHolderMethod(Class<?> nmsItemStackClass) throws NoSuchMethodException {
        for (String name : new String[]{"getItemHolder", "typeHolder"}) {
            try {
                return nmsItemStackClass.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : nmsItemStackClass.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (!"net.minecraft.core.Holder".equals(m.getReturnType().getName())) continue;
            return m;
        }
        throw new NoSuchMethodException("ItemStack item holder accessor not found");
    }

    private Method findHolderIsTagMethod(Class<?> holderClass, Object tagInstance) throws NoSuchMethodException {
        if (tagInstance != null) {
            try {
                return holderClass.getMethod("is", tagInstance.getClass());
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : holderClass.getMethods()) {
            if (!m.getName().equals("is")) continue;
            if (m.getParameterCount() != 1) continue;
            if (m.getReturnType() != boolean.class) continue;
            final Class<?> param = m.getParameterTypes()[0];
            if (param == Object.class) continue;
            if (tagInstance != null && param.isInstance(tagInstance)) {
                return m;
            }
            if (param.getName().contains("TagKey")) {
                return m;
            }
        }
        throw new NoSuchMethodException("Holder#is(tag) not found");
    }

    public boolean isBlockingSword(Player player) {
        if (player == null) return false;
        try {
            final Object nmsPlayer = craftPlayerGetHandle.invoke(player);
            if (nmsPlayer == null) return false;
            final Object useItem = nmsGetUseItem.invoke(nmsPlayer);
            if (useItem == null) return false;
            if (nmsItemStackIs != null) {
                return (boolean) nmsItemStackIs.invoke(useItem, nmsSwordTag);
            }
            final Object holder = nmsGetItemHolder.invoke(useItem);
            if (holder == null) return false;
            return (boolean) nmsHolderIsTag.invoke(holder, nmsSwordTag);
        } catch (Throwable ignored) {
            return false;
        }
    }

}
