package kernitus.plugin.OldCombatMechanics;


import org.bukkit.Material;
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
	public void onItemEquip(PlayerItemHeldEvent e){
		Player p = e.getPlayer();
		ItemStack item = p.getInventory().getLeggings();

		if(item!=null){
			//Give attributes to item in leggings slot
			ItemStack newItem = addNBTTags(p,item);
			if(newItem!=null)
			p.getInventory().setLeggings(newItem);
		}
		else{
			//Place wooden button with attributes
			item = new ItemStack(Material.WOOD_BUTTON);
			item.setAmount(-1);
			ItemStack newItem = addNBTTags(p,item);
			if(newItem!=null)
			p.getInventory().setLeggings(addNBTTags(p,item));
		}
	}

	public boolean hasTags(ItemStack item){
		net.minecraft.server.v1_9_R1.ItemStack nmsStack = CraftItemStack.asNMSCopy(item);
		NBTTagCompound compound = nmsStack.getTag();
		if(compound==null){
			return false;
		}
			
		if(compound.hasKey("AttributeModifiers")){

			NBTTagCompound attributes = compound.getCompound("AttributeModifiers");

			if(attributes.hasKey("AttributeName")){
				String name = attributes.getString("AttributeName");
				if(name.equals("generic.attackSpeed"))
					return true;
				else{
					System.out.println("YEE2");
					return false;
				}
			}
			else
				return false;
		}
		else
			return false;
	}

	public ItemStack addNBTTags(Player p, ItemStack is){
		if(!hasTags(is)){
			net.minecraft.server.v1_9_R1.ItemStack nmsStack = CraftItemStack.asNMSCopy(is);
			NBTTagCompound compound = nmsStack.getTag();
			if (compound == null) {
				compound = new NBTTagCompound();
				nmsStack.setTag(compound);
				compound = nmsStack.getTag();
			}

			NBTTagList list = new NBTTagList();
			NBTTagCompound tags = new NBTTagCompound();

			tags.setString("AttributeName", "generic.attackSpeed");
			tags.setString("Name", "generic.attackSpeed");
			tags.setDouble("Amount", 99999999);
			tags.setInt("Operation", 0);
			tags.setLong("UUIDMost", 90498);
			tags.setLong("UUIDLeast", 161150);

			list.add(tags);
			compound.set("AttributeModifiers", list);

			nmsStack.setTag(compound);
			return CraftItemStack.asBukkitCopy(nmsStack);

			//{AttributeModifiers:[{AttributeName:"generic.attackSpeed",Name:generic.attackSpeed,Amount:99999999,Operation:0,UUIDMost:90498,UUIDLeast:161150}]}
		}
		return null;
	}
}