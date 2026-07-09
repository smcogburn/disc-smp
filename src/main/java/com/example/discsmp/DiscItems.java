package com.example.discsmp;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and identifies the unique disc items. */
public final class DiscItems {

    public static NamespacedKey DISC_KEY;

    private DiscItems() {}

    public static void init(DiscSMPPlugin plugin) {
        DISC_KEY = new NamespacedKey(plugin, "disc_id");
    }

    public static ItemStack create(DiscType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + type.getFlavor());
        lore.add("");
        lore.add(type.getColor() + "♫ " + ChatColor.WHITE + type.getAbilityText());
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Disc " + type.getNumber() + " of 10 • One of a kind");
        meta.setLore(lore);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(DISC_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the disc type of an item, or null if it is not one of the ten discs. */
    public static DiscType getDiscType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(DISC_KEY, PersistentDataType.STRING);
        return id == null ? null : DiscType.byId(id);
    }
}
