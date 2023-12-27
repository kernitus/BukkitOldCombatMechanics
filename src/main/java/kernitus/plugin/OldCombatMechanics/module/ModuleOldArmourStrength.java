/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DefenceUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reverts the armour strength changes to 1.8 calculations, including enchantments.
 * Also recalculates resistance and absorption accordingly.
 * <p>
 * It is based on <a href="https://minecraft.gamepedia.com/index.php?title=Armor&oldid=909187">this revision</a>
 * of the minecraft wiki.
 */
public class ModuleOldArmourStrength extends OCMModule {
// Defence order is armour defence points -> resistance -> armour enchants -> absorption

    private boolean randomness;

    public ModuleOldArmourStrength(OCMMain plugin) {
        super(plugin, "old-armour-strength");
        reload();
    }

    @Override
    public void reload() {
        randomness = module().getBoolean("randomness");
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        // 1.8 NMS: Damage = (damage after blocking * (25 - total armour strength)) / 25
        if (!(e.getEntity() instanceof LivingEntity)) return;

        final LivingEntity damagedEntity = (LivingEntity) e.getEntity();

        // If there was an attacker, and he does not have this module enabled, return
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && e instanceof EntityDamageByEntityEvent) {
            final Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
            if(!isEnabled(damager, damagedEntity)) return;
        }

        final Map<EntityDamageEvent.DamageModifier, Double> damageModifiers =
                Arrays.stream(EntityDamageEvent.DamageModifier.values())
                        .filter(e::isApplicable)
                        .collect(Collectors.toMap(m -> m, e::getDamage));

        DefenceUtils.calculateDefenceDamageReduction(damagedEntity, damageModifiers, e.getCause(), randomness);

        // Set the modifiers back to the event
        damageModifiers.forEach(e::setDamage);

        debug("BASE: " + damageModifiers.get(EntityDamageEvent.DamageModifier.BASE));
        debug("BLOCKING: " + damageModifiers.get(EntityDamageEvent.DamageModifier.BLOCKING));
        debug("ARMOUR: " + damageModifiers.get(EntityDamageEvent.DamageModifier.ARMOR));
        debug("RESISTANCE: " + damageModifiers.get(EntityDamageEvent.DamageModifier.RESISTANCE));
        debug("ARMOUR ENCHS: " + damageModifiers.get(EntityDamageEvent.DamageModifier.MAGIC));
        debug("ABSORPTION: " + damageModifiers.get(EntityDamageEvent.DamageModifier.ABSORPTION));

        final double finalDamage = damageModifiers.values().stream().reduce(0.0, Double::sum);
        debug("Final damage after defence calc: " + finalDamage);
    }
}
