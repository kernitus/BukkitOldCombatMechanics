package kernitus.plugin.OldCombatMechanics.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Paper-only helper: reflection-based to avoid linking Paper API at compile time.
 *
 * Performance: all reflective lookups are done once in the constructor; hot calls only use cached MethodHandles.
 * We deliberately swallow failures here: if Paper changes an API/method signature, we prefer "no sword blocking
 * components" over hard-failing the whole plugin.
 */
public class PaperSwordBlocking {

    private final Object consumableType;
    private final Object blockingType;
    private final Object consumableValue;
    private final Object blockingValue;
    private final MethodHandle itemSetData;
    private final MethodHandle itemHasData;
    private final MethodHandle itemUnsetData;

    public PaperSwordBlocking() throws Exception {
        final Class<?> dct = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
        final Field consumableField = dct.getField("CONSUMABLE");
        consumableType = consumableField.get(null);
        Object blockingTypeTemp = null;
        try {
            blockingTypeTemp = dct.getField("BLOCKING").get(null);
        } catch (Throwable ignored) {
        }
        blockingType = blockingTypeTemp;

        final Class<?> consumableClass = Class.forName("io.papermc.paper.datacomponent.item.Consumable");
        final Class<?> consumableBuilder = Class.forName("io.papermc.paper.datacomponent.item.Consumable$Builder");
        final Class<?> animationEnum = Class.forName("io.papermc.paper.datacomponent.item.Consumable$Animation");
        Object builder = consumableClass.getMethod("consumable").invoke(null);
        final Object blockAnim = animationEnum.getField("BLOCK").get(null);
        builder = consumableBuilder.getMethod("animation", animationEnum).invoke(builder, blockAnim);
        builder = consumableBuilder.getMethod("useDuration", int.class).invoke(builder, 3600);
        consumableValue = consumableBuilder.getMethod("build").invoke(builder);

        Object blockingTemp = null;
        try {
            final Class<?> blockingClass = Class.forName("io.papermc.paper.datacomponent.item.Blocking");
            blockingTemp = blockingClass.getMethod("blocking").invoke(null);
        } catch (Throwable ignored) {
        }
        blockingValue = blockingTemp;

        final Class<?> dctClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        itemSetData = lookup.unreflect(findSetDataMethod(dctClass));
        itemHasData = lookup.unreflect(ItemStack.class.getMethod("hasData", dctClass));
        itemUnsetData = lookup.unreflect(ItemStack.class.getMethod("unsetData", dctClass));
    }

    private java.lang.reflect.Method findSetDataMethod(Class<?> typeClass) throws NoSuchMethodException {
        for (java.lang.reflect.Method m : ItemStack.class.getMethods()) {
            if (!m.getName().equals("setData")) continue;
            if (m.getParameterCount() != 2) continue;
            // First parameter should be DataComponentType (or supertype)
            if (!m.getParameterTypes()[0].isAssignableFrom(typeClass) && !typeClass.isAssignableFrom(m.getParameterTypes()[0])) continue;
            return m;
        }
        throw new NoSuchMethodException("ItemStack#setData(DataComponentType, value) not found");
    }

    public boolean supportsPaperComponents() {
        return consumableType != null && itemSetData != null;
    }

    public void applyComponents(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !isSword(stack.getType())) return;
        try {
            if (!((boolean) itemHasData.invoke(stack, consumableType))) {
                itemSetData.invoke(stack, consumableType, consumableValue);
            }
            if (blockingType != null && blockingValue != null && !((boolean) itemHasData.invoke(stack, blockingType))) {
                itemSetData.invoke(stack, blockingType, blockingValue);
            }
        } catch (Throwable ignored) {
        }
    }

    public void clearComponents(ItemStack stack) {
        if (stack == null) return;
        try {
            if ((boolean) itemHasData.invoke(stack, consumableType)) {
                itemUnsetData.invoke(stack, consumableType);
            }
            if (blockingType != null && (boolean) itemHasData.invoke(stack, blockingType)) {
                itemUnsetData.invoke(stack, blockingType);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isSword(Material mat) {
        return mat != null && mat.name().endsWith("_SWORD");
    }
}
