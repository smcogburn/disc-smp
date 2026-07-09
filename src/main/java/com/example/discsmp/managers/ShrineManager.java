package com.example.discsmp.managers;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StructureSearchResult;

import java.util.Map;

/**
 * Shrines: one per disc, hidden inside a vanilla structure. Approaching an
 * unclaimed shrine triggers a dramatic omen that reveals the recipe; bringing
 * the materials and right-clicking the sculk catalyst altar forges the disc.
 */
public class ShrineManager {

    private static final int OMEN_RADIUS = 24;

    private final DiscSMPPlugin plugin;
    private final DataStore data;

    public ShrineManager(DiscSMPPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    /** Registers a shrine and places its sculk catalyst altar. */
    public void setShrine(DiscType type, Location loc) {
        Location block = loc.getBlock().getLocation();
        data.setShrine(type, block);
        block.getBlock().setType(Material.SCULK_CATALYST);
    }

    /**
     * Finds the nearest matching structure to the world's spawn and drops the
     * shrine at ground level there. Admins can fine-tune with /discsmp setshrine.
     */
    public Location locateAndSetShrine(DiscType type) {
        World world = pickWorld(type);
        if (world == null) return null;
        Structure structure = Registry.STRUCTURE.get(NamespacedKey.minecraft(type.getStructureKey()));
        if (structure == null) return null;
        StructureSearchResult result =
                world.locateNearestStructure(world.getSpawnLocation(), structure, 500, false);
        if (result == null) return null;
        Location found = result.getLocation();
        int y = world.getHighestBlockYAt(found.getBlockX(), found.getBlockZ());
        if (world.getEnvironment() == World.Environment.NETHER) y = 64;
        Location shrine = new Location(world, found.getBlockX(), y + 1, found.getBlockZ());
        setShrine(type, shrine);
        return shrine;
    }

    private World pickWorld(DiscType type) {
        boolean nether = type == DiscType.BERSERKER || type == DiscType.PHOENIX;
        for (World w : Bukkit.getWorlds()) {
            if (nether && w.getEnvironment() == World.Environment.NETHER) return w;
            if (!nether && w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return null;
    }

    /** Called on a timer: shows the omen to players approaching an unclaimed shrine. */
    public void tickOmens() {
        for (DiscType type : DiscType.values()) {
            Location shrine = data.getShrine(type);
            if (shrine == null || data.isClaimed(type)) continue;
            for (Player p : shrine.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(shrine) > OMEN_RADIUS * OMEN_RADIUS) continue;
                if (data.hasSeenOmen(p.getUniqueId(), type)) {
                    // subtle ambience so returning players can find the altar again
                    shrine.getWorld().spawnParticle(Particle.SCULK_SOUL,
                            shrine.clone().add(0.5, 1.2, 0.5), 3, 0.2, 0.4, 0.2, 0.01);
                } else {
                    data.markOmenSeen(p.getUniqueId(), type);
                    playOmen(p, type, shrine);
                }
            }
        }
    }

    /** The dramatic reveal: darkness falls, a voice names the disc, the recipe is shown. */
    private void playOmen(Player p, DiscType type, Location shrine) {
        p.setPlayerTime(18000, false); // the world darkens to midnight, for this player only
        p.playSound(shrine, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
        p.playSound(shrine, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.5f);
        p.sendTitle(ChatColor.DARK_GRAY + "❖ " + type.getColor() + ChatColor.BOLD + "A shrine stirs..." +
                ChatColor.DARK_GRAY + " ❖", ChatColor.GRAY + "Something ancient sleeps here.", 10, 50, 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                p.playSound(shrine, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.0f, 0.6f);
                p.sendTitle(type.getDisplayName(),
                        ChatColor.GRAY + "" + ChatColor.ITALIC + type.getFlavor(), 10, 70, 20);
                shrine.getWorld().spawnParticle(Particle.SCULK_SOUL,
                        shrine.clone().add(0.5, 1.2, 0.5), 40, 0.5, 1.0, 0.5, 0.05);
                sendRecipe(p, type);
            }
        }.runTaskLater(plugin, 60L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) p.resetPlayerTime(); // daylight returns
            }
        }.runTaskLater(plugin, 200L);
    }

    public void sendRecipe(Player p, DiscType type) {
        p.sendMessage("");
        p.sendMessage(type.getColor() + "" + ChatColor.BOLD + "  ♫ The shrine of the " + type.getTheme());
        p.sendMessage(ChatColor.GRAY + "  Bring these offerings and press them into the catalyst:");
        for (Map.Entry<Material, Integer> e : type.getRecipe().entrySet()) {
            p.sendMessage(ChatColor.DARK_GRAY + "   ▪ " + ChatColor.WHITE + e.getValue() + "x "
                    + ChatColor.GRAY + prettify(e.getKey()));
        }
        p.sendMessage("");
    }

