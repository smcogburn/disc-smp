package com.example.discsmp.managers;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Applies each disc's power while it is held (main or off hand), the Pain
 * disc's curse aura, and the Gambling disc's per-owner dice roll.
 */
public class AbilityManager {

    private static final double PAIN_AURA_RADIUS = 8.0;

    private final DiscSMPPlugin plugin;
    private final DataStore data;
    private final Random random = new Random();
    /** Players mid-dice-roll; the gamble power is suspended until the roll lands. */
    private final Set<UUID> rolling = new java.util.HashSet<>();

    public AbilityManager(DiscSMPPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    /** Runs every 2 seconds. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<DiscType> held = EnumSet.noneOf(DiscType.class);
            DiscType main = DiscItems.getDiscType(p.getInventory().getItemInMainHand());
            DiscType off = DiscItems.getDiscType(p.getInventory().getItemInOffHand());
            if (main != null) held.add(main);
            if (off != null) held.add(off);
            if (held.isEmpty()) continue;

            for (DiscType type : held) {
                for (PotionEffect effect : type.getHeldEffects()) {
                    p.addPotionEffect(effect);
                }
                if (type == DiscType.PAIN) {
                    applyPainAura(p);
                }
                if (type == DiscType.GAMBLING) {
                    applyGamble(p);
                }
            }
        }
    }

    private void applyPainAura(Player holder) {
        for (Entity e : holder.getNearbyEntities(PAIN_AURA_RADIUS, PAIN_AURA_RADIUS, PAIN_AURA_RADIUS)) {
            if (!(e instanceof LivingEntity living)) continue;
            for (PotionEffect effect : DiscType.PAIN_AURA) {
                living.addPotionEffect(effect);
            }
        }
    }

    private void applyGamble(Player p) {
        if (rolling.contains(p.getUniqueId())) return;
        UUID owner = data.getGambleOwner();
        if (owner == null || !owner.equals(p.getUniqueId())) {
            // The disc changed hands without a pickup event (e.g. inventory swap) - reroll.
            onDiscAcquired(p, DiscType.GAMBLING);
            return;
        }
        PotionEffectType type = resolveEffect(data.getGambleEffect());
        if (type != null) {
            p.addPotionEffect(new PotionEffect(type, 160, 4, true, false, true));
        }
    }

    /**
     * Called whenever a disc enters a player's possession. Keeps the owner
     * record fresh and rerolls the Gambling disc for a new holder.
     */
    public void onDiscAcquired(Player p, DiscType type) {
        UUID previous = data.getOwner(type);
        data.setOwner(type, p.getUniqueId(), p.getName());
        if (type == DiscType.GAMBLING) {
            UUID gambleOwner = data.getGambleOwner();
            if (gambleOwner == null || !gambleOwner.equals(p.getUniqueId())) {
                rollDice(p);
            }
        } else if (previous == null || !previous.equals(p.getUniqueId())) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
            p.sendMessage(type.getColor() + "♫ " + ChatColor.GRAY + "The " + type.getColor()
                    + type.getTheme() + ChatColor.GRAY + " is yours now. " + ChatColor.WHITE
                    + type.getAbilityText() + ChatColor.GRAY + ".");
        }
    }

    /** The dice-roll animation: powers flicker past on the action bar, then one lands. */
    public void rollDice(Player p) {
        if (!rolling.add(p.getUniqueId())) return;
        data.setGamble(p.getUniqueId(), null);
        p.sendMessage(DiscType.GAMBLING.getColor() + "♫ " + ChatColor.GRAY
                + "The Gambling disc senses a new hand... the dice are rolling.");

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline()) {
                    rolling.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                ticks += 3;
                PotionEffectType shown = DiscType.GAMBLE_POOL
                        .get(random.nextInt(DiscType.GAMBLE_POOL.size()));
                if (ticks < 60) {
                    p.sendActionBar(ChatColor.DARK_PURPLE + "⚄ " + ChatColor.LIGHT_PURPLE
                            + prettyEffect(shown) + " V" + ChatColor.DARK_PURPLE + " ⚄");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                            0.7f, 0.7f + (ticks / 60.0f));
                } else {
                    cancel();
                    rolling.remove(p.getUniqueId());
                    data.setGamble(p.getUniqueId(), shown.getKey().getKey());
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    p.sendTitle(ChatColor.DARK_PURPLE + "⚄ " + ChatColor.LIGHT_PURPLE + ChatColor.BOLD
                                    + prettyEffect(shown) + " V" + ChatColor.DARK_PURPLE + " ⚄",
                            ChatColor.GRAY + "The dice have spoken.", 5, 60, 20);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private PotionEffectType resolveEffect(String key) {
        if (key == null) return null;
        return Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }

    private String prettyEffect(PotionEffectType type) {
        return capitalizeWords(type.getKey().getKey().replace('_', ' '));
    }

    private String capitalizeWords(String s) {
        StringBuilder sb = new StringBuilder();
        for (String part : s.split(" ")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
