package kernitus.plugin.OldCombatMechanics;


import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_9_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.minecraft.server.v1_9_R2.NBTTagCompound;
import net.minecraft.server.v1_9_R2.NBTTagList;

public class OCMListener implements Listener{
	
	private OCMMain plugin;
	public OCMListener(OCMMain instance){
		this.plugin = instance;
	}

	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent e){
		OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);
		Player p = e.getPlayer();
		if(p.isOp()){
			updateChecker.updateNeeded();
			if(plugin.getConfig().getBoolean("settings.checkForUpdates")){
				if(updateChecker.updateNeeded()){
					p.sendMessage("An update for OldCombatMechanics to version " + updateChecker.getVersion()+"is available!");
					p.sendMessage("Click here to download it:"+updateChecker.getLink());
				}
			}
		}
	}
	@EventHandler
	public void onItemEquip(PlayerItemHeldEvent e){
		Player p = e.getPlayer();
		ItemStack item = p.getInventory().getLeggings();

		if(item!=null){
			//Give attributes to item in slot
			ItemStack newItem = NBTTags(p,item);
			if(newItem!=null)
				p.getInventory().setLeggings(newItem);
		}
		else{//Give attributes to wooden button in slot
		
			//Getting values user set from config.yml
			String type = plugin.getConfig().getString("item.type");
			String customName = plugin.getConfig().getString("item.custom-name");
			String lore1 = plugin.getConfig().getString("item.lore1");
			String lore2 = plugin.getConfig().getString("item.lore2");
			
			if(type==null)
				type = "wood_button";
			Material material = Material.matchMaterial(type.toUpperCase());
			if(material==null)
				material = Material.WOOD_BUTTON;
			ItemStack newItem = new ItemStack(material,-1);
			
			newItem = NBTTags(p,newItem);
			if(newItem!=null){
			
			ItemMeta im = newItem.getItemMeta();
			if(customName!=null&&!customName.isEmpty())
				im.setDisplayName(customName.replaceAll("(?i)&([a-fk-r0-9])", "\u00A7$1"));
			List<String> lores = new ArrayList<String>();
			if(lore1!=null)
				lores.add(lore1.replaceAll("(?i)&([a-fk-r0-9])", "\u00A7$1"));
			if(lore2!=null)
				lores.add(lore2.replaceAll("(?i)&([a-fk-r0-9])", "\u00A7$1"));
			if(!lores.isEmpty())
				im.setLore(lores);
			newItem.setItemMeta(im);
			//Setting newly created item
			p.getInventory().setLeggings(newItem);
			}
		}
	}

	public boolean hasTags(ItemStack item){
		net.minecraft.server.v1_9_R2.ItemStack nmsStack = CraftItemStack.asNMSCopy(item);

		//Checking if there are already tags present
		NBTTagCompound compound = nmsStack.getTag();
		if(compound==null)
			compound = new NBTTagCompound();

		//Checking if there are already Attribute Modifiers
		if(compound.hasKey("AttributeModifiers")){
			NBTTagList am = compound.getList("AttributeModifiers",10);
			
			for(int i=0; i<am.size(); i++){
				NBTTagCompound cc = am.get(i);
				
				if(cc.hasKey("AttributeName")){

					String attributeName = cc.getString("AttributeName");

					if(attributeName.equals("generic.attackSpeed")){
						int attackSpeed = cc.getInt("Amount");
						if(attackSpeed==1025){
							return true;
						}
					}
				}
			}
			//By the time we are we have checked through all tags so none must have been found
			return false;
		}
		else
			return false;//No Attribute Modifiers present
	}

	public ItemStack NBTTags(Player p, ItemStack is){
		if(!hasTags(is)){
			net.minecraft.server.v1_9_R2.ItemStack nmsStack = CraftItemStack.asNMSCopy(is);
			NBTTagCompound compound = nmsStack.getTag();
			if(compound==null){
				compound = new NBTTagCompound();
			}
			NBTTagList list = compound.getList("AttributeModifiers", 10);
			if(list==null){
				list = new NBTTagList();
			}
			//Setting attack speed
			NBTTagCompound tags = new NBTTagCompound();
			tags.setString("AttributeName", "generic.attackSpeed");
			tags.setString("Name", "generic.attackSpeed");
			tags.setDouble("Amount", 1025);
			tags.setInt("Operation", 0);
			tags.setLong("UUIDMost", 90498);
			tags.setLong("UUIDLeast", 161150);
			tags.setString("Slot", "legs");

			list.add(tags);

			//Setting armour defence points
			NBTTagCompound defs = new NBTTagCompound();
			defs.setString("AttributeName", "generic.armor");
			defs.setString("Name", "generic.armor");
			defs.setDouble("Amount", getArmourDP(is));
			defs.setInt("Operation", 0);
			defs.setLong("UUIDMost", 666);
			defs.setLong("UUIDLeast", 6666);
			defs.setString("Slot", "legs");
			
			list.add(defs);
			
			//Setting armour toughness
			NBTTagCompound toughs = new NBTTagCompound();
			toughs.setString("AttributeName", "generic.armorToughness");
			toughs.setString("Name", "generic.armorToughness");
			toughs.setDouble("Amount", getArmourT(is));
			toughs.setInt("Operation", 0);
			toughs.setLong("UUIDMost", 666);
			toughs.setLong("UUIDLeast", 6666);
			toughs.setString("Slot", "legs");
			
			list.add(toughs);
			
			compound.set("AttributeModifiers", list);
			nmsStack.setTag(compound);
			
			return CraftItemStack.asBukkitCopy(nmsStack);
		}
		else{
			return null;
		}
	}
	public int getArmourDP(ItemStack is){
		switch(is.getType().name().toLowerCase()){
		case "leather_leggings":
			return 2;
		case "gold_leggings":
			return 3;
		case "chainmail_leggings":
			return 4;
		case "iron_leggings":
			return 5;
		case "diamond_leggings":
			return 6;
		default: return 0;
		}
	}
	public int getArmourT(ItemStack is){
		switch(is.getType().name().toLowerCase()){
		case "diamond_leggings":
			return 2;
		default: return 0;
		}
	}
}