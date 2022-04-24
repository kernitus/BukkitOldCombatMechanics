/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.versions.materials;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A material that tries each name in a given list until it finds a working material.
 */
public class NameListVersionedMaterial implements VersionedMaterial {

    private Material finalMaterial;

    private NameListVersionedMaterial(Material finalMaterial){
        this.finalMaterial = finalMaterial;
    }

    @Override
    public ItemStack newInstance(){
        return new ItemStack(finalMaterial);
    }

    @Override
    public boolean isSame(ItemStack other){
        return other.getType() == finalMaterial;
    }

    /**
     * Returns a new {@link VersionedMaterial} that picks the first working one from a list of names.
     *
     * @param names the names of the materials
     * @return the versioned material
     * @throws IllegalArgumentException if no material was valid
     */
    public static VersionedMaterial ofNames(String... names){
        for(String name : names){
            Material material = Material.matchMaterial(name);
            if(material != null){
                return new NameListVersionedMaterial(material);
            }

            material = Material.matchMaterial(name, true);
            if(material != null){
                return new NameListVersionedMaterial(material);
            }
        }

        throw new IllegalArgumentException("Could not find any working material, tried: " + String.join(",", names) + ".");
    }
}
