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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ritual of the ten songs: attune every disc by playing it in a jukebox,
 * then bring all ten to a jukebox and sneak-right-click to begin. The discs
 * rise, combine, and are destroyed - and one weapon of legend may be claimed.
 */
public class RitualManager implements Listener {

    public static NamespacedKey WEAPON_KEY;

    private static final int[] GUI_SLOTS = {10, 11, 12, 14, 15, 16};

    private final DiscSMPPlugin plugin;
    private final DataStore data;
    /** Players who clicked a weapon and must now type its name in chat. */
    private final Map<UUID, WeaponType> pendingName = new ConcurrentHashMap<>();

    public RitualManager(DiscSMPPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
        WEAPON_KEY = new NamespacedKey(plugin, "ritual_weapon");
    }

    // ---- Attunement ----

    /** Called when a player puts one of the discs into a jukebox. */
    public void onDiscPlayed(Player p, DiscType type) {
        if (data.addAttuned(p.getUniqueId(), type)) {
            int count = data.getAttuned(p.getUniqueId()).size();
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
            p.sendMessage(type.getColor() + "♫ " + ChatColor.GRAY + "The jukebox drinks in the song of the "
                    + type.getColor() + type.getTheme() + ChatColor.GRAY + ". "
                    + ChatColor.WHITE + count + ChatColor.GRAY + "/10 songs attuned.");
            if (count == 10) {
                p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                        + "All ten songs echo within you. "
                        + ChatColor.RESET + ChatColor.GRAY
                        + "Bring the ten discs to a jukebox and sneak-right-click it to begin the ritual.");
            }
        }
    }

    // ---- The ritual ----

    /** Sneak-right-click on a jukebox. Returns true if the interaction was consumed. */
    public boolean tryStartRitual(Player p, Location jukebox) {
        if (data.mayChooseWeapon(p.getUniqueId())) {
            // completed the ritual earlier (e.g. before a restart) - resume the choice
            openWeaponGui(p);
            return true;
        }
        Set<DiscType> attuned = data.getAttuned(p.getUniqueId());
        if (attuned.size() < DiscType.values().length) {
            p.sendMessage(ChatColor.GRAY + "The jukebox hums faintly. " + ChatColor.WHITE
                    + attuned.size() + ChatColor.GRAY
                    + "/10 songs attuned - every disc must be played in a jukebox first.");
            return true;
        }

        // All ten discs must physically be in the player's inventory.
        Map<DiscType, ItemStack> found = new EnumMap<>(DiscType.class);
        for (ItemStack item : p.getInventory().getContents()) {
            DiscType t = DiscItems.getDiscType(item);
            if (t != null) found.putIfAbsent(t, item);
        }
        if (found.size() < DiscType.values().length) {
            p.sendMessage(ChatColor.GRAY + "You carry " + ChatColor.WHITE + found.size()
                    + ChatColor.GRAY + "/10 discs. The ritual demands all ten in your possession.");
            return true;
        }

        // Consume the discs; they return to their shrines, craftable again.
        for (ItemStack item : found.values()) {
            item.setAmount(0);
        }
        for (DiscType t : DiscType.values()) {
            data.setUnclaimed(t);
            plugin.getShrineManager().updateDisplay(t);
        }
        if (data.getGambleOwner() != null) data.setGamble(null, null);
        data.clearAttuned(p.getUniqueId());

        playRitualAnimation(p, jukebox);
        return true;
    }

    private void playRitualAnimation(Player p, Location jukebox) {
        World world = jukebox.getWorld();
        Location center = jukebox.clone().add(0.5, 1.2, 0.5);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "♫ " + p.getName()
                + " has begun the Ritual of the Ten Songs...");
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
        p.setPlayerTime(18000, false);

        // The ten discs rise in a circle and spiral into a single point of light.
        List<Item> risen = new ArrayList<>();
        DiscType[] types = DiscType.values();
        for (int i = 0; i < types.length; i++) {
            double angle = 2 * Math.PI * i / types.length;
            Location spawn = center.clone().add(Math.cos(angle) * 1.6, 0.2, Math.sin(angle) * 1.6);
            Item item = world.dropItem(spawn, DiscItems.create(types[i]));
            item.setGravity(false);
            item.setVelocity(new Vector(0, 0, 0));
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setInvulnerable(true);
            item.setGlowing(true);
            risen.add(item);
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 2;
                double progress = ticks / 100.0;
                Location target = center.clone().add(0, 3.0, 0);
                for (int i = 0; i < risen.size(); i++) {
                    Item item = risen.get(i);
                    if (item.isDead()) continue;
                    double angle = 2 * Math.PI * i / risen.size() + progress * 4 * Math.PI;
                    double radius = 1.6 * (1.0 - progress);
                    Location pos = center.clone().add(
                            Math.cos(angle) * radius,
                            0.2 + progress * 2.8,
                            Math.sin(angle) * radius);
                    item.teleport(pos);
                    world.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
                }
                if (ticks % 20 == 0) {
                    world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.5f + (float) progress);
                }
                if (ticks >= 100) {
                    cancel();
                    risen.forEach(Item::remove);
                    world.spawnParticle(Particle.FLASH, target, 3);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, target, 120, 0.8, 0.8, 0.8, 0.4);
                    world.strikeLightningEffect(jukebox);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    world.playSound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1.4f);

                    Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "♫ The ten discs are no more. "
                            + ChatColor.LIGHT_PURPLE + p.getName()
                            + ChatColor.DARK_PURPLE + " may now claim a weapon of legend. "
                            + ChatColor.GRAY + "The discs may be forged anew at their shrines.");

                    data.setMayChooseWeapon(p.getUniqueId(), true);
                    if (p.isOnline()) {
                        p.resetPlayerTime();
                        openWeaponGui(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
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

        ItemStack weapon = createWeapon(type, name, p.getName());
        p.getInventory().addItem(weapon).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));

        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
        p.sendTitle(type.getColor() + "" + ChatColor.BOLD + name,
                ChatColor.GRAY + "The " + type.getDisplayName() + " of legend", 10, 80, 30);
        Bukkit.broadcastMessage(type.getColor() + "⚔ " + ChatColor.BOLD + p.getName()
                + ChatColor.RESET + type.getColor() + " has claimed " + ChatColor.BOLD + name
                + ChatColor.RESET + type.getColor() + ", the " + type.getDisplayName()
                + " of legend. It is off the table forever.");
    }

    public ItemStack createWeapon(WeaponType type, String name, String ownerName) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getColor() + "" + ChatColor.BOLD + name);
        List<String> lore = new ArrayList<>(Arrays.asList(
                ChatColor.GRAY + "" + ChatColor.ITALIC + "Forged from the ten songs.",
                ChatColor.DARK_GRAY + "Claimed by " + ownerName,
                ""));
        lore.add(ChatColor.GOLD + "Unbreakable. Indestructible.");
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
