package com.example.discsmp.managers;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import com.example.discsmp.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Ritual of the Ten Songs: all ten discs must play in ten jukeboxes AT THE
 * SAME TIME, close together. One disc alone does nothing. When the tenth song
 * starts, the discs rise from their jukeboxes, spiral into a single point of
 * light, and are destroyed - then a weapon of legend descends from the heavens.
 */
public class RitualManager implements Listener {

    public static NamespacedKey WEAPON_KEY;

    private static final int[] GUI_SLOTS = {10, 11, 12, 14, 15, 16};
    /** All ten jukeboxes must be within this many blocks of the last one. */
    private static final double MAX_SPREAD = 32;

    private final DiscSMPPlugin plugin;
    private final DataStore data;
    /** Where each disc was last heard playing. Verified against real block state. */
    private final Map<DiscType, Location> playing = new ConcurrentHashMap<>();
    /** Where the ritual completed, so the weapon can descend there. */
    private final Map<UUID, Location> ritualSites = new ConcurrentHashMap<>();
    /** Players who clicked a weapon and must now type its name in chat. */
    private final Map<UUID, WeaponType> pendingName = new ConcurrentHashMap<>();
    private boolean ritualRunning = false;

    public RitualManager(DiscSMPPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
        WEAPON_KEY = new NamespacedKey(plugin, "ritual_weapon");
    }

    // ---- Tracking which discs are singing ----

