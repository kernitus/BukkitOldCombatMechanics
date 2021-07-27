package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Reverts the armour strength changes.
 * <p>
 * It is based on <a href="https://minecraft.gamepedia.com/index.php?title=Armor&oldid=909187">this revision</a>
 * of the minecraft wiki.
 */
public class ModuleOldArmourStrength extends Module {

    private static final double REDUCTION_PER_ARMOUR_POINT = 0.04;

    private static final Set<EntityDamageEvent.DamageCause> NON_REDUCED_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.STARVATION,
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.LIGHTNING
    );

    static {
        if (Reflector.versionIsNewerOrEqualAs(1, 17, 0))
            NON_REDUCED_CAUSES.add(EntityDamageEvent.DamageCause.FREEZE);
    }

    public ModuleOldArmourStrength(OCMMain plugin) {
        super(plugin, "old-armour-strength");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent e) {
        //1.8 NMS: Damage = 25 / (damage after blocking * (25 - total armour strength))

        if (!(e.getEntity() instanceof LivingEntity)) return;

        LivingEntity damagedEntity = (LivingEntity) e.getEntity();

        if (!e.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) return;

        final double armourPoints = damagedEntity.getAttribute(Attribute.GENERIC_ARMOR).getValue();
        final double reductionPercentage = armourPoints * REDUCTION_PER_ARMOUR_POINT;

        // Apply armour damage reduction after blocking reduction
        final double reducedDamage = (e.getDamage() + e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)) * reductionPercentage;
        final EntityDamageEvent.DamageCause damageCause = e.getCause();

        if (!NON_REDUCED_CAUSES.contains(damageCause) && e.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            //debug("Damage Cause: " + damageCause + " is applicable");
            e.setDamage(EntityDamageEvent.DamageModifier.ARMOR, -reducedDamage);
        }

        // Don't calculate enchantment reduction if damage is already 0. NMS 1.8 does it this way.
        final double enchantmentReductionPercentage = e.getFinalDamage() <= 0 ? 0 :
                calculateEnchantmentReductionPercentage(damagedEntity.getEquipment(), e.getCause());

        if (enchantmentReductionPercentage > 0) {
            //Reset MAGIC (Armour enchants) damage
            e.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);

            //Set new MAGIC (Armour enchants) damage
            e.setDamage(EntityDamageEvent.DamageModifier.MAGIC,
                    -e.getFinalDamage() * enchantmentReductionPercentage);
        }

        debug(String.format("Reductions: Armour %.0f, Ench %.0f, Total %.2f, Final Damage: %.2f", reductionPercentage * 100,
                enchantmentReductionPercentage * 100, (reductionPercentage + (1 - reductionPercentage) * enchantmentReductionPercentage) * 100,
                e.getFinalDamage()), damagedEntity);
    }

    private double calculateEnchantmentReductionPercentage(EntityEquipment equipment, EntityDamageEvent.DamageCause cause) {
        int totalEpf = 0;
        for (ItemStack armourItem : equipment.getArmorContents()) {
            if (armourItem != null && armourItem.getType() != Material.AIR) {
                for (EnchantmentType enchantmentType : EnchantmentType.values()) {

                    if (!enchantmentType.protectsAgainst(cause)) continue;

                    int enchantmentLevel = armourItem.getEnchantmentLevel(enchantmentType.getEnchantment());

                    if (enchantmentLevel > 0) {
                        totalEpf += enchantmentType.getEpf(enchantmentLevel);
                    }
                }
            }
        }

        //debug("Initial total EPF: " + totalEpf);

        // capped at 25
        totalEpf = Math.min(25, totalEpf);

        totalEpf = (int) Math.ceil(totalEpf * ThreadLocalRandom.current().nextDouble(0.5, 1));

        // capped at 20
        totalEpf = Math.min(20, totalEpf);

        return REDUCTION_PER_ARMOUR_POINT * totalEpf;
    }

    private enum EnchantmentType {
        // Data from https://minecraft.fandom.com/wiki/Armor#Mechanics
        PROTECTION(() -> {
            EnumSet<EntityDamageEvent.DamageCause> damageCauses = EnumSet.of(
                    EntityDamageEvent.DamageCause.CONTACT,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
                    EntityDamageEvent.DamageCause.PROJECTILE,
                    EntityDamageEvent.DamageCause.FALL,
                    EntityDamageEvent.DamageCause.FIRE,
                    EntityDamageEvent.DamageCause.LAVA,
                    EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                    EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                    EntityDamageEvent.DamageCause.LIGHTNING,
                    EntityDamageEvent.DamageCause.POISON,
                    EntityDamageEvent.DamageCause.MAGIC,
                    EntityDamageEvent.DamageCause.WITHER,
                    EntityDamageEvent.DamageCause.FALLING_BLOCK,
                    EntityDamageEvent.DamageCause.THORNS,
                    EntityDamageEvent.DamageCause.DRAGON_BREATH
            );
            if (Reflector.versionIsNewerOrEqualAs(1, 10, 0))
                damageCauses.add(EntityDamageEvent.DamageCause.HOT_FLOOR);

            return damageCauses;
        },
                0.75, Enchantment.PROTECTION_ENVIRONMENTAL),
        FIRE_PROTECTION(() -> {
            EnumSet<EntityDamageEvent.DamageCause> damageCauses = EnumSet.of(
                    EntityDamageEvent.DamageCause.FIRE,
                    EntityDamageEvent.DamageCause.FIRE_TICK,
                    EntityDamageEvent.DamageCause.LAVA
            );

            if (Reflector.versionIsNewerOrEqualAs(1, 10, 0)) {
                damageCauses.add(EntityDamageEvent.DamageCause.HOT_FLOOR);
            }

            return damageCauses;
        }, 1.25, Enchantment.PROTECTION_FIRE),
        BLAST_PROTECTION(() -> EnumSet.of(
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        ), 1.5, Enchantment.PROTECTION_EXPLOSIONS),
        PROJECTILE_PROTECTION(() -> EnumSet.of(
                EntityDamageEvent.DamageCause.PROJECTILE
        ), 1.5, Enchantment.PROTECTION_PROJECTILE),
        FALL_PROTECTION(() -> EnumSet.of(
                EntityDamageEvent.DamageCause.FALL
        ), 2.5, Enchantment.PROTECTION_FALL);

        private final Set<EntityDamageEvent.DamageCause> protection;
        private final double typeModifier;
        private final Enchantment enchantment;

        EnchantmentType(Supplier<Set<EntityDamageEvent.DamageCause>> protection, double typeModifier, Enchantment enchantment) {
            this.protection = protection.get();
            this.typeModifier = typeModifier;
            this.enchantment = enchantment;
        }

        /**
         * Returns whether the armour protects against the given damage cause.
         *
         * @param cause the damage cause
         * @return true if the armour protects against the given damage cause
         */
        public boolean protectsAgainst(EntityDamageEvent.DamageCause cause) {
            return protection.contains(cause);
        }

        /**
         * Returns the bukkit enchantment.
         *
         * @return the bukkit enchantment
         */
        public Enchantment getEnchantment() {
            return enchantment;
        }

        /**
         * Returns the enchantment protection factor (EPF).
         *
         * @param level the level of the enchantment
         * @return the EPF
         */
        public int getEpf(int level) {
            // floor ( (6 + level^2) * TypeModifier / 3 )
            return (int) Math.floor((6 + level * level) * typeModifier / 3);
        }
    }
}
