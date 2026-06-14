package com.example.discsmp.listeners;

import com.example.discsmp.DiscSMPPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class DiscListener implements Listener {
    private final DiscSMPPlugin plugin;

    public DiscListener(DiscSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        Byte b = meta.getPersistentDataContainer().get(DiscSMPPlugin.DISC_KEY, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return;

        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.MUSIC_DISC_11, 1.0f, 1.0f);
        event.getPlayer().sendMessage(ChatColor.GREEN + "Playing disc...");
    }
}
