package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleAttackCooldown extends Module {

	public ModuleAttackCooldown(OCMMain plugin) {
		super(plugin, "disable-attack-cooldown");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerJoinEvent e) {

		Player p = e.getPlayer();
		World world = p.getWorld();

		double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.general-attack-speed");

		if (Config.moduleEnabled("disable-attack-cooldown", world)) {// Setting to no cooldown
			AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
			double baseValue = attribute.getBaseValue();
			if (baseValue != GAS) {
				attribute.setBaseValue(GAS);
				p.saveData();
			}
		} else {// Re-enabling cooldown
			AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
			double baseValue = attribute.getBaseValue();
			if (baseValue != 4) {
				attribute.setBaseValue(4);
				p.saveData();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuit(PlayerQuitEvent e){
		Player player = e.getPlayer();
		AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
		double baseValue = attribute.getBaseValue();
		if (baseValue != 4){ //If basevalues is not 1.9 default, set it back
			attribute.setBaseValue( 4 );
			player.saveData();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onWorldChange(PlayerChangedWorldEvent e) {

		Player player = e.getPlayer();
		World world = player.getWorld();

		AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
		double baseValue = attribute.getBaseValue();

		if (isEnabled(world)) {//Disabling cooldown

			double GAS = module().getDouble("general-attack-speed");

			if (baseValue != GAS) {
				attribute.setBaseValue(GAS);
				player.saveData();
			}

		} else {//Re-enabling cooldown

			if (baseValue != 4) {
				attribute.setBaseValue(4);
				player.saveData();
			}

		}

	}

}
