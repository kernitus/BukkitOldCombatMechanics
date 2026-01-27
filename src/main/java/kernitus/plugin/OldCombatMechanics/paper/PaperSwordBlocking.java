package kernitus.plugin.OldCombatMechanics.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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

    private static final float BLOCK_CONSUME_SECONDS = 1.6f;

    private final MethodHandle nmsApplyComponents;
    private final MethodHandle nmsSetComponent;
    private final MethodHandle nmsRemoveComponent;
    private final MethodHandle nmsGetComponentsPatch;
    private final MethodHandle nmsPatchGet;
    private final Object addConsumablePatch;
    private final Object removeConsumablePatch;
    private final Object nmsConsumableType;
    private final Object nmsConsumableComponent;
    private final Method paperSetDataValue;
    private final Method paperSetDataBuilder;
    private final Method paperUnsetData;
    private final Method paperHasData;
    private final Method paperEnsureServerConversions;
    private final Method paperCopyDataFrom;
    private final Object paperConsumableType;
    private final Object paperConsumableValue;
    private final Object paperBaseConsumable;
    private final Method paperBaseConsumableToBuilder;
    private final Method paperConsumableBuilderFactory;
    private final Class<?> paperUseAnimClass;
    private final Object paperBlockAnimation;
    private final Class<?> paperValuedTypeClass;
    private final Class<?> paperBuilderClass;
    private static final Predicate<Object> COPY_ALL_COMPONENTS = ignored -> true;
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
        nmsConsumableComponent = consumableComponent;

        // Build patches once:
        // DataComponentPatch.builder().set(DataComponents.CONSUMABLE, consumableComponent).build()
        // DataComponentPatch.builder().remove(DataComponents.CONSUMABLE).build()
        final Class<?> nmsDataComponents = Class.forName("net.minecraft.core.component.DataComponents");
        final Object consumableType = nmsDataComponents.getField("CONSUMABLE").get(null);
        nmsConsumableType = consumableType;
        final Class<?> nmsPatch = Class.forName("net.minecraft.core.component.DataComponentPatch");
        final Object patchBuilderAdd = nmsPatch.getMethod("builder").invoke(null);
        final Method setMethod = findPatchSetMethod(patchBuilderAdd.getClass());
        setMethod.invoke(patchBuilderAdd, consumableType, consumableComponent);
        addConsumablePatch = patchBuilderAdd.getClass().getMethod("build").invoke(patchBuilderAdd);

        final Object patchBuilderRemove = nmsPatch.getMethod("builder").invoke(null);
        final Method removeMethod = findPatchRemoveMethod(patchBuilderRemove.getClass());
        removeMethod.invoke(patchBuilderRemove, consumableType);
        removeConsumablePatch = patchBuilderRemove.getClass().getMethod("build").invoke(patchBuilderRemove);

        Method setDataValueHandle = null;
        Method setDataBuilderHandle = null;
        Method unsetDataHandle = null;
        Method hasDataHandle = null;
        Method ensureServerConversionsHandle = null;
        Method copyDataFromHandle = null;
        Object consumableTypePaper = null;
        Object consumableValuePaper = null;
        Object baseConsumablePaper = null;
        Method baseConsumableToBuilder = null;
        Method consumableBuilderFactory = null;
        Class<?> useAnimClass = null;
        Object blockAnimation = null;
        Class<?> valuedTypeClass = null;
        Class<?> builderClass = null;
        try {
            final Class<?> paperItemStack = Class.forName("org.bukkit.inventory.ItemStack");
            final Class<?> paperDataComponentType = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
            final Class<?> paperDataComponentTypeValued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");
            final Class<?> paperDataComponentBuilder = Class.forName("io.papermc.paper.datacomponent.DataComponentBuilder");
            valuedTypeClass = paperDataComponentTypeValued;
            builderClass = paperDataComponentBuilder;
            final Class<?> paperDataComponentTypes = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            consumableTypePaper = paperDataComponentTypes.getField("CONSUMABLE").get(null);

            final Method getData = paperItemStack.getMethod("getData", paperDataComponentTypeValued);
            final Object bread = new ItemStack(Material.BREAD);
            baseConsumablePaper = getData.invoke(bread, consumableTypePaper);
            useAnimClass = Class.forName("io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation");
            blockAnimation = useAnimClass.getField("BLOCK").get(null);
            if (baseConsumablePaper != null) {
                baseConsumableToBuilder = baseConsumablePaper.getClass().getMethod("toBuilder");
            }
            final Class<?> paperConsumable = Class.forName("io.papermc.paper.datacomponent.item.Consumable");
            consumableBuilderFactory = paperConsumable.getMethod("consumable");

            Method setDataMethodValue = null;
            Method setDataMethodBuilder = null;
            for (Method m : paperItemStack.getMethods()) {
                if (!m.getName().equals("setData")) continue;
                if (m.getParameterCount() != 2) continue;
                if (!paperDataComponentTypeValued.isAssignableFrom(m.getParameterTypes()[0])) continue;
                if (paperDataComponentBuilder.isAssignableFrom(m.getParameterTypes()[1])) {
                    setDataMethodBuilder = m;
                    continue;
                }
                setDataMethodValue = m;
            }

            if (setDataMethodValue != null || setDataMethodBuilder != null) {
                final Method unset = paperItemStack.getMethod("unsetData", paperDataComponentType);
                final Method has = paperItemStack.getMethod("hasData", paperDataComponentType);
                final Method ensure = paperItemStack.getMethod("ensureServerConversions");
                final Method copyFrom = paperItemStack.getMethod("copyDataFrom", paperItemStack, Predicate.class);
                if (setDataMethodValue != null) {
                    setDataMethodValue.setAccessible(true);
                }
                if (setDataMethodBuilder != null) {
                    setDataMethodBuilder.setAccessible(true);
                }
                unset.setAccessible(true);
                has.setAccessible(true);
                ensure.setAccessible(true);
                copyFrom.setAccessible(true);
                setDataValueHandle = setDataMethodValue;
                setDataBuilderHandle = setDataMethodBuilder;
                unsetDataHandle = unset;
                hasDataHandle = has;
                ensureServerConversionsHandle = ensure;
                copyDataFromHandle = copyFrom;
            }
        } catch (Throwable ignored) {
            consumableTypePaper = null;
            consumableValuePaper = null;
            baseConsumablePaper = null;
            baseConsumableToBuilder = null;
            consumableBuilderFactory = null;
            useAnimClass = null;
            blockAnimation = null;
        }

        paperSetDataValue = setDataValueHandle;
        paperSetDataBuilder = setDataBuilderHandle;
        paperUnsetData = unsetDataHandle;
        paperHasData = hasDataHandle;
        paperEnsureServerConversions = ensureServerConversionsHandle;
        paperCopyDataFrom = copyDataFromHandle;
        paperConsumableType = consumableTypePaper;
        paperConsumableValue = consumableValuePaper;
        paperBaseConsumable = baseConsumablePaper;
        paperBaseConsumableToBuilder = baseConsumableToBuilder;
        paperConsumableBuilderFactory = consumableBuilderFactory;
        paperUseAnimClass = useAnimClass;
        paperBlockAnimation = blockAnimation;
        paperValuedTypeClass = valuedTypeClass;
        paperBuilderClass = builderClass;

        // NMS ItemStack#applyComponents(DataComponentPatch)
        final Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");
        final Method apply = nmsItemStack.getMethod("applyComponents", nmsPatch);
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        nmsApplyComponents = lookup.unreflect(apply);

        final Class<?> nmsComponentType = Class.forName("net.minecraft.core.component.DataComponentType");
        nmsSetComponent = lookup.unreflect(findItemStackSetMethod(nmsItemStack, nmsComponentType));
        nmsRemoveComponent = lookup.unreflect(findItemStackRemoveMethod(nmsItemStack, nmsComponentType));

        nmsGetComponentsPatch = lookup.unreflect(nmsItemStack.getMethod("getComponentsPatch"));
        nmsPatchGet = lookup.unreflect(nmsPatch.getMethod("get", nmsComponentType));

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
        applyComponentsInternal(stack, true);
    }

    private void applyComponentsInternal(ItemStack stack, boolean allowTestSync) {
        if (stack == null || stack.getType() == Material.AIR || !isSword(stack.getType())) return;
        boolean applied = false;
        if (paperSetDataValue != null && paperConsumableType != null) {
            final Object value = paperConsumableValue != null ? paperConsumableValue : buildPaperConsumableValue();
            if (value != null) {
                try {
                    applied = setPaperDataValue(stack, value);
                } catch (Throwable ignored) {
                    applied = false;
                }
            }
        }
        if (!applied && paperSetDataBuilder != null && paperConsumableType != null) {
            final Object builder = buildPaperConsumableBuilder();
            if (builder != null) {
                try {
                    applied = setPaperDataBuilder(stack, builder);
                } catch (Throwable ignored) {
                    applied = false;
                }
            }
        }
        if (!applied && paperConsumableType != null && paperValuedTypeClass != null) {
            final Object value = buildPaperConsumableValue();
            if (value != null) {
                applied = setPaperDataValue(stack, value);
            }
        }
        if (!applied && paperConsumableType != null && paperBuilderClass != null) {
            final Object builder = buildPaperConsumableBuilder();
            if (builder != null) {
                applied = setPaperDataBuilder(stack, builder);
            }
        }
        if (applied) {
            ensureServerConversions(stack);
            if (allowTestSync) {
                syncTestInventories(stack);
            }
            return;
        }
        try {
            final Field handleField = resolveCraftItemStackHandleField(stack);
            final Object nms = handleField.get(stack);
            if (nms != null) {
                nmsSetComponent.invoke(nms, nmsConsumableType, nmsConsumableComponent);
            }
            ensureServerConversions(stack);
            if (allowTestSync) {
                syncTestInventories(stack);
            }
        } catch (Throwable ignored) {
        }
    }

    public void clearComponents(ItemStack stack) {
        if (stack == null) return;
        boolean cleared = false;
        if (paperUnsetData != null && paperConsumableType != null) {
            try {
                paperUnsetData.invoke(stack, paperConsumableType);
                cleared = true;
            } catch (Throwable ignored) {
                cleared = false;
            }
        }
        if (cleared) {
            ensureServerConversions(stack);
            return;
        }
        try {
            final Field handleField = resolveCraftItemStackHandleField(stack);
            final Object nms = handleField.get(stack);
            if (nms != null) {
                nmsRemoveComponent.invoke(nms, nmsConsumableType);
            }
            ensureServerConversions(stack);
        } catch (Throwable ignored) {
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

    private Method findItemStackRemoveMethod(Class<?> nmsItemStackClass, Class<?> nmsComponentTypeClass) throws NoSuchMethodException {
        for (Method m : nmsItemStackClass.getMethods()) {
            if (!m.getName().equals("remove")) continue;
            if (m.getParameterCount() != 1) continue;
            if (!m.getParameterTypes()[0].isAssignableFrom(nmsComponentTypeClass)) continue;
            return m;
        }
        throw new NoSuchMethodException("ItemStack#remove(component) not found");
    }

    public boolean hasConsumableComponent(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !isSword(stack.getType())) return false;
        if (paperHasData != null && paperConsumableType != null) {
            try {
                final Object result = paperHasData.invoke(stack, paperConsumableType);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            final Field handleField = resolveCraftItemStackHandleField(stack);
            final Object nms = handleField.get(stack);
            if (nms == null) return false;
            final Object patch = nmsGetComponentsPatch.invoke(nms);
            if (patch == null) return false;
            final Object entry = nmsPatchGet.invoke(patch, nmsConsumableType);
            if (!(entry instanceof Optional)) return false;
            return ((Optional<?>) entry).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void ensureServerConversions(ItemStack stack) {
        if (paperEnsureServerConversions == null || paperCopyDataFrom == null || stack == null) return;
        try {
            final Object converted = paperEnsureServerConversions.invoke(stack);
            if (!(converted instanceof ItemStack)) return;
            if (converted == stack) return;
            paperCopyDataFrom.invoke(stack, converted, COPY_ALL_COMPONENTS);
        } catch (Throwable ignored) {
        }
    }

    private boolean setPaperDataValue(ItemStack stack, Object value) {
        if (stack == null || value == null || paperConsumableType == null) return false;
        try {
            Method method = paperSetDataValue;
            if (method == null && paperValuedTypeClass != null) {
                method = stack.getClass().getMethod("setData", paperValuedTypeClass, Object.class);
            }
            if (method == null) return false;
            method.invoke(stack, paperConsumableType, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean setPaperDataBuilder(ItemStack stack, Object builder) {
        if (stack == null || builder == null || paperConsumableType == null || paperBuilderClass == null) return false;
        try {
            Method method = paperSetDataBuilder;
            if (method == null) {
                method = stack.getClass().getMethod("setData", paperValuedTypeClass, paperBuilderClass);
            }
            if (method == null) return false;
            method.invoke(stack, paperConsumableType, builder);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object buildPaperConsumableBuilder() {
        if (paperConsumableType == null || paperUseAnimClass == null || paperBlockAnimation == null) return null;
        try {
            Object builder = null;
            boolean fromBase = false;
            if (paperBaseConsumable != null && paperBaseConsumableToBuilder != null) {
                builder = paperBaseConsumableToBuilder.invoke(paperBaseConsumable);
                fromBase = true;
            }
            if (builder == null && paperConsumableBuilderFactory != null) {
                builder = paperConsumableBuilderFactory.invoke(null);
                fromBase = false;
            }
            if (builder == null) return null;
            Object configured = builder;
            if (!fromBase) {
                configured = configured.getClass().getMethod("consumeSeconds", float.class).invoke(configured, BLOCK_CONSUME_SECONDS);
            }
            final Object withAnim = configured.getClass().getMethod("animation", paperUseAnimClass).invoke(configured, paperBlockAnimation);
            return withAnim;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object buildPaperConsumableValue() {
        final Object builder = buildPaperConsumableBuilder();
        if (builder == null) return null;
        try {
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void syncTestInventories(ItemStack stack) {
        if (!isTestServer() || stack == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            final PlayerInventory inventory = player.getInventory();
            final int size = inventory.getSize();
            for (int slot = 0; slot < size; slot++) {
                final ItemStack candidate = inventory.getItem(slot);
                if (candidate == null) continue;
                if (!candidate.isSimilar(stack)) continue;
                applyComponentsInternal(candidate, false);
                inventory.setItem(slot, candidate);
            }

            final ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && cursor.isSimilar(stack)) {
                applyComponentsInternal(cursor, false);
                player.setItemOnCursor(cursor);
            }
        }
    }

    private boolean isTestServer() {
        return Bukkit.getPluginManager().getPlugin("OldCombatMechanicsTest") != null;
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
