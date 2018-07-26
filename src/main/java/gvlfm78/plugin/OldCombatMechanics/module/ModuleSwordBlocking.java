package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.RunnableSeries;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class ModuleSwordBlocking extends Module {

    private static final ItemStack SHIELD = new ItemStack(Material.SHIELD);
    private final Map<UUID, ItemStack> storedOffhandItems = new HashMap<>();
    private final Map<UUID, RunnableSeries> correspondingTasks = new HashMap<>();
    private int restoreDelay;
    private String blockingDamageReduction;
    private boolean blacklist;
    private List<Material> noBlockingItems = new ArrayList<>();

    public ModuleSwordBlocking(OCMMain plugin){
        super(plugin, "sword-blocking");
    }

    @Override
    public void reload(){
        restoreDelay = module().getInt("restoreDelay", 40);
        blockingDamageReduction = module().getString("blockingDamageReduction", "1")
                .replaceAll(" ", "");
        blacklist = module().getBoolean("blacklist");
        noBlockingItems = ConfigUtils.loadMaterialList(module(), "noBlockingItems");
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e){
        if(e.getItem() == null) return;

        Action action = e.getAction();

        if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if(action == Action.RIGHT_CLICK_BLOCK && Config.getInteractiveBlocks().contains(e.getClickedBlock().getType()))
            return;

        Player p = e.getPlayer();
        World world = p.getWorld();

        if(!isEnabled(world)) return;

        UUID id = p.getUniqueId();

        if(p.isBlocking()){
            tryCancelTask(id);
        } else {
            ItemStack item = e.getItem();

            if(!isHoldingSword(item.getType()) || hasShield(p)) return;

            PlayerInventory inv = p.getInventory();

            boolean isANoBlockingItem = noBlockingItems.contains(inv.getItemInOffHand().getType());

            if(blacklist && isANoBlockingItem || !blacklist && !isANoBlockingItem) return;

            storedOffhandItems.put(id, inv.getItemInOffHand());

            inv.setItemInOffHand(SHIELD);
        }

        scheduleRestore(p);
    }

    @EventHandler
    public void onHotBarChange(PlayerItemHeldEvent e){
        tryCancelTask(e.getPlayer().getUniqueId());
        restore(e.getPlayer());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent e){
        Entity ent = e.getEntity();

        if(!(ent instanceof Player)) return;

        Player p = (Player) ent;

        if(isBlocking(p.getUniqueId())){
            //If it's a player blocking
            //Instead of reducing damage to 33% apply config reduction

            double damageReduction = e.getDamage(); //Reducing by this would mean blocking all damage

            if(blockingDamageReduction.matches("\\d{1,3}%")){
                //Reduce damage by percentage
                int percentage = Integer.parseInt(blockingDamageReduction.replace("%", ""));
                damageReduction = damageReduction * percentage / 100;
            } else if(blockingDamageReduction.matches("\\d+")){
                //Reduce by specified amount of half-hearts
                damageReduction = Integer.parseInt(blockingDamageReduction);
            } else damageReduction = 0;

            if(damageReduction < 0) damageReduction = 0;

            //Only reduce damage if they were hit head on, i.e. the shield blocked some of the damage
            if(e.getDamage(DamageModifier.BLOCKING) >= 0) return;

            //Also make sure reducing the damage doesn't result in negative damage
            e.setDamage(DamageModifier.BLOCKING, 0);

            if(e.getFinalDamage() >= damageReduction)
                e.setDamage(DamageModifier.BLOCKING, damageReduction * -1);

            //Make maximum reduction possible be up to amount specified in config

            if(!isSettingEnabled("shieldFullBlock")){
                double minDamage = module().getDouble("minimumDamage");
                if(e.getFinalDamage() < minDamage)
                    e.setDamage(minDamage);
            }


            debug("Damage reduced by: " + e.getDamage(DamageModifier.BLOCKING), p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e){
        restore(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogout(PlayerQuitEvent e){
        restore(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e){
        if(!isBlocking(e.getEntity().getUniqueId())) return;

        Player p = e.getEntity();
        UUID id = p.getUniqueId();

        e.getDrops().replaceAll(item -> {

            if(item.getType().equals(Material.SHIELD))
                item = storedOffhandItems.remove(id);

            return item;
        });

        // No need for any restore tasks, we've already done that
        tryCancelTask(id);
        // Handle keepInventory = true
        restore(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e){
        Player p = e.getPlayer();
        if(isBlocking(p.getUniqueId()))
            e.setCancelled(true);

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e){

        if(e.getWhoClicked() instanceof Player){
            Player p = (Player) e.getWhoClicked();

            if(isBlocking(p.getUniqueId())){
                ItemStack cursor = e.getCursor();
                ItemStack current = e.getCurrentItem();
                if(cursor != null && cursor.getType() == Material.SHIELD ||
                        current != null && current.getType() == Material.SHIELD){
                    e.setCancelled(true);
                    restore(p);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent e){
        Item is = e.getItemDrop();

        Player p = e.getPlayer();

        if(isBlocking(p.getUniqueId()) && is.getItemStack().getType() == Material.SHIELD){
            e.setCancelled(true);
            restore(p);
        }
    }

    private void scheduleRestore(final Player player){
        BukkitRunnable removeItem = new BukkitRunnable() {
            @Override
            public void run(){
                restore(player);
            }
        };
        removeItem.runTaskLater(plugin, restoreDelay);

        BukkitRunnable checkBlocking = new BukkitRunnable() {
            @Override
            public void run(){
                if(!player.isBlocking()){
                    restore(player);
                    tryCancelTask(player.getUniqueId());
                }
            }
        };
        checkBlocking.runTaskTimer(plugin, 10L, 2L);

        correspondingTasks.put(player.getUniqueId(), new RunnableSeries(removeItem, checkBlocking));
    }

    private void restore(Player p){
        UUID id = p.getUniqueId();

        if(!isBlocking(id)) return;

        if(p.isBlocking()) //They are still blocking with the shield so postpone restoring
            postponeRestoring(p);
        else {
            p.getInventory().setItemInOffHand(storedOffhandItems.get(id));
            storedOffhandItems.remove(id);
        }
    }

    private void tryCancelTask(UUID id){
        RunnableSeries series = correspondingTasks.remove(id);
        if(series != null) series.cancelAll();
    }

    private void postponeRestoring(Player p){
        UUID id = p.getUniqueId();
        Optional.ofNullable(correspondingTasks.get(id))
                .ifPresent(RunnableSeries::cancelAll);

        correspondingTasks.remove(id);
        scheduleRestore(p);
    }

    private boolean isBlocking(UUID uuid){
        return storedOffhandItems.containsKey(uuid);
    }

    private boolean hasShield(Player p){
        return p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isHoldingSword(Material mat){
        return mat.toString().endsWith("_SWORD");
    }
}
