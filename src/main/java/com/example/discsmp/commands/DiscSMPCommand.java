package com.example.discsmp.commands;

import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Admin tools: /discsmp <setshrine|locate|give|status|reset|reroll|recipe> */
public class DiscSMPCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "setshrine", "locate", "give", "status", "reset", "reroll", "recipe");

    private final DiscSMPPlugin plugin;

    public DiscSMPCommand(DiscSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/discsmp <" + String.join("|", SUBCOMMANDS) + "> [disc] [player]");
            return true;
        }
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "status" -> {
                sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "The Ten Discs");
                for (DiscType t : DiscType.values()) {
                    boolean claimed = plugin.getDataStore().isClaimed(t);
                    Location shrine = plugin.getDataStore().getShrine(t);
                    String shrineInfo = shrine == null ? ChatColor.RED + "no shrine set"
                            : ChatColor.GRAY + "shrine " + shrine.getBlockX() + "," + shrine.getBlockY()
                            + "," + shrine.getBlockZ() + " (" + shrine.getWorld().getName() + ")";
                    String owner = claimed
                            ? ChatColor.WHITE + "held by " + plugin.getDataStore().getOwnerName(t)
                            : ChatColor.DARK_GRAY + "unclaimed";
                    sender.sendMessage(t.getColor() + " " + t.getNumber() + ". " + t.getTheme()
                            + " " + owner + ChatColor.DARK_GRAY + " • " + shrineInfo);
                }
                return true;
            }
            case "setshrine" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "In-game only.");
                    return true;
                }
                DiscType t = requireDisc(sender, args);
                if (t == null) return true;
                Block target = p.getTargetBlockExact(6);
                Location loc = target != null ? target.getLocation() : p.getLocation();
                plugin.getShrineManager().setShrine(t, loc);
                sender.sendMessage(t.getColor() + "Shrine of the " + t.getTheme()
                        + ChatColor.GREEN + " set at " + loc.getBlockX() + "," + loc.getBlockY()
                        + "," + loc.getBlockZ() + ChatColor.GRAY + " (sculk catalyst placed).");
                return true;
            }
            case "locate" -> {
                List<DiscType> targets = args.length >= 2 && args[1].equalsIgnoreCase("all")
                        ? Arrays.asList(DiscType.values())
                        : Collections.singletonList(requireDisc(sender, args));
                if (targets.get(0) == null) return true;
                sender.sendMessage(ChatColor.GRAY + "Searching for structures, this can take a moment...");
                for (DiscType t : targets) {
                    Location loc = plugin.getShrineManager().locateAndSetShrine(t);
                    if (loc == null) {
                        sender.sendMessage(ChatColor.RED + "Could not locate " + t.getStructureName()
                                + " for the " + t.getTheme() + ". Set it manually with /discsmp setshrine.");
                    } else {
                        sender.sendMessage(t.getColor() + t.getTheme() + ChatColor.GREEN + " shrine set at "
                                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                                + ChatColor.GRAY + " in " + loc.getWorld().getName()
                                + " - verify placement inside the structure!");
                    }
                }
                return true;
            }
            case "give" -> {
                DiscType t = requireDisc(sender, args);
                if (t == null) return true;
                Player target = args.length >= 3 ? Bukkit.getPlayer(args[2])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                plugin.getDataStore().setClaimed(t, target.getUniqueId(), target.getName());
                target.getInventory().addItem(DiscItems.create(t));
                plugin.getAbilityManager().onDiscAcquired(target, t);
                sender.sendMessage(ChatColor.GREEN + "Gave the " + t.getTheme() + " to " + target.getName() + ".");
                return true;
            }
            case "reset" -> {
                DiscType t = requireDisc(sender, args);
                if (t == null) return true;
                plugin.getDataStore().setUnclaimed(t);
                if (t == DiscType.GAMBLING) plugin.getDataStore().setGamble(null, null);
                sender.sendMessage(ChatColor.GREEN + "The " + t.getTheme()
                        + " is unclaimed again and can be forged at its shrine.");
                return true;
            }
            case "reroll" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayer(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                plugin.getDataStore().setGamble(null, null);
                plugin.getAbilityManager().rollDice(target);
                return true;
            }
            case "recipe" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "In-game only.");
                    return true;
                }
                DiscType t = requireDisc(sender, args);
                if (t == null) return true;
                plugin.getShrineManager().sendRecipe(p, t);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                return true;
            }
        }
    }

    private DiscType requireDisc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Specify a disc: "
                    + Arrays.toString(Arrays.stream(DiscType.values()).map(Enum::name).toArray()));
            return null;
        }
        DiscType t = DiscType.byId(args[1]);
        if (t == null && !args[1].equalsIgnoreCase("all")) {
            sender.sendMessage(ChatColor.RED + "Unknown disc: " + args[1]);
        }
        return t;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBCOMMANDS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reroll")) {
            for (DiscType t : DiscType.values()) {
                if (t.name().toLowerCase().startsWith(args[1].toLowerCase())) out.add(t.name().toLowerCase());
            }
            if (args[0].equalsIgnoreCase("locate") && "all".startsWith(args[1].toLowerCase())) out.add("all");
        }
        return out;
    }
}