    /** Called right after a player puts one of the discs into a jukebox. */
    public void onDiscInserted(Player p, DiscType type, Block jukeboxBlock) {
        // one tick later the record is actually inside the jukebox
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (jukeboxBlock.getType() != Material.JUKEBOX) return;
            Jukebox state = (Jukebox) jukeboxBlock.getState();
            if (DiscItems.getDiscType(state.getRecord()) != type) return;
            playing.put(type, jukeboxBlock.getLocation());

            Map<DiscType, Location> chorus = validPlaying(jukeboxBlock.getLocation());
            int count = chorus.size();
            if (count > 1) {
                p.sendActionBar(type.getColor() + "♫ " + ChatColor.GRAY + count
                        + "/10 songs sing together");
            }
            if (count == DiscType.values().length && !ritualRunning) {
                startRitual(p, chorus);
            } else if (count < playingCount() && playingCount() == DiscType.values().length) {
                p.sendMessage(ChatColor.GRAY
                        + "All ten songs play, but they are too far apart to weave together.");
            }
        });
    }

    /** A jukebox with a record was clicked (eject) or broken - forget its song. */
    public void onJukeboxDisturbed(Location loc) {
        playing.values().removeIf(l -> l.getWorld().equals(loc.getWorld())
                && l.getBlockX() == loc.getBlockX()
                && l.getBlockY() == loc.getBlockY()
                && l.getBlockZ() == loc.getBlockZ());
    }

    private int playingCount() {
        return validPlaying(null).size();
    }

    /**
     * Re-checks every tracked jukebox against the real world: still a jukebox,
     * still holds its disc, still audibly playing. If {@code near} is given,
     * only jukeboxes within MAX_SPREAD of it count toward the chorus.
     */
    private Map<DiscType, Location> validPlaying(Location near) {
        Map<DiscType, Location> valid = new EnumMap<>(DiscType.class);
        for (Map.Entry<DiscType, Location> e : new EnumMap<>(playing).entrySet()) {
            Location loc = e.getValue();
            Block b = loc.getBlock();
            if (b.getType() != Material.JUKEBOX) {
                playing.remove(e.getKey());
                continue;
            }
            Jukebox state = (Jukebox) b.getState();
            if (DiscItems.getDiscType(state.getRecord()) != e.getKey() || !state.isPlaying()) {
                playing.remove(e.getKey());
                continue;
            }
            if (near != null && (!loc.getWorld().equals(near.getWorld())
                    || loc.distanceSquared(near) > MAX_SPREAD * MAX_SPREAD)) {
                continue; // playing, but not part of this chorus
            }
            valid.put(e.getKey(), loc);
        }
        return valid;
    }

    /** Sneak-right-click on a jukebox: resume a pending choice, or explain the rite. */
    public void handleSneakClick(Player p) {
        if (data.mayChooseWeapon(p.getUniqueId())) {
            openWeaponGui(p);
            return;
        }
        p.sendMessage(ChatColor.DARK_PURPLE + "♫ " + ChatColor.GRAY
                + "The rite demands all ten songs at once: ten discs, ten jukeboxes, "
                + "side by side, all playing together.");
    }

    // ---- The ritual ----

    private void startRitual(Player p, Map<DiscType, Location> chorus) {
        ritualRunning = true;
        playing.clear();

        // centroid of the ten jukeboxes = the heart of the ritual
        World world = p.getWorld();
        double sx = 0, sy = 0, sz = 0;
        for (Location loc : chorus.values()) {
            sx += loc.getX();
            sy += loc.getY();
            sz += loc.getZ();
        }
        int n = chorus.size();
        Location heart = new Location(world, sx / n + 0.5, sy / n + 1.0, sz / n + 0.5);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                + "♫ Ten songs sing as one. " + p.getName()
                + " has begun the Ritual of the Ten Songs...");
        world.playSound(heart, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
        p.setPlayerTime(18000, false);

        // pull the discs out of their jukeboxes as floating, glowing items
        List<Item> risen = new ArrayList<>();
        for (Map.Entry<DiscType, Location> e : chorus.entrySet()) {
            Block b = e.getValue().getBlock();
            if (b.getType() == Material.JUKEBOX) {
                Jukebox state = (Jukebox) b.getState();
                state.setRecord(null);
                state.update();
            }
            Item item = world.dropItem(e.getValue().clone().add(0.5, 1.2, 0.5),
                    DiscItems.create(e.getKey()));
            item.setGravity(false);
            item.setVelocity(new Vector(0, 0, 0));
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setInvulnerable(true);
            item.setGlowing(true);
            risen.add(item);
        }

        List<Location> starts = risen.stream().map(Item::getLocation).toList();
        Location target = heart.clone().add(0, 4.0, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 2;
                double progress = Math.min(1.0, ticks / 140.0);
                for (int i = 0; i < risen.size(); i++) {
                    Item item = risen.get(i);
                    if (item.isDead()) continue;
                    Location s = starts.get(i);
                    double angle = progress * 4 * Math.PI + i;
                    double wobble = Math.sin(progress * Math.PI) * 1.2;
                    Location pos = new Location(world,
                            lerp(s.getX(), target.getX(), progress) + Math.cos(angle) * wobble * (1 - progress),
                            lerp(s.getY(), target.getY(), progress),
                            lerp(s.getZ(), target.getZ(), progress) + Math.sin(angle) * wobble * (1 - progress));
                    item.teleport(pos);
                    world.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
                }
                if (ticks % 20 == 0) {
                    world.playSound(heart, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.5f + (float) progress);
                }
                if (ticks >= 140) {
                    cancel();
                    risen.forEach(Item::remove);
                    finishRitual(p, heart);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finishRitual(Player p, Location heart) {
        World world = heart.getWorld();
        Location apex = heart.clone().add(0, 4.0, 0);

        ShrineManager.flash(world, apex, 3);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, apex, 150, 1.0, 1.0, 1.0, 0.5);
        world.strikeLightningEffect(heart);
        world.playSound(apex, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        world.playSound(apex, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1.4f);

        for (DiscType t : DiscType.values()) {
            data.setUnclaimed(t);
            plugin.getShrineManager().updateDisplay(t);
        }
        if (data.getGambleOwner() != null) data.setGamble(null, null);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "♫ The ten discs are no more. "
                + ChatColor.LIGHT_PURPLE + p.getName()
                + ChatColor.DARK_PURPLE + " may now claim a weapon of legend. "
                + ChatColor.GRAY + "The discs may be forged anew at their shrines.");

        data.setMayChooseWeapon(p.getUniqueId(), true);
        ritualSites.put(p.getUniqueId(), heart);
        ritualRunning = false;
        if (p.isOnline()) {
            p.resetPlayerTime();
            openWeaponGui(p);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ---- Weapon choice GUI ----

    public void openWeaponGui(Player p) {
        Inventory inv = Bukkit.createInventory(new RitualHolder(), 27,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Choose Your Weapon");
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        WeaponType[] types = WeaponType.values();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(GUI_SLOTS[i], guiItem(types[i]));
        }
        p.openInventory(inv);
    }

    private ItemStack guiItem(WeaponType type) {
        boolean taken = data.isWeaponTaken(type.name());
        ItemStack item = new ItemStack(taken ? Material.BARRIER : type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (taken) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + type.getDisplayName());
            lore.add(ChatColor.RED + "Already claimed by " + data.getWeaponOwner(type.name()) + ".");
            lore.add(ChatColor.DARK_GRAY + "Once taken, a weapon is off the table forever.");
        } else {
            meta.setDisplayName(type.getColor() + "" + ChatColor.BOLD + "The " + type.getDisplayName());
            type.getEnchants().forEach((ench, lvl) -> lore.add(ChatColor.GRAY + "▪ "
                    + prettyEnchant(ench.getKey().getKey()) + " " + roman(lvl)));
            lore.add("");
            lore.add(ChatColor.GOLD + "Unbreakable. Cannot be destroyed.");
            lore.add(ChatColor.LIGHT_PURPLE + "Click to claim and name it.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RitualHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!data.mayChooseWeapon(p.getUniqueId())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE
                || clicked.getType() == Material.BARRIER) return;

        WeaponType chosen = null;
        WeaponType[] types = WeaponType.values();
        for (int i = 0; i < types.length; i++) {
            if (event.getSlot() == GUI_SLOTS[i]) chosen = types[i];
        }
        if (chosen == null || data.isWeaponTaken(chosen.name())) return;

        pendingName.put(p.getUniqueId(), chosen);
        p.closeInventory();
        p.sendMessage(chosen.getColor() + "" + ChatColor.BOLD + "You have chosen the "
                + chosen.getDisplayName() + ". " + ChatColor.RESET + ChatColor.GRAY
                + "Type its name in chat. It will bear that name forever.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        WeaponType type = pendingName.remove(event.getPlayer().getUniqueId());
        if (type == null) return;
        event.setCancelled(true);
        String name = ChatColor.stripColor(event.getMessage()).trim();
        final String finalName = name.isEmpty() || name.length() > 40
                ? "The " + type.getDisplayName() : name;
        Bukkit.getScheduler().runTask(plugin, () -> giveWeapon(event.getPlayer(), type, finalName));
    }

    private void giveWeapon(Player p, WeaponType type, String name) {
        if (data.isWeaponTaken(type.name())) {
            p.sendMessage(ChatColor.RED + "Someone claimed the " + type.getDisplayName()
                    + " before you. Choose again.");
            openWeaponGui(p);
            return;
        }
        data.setMayChooseWeapon(p.getUniqueId(), false);
        data.setWeaponTaken(type.name(), p.getName(), name);

        Bukkit.broadcastMessage(type.getColor() + "⚔ " + ChatColor.BOLD + p.getName()
                + ChatColor.RESET + type.getColor() + " has claimed " + ChatColor.BOLD + name
                + ChatColor.RESET + type.getColor() + ", the " + type.getDisplayName()
                + " of legend. It is off the table forever.");

        ItemStack weapon = createWeapon(type, name, p.getName());
        descendFromHeavens(p, type, name, weapon);
    }

    /** The claimed weapon descends slowly from the sky at the heart of the ritual. */
    private void descendFromHeavens(Player p, WeaponType type, String name, ItemStack weapon) {
        Location site = ritualSites.remove(p.getUniqueId());
        if (site == null || !site.getWorld().equals(p.getWorld())) site = p.getLocation();
        final Location ground = site.clone();
        World world = ground.getWorld();
        Location start = ground.clone().add(0, 25, 0);

        Item item = world.dropItem(start, weapon);
        item.setGravity(false);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setInvulnerable(true);
        item.setPersistent(true);
        item.setGlowing(true);
        item.setCustomName(type.getColor() + "" + ChatColor.BOLD + name);
        item.setCustomNameVisible(true);

        world.playSound(ground, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        p.sendTitle(type.getColor() + "" + ChatColor.BOLD + name,
                ChatColor.GRAY + "descends from the heavens...", 10, 80, 30);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 2;
                if (item.isDead()) {
                    cancel();
                    return;
                }
                Location pos = item.getLocation();
                // halo spiraling around the descending weapon
                double angle = ticks * 0.35;
                world.spawnParticle(Particle.END_ROD,
                        pos.clone().add(Math.cos(angle) * 0.8, 0.2, Math.sin(angle) * 0.8),
                        2, 0, 0, 0, 0);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, pos, 2, 0.1, 0.1, 0.1, 0.02);
                if (pos.getY() > ground.getY() + 1.2) {
                    item.teleport(pos.subtract(0, 0.35, 0));
                } else {
                    cancel();
                    item.setGravity(true);
                    item.setPickupDelay(0);
                    ShrineManager.flash(world, pos, 2);
                    world.strikeLightningEffect(ground);
                    world.playSound(pos, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    public ItemStack createWeapon(WeaponType type, String name, String ownerName) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getColor() + "" + ChatColor.BOLD + name);
        List<String> lore = new ArrayList<>(List.of(
                ChatColor.GRAY + "" + ChatColor.ITALIC + "Forged from the ten songs.",
                ChatColor.DARK_GRAY + "Claimed by " + ownerName,
                "",
                ChatColor.GOLD + "Unbreakable. Indestructible."));
        meta.setLore(lore);
        meta.setUnbreakable(true);
        try {
            meta.setFireResistant(true);
        } catch (Throwable ignored) {
            // older API without fire resistance component
        }
        meta.getPersistentDataContainer().set(WEAPON_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        type.getEnchants().forEach((ench, lvl) -> item.addUnsafeEnchantment(ench, lvl));
        return item;
    }

    public static boolean isRitualWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(WEAPON_KEY, PersistentDataType.STRING);
    }

    private String prettyEnchant(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private String roman(int n) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? numerals[n - 1] : String.valueOf(n);
    }

    /** Marks our weapon-choice inventory so clicks can be recognized safely. */
    public static class RitualHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
