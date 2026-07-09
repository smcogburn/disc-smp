package com.example.discsmp.managers;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Disc powers apply while the disc is ANYWHERE in a player's inventory, are
 * refreshed forever, and show no potion icons or particles - sneaky.
 *
 * Phantom is special: Speed II is constant, but true invisibility pulses
 * (30s on, 30s off) - and while it's on, the wearer's armor is hidden from
 * everyone too, leaving only a floating held item.
 */
public class AbilityManager {

    private static final double PAIN_AURA_RADIUS = 8.0;
    private static final long INVIS_CYCLE_SECONDS = 30;

    private final DiscSMPPlugin plugin;
    private final DataStore data;
    private final Random random = new Random();
    /** Players mid-dice-roll; the gamble power is suspended until the roll lands. */
    private final Set<UUID> rolling = new HashSet<>();
    /** Effects we applied last tick, so we can cleanly remove them when a disc leaves. */
    private final Map<UUID, Set<PotionEffectType>> applied = new HashMap<>();
    /** Players whose armor is currently hidden by the Phantom cloak. */
    private final Set<UUID> cloaked = new HashSet<>();

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public AbilityManager(DiscSMPPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    /** Runs every 2 seconds. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<DiscType> discs = discsInInventory(p);
            Map<PotionEffectType, Integer> desired = new HashMap<>();

            for (DiscType type : discs) {
                for (PotionEffect effect : type.getHeldEffects()) {
                    desired.merge(effect.getType(), effect.getAmplifier(), Math::max);
                }
                if (type == DiscType.PAIN) applyPainAura(p);
                if (type == DiscType.GAMBLING) addGambleEffect(p, desired);
                if (type == DiscType.PHANTOM && invisPhaseOn()) {
                    desired.merge(PotionEffectType.INVISIBILITY, 0, Math::max);
                }
            }

            // apply/refresh what the discs grant, silently (no icon, no particles)
            for (Map.Entry<PotionEffectType, Integer> e : desired.entrySet()) {
                p.addPotionEffect(new PotionEffect(e.getKey(), 220, e.getValue(), false, false, false));
            }
            // strip whatever we granted before that is no longer earned
            Set<PotionEffectType> before = applied.remove(p.getUniqueId());
            if (before != null) {
                for (PotionEffectType old : before) {
                    if (!desired.containsKey(old)) p.removePotionEffect(old);
                }
            }
            if (!desired.isEmpty()) applied.put(p.getUniqueId(), desired.keySet());

            // Phantom cloak: hide armor from everyone while the invisibility pulse is on
            boolean wantCloak = discs.contains(DiscType.PHANTOM) && invisPhaseOn();
            if (wantCloak) {
                cloaked.add(p.getUniqueId());
                sendArmor(p, true);
            } else if (cloaked.remove(p.getUniqueId())) {
                sendArmor(p, false);
            }
        }
        cloaked.removeIf(id -> Bukkit.getPlayer(id) == null);
        applied.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** 30 seconds on, 30 seconds off, forever, on a shared clock. */
    private boolean invisPhaseOn() {
        return (System.currentTimeMillis() / 1000 / INVIS_CYCLE_SECONDS) % 2 == 0;
    }

    /** Shows every other player either air (cloaked) or the real armor. */
    private void sendArmor(Player target, boolean hidden) {
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        ItemStack[] armor = {target.getInventory().getHelmet(), target.getInventory().getChestplate(),
                target.getInventory().getLeggings(), target.getInventory().getBoots()};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            ItemStack real = armor[i] == null ? new ItemStack(Material.AIR) : armor[i];
            equipment.put(ARMOR_SLOTS[i], hidden ? new ItemStack(Material.AIR) : real);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            viewer.sendEquipmentChange(target, equipment);
        }
    }

    private Set<DiscType> discsInInventory(Player p) {
        Set<DiscType> found = EnumSet.noneOf(DiscType.class);
        for (ItemStack item : p.getInventory().getContents()) {
            DiscType t = DiscItems.getDiscType(item);
            if (t != null) found.add(t);
        }
        return found;
    }

    private void applyPainAura(Player holder) {
        for (Entity e : holder.getNearbyEntities(PAIN_AURA_RADIUS, PAIN_AURA_RADIUS, PAIN_AURA_RADIUS)) {
            if (!(e instanceof LivingEntity living)) continue;
            for (PotionEffect effect : DiscType.PAIN_AURA) {
                living.addPotionEffect(effect);
            }
        }
    }

    private void addGambleEffect(Player p, Map<PotionEffectType, Integer> desired) {
        if (rolling.contains(p.getUniqueId())) return;
        UUID owner = data.getGambleOwner();
        if (owner == null || !owner.equals(p.getUniqueId())) {
            // The disc changed hands without a pickup event (e.g. inventory swap) - reroll.
            onDiscAcquired(p, DiscType.GAMBLING);
            return;
        }
        PotionEffectType type = resolveEffect(data.getGambleEffect());
        if (type != null) desired.merge(type, 4, Math::max);
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
