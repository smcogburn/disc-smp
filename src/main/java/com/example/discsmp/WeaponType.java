package com.example.discsmp;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The six ritual weapons. Enchanted far beyond vanilla limits; each may only
 * ever be claimed once.
 */
public enum WeaponType {

    SWORD("Sword", Material.NETHERITE_SWORD, ChatColor.RED,
            ench(Enchantment.SHARPNESS, 7, Enchantment.FIRE_ASPECT, 3,
                    Enchantment.LOOTING, 5, Enchantment.SWEEPING_EDGE, 4)),

    AXE("Axe", Material.NETHERITE_AXE, ChatColor.DARK_RED,
            ench(Enchantment.SHARPNESS, 7, Enchantment.EFFICIENCY, 6,
                    Enchantment.FIRE_ASPECT, 2, Enchantment.KNOCKBACK, 2)),

    BOW("Bow", Material.BOW, ChatColor.GREEN,
            ench(Enchantment.POWER, 7, Enchantment.FLAME, 2,
                    Enchantment.PUNCH, 3, Enchantment.INFINITY, 1)),

    MACE("Mace", Material.MACE, ChatColor.GOLD,
            ench(Enchantment.DENSITY, 7, Enchantment.WIND_BURST, 4,
                    Enchantment.BREACH, 6, Enchantment.FIRE_ASPECT, 2)),

    SPEAR("Spear", Material.TRIDENT, ChatColor.YELLOW,
            ench(Enchantment.IMPALING, 7, Enchantment.LOYALTY, 4,
                    Enchantment.CHANNELING, 1, Enchantment.SHARPNESS, 6)),

    TRIDENT("Trident", Material.TRIDENT, ChatColor.AQUA,
            ench(Enchantment.RIPTIDE, 4, Enchantment.IMPALING, 7,
                    Enchantment.SHARPNESS, 5));

    private final String displayName;
    private final Material material;
    private final ChatColor color;
    private final Map<Enchantment, Integer> enchants;

    WeaponType(String displayName, Material material, ChatColor color, Map<Enchantment, Integer> enchants) {
        this.displayName = displayName;
        this.material = material;
        this.color = color;
        this.enchants = enchants;
    }

    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public ChatColor getColor() { return color; }
    public Map<Enchantment, Integer> getEnchants() { return enchants; }

    public static WeaponType byId(String id) {
        for (WeaponType t : values()) {
            if (t.name().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    private static Map<Enchantment, Integer> ench(Object... pairs) {
        Map<Enchantment, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
