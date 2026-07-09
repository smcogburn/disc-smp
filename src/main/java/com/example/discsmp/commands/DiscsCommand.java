package com.example.discsmp.commands;

import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /discs - every player can see the state of the hunt. */
public class DiscsCommand implements CommandExecutor {

    private final DiscSMPPlugin plugin;

    public DiscsCommand(DiscSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "  ♫ The Ten Discs of the Disc SMP");
        for (DiscType t : DiscType.values()) {
            String state = plugin.getDataStore().isClaimed(t)
                    ? ChatColor.WHITE + "held by " + ChatColor.BOLD + plugin.getDataStore().getOwnerName(t)
                    : ChatColor.DARK_GRAY + "sleeping in " + t.getStructureName();
            sender.sendMessage(t.getColor() + "   " + t.getNumber() + ". " + t.getTheme()
                    + ChatColor.GRAY + " (" + t.getSongName() + ") " + ChatColor.DARK_GRAY + "— " + state);
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_GRAY + "  " + ChatColor.ITALIC
                + "The rite: ten discs, ten jukeboxes, side by side, all singing at once.");
        sender.sendMessage("");
        return true;
    }
}
