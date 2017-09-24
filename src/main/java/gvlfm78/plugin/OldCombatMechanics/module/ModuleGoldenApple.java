package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Rayzr522 on 25/6/16.
 */
public class ModuleGoldenApple extends Module {

	private List<PotionEffect> enchantedGoldenAppleEffects, goldenAppleEffects;
	private ItemStack napple = new ItemStack(Material.GOLDEN_APPLE, 1, (short) 1);
	private ShapedRecipe r;

	public static ModuleGoldenApple INSTANCE;

	public ModuleGoldenApple(OCMMain plugin) {
		super(plugin, "old-golden-apples");
		INSTANCE = this;
		reloadRecipes();
	}

	@SuppressWarnings("deprecated")
	public void reloadRecipes(){
		enchantedGoldenAppleEffects = getPotionEffects("napple");
		goldenAppleEffects = getPotionEffects("gapple");

		try{
			r = new ShapedRecipe(new NamespacedKey(plugin, "MINECRAFT"), napple);
		}
		catch(NoClassDefFoundError e) {
			r = new ShapedRecipe(napple);
		}
		r.shape("ggg", "gag", "ggg").setIngredient('g', Material.GOLD_BLOCK).setIngredient('a', Material.APPLE);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPrepareItemCraft(PrepareItemCraftEvent e) {
		if (e.getInventory() == null) return;

		ItemStack item = e.getInventory().getResult();
		if (item == null) return; // This should never ever ever ever run. If it does then you probably screwed something up.

		if (item.getType() == Material.GOLDEN_APPLE && item.getDurability() == (short) 1) {

			World world = e.getView().getPlayer().getWorld();

			if (isSettingEnabled("no-conflict-mode")) return;

			if (!isEnabled(world))
				e.getInventory().setResult(null);
			else if (isEnabled(world) && !isSettingEnabled("enchanted-golden-apple-crafting"))
				e.getInventory().setResult(null);
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onItemConsume(PlayerItemConsumeEvent e) {

		if (e.getItem() == null || e.getItem().getType() != Material.GOLDEN_APPLE) return;

		if (!isEnabled(e.getPlayer().getWorld()) || !isSettingEnabled("old-potion-effects")) return;

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

			for (PotionEffect effect : enchantedGoldenAppleEffects)
				e.getPlayer().removePotionEffect(effect.getType());

			e.getPlayer().addPotionEffects(enchantedGoldenAppleEffects);

		} else {

			for (PotionEffect effect : goldenAppleEffects)
				e.getPlayer().removePotionEffect(effect.getType());

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

		else if(mainHand.getType() == Material.GOLDEN_APPLE)
			inv.setItemInMainHand(item);
		//The bug occurs here, so we must check which hand has the apples
		// A player can't eat food in the offhand if there is any in the main hand
		// On this principle if there are gapples in the mainhand it must be that one, else it's the offhand
	}
	public List<PotionEffect> getPotionEffects(String apple){
		List<PotionEffect> appleEffects = new ArrayList<>();

		ConfigurationSection sect = module().getConfigurationSection(apple + "-effects");
		for(String key : sect.getKeys(false)){
			int duration = sect.getInt(key + ".duration");
			int amplifier = sect.getInt(key + ".amplifier");

			PotionEffect fx = new PotionEffect(PotionEffectType.getByName(key), duration, amplifier);
			appleEffects.add(fx);
		}
		return appleEffects;
	}

	public void registerCrafting(){
		if (isEnabled() && module().getBoolean("enchanted-golden-apple-crafting")) {
			if(Bukkit.getRecipesFor(napple).size() > 0) return;
			Bukkit.addRecipe(r);
			Messenger.debug("Added napple recipe");
		}
	}
}
