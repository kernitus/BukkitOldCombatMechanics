package gvlfm78.plugin.OldCombatMechanics.module;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleGoldenApple extends Module {

	private List<PotionEffect> enchantedGoldenAppleEffects = Arrays.asList(new PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 4), new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 300 * 20, 0), new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300 * 20, 0), new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0));
	private List<PotionEffect> goldenAppleEffects = Arrays.asList(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1), new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0));

	public ModuleGoldenApple(OCMMain plugin) {
		super(plugin, "old-golden-apples");
	}

	public static boolean RECIPE_ALREADY_EXISTED = false;

	@EventHandler(priority = EventPriority.HIGH)
	public void onPrepareItemCraft(PrepareItemCraftEvent e) {
	    
	        if (e.getInventory() == null) {
	            return;
	        }
		ItemStack item = e.getInventory().getResult();
		if (item == null) {
		    // This should never ever ever ever run. If it does then you probably screwed something up.
		    return;
		}

		if (item.getType() == Material.GOLDEN_APPLE && item.getDurability() == (short) 1) {

			World world = e.getView().getPlayer().getWorld();

			if (isSettingEnabled("no-conflict-mode")) {

				return;
			}

			if (!isEnabled(world)) {

				e.getInventory().setResult(null);

			} else if (isEnabled(world) && !isSettingEnabled("enchanted-golden-apple-crafting")) {

				e.getInventory().setResult(null);

			}

		}

	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onItemConsume(PlayerItemConsumeEvent e) {

		if (e.getItem().getType() != Material.GOLDEN_APPLE) {
			return;
		}

		if (!isEnabled(e.getPlayer().getWorld()) || !isSettingEnabled("old-potion-effects")) {
			return;
		}

		e.setCancelled(true);

		ItemStack originalItem = e.getItem();

		ItemStack item = e.getItem();

		Player p = e.getPlayer();
		PlayerInventory inv = p.getInventory();

		int foodLevel = p.getFoodLevel();
		foodLevel = foodLevel + 4 > 20 ? 20 : foodLevel + 4;

		item.setAmount(item.getAmount() - 1);

		p.setFoodLevel(foodLevel);

		if (item.getDurability() == (short) 1) {

			for (PotionEffect effect : enchantedGoldenAppleEffects) {
				e.getPlayer().removePotionEffect(effect.getType());
			}

			e.getPlayer().addPotionEffects(enchantedGoldenAppleEffects);

		} else {

			for (PotionEffect effect : goldenAppleEffects) {
				e.getPlayer().removePotionEffect(effect.getType());
			}

			e.getPlayer().addPotionEffects(goldenAppleEffects);

		}
		if (item.getAmount() <= 0)
			item = null;

		ItemStack mainHand = inv.getItemInMainHand();
		ItemStack offHand = inv.getItemInOffHand();

		if(mainHand.equals(originalItem))
			inv.setItemInMainHand(item);

		else if(offHand.equals(originalItem))
			inv.setItemInOffHand(item);

		else{//The bug occurs here, so we must check which hand has the apples
			// A player can't eat food in the offhand if there is any in the main hand
			// On this principle if there are gapples in the mainhand it must be that one, else it's the offhand
			if(mainHand.getType().equals(Material.GOLDEN_APPLE))
				inv.setItemInMainHand(item);

			else
				p.sendMessage("4: "+originalItem.getAmount()+" "+item.getAmount());
		}
	}
}
