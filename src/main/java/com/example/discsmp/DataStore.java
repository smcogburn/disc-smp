package com.example.discsmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * All persistent state: shrine locations, disc ownership, ritual attunement,
 * gamble rolls, weapon claims, totem uses and the mace count.
 */
public class DataStore {

    private final DiscSMPPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public DataStore(DiscSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    // ---- Shrines ----

    public void setShrine(DiscType type, Location loc) {
        String p = "shrines." + type.name();
        yaml.set(p + ".world", loc.getWorld().getName());
        yaml.set(p + ".x", loc.getBlockX());
        yaml.set(p + ".y", loc.getBlockY());
        yaml.set(p + ".z", loc.getBlockZ());
        save();
    }

    public Location getShrine(DiscType type) {
        String p = "shrines." + type.name();
        String worldName = yaml.getString(p + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, yaml.getInt(p + ".x"), yaml.getInt(p + ".y"), yaml.getInt(p + ".z"));
    }

    /** UUID of the floating TextDisplay above a shrine's altar. */
    public UUID getShrineDisplay(DiscType type) {
        String s = yaml.getString("shrines." + type.name() + ".display");
        return s == null ? null : UUID.fromString(s);
    }

    public void setShrineDisplay(DiscType type, UUID id) {
        yaml.set("shrines." + type.name() + ".display", id == null ? null : id.toString());
        save();
    }

    // ---- Disc ownership / existence ----

    /** True if the disc currently exists in the world (has been crafted and not destroyed). */
    public boolean isClaimed(DiscType type) {
        return yaml.getBoolean("discs." + type.name() + ".claimed", false);
    }

    public void setClaimed(DiscType type, UUID owner, String ownerName) {
        String p = "discs." + type.name();
        yaml.set(p + ".claimed", true);
        yaml.set(p + ".owner", owner == null ? null : owner.toString());
        yaml.set(p + ".ownerName", ownerName);
        save();
    }

    /** Disc destroyed or consumed by the ritual: it may be crafted again. */
    public void setUnclaimed(DiscType type) {
        String p = "discs." + type.name();
        yaml.set(p + ".claimed", false);
        yaml.set(p + ".owner", null);
        yaml.set(p + ".ownerName", null);
        save();
    }

    public String getOwnerName(DiscType type) {
        return yaml.getString("discs." + type.name() + ".ownerName");
    }

    public UUID getOwner(DiscType type) {
        String s = yaml.getString("discs." + type.name() + ".owner");
        return s == null ? null : UUID.fromString(s);
    }

    public void setOwner(DiscType type, UUID owner, String ownerName) {
        String p = "discs." + type.name();
        yaml.set(p + ".owner", owner.toString());
        yaml.set(p + ".ownerName", ownerName);
        save();
    }

    // ---- Omen (shrine intro shown once per player per shrine) ----

    public boolean hasSeenOmen(UUID player, DiscType type) {
        return yaml.getStringList("omens." + player).contains(type.name());
    }

    public void markOmenSeen(UUID player, DiscType type) {
        List<String> list = yaml.getStringList("omens." + player);
        if (!list.contains(type.name())) {
            list.add(type.name());
            yaml.set("omens." + player, list);
            save();
        }
    }

    // ---- Ritual attunement (discs played in a jukebox, per player) ----

    public Set<DiscType> getAttuned(UUID player) {
        Set<DiscType> set = new HashSet<>();
        for (String s : yaml.getStringList("attuned." + player)) {
            DiscType t = DiscType.byId(s);
            if (t != null) set.add(t);
        }
        return set;
    }

    public boolean addAttuned(UUID player, DiscType type) {
        List<String> list = yaml.getStringList("attuned." + player);
        if (list.contains(type.name())) return false;
        list.add(type.name());
        yaml.set("attuned." + player, list);
        save();
        return true;
    }

    public void clearAttuned(UUID player) {
        yaml.set("attuned." + player, null);
        save();
    }

    // ---- Gambling disc roll (per current owner) ----

    public String getGambleEffect() {
        return yaml.getString("gamble.effect");
    }

    public UUID getGambleOwner() {
        String s = yaml.getString("gamble.owner");
        return s == null ? null : UUID.fromString(s);
    }

    public void setGamble(UUID owner, String effectKey) {
        yaml.set("gamble.owner", owner == null ? null : owner.toString());
        yaml.set("gamble.effect", effectKey);
        save();
    }

    // ---- Ritual weapons ----

    /** Survives restarts: a player who completed the ritual may still pick a weapon. */
    public boolean mayChooseWeapon(UUID player) {
        return yaml.getBoolean("mayChoose." + player, false);
    }

    public void setMayChooseWeapon(UUID player, boolean value) {
        yaml.set("mayChoose." + player, value ? true : null);
        save();
    }

    public boolean isWeaponTaken(String weaponId) {
        return yaml.getString("weapons." + weaponId + ".owner") != null;
    }

    public String getWeaponOwner(String weaponId) {
        return yaml.getString("weapons." + weaponId + ".owner");
    }

    public void setWeaponTaken(String weaponId, String ownerName, String weaponName) {
        yaml.set("weapons." + weaponId + ".owner", ownerName);
        yaml.set("weapons." + weaponId + ".name", weaponName);
        save();
    }

    // ---- Rules ----

    public int getTotemUses(UUID player) {
        return yaml.getInt("totems." + player, 0);
    }

    public void incrementTotemUses(UUID player) {
        yaml.set("totems." + player, getTotemUses(player) + 1);
        save();
    }

    public int getMaceCount() {
        return yaml.getInt("maceCount", 0);
    }

    public void incrementMaceCount() {
        yaml.set("maceCount", getMaceCount() + 1);
        save();
    }

    public boolean isBorderSet() {
        return yaml.getBoolean("borderSet", false);
    }

    public void markBorderSet() {
        yaml.set("borderSet", true);
        save();
    }

    ConfigurationSection raw() {
        return yaml;
    }
}
