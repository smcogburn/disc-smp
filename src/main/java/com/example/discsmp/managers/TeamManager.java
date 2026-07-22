package com.example.discsmp.managers;

import com.example.discsmp.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The informal party system. A team is a named, persistent group of players who
 * share their discs: while any online member carries a disc, every online member
 * gets its power (see {@link AbilityManager}). Members can also glow so they can
 * find each other in the world.
 *
 * Invites are kept in memory only - they expire quickly and needn't survive a restart.
 */
public class TeamManager {

    private static final long INVITE_TTL_MS = 60_000;

    private final DataStore data;

    /** invitee UUID -> pending invite (most recent wins). */
    private final Map<UUID, Invite> invites = new HashMap<>();
    /** Members we currently have glowing, so we can cleanly stop when it's turned off. */
    private final Set<UUID> glowing = new HashSet<>();

    public TeamManager(DataStore data) {
        this.data = data;
    }

    private record Invite(String teamId, long expiresAt) {}

    // ---- Queries used across the plugin ----

    /** Online members of the given player's team, excluding the player themselves. */
    public List<Player> onlineTeammates(UUID player) {
        String teamId = data.getPlayerTeam(player);
        if (teamId == null) return List.of();
        List<Player> out = new ArrayList<>();
        for (UUID member : data.getTeamMembers(teamId)) {
            if (member.equals(player)) continue;
            Player p = Bukkit.getPlayer(member);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** True if both players are on the same team (and it's a real team). */
    public boolean sameTeam(UUID a, UUID b) {
        String ta = data.getPlayerTeam(a);
        return ta != null && ta.equals(data.getPlayerTeam(b));
    }

    // ---- Invites (in-memory) ----

    public void invite(UUID target, String teamId) {
        invites.put(target, new Invite(teamId, System.currentTimeMillis() + INVITE_TTL_MS));
    }

    /** The team the player was most recently invited to, or null if none/expired. */
    public String pendingInvite(UUID player) {
        Invite invite = invites.get(player);
        if (invite == null) return null;
        if (System.currentTimeMillis() > invite.expiresAt() || !data.teamExists(invite.teamId())) {
            invites.remove(player);
            return null;
        }
        return invite.teamId();
    }

    public void clearInvite(UUID player) {
        invites.remove(player);
    }

    // ---- Glow: runs on the same 2s cadence as the ability tick ----

    /** Members of a glow-enabled team glow while at least one teammate is online. */
    public void tickGlow() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String teamId = data.getPlayerTeam(p.getUniqueId());
            boolean shouldGlow = teamId != null
                    && data.isTeamGlow(teamId)
                    && !onlineTeammates(p.getUniqueId()).isEmpty();
            if (shouldGlow) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, 60, 0, false, false, false));
                glowing.add(p.getUniqueId());
            } else if (glowing.remove(p.getUniqueId())) {
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        glowing.removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** Generates a short, unique id for a new team. */
    public String newTeamId() {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        } while (data.teamExists(id));
        return id;
    }
}
