package gvlfm78.plugin.OldCombatMechanics;


import net.minecraft.server.v1_9_R1.*;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class OCMListener implements Listener{

	protected OCMUpdateChecker updateChecker;

	private OCMMain plugin;
	public OCMListener(OCMMain instance){
		this.plugin = instance;
	}

	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent e){
		Player p = e.getPlayer();
		if(p.isOp()){
			this.updateChecker = new OCMUpdateChecker(plugin, "http://dev.bukkit.org/bukkit-plugins/old-combat-mechanics/files.rss");
			this.updateChecker.updateNeeded();
			if(plugin.getConfig().getBoolean("settings.checkForUpdates")){
				if(this.updateChecker.updateNeeded()){
					p.sendMessage("An update of OldCombatMechanics to version " + this.updateChecker.getVersion()+"is available!");
					p.sendMessage("Click here to download it:"+this.updateChecker.getLink());
				}
			}
		}
	}
	@EventHandler
	public void onWeaponEquip(PlayerItemHeldEvent e){
		Player p = e.getPlayer();
		ItemStack item = p.getInventory().getItemInMainHand();
		Material material = item.getType();
		switch(material){
		case DIAMOND_SWORD:addNBTTag(p,item); break;
		}
	}
	public void addNBTTag(Player p, ItemStack is){
		ItemMeta meta = is.getItemMeta();
		NBTTagCompound NBTTC = new NBTTagCompound();
		//{AttributeModifiers:[{AttributeName:"generic.attackSpeed",Name:generic.attackSpeed,Amount:99999999,Operation:0,UUIDMost:90498,UUIDLeast:161150}]}
	}
}