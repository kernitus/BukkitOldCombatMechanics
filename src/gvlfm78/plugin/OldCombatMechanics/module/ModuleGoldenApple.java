package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.Config;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleGoldenApple extends Module {

    private List<PotionEffect> enchantedGoldenAppleEffects = Arrays.asList(new PotionEffect(PotionEffectType.REGENERATION, 30*20, 4), new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 300*20, 0), new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300*20, 0), new PotionEffect(PotionEffectType.ABSORPTION, 120*20, 0));
    private List<PotionEffect> goldenAppleEffects = Arrays.asList(new PotionEffect(PotionEffectType.REGENERATION, 5*20, 1), new PotionEffect(PotionEffectType.ABSORPTION, 120*20, 0));

    public ModuleGoldenApple(OCMMain plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {

        ItemStack item = e.getInventory().getResult();

        if (item.getType() == Material.GOLDEN_APPLE && item.getDurability() == (short) 1) {

            if (Config.moduleEnabled("old-golden-apples") && !Config.moduleSettingEnabled("old-golden-apples", "enchant-golden-apple-crafting")) {

                e.getInventory().setResult(null);

            }

        }

    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemConsume(PlayerItemConsumeEvent e) {

        if (e.getItem().getType() != Material.GOLDEN_APPLE) {
            return;
        }

        if (!Config.moduleEnabled("old-golden-apples") || !Config.moduleSettingEnabled("old-golden-apples", "old-potion-effects")) {
            return;
        }

        ItemStack item = e.getItem();

        e.setCancelled(true);

        int foodLevel = e.getPlayer().getFoodLevel();
        foodLevel = foodLevel + 4 > 20 ? 20 : foodLevel + 4;

        item.setAmount(item.getAmount() - 1);

        if (item.getDurability() == (short) 1) {

            e.getPlayer().addPotionEffects(enchantedGoldenAppleEffects);

        } else {

            e.getPlayer().addPotionEffects(goldenAppleEffects);

        }

    }

}
