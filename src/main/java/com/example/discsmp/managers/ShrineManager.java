package com.example.discsmp.managers;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shrines: one themed altar per disc, hidden inside its vanilla structure.
 * Approaching an unclaimed shrine triggers a dramatic omen; right-clicking the
 * altar opens an offering GUI that shows the recipe and forges the disc from
 * the player's own inventory (nothing is ever deposited, so nothing can be
 * stolen). Altar blocks are indestructible.
 */
public class ShrineManager {

    private static final int OMEN_RADIUS = 24;

    private final DiscSMPPlugin plugin;
    private final DataStore data;
    private final Random random = new Random();

    public ShrineManager(DiscSMPPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    // ---- Altar design ----

    /** Blocks each altar is built from, matched to the disc's theme. */
    private record AltarStyle(Material base, Material corner, Material core, Material light) {}

    private static AltarStyle style(DiscType type) {
        return switch (type) {
            case BERSERKER -> new AltarStyle(Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.POLISHED_BLACKSTONE, Material.GILDED_BLACKSTONE, Material.SHROOMLIGHT);
            case ACROBAT -> new AltarStyle(Material.MOSSY_STONE_BRICKS,
                    Material.MOSS_BLOCK, Material.EMERALD_BLOCK, Material.VERDANT_FROGLIGHT);
            case VAMPIRE -> new AltarStyle(Material.DARK_OAK_PLANKS,
                    Material.DARK_OAK_LOG, Material.REDSTONE_BLOCK, Material.PEARLESCENT_FROGLIGHT);
            case JUGGERNAUT -> new AltarStyle(Material.SMOOTH_SANDSTONE,
                    Material.CUT_SANDSTONE, Material.GOLD_BLOCK, Material.OCHRE_FROGLIGHT);
            case PHOENIX -> new AltarStyle(Material.NETHER_BRICKS,
                    Material.CHISELED_NETHER_BRICKS, Material.MAGMA_BLOCK, Material.GLOWSTONE);
            case PAIN -> new AltarStyle(Material.MUD_BRICKS,
                    Material.BONE_BLOCK, Material.CRYING_OBSIDIAN, Material.OCHRE_FROGLIGHT);
            case POSEIDON -> new AltarStyle(Material.PRISMARINE_BRICKS,
                    Material.DARK_PRISMARINE, Material.SEA_LANTERN, Material.SEA_LANTERN);
            case MINE_AND_CRAFT -> new AltarStyle(Material.STONE_BRICKS,
                    Material.IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.GLOWSTONE);
            case PHANTOM -> new AltarStyle(Material.DEEPSLATE_TILES,
                    Material.REINFORCED_DEEPSLATE, Material.SCULK_CATALYST, Material.AMETHYST_BLOCK);
            case GAMBLING -> new AltarStyle(Material.TUFF_BRICKS,
                    Material.CHISELED_COPPER, Material.AMETHYST_BLOCK, Material.GLOWSTONE);
        };
    }

    /** Registers a shrine and builds its themed altar around the core block. */
    public void setShrine(DiscType type, Location loc) {
        Location core = loc.getBlock().getLocation();
        removeDisplay(type);
        data.setShrine(type, core);
        buildAltar(type, core);
    }

    /**
     * Altar layout (core = the stored shrine location, the block you click):
     * a 3x3 themed floor one block down, the glowing core at the center,
     * corner pillars with light blocks on top, and a floating label.
     */
    private void buildAltar(DiscType type, Location core) {
        World world = core.getWorld();
        AltarStyle style = style(type);
        int x = core.getBlockX(), y = core.getBlockY(), z = core.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean cornerCol = dx != 0 && dz != 0;
                world.getBlockAt(x + dx, y - 1, z + dz)
                        .setType(cornerCol ? style.corner() : style.base());
                // clear headroom, then place the altar pieces
                world.getBlockAt(x + dx, y, z + dz).setType(Material.AIR);
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR);
                if (cornerCol) {
                    world.getBlockAt(x + dx, y, z + dz).setType(style.corner());
                    world.getBlockAt(x + dx, y + 1, z + dz).setType(style.light());
                }
            }
        }
        world.getBlockAt(x, y, z).setType(style.core());

        TextDisplay label = world.spawn(core.clone().add(0.5, 1.7, 0.5), TextDisplay.class, d -> {
            d.setText(type.getColor() + "" + ChatColor.BOLD + "✦ Altar of the " + type.getTheme() + " ✦"
                    + ChatColor.RESET + "\n" + ChatColor.GRAY + "Right-click to make your offering");
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(true);
        });
        data.setShrineDisplay(type, label.getUniqueId());
        updateDisplay(type);
    }

    private void removeDisplay(DiscType type) {
        java.util.UUID id = data.getShrineDisplay(type);
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
        data.setShrineDisplay(type, null);
    }

    /** Keeps the floating label in sync with the disc's claimed state. */
    public void updateDisplay(DiscType type) {
        java.util.UUID id = data.getShrineDisplay(type);
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (!(e instanceof TextDisplay label)) return;
        if (data.isClaimed(type)) {
            label.setText(type.getColor() + "" + ChatColor.BOLD + "✦ Altar of the " + type.getTheme() + " ✦"
                    + ChatColor.RESET + "\n" + ChatColor.DARK_GRAY + "The disc walks the world with "
                    + ChatColor.GRAY + data.getOwnerName(type));
        } else {
            label.setText(type.getColor() + "" + ChatColor.BOLD + "✦ Altar of the " + type.getTheme() + " ✦"
                    + ChatColor.RESET + "\n" + ChatColor.GRAY + "Right-click to make your offering");
        }
    }

    /** The disc whose altar (any block of it) is at this location, or null. */
    public DiscType altarAt(Location loc) {
        for (DiscType type : DiscType.values()) {
            Location s = data.getShrine(type);
            if (s == null || !s.getWorld().equals(loc.getWorld())) continue;
            if (Math.abs(loc.getBlockX() - s.getBlockX()) <= 1
                    && Math.abs(loc.getBlockZ() - s.getBlockZ()) <= 1
                    && loc.getBlockY() >= s.getBlockY() - 1
                    && loc.getBlockY() <= s.getBlockY() + 1) {
                return type;
            }
        }
        return null;
    }

    // ---- Locating shrines inside structures ----

    /**
     * Picks a random spot in the world (away from spawn), finds the nearest
     * matching structure, and plants the altar in an open space inside the
     * structure's generated bounding box - deep in the mineshaft, not on the
     * surface above it.
     */
    public Location locateAndSetShrine(DiscType type) {
        World world = pickWorld(type);
        if (world == null) return null;
        Structure structure = Registry.STRUCTURE.get(NamespacedKey.minecraft(type.getStructureKey()));
        if (structure == null) return null;

        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        double borderRadius = world.getWorldBorder().getSize() / 2 - 128;
        double minSpawnDist = nether ? 250 : 1500;
        Location spawn = world.getSpawnLocation();

        Location found = null;
        for (int attempt = 0; attempt < 8 && found == null; attempt++) {
            double ox = (random.nextDouble() * 2 - 1) * borderRadius;
            double oz = (random.nextDouble() * 2 - 1) * borderRadius;
            StructureSearchResult result = world.locateNearestStructure(
                    new Location(world, ox, 64, oz), structure, 250, false);
            if (result == null) continue;
            Location loc = result.getLocation();
            if (Math.abs(loc.getX()) > borderRadius || Math.abs(loc.getZ()) > borderRadius) continue;
            double dx = loc.getX() - spawn.getX(), dz = loc.getZ() - spawn.getZ();
            if (dx * dx + dz * dz < minSpawnDist * minSpawnDist) continue;
            found = loc;
        }
        if (found == null) {
            // last resort: search from spawn, accept whatever exists
            StructureSearchResult result = world.locateNearestStructure(spawn, structure, 500, false);
            if (result == null) return null;
            found = result.getLocation();
        }

        Location core = findSpotInside(world, structure, found);
        if (core == null) core = surfaceFallback(world, found);
        setShrine(type, core);
        return core;
    }

    /**
     * Uses the generated structure's bounding box to find an open floor spot
     * actually inside it: sweeps columns outward from the box center looking
     * for solid ground with two blocks of air above, between the box's own
     * floor and ceiling.
     */
    private Location findSpotInside(World world, Structure structure, Location hint) {
        Chunk chunk = hint.getChunk(); // forces generation so the structure data exists
        GeneratedStructure generated = null;
        for (GeneratedStructure gs : chunk.getStructures(structure)) {
            generated = gs;
        }
        if (generated == null) return null;
        BoundingBox box = generated.getBoundingBox();

        int cx = (int) Math.floor(box.getCenterX());
        int cz = (int) Math.floor(box.getCenterZ());
        int top = (int) box.getMaxY() - 1;
        int bottom = (int) Math.max(box.getMinY(), world.getMinHeight() + 1);

        for (int r = 0; r <= 16; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // ring only
                    int x = cx + dx, z = cz + dz;
                    if (!box.contains(x + 0.5, box.getCenterY(), z + 0.5)) continue;
                    for (int y = top; y > bottom; y--) {
                        Block floor = world.getBlockAt(x, y, z);
                        if (floor.getType().isSolid()
                                && world.getBlockAt(x, y + 1, z).isPassable()
                                && !world.getBlockAt(x, y + 1, z).isLiquid()
                                && world.getBlockAt(x, y + 2, z).isPassable()
                                && !world.getBlockAt(x, y + 2, z).isLiquid()) {
                            return new Location(world, x, y + 1, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    private Location surfaceFallback(World world, Location found) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            int x = found.getBlockX(), z = found.getBlockZ();
            for (int y = 32; y < 110; y++) {
                if (world.getBlockAt(x, y, z).getType().isSolid()
                        && world.getBlockAt(x, y + 1, z).isPassable()
                        && world.getBlockAt(x, y + 2, z).isPassable()) {
                    return new Location(world, x, y + 1, z);
                }
            }
            return new Location(world, x, 64, z);
        }
        int y = world.getHighestBlockYAt(found.getBlockX(), found.getBlockZ());
        return new Location(world, found.getBlockX(), y + 1, found.getBlockZ());
    }

    private World pickWorld(DiscType type) {
        boolean nether = type == DiscType.BERSERKER || type == DiscType.PHOENIX;
        for (World w : Bukkit.getWorlds()) {
            if (nether && w.getEnvironment() == World.Environment.NETHER) return w;
            if (!nether && w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return null;
    }

    // ---- Omens ----

    /** Called on a timer: shows the omen to players approaching an unclaimed shrine. */
    public void tickOmens() {
        for (DiscType type : DiscType.values()) {
            Location shrine = data.getShrine(type);
            if (shrine == null || data.isClaimed(type)) continue;
            Location center = shrine.clone().add(0.5, 1.2, 0.5);
            for (Player p : shrine.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(shrine) > OMEN_RADIUS * OMEN_RADIUS) continue;
                if (data.hasSeenOmen(p.getUniqueId(), type)) {
                    shrine.getWorld().spawnParticle(Particle.END_ROD, center, 4, 0.3, 0.5, 0.3, 0.01);
                } else {
                    data.markOmenSeen(p.getUniqueId(), type);
                    playOmen(p, type, shrine);
                }
            }
        }
    }

    /** The dramatic reveal: darkness falls, a voice names the disc, the altar is shown. */
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
        p.sendMessage(ChatColor.GRAY + "  Right-click the altar with these offerings in your inventory:");
        for (Map.Entry<Material, Integer> e : type.getRecipe().entrySet()) {
            p.sendMessage(ChatColor.DARK_GRAY + "   ▪ " + ChatColor.WHITE + e.getValue() + "x "
                    + ChatColor.GRAY + prettify(e.getKey()));
        }
        p.sendMessage("");
    }

    // ---- The offering GUI ----

    /** Marks an altar inventory; purely informational, items never leave the player. */
    public static class AltarHolder implements InventoryHolder {
        public final DiscType type;
        public AltarHolder(DiscType type) { this.type = type; }
        @Override
        public Inventory getInventory() { return null; }
    }

    public static final int FORGE_SLOT = 22;
    private static final int[] INGREDIENT_SLOTS = {10, 12, 14, 16};

    public void openAltarGui(Player p, DiscType type) {
        Inventory inv = Bukkit.createInventory(new AltarHolder(type), 27,
                type.getColor() + "" + ChatColor.BOLD + "Altar of the " + type.getTheme());

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        boolean claimed = data.isClaimed(type);
        boolean ready = !claimed;
        int slot = 0;
        for (Map.Entry<Material, Integer> e : type.getRecipe().entrySet()) {
            int have = countItems(p, e.getKey());
            boolean enough = have >= e.getValue();
            if (!enough) ready = false;

            ItemStack ing = new ItemStack(e.getKey(), Math.min(e.getValue(), e.getKey().getMaxStackSize()));
            ItemMeta meta = ing.getItemMeta();
            meta.setDisplayName((enough ? ChatColor.GREEN : ChatColor.RED) + "" + e.getValue()
                    + "x " + prettify(e.getKey()));
            meta.setLore(List.of((enough ? ChatColor.GREEN + "✔ You carry " : ChatColor.RED + "✘ You carry ")
                    + have + "/" + e.getValue()));
            ing.setItemMeta(meta);
            inv.setItem(INGREDIENT_SLOTS[slot % INGREDIENT_SLOTS.length], ing);

            inv.setItem(INGREDIENT_SLOTS[slot % INGREDIENT_SLOTS.length] - 9, pane(
                    enough ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    enough ? ChatColor.GREEN + "Offering ready" : ChatColor.RED + "Offering missing", null));
            slot++;
        }

        if (claimed) {
            inv.setItem(FORGE_SLOT, pane(Material.BARRIER,
                    ChatColor.RED + "The disc already walks the world",
                    List.of(ChatColor.GRAY + "Held by " + data.getOwnerName(type))));
        } else if (ready) {
            ItemStack forge = new ItemStack(type.getMaterial());
            ItemMeta meta = forge.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "⚒ FORGE THE "
                    + type.getTheme().toUpperCase());
            meta.setLore(List.of(ChatColor.GRAY + "Consumes the offerings from your inventory.",
                    ChatColor.GRAY + "" + ChatColor.ITALIC + type.getFlavor()));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            forge.setItemMeta(meta);
            inv.setItem(FORGE_SLOT, forge);
        } else {
            inv.setItem(FORGE_SLOT, pane(Material.ORANGE_STAINED_GLASS_PANE,
                    ChatColor.GOLD + "Gather all the offerings to forge",
                    List.of(ChatColor.GRAY + "The altar takes them straight from",
                            ChatColor.GRAY + "your inventory - nothing is stored here.")));
        }

        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 0.7f);
        p.openInventory(inv);
    }

    private ItemStack pane(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int countItems(Player p, Material mat) {
        int total = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == mat && DiscItems.getDiscType(item) == null) {
                total += item.getAmount();
            }
        }
        return total;
    }

    // ---- Forging ----

    /** Clicked the forge button: consume the offerings and hand over the disc immediately. */
    public void tryForge(Player p, DiscType type) {
        if (data.isClaimed(type)) {
            String owner = data.getOwnerName(type);
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
            p.sendMessage(ChatColor.DARK_GRAY + "The altar is silent. " + type.getDisplayName()
                    + ChatColor.DARK_GRAY + " already walks the world"
                    + (owner != null ? " with " + ChatColor.GRAY + owner : "") + ChatColor.DARK_GRAY + ".");
            return;
        }
        if (!hasMaterials(p, type)) {
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.BLOCK_SCULK_CATALYST_BREAK, 0.8f, 0.5f);
            p.sendMessage(ChatColor.RED + "The altar rejects you. Your offerings are incomplete.");
            return;
        }

        consumeMaterials(p, type);
        p.closeInventory();
        data.setClaimed(type, p.getUniqueId(), p.getName());

        // The disc goes straight to the player - the ceremony afterwards is pure theater.
        ItemStack disc = DiscItems.create(type);
        p.getInventory().addItem(disc).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));

        Location shrine = data.getShrine(type);
        Location center = (shrine != null && shrine.getWorld().equals(p.getWorld())
                ? shrine.clone() : p.getLocation()).add(0.5, 1.0, 0.5);

        announceForge(p, type);
        grantAdvancementToast(p, type);
        plugin.getAbilityManager().onDiscAcquired(p, type);
        updateDisplay(type);
        playForgeCeremony(p, type, center);
    }

    private void announceForge(Player p, DiscType type) {
        p.sendTitle(type.getDisplayName(), ChatColor.WHITE + type.getAbilityText(), 10, 80, 30);
        p.sendMessage("");
        p.sendMessage(type.getColor() + "" + ChatColor.BOLD + "  ♫ You have forged the " + type.getTheme() + "!");
        p.sendMessage(ChatColor.GRAY + "  " + ChatColor.ITALIC + type.getFlavor());
        p.sendMessage(ChatColor.WHITE + "  Power: " + type.getAbilityText());
        p.sendMessage("");

        Bukkit.broadcastMessage(type.getColor() + "♫ " + ChatColor.BOLD + p.getName()
                + ChatColor.RESET + type.getColor() + " has forged " + type.getDisplayName()
                + type.getColor() + " in " + type.getStructureName() + "!");

        // the disc's song rings out for the whole server, like the dragon's death
        for (Player everyone : Bukkit.getOnlinePlayers()) {
            everyone.playSound(everyone.getLocation(), type.getSong(), 0.7f, 1.0f);
        }
    }

    private void playForgeCeremony(Player p, DiscType type, Location center) {
        World world = center.getWorld();
        world.strikeLightningEffect(center);
        p.setPlayerTime(18000, false);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 80, 0.6, 1.2, 0.6, 0.2);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 60, 0.4, 1.0, 0.4, 0.05);
        world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                step++;
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, step * 0.3, 0),
                        12, 0.3, 0.1, 0.3, 0.02);
                if (step >= 6) {
                    cancel();
                    flash(world, center, 2);
                    if (p.isOnline()) p.resetPlayerTime();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
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
            if (countItems(p, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    private void consumeMaterials(Player p, DiscType type) {
        PlayerInventory inv = p.getInventory();
        for (Map.Entry<Material, Integer> e : type.getRecipe().entrySet()) {
            inv.removeItem(new ItemStack(e.getKey(), e.getValue()));
        }
    }

    /** FLASH requires a Color on newer Paper builds; spawn it with whichever data it wants. */
    public static void flash(World world, Location loc, int count) {
        if (Particle.FLASH.getDataType() == org.bukkit.Color.class) {
            world.spawnParticle(Particle.FLASH, loc, count, 0, 0, 0, 0, org.bukkit.Color.WHITE);
        } else {
            world.spawnParticle(Particle.FLASH, loc, count);
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
