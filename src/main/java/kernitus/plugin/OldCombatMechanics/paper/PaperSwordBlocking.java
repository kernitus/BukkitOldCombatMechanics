package kernitus.plugin.OldCombatMechanics.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Capability boundary: modern component types are confined to PublicSwordComponents. */
public class PaperSwordBlocking {
    private final Components components;
    private boolean activeItemApiMissing;
    private boolean nativeUseResolved;
    private NativeSwordUse nativeUse;

    interface Components {
        void applyComponents(ItemStack stack);
        boolean clearComponents(ItemStack stack);
        boolean hasConsumableComponent(ItemStack stack);
    }

    public PaperSwordBlocking() throws Exception {
        Components selected;
        try {
            selected = new PublicSwordComponents();
        } catch (NoClassDefFoundError | NoSuchMethodError missing) {
            // Consumables existed before every Paper build exposed their public API.
            selected = new LegacySwordComponents();
        }
        components = selected;
    }

    public void applyComponents(ItemStack stack) {
        components.applyComponents(stack);
    }

    public boolean clearComponents(ItemStack stack) {
        return components.clearComponents(stack);
    }

    public boolean hasConsumableComponent(ItemStack stack) {
        return components.hasConsumableComponent(stack);
    }

    public boolean isBlockingSword(Player player) {
        if (player == null) return false;
        if (!activeItemApiMissing) {
            try {
                if (!player.hasActiveItem()) return false;
                final ItemStack active = player.getActiveItem();
                return active != null && isSword(active.getType());
            } catch (NoSuchMethodError missing) {
                activeItemApiMissing = true;
            }
        }
        if (!nativeUseResolved) {
            nativeUseResolved = true;
            try {
                nativeUse = new NativeSwordUse();
            } catch (ReflectiveOperationException missing) {
                // Missing inspection must not disable component operations.
            }
        }
        return nativeUse != null && nativeUse.isBlockingSword(player);
    }

    static boolean isSword(Material type) {
        return type != null && type.name().endsWith("_SWORD");
    }
}