    /** Right-clicking a shrine's sculk catalyst: forge the disc if the offerings are present. */
    public boolean handleAltarClick(Player p, Location clicked) {
        DiscType type = shrineAt(clicked);
        if (type == null) return false;

        if (data.isClaimed(type)) {
            String owner = data.getOwnerName(type);
            p.playSound(clicked, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
            p.sendMessage(ChatColor.DARK_GRAY + "The shrine is silent. " + type.getDisplayName()
                    + ChatColor.DARK_GRAY + " already walks the world"
                    + (owner != null ? " with " + ChatColor.GRAY + owner : "") + ChatColor.DARK_GRAY + ".");
            return true;
        }

        if (!hasMaterials(p, type)) {
            p.playSound(clicked, Sound.BLOCK_SCULK_CATALYST_BREAK, 0.8f, 0.5f);
            p.sendMessage(ChatColor.RED + "The catalyst rejects you. Your offerings are incomplete.");
            sendRecipe(p, type);
            return true;
        }

        consumeMaterials(p, type);
        data.setClaimed(type, p.getUniqueId(), p.getName());
        playForgeSequence(p, type, clicked);
        return true;
    }

    public DiscType shrineAt(Location loc) {
        for (DiscType type : DiscType.values()) {
            Location shrine = data.getShrine(type);
            if (shrine != null && shrine.getWorld().equals(loc.getWorld())
                    && shrine.getBlockX() == loc.getBlockX()
                    && shrine.getBlockY() == loc.getBlockY()
                    && shrine.getBlockZ() == loc.getBlockZ()) {
                return type;
            }
        }
        return null;
    }

    /** The forging ceremony: lightning, soulfire, the song itself, and the disc. */
    private void playForgeSequence(Player p, DiscType type, Location altar) {
        World world = altar.getWorld();
        Location center = altar.clone().add(0.5, 1.0, 0.5);

        world.strikeLightningEffect(altar);
        p.setPlayerTime(18000, false);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 80, 0.6, 1.2, 0.6, 0.2);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 60, 0.4, 1.0, 0.4, 0.05);
        world.playSound(center, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                step++;
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, step * 0.3, 0),
                        12, 0.3, 0.1, 0.3, 0.02);
                if (step == 3) {
                    world.playSound(center, type.getSong(), 1.0f, 1.0f);
                }
                if (step >= 6) {
                    cancel();
                    deliverDisc(p, type, center);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void deliverDisc(Player p, DiscType type, Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.FLASH, center, 2);
        world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        ItemStack disc = DiscItems.create(type);
        p.getInventory().addItem(disc).values()
                .forEach(left -> world.dropItemNaturally(p.getLocation(), left));

        p.sendTitle(type.getDisplayName(), ChatColor.WHITE + type.getAbilityText(), 10, 80, 30);
        p.sendMessage("");
        p.sendMessage(type.getColor() + "" + ChatColor.BOLD + "  ♫ You have forged the " + type.getTheme() + "!");
        p.sendMessage(ChatColor.GRAY + "  " + ChatColor.ITALIC + type.getFlavor());
        p.sendMessage(ChatColor.WHITE + "  Power: " + type.getAbilityText());
        p.sendMessage("");

        Bukkit.broadcastMessage(type.getColor() + "♫ " + ChatColor.BOLD + p.getName()
                + ChatColor.RESET + type.getColor() + " has forged " + type.getDisplayName()
                + type.getColor() + " in " + type.getStructureName() + "!");

        grantAdvancementToast(p, type);
        plugin.getAbilityManager().onDiscAcquired(p, type);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) p.resetPlayerTime();
            }
        }.runTaskLater(plugin, 100L);
    }

    /** Pops a real advancement toast via a runtime-loaded advancement; chat fallback is above. */
    private void grantAdvancementToast(Player p, DiscType type) {
        try {
            NamespacedKey key = new NamespacedKey(plugin, "disc_" + type.name().toLowerCase());
            Advancement adv = Bukkit.getAdvancement(key);
            if (adv == null) {
                String json = "{\"display\":{\"icon\":{\"id\":\"minecraft:"
                        + type.getMaterial().name().toLowerCase() + "\"},"
                        + "\"title\":\"" + type.getTheme() + " (" + type.getSongName() + ")\","
                        + "\"description\":\"" + type.getAbilityText() + "\","
                        + "\"frame\":\"challenge\",\"show_toast\":true,"
                        + "\"announce_to_chat\":false,\"hidden\":true},"
                        + "\"criteria\":{\"forged\":{\"trigger\":\"minecraft:impossible\"}}}";
                adv = Bukkit.getUnsafe().loadAdvancement(key, json);
            }
            if (adv != null) {
                AdvancementProgress progress = p.getAdvancementProgress(adv);
                for (String criterion : progress.getRemainingCriteria()) {
                    progress.awardCriteria(criterion);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not show advancement toast: " + t.getMessage());
        }
    }

    private boolean hasMaterials(Player p, DiscType type) {
        for (Map.Entry<Material, Integer> e : type.getRecipe().entrySet()) {
            if (!p.getInventory().containsAtLeast(new ItemStack(e.getKey()), e.getValue())) return false;
        }
        return true;
    }

    private void consumeMaterials(Player p, DiscType type) {
        PlayerInventory inv = p.getInventory();
        for (Map.Entry<Material, Integer> e : type.getRecipe().entrySet()) {
            inv.removeItem(new ItemStack(e.getKey(), e.getValue()));
        }
    }

    public static String prettify(Material mat) {
        String[] parts = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
