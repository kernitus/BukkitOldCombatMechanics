/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;

public final class WeaknessCompensation {
    public static final UUID MODIFIER_UUID = UUID.fromString("5cf9f56c-7b95-4d39-9e1f-7c0b4c84f26c");
    private static final String MODIFIER_NAME = "OCM-Weakness-Compensation";
    private static final double MODIFIER_AMOUNT = 4.0;

    private WeaknessCompensation() {
    }

    public static boolean hasModifier(LivingEntity entity) {
        final AttributeInstance attribute = getAttackDamageAttribute(entity);
        if (attribute == null) return false;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (MODIFIER_UUID.equals(modifier.getUniqueId())) return true;
        }
        return false;
    }

    public static void apply(LivingEntity entity) {
        final AttributeInstance attribute = getAttackDamageAttribute(entity);
        if (attribute == null || hasModifier(entity)) return;
        final AttributeModifier modifier = new AttributeModifier(
                MODIFIER_UUID,
                MODIFIER_NAME,
                MODIFIER_AMOUNT,
                AttributeModifier.Operation.ADD_NUMBER
        );
        attribute.addModifier(modifier);
    }

    public static void remove(LivingEntity entity) {
        final AttributeInstance attribute = getAttackDamageAttribute(entity);
        if (attribute == null) return;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (MODIFIER_UUID.equals(modifier.getUniqueId())) {
                attribute.removeModifier(modifier);
            }
        }
    }

    private static AttributeInstance getAttackDamageAttribute(LivingEntity entity) {
        final Attribute attribute = XAttribute.ATTACK_DAMAGE.get();
        if (attribute == null) return null;
        return entity.getAttribute(attribute);
    }
}
