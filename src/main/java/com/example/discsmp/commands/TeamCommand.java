package com.example.discsmp.commands;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.managers.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /team - the informal party system. Create a team, invite friends, and share
 * your discs' powers with everyone on it.
 */
public class TeamCommand implements TabExecutor {

    private static final int MAX_NAME_LENGTH = 24;
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "invite", "accept", "leave", "kick", "rename", "disband", "list", "glow", "help");

    private final DataStore data;
    private final TeamManager teams;

    public TeamCommand(DiscSMPPlugin plugin) {
        this.data = plugin.getDataStore();
        this.teams = plugin.getTeamManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Teams are for players only.");
            return true;
        }
        if (args.length == 0) {
            showTeam(p);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(p, args);
            case "invite", "add" -> invite(p, args);
            case "accept", "join" -> accept(p, args);
            case "leave" -> leave(p);
            case "kick", "remove" -> kick(p, args);
            case "rename" -> rename(p, args);
            case "disband" -> disband(p);
            case "list", "info" -> showTeam(p);
            case "glow" -> glow(p);
            default -> help(p);
        }
        return true;
    }

    // ---- Subcommands ----

    private void create(Player p, String[] args) {
        if (data.getPlayerTeam(p.getUniqueId()) != null) {
            p.sendMessage(ChatColor.RED + "You're already on a team. Use "
                    + ChatColor.YELLOW + "/team leave" + ChatColor.RED + " first.");
            return;
        }
        String name = joinName(args);
        String error = validateName(name);
        if (error != null) {
            p.sendMessage(ChatColor.RED + error);
            return;
        }
        String teamId = teams.newTeamId();
        data.createTeam(teamId, name, p.getUniqueId(), p.getName());
        p.sendMessage(ChatColor.GREEN + "Team " + accent(name) + ChatColor.GREEN
                + " created. You are its leader. Invite friends with "
                + ChatColor.YELLOW + "/team invite <player>" + ChatColor.GREEN + ".");
    }

    private void invite(Player p, String[] args) {
        String teamId = requireTeam(p);
        if (teamId == null) return;
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /team invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "Player not found or not online.");
            return;
        }
        if (target.equals(p)) {
            p.sendMessage(ChatColor.RED + "You can't invite yourself.");
            return;
        }
        if (data.getPlayerTeam(target.getUniqueId()) != null) {
            p.sendMessage(ChatColor.RED + target.getName() + " is already on a team.");
            return;
        }
        teams.invite(target.getUniqueId(), teamId);
        String name = data.getTeamName(teamId);
        p.sendMessage(ChatColor.GREEN + "Invited " + ChatColor.WHITE + target.getName()
                + ChatColor.GREEN + " to " + accent(name) + ChatColor.GREEN + ".");
        target.sendMessage("");
        target.sendMessage(ChatColor.AQUA + p.getName() + ChatColor.GRAY + " invited you to join "
                + accent(name) + ChatColor.GRAY + ".");
        target.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/team accept"
                + ChatColor.GRAY + " within 60 seconds to join.");
        target.sendMessage("");
    }

    private void accept(Player p, String[] args) {
        if (data.getPlayerTeam(p.getUniqueId()) != null) {
            p.sendMessage(ChatColor.RED + "You're already on a team.");
            return;
        }
        String teamId;
        if (args.length >= 2) {
            teamId = data.findTeamByName(joinName(args));
            if (teamId == null || !teamId.equals(teams.pendingInvite(p.getUniqueId()))) {
                p.sendMessage(ChatColor.RED + "You have no pending invite to that team.");
                return;
            }
        } else {
            teamId = teams.pendingInvite(p.getUniqueId());
            if (teamId == null) {
                p.sendMessage(ChatColor.RED + "You have no pending team invite.");
                return;
            }
        }
        teams.clearInvite(p.getUniqueId());
        data.addMember(teamId, p.getUniqueId(), p.getName());
        String name = data.getTeamName(teamId);
        p.sendMessage(ChatColor.GREEN + "You joined " + accent(name) + ChatColor.GREEN
                + "! You now share discs with your team.");
        broadcastTeam(teamId, ChatColor.AQUA + p.getName() + ChatColor.GRAY + " joined the team.", p);
    }

    private void leave(Player p) {
        String teamId = requireTeam(p);
        if (teamId == null) return;
        String name = data.getTeamName(teamId);
        boolean wasLeader = p.getUniqueId().equals(data.getTeamLeader(teamId));
        data.removeMember(teamId, p.getUniqueId());
        p.sendMessage(ChatColor.GRAY + "You left " + accent(name) + ChatColor.GRAY + ".");

        List<UUID> remaining = data.getTeamMembers(teamId);
        if (remaining.isEmpty()) {
            data.deleteTeam(teamId);
            return;
        }
        if (wasLeader) {
            UUID heir = remaining.get(0);
            data.setTeamLeader(teamId, heir);
            broadcastTeam(teamId, ChatColor.GRAY + p.getName() + " left. "
                    + ChatColor.WHITE + data.getMemberName(teamId, heir)
                    + ChatColor.GRAY + " is the new leader.", null);
        } else {
            broadcastTeam(teamId, ChatColor.GRAY + p.getName() + " left the team.", null);
        }
    }

    private void kick(Player p, String[] args) {
        String teamId = requireLeader(p);
        if (teamId == null) return;
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /team kick <player>");
            return;
        }
        OfflinePlayer target = resolveMember(teamId, args[1]);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "No one named " + args[1] + " is on your team.");
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Use " + ChatColor.YELLOW + "/team disband"
                    + ChatColor.RED + " to remove yourself as leader.");
            return;
        }
        data.removeMember(teamId, target.getUniqueId());
        p.sendMessage(ChatColor.GREEN + "Removed " + ChatColor.WHITE + target.getName()
                + ChatColor.GREEN + " from the team.");
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.RED + "You were removed from "
                    + accent(data.getTeamName(teamId)) + ChatColor.RED + ".");
        }
        broadcastTeam(teamId, ChatColor.GRAY + target.getName() + " was removed from the team.", null);
    }

    private void rename(Player p, String[] args) {
        String teamId = requireLeader(p);
        if (teamId == null) return;
        String name = joinName(args);
        String error = validateName(name);
        if (error != null) {
            p.sendMessage(ChatColor.RED + error);
            return;
        }
        data.setTeamName(teamId, name);
        broadcastTeam(teamId, ChatColor.GREEN + "The team is now called " + accent(name)
                + ChatColor.GREEN + ".", null);
    }

    private void disband(Player p) {
        String teamId = requireLeader(p);
        if (teamId == null) return;
        String name = data.getTeamName(teamId);
        broadcastTeam(teamId, ChatColor.RED + accent(name) + ChatColor.RED + " has been disbanded.", null);
        data.deleteTeam(teamId);
    }

    private void glow(Player p) {
        String teamId = requireLeader(p);
        if (teamId == null) return;
        boolean now = !data.isTeamGlow(teamId);
        data.setTeamGlow(teamId, now);
        broadcastTeam(teamId, ChatColor.GRAY + "Team glow is now "
                + (now ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF") + ChatColor.GRAY + ".", null);
    }

    private void showTeam(Player p) {
        String teamId = data.getPlayerTeam(p.getUniqueId());
        if (teamId == null) {
            p.sendMessage(ChatColor.GRAY + "You're not on a team. Start one with "
                    + ChatColor.YELLOW + "/team create <name>" + ChatColor.GRAY + ".");
            return;
        }
        UUID leader = data.getTeamLeader(teamId);
        p.sendMessage("");
        p.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "  ♫ "
                + accent(data.getTeamName(teamId)));
        for (UUID member : data.getTeamMembers(teamId)) {
            boolean online = Bukkit.getPlayer(member) != null;
            String tag = member.equals(leader) ? ChatColor.GOLD + " ★" : "";
            p.sendMessage((online ? ChatColor.GREEN + "   ● " : ChatColor.DARK_GRAY + "   ○ ")
                    + (online ? ChatColor.WHITE : ChatColor.GRAY)
                    + data.getMemberName(teamId, member) + tag);
        }
        p.sendMessage(ChatColor.DARK_GRAY + "  Glow: "
                + (data.isTeamGlow(teamId) ? "on" : "off")
                + " • ★ = leader");
        p.sendMessage("");
    }

    private void help(Player p) {
        p.sendMessage("");
        p.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "  ♫ Team commands");
        line(p, "create <name>", "start a new team");
        line(p, "invite <player>", "invite an online player");
        line(p, "accept", "accept your pending invite");
        line(p, "leave", "leave your team");
        line(p, "kick <player>", "remove a member (leader)");
        line(p, "rename <name>", "rename the team (leader)");
        line(p, "glow", "toggle team glow (leader)");
        line(p, "disband", "delete the team (leader)");
        line(p, "list", "show your team");
        p.sendMessage("");
    }

    // ---- Helpers ----

    private void line(Player p, String usage, String desc) {
        p.sendMessage(ChatColor.YELLOW + "  /team " + usage + ChatColor.DARK_GRAY + " — "
                + ChatColor.GRAY + desc);
    }

    /** Returns the player's team id, or null (with a message) if they have none. */
    private String requireTeam(Player p) {
        String teamId = data.getPlayerTeam(p.getUniqueId());
        if (teamId == null) {
            p.sendMessage(ChatColor.RED + "You're not on a team. Create one with "
                    + ChatColor.YELLOW + "/team create <name>" + ChatColor.RED + ".");
        }
        return teamId;
    }

    /** Returns the player's team id only if they lead it, else null (with a message). */
    private String requireLeader(Player p) {
        String teamId = requireTeam(p);
        if (teamId == null) return null;
        if (!p.getUniqueId().equals(data.getTeamLeader(teamId))) {
            p.sendMessage(ChatColor.RED + "Only the team leader can do that.");
            return null;
        }
        return teamId;
    }

    /** Finds a team member by name (online or offline), or null. */
    private OfflinePlayer resolveMember(String teamId, String name) {
        for (UUID member : data.getTeamMembers(teamId)) {
            if (data.getMemberName(teamId, member).equalsIgnoreCase(name)) {
                return Bukkit.getOfflinePlayer(member);
            }
        }
        return null;
    }

    private void broadcastTeam(String teamId, String message, Player except) {
        for (UUID member : data.getTeamMembers(teamId)) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && !online.equals(except)) online.sendMessage(message);
        }
    }

    private String joinName(String[] args) {
        return String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
    }

    private String validateName(String name) {
        if (name.isEmpty()) return "Give your team a name: /team create <name>";
        if (ChatColor.stripColor(name).length() > MAX_NAME_LENGTH) {
            return "That name is too long (max " + MAX_NAME_LENGTH + " characters).";
        }
        String existing = data.findTeamByName(name);
        if (existing != null) return "A team named \"" + name + "\" already exists.";
        return null;
    }

    private String accent(String name) {
        return ChatColor.AQUA + "" + ChatColor.BOLD + name + ChatColor.RESET;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && sender instanceof Player p) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("add")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        out.add(online.getName());
                    }
                }
            } else if (sub.equals("kick") || sub.equals("remove")) {
                String teamId = data.getPlayerTeam(p.getUniqueId());
                if (teamId != null) {
                    for (UUID member : data.getTeamMembers(teamId)) {
                        String name = data.getMemberName(teamId, member);
                        if (name.toLowerCase().startsWith(args[1].toLowerCase())) out.add(name);
                    }
                }
            }
        }
        return out;
    }
}
