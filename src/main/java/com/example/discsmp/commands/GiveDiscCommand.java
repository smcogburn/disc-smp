package com.example.discsmp.commands;

import com.example.discsmp.DiscSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class GiveDiscCommand implements CommandExecutor {
    private final DiscSMPPlugin plugin;

    public GiveDiscCommand(DiscSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found");
                return true;
            }
        } else {
            if (sender instanceof Player) target = (Player) sender;
            else {
                sender.sendMessage(ChatColor.RED + "Specify a player");
                return true;
            }
        }

        ItemStack disc = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = disc.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Disc SMP");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "A special disc", ChatColor.GRAY + "Right-click to play."));
            meta.getPersistentDataContainer().set(DiscSMPPlugin.DISC_KEY, PersistentDataType.BYTE, (byte) 1);
            disc.setItemMeta(meta);
        }

        target.getInventory().addItem(disc);
        sender.sendMessage(ChatColor.GREEN + "Gave disc to " + target.getName());
        return true;
    }
}
