package gvlfm78.plugin.OldCombatMechanics;


import org.bukkit.craftbukkit.v1_9_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;

import net.minecraft.server.v1_9_R1.NBTTagCompound;
import net.minecraft.server.v1_9_R1.NBTTagList;

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
		ItemStack is = p.getInventory().getItemInMainHand();
		NBTTagCompound tags = new NBTTagCompound();
		net.minecraft.server.v1_9_R1.ItemStack nmsStack = CraftItemStack.asNMSCopy(is);
		tags = nmsStack.getTag();
		if(tags!=null){
		String stuff = String.valueOf(tags.getString(("generic.attackSpeed")));
		NBTTagList list = tags.getList("", 0);
		p.sendMessage(list.toString());
		if(stuff!=null)
		p.sendMessage(stuff);
		else
			p.sendMessage("Stuff is null!");
		}
		else
			p.sendMessage("No NBT tags on this item");
		/*ItemStack item = p.getInventory().getHelmet();
		if(item==null){
			//Place wooden button with attributes
			item = new ItemStack(Material.WOOD_BUTTON);
			addNBTTags(p,item);
			p.getInventory().setHelmet(addNBTTags(p,item));
		}
		else
		{
			//Give attributes to helmet
			p.getInventory().setHelmet(addNBTTags(p,item));
		}*/
	}
	public ItemStack addNBTTags(Player p, ItemStack is){
		net.minecraft.server.v1_9_R1.ItemStack nmsStack = CraftItemStack.asNMSCopy(is);

		NBTTagCompound tags = new NBTTagCompound();
		tags.setString("AttributeName", "generic.attackSpeed");
		tags.setString("Name", "generic.attackSpeed");
		tags.setInt("Amount", 99999999);
		tags.setInt("Operation", 0);
		tags.setInt("UUIDMost", 90498);
		tags.setInt("UUIDLeast", 161150);
		nmsStack.setTag(tags);
		return CraftItemStack.asBukkitCopy(nmsStack);
		
		//{AttributeModifiers:[{AttributeName:"generic.attackSpeed",Name:generic.attackSpeed,Amount:99999999,Operation:0,UUIDMost:90498,UUIDLeast:161150}]}
	}
}