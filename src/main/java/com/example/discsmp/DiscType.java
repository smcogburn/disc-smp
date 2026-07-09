package com.example.discsmp;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ten unique discs of the Disc SMP. Only one of each exists in the world.
 */
public enum DiscType {

    BERSERKER(1, "Berserker", "Pigstep", ChatColor.RED,
            Material.MUSIC_DISC_PIGSTEP, Sound.MUSIC_DISC_PIGSTEP,
            "a lone Bastion", "bastion_remnant",
            "Forged in piglin warfields, it beats like a war drum.",
            "Strength II while carried",
            recipe(Material.GOLD_BLOCK, 16, Material.NETHERITE_BLOCK, 1,
                    Material.BLAZE_ROD, 8, Material.CRYING_OBSIDIAN, 8),
            effects(effect(PotionEffectType.STRENGTH, 1))),

    ACROBAT(2, "Acrobat", "Cat", ChatColor.GREEN,
            Material.MUSIC_DISC_CAT, Sound.MUSIC_DISC_CAT,
            "a lost Jungle Temple", "jungle_pyramid",
            "Light as a cat's step, it never quite touches the ground.",
            "Jump Boost III while carried",
            recipe(Material.MOSS_BLOCK, 32, Material.EMERALD_BLOCK, 2,
                    Material.RABBIT_FOOT, 4, Material.BAMBOO_BLOCK, 16),
            effects(effect(PotionEffectType.JUMP_BOOST, 2))),

    VAMPIRE(3, "Vampire", "Chirp", ChatColor.DARK_RED,
            Material.MUSIC_DISC_CHIRP, Sound.MUSIC_DISC_CHIRP,
            "a haunted Woodland Mansion", "mansion",
            "It hums with stolen heartbeats.",
            "Regeneration II while carried",
            recipe(Material.GHAST_TEAR, 8, Material.REDSTONE_BLOCK, 16,
                    Material.FERMENTED_SPIDER_EYE, 16, Material.WITHER_ROSE, 1),
            effects(effect(PotionEffectType.REGENERATION, 1))),

    JUGGERNAUT(4, "Juggernaut", "Blocks", ChatColor.GOLD,
            Material.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_BLOCKS,
            "a buried Desert Temple", "desert_pyramid",
            "Heavy as a mountain, patient as the sand.",
            "Resistance II + Health Boost II while carried",
            recipe(Material.DIAMOND_BLOCK, 8, Material.OBSIDIAN, 32,
                    Material.SHULKER_SHELL, 12, Material.ENCHANTED_GOLDEN_APPLE, 4),
            effects(effect(PotionEffectType.RESISTANCE, 1), effect(PotionEffectType.HEALTH_BOOST, 1))),

    PHOENIX(5, "Phoenix", "Otherside", ChatColor.LIGHT_PURPLE,
            Material.MUSIC_DISC_OTHERSIDE, Sound.MUSIC_DISC_OTHERSIDE,
            "a burning Nether Fortress", "fortress",
            "From the ashes of the other side, it rises.",
            "Fire Resistance while carried",
            recipe(Material.MAGMA_BLOCK, 32, Material.BLAZE_ROD, 12,
                    Material.MAGMA_CREAM, 16, Material.GOLD_BLOCK, 8),
            effects(effect(PotionEffectType.FIRE_RESISTANCE, 0))),

    PAIN(6, "Pain", "11", ChatColor.DARK_GRAY,
            Material.MUSIC_DISC_11, Sound.MUSIC_DISC_11,
            "a crooked Witch Hut", "swamp_hut",
            "Those who hear its broken song wish they hadn't.",
            "Curses nearby enemies with Weakness III + Blindness",
            recipe(Material.FERMENTED_SPIDER_EYE, 16, Material.BONE_BLOCK, 16,
                    Material.SOUL_SAND, 32, Material.WITHER_SKELETON_SKULL, 6),
            Collections.emptyList()),

    POSEIDON(7, "Poseidon", "Wait", ChatColor.AQUA,
            Material.MUSIC_DISC_WAIT, Sound.MUSIC_DISC_WAIT,
            "a drowned Ocean Monument", "monument",
            "The tide answers to whoever carries it.",
            "Dolphin's Grace II + Water Breathing while carried",
            recipe(Material.SEA_LANTERN, 16, Material.HEART_OF_THE_SEA, 1,
                    Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 2, Material.SPONGE, 8),
            effects(effect(PotionEffectType.DOLPHINS_GRACE, 1), effect(PotionEffectType.WATER_BREATHING, 0))),

    MINE_AND_CRAFT(8, "Mine & Craft", "Stal", ChatColor.YELLOW,
            Material.MUSIC_DISC_STAL, Sound.MUSIC_DISC_STAL,
            "a forgotten Mineshaft", "mineshaft",
            "It remembers every pickaxe swing ever taken.",
            "Haste III while carried",
            recipe(Material.IRON_BLOCK, 16, Material.GOLD_BLOCK, 8,
                    Material.DIAMOND_BLOCK, 6, Material.REDSTONE_BLOCK, 16),
            effects(effect(PotionEffectType.HASTE, 2))),

    PHANTOM(9, "Phantom", "Tears", ChatColor.DARK_AQUA,
            Material.MUSIC_DISC_TEARS, Sound.MUSIC_DISC_TEARS,
            "a silent Ancient City", "ancient_city",
            "It plays a song the Warden weeps to.",
            "Speed II, and true invisibility (armor too) that comes and goes",
            recipe(Material.PHANTOM_MEMBRANE, 4, Material.ECHO_SHARD, 8,
                    Material.ELYTRA, 1, Material.ENDER_PEARL, 16),
            // invisibility is special-cased in AbilityManager: 30s on / 30s off, hides armor
            effects(effect(PotionEffectType.SPEED, 1))),

    GAMBLING(10, "Gambling", "Creator", ChatColor.DARK_PURPLE,
            Material.MUSIC_DISC_CREATOR, Sound.MUSIC_DISC_CREATOR,
            "the Trial Chambers", "trial_chambers",
            "Every hand that holds it rolls the dice anew.",
            "A random Level V power - rerolled for each new owner",
            recipe(Material.OMINOUS_TRIAL_KEY, 4, Material.FISHING_ROD, 1,
                    Material.AMETHYST_BLOCK, 16, Material.GOLD_BLOCK, 8),
            Collections.emptyList());

    /** Effects the Pain disc inflicts on nearby enemies (not the holder). */
    public static final List<PotionEffect> PAIN_AURA = effects(
            effect(PotionEffectType.WEAKNESS, 2), effect(PotionEffectType.BLINDNESS, 0));

    /** Pool the Gambling disc rolls from, always at amplifier 4 (Level V). */
    public static final List<PotionEffectType> GAMBLE_POOL = Arrays.asList(
            PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
            PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.JUMP_BOOST,
            PotionEffectType.ABSORPTION, PotionEffectType.LUCK);

    private final int number;
    private final String theme;
    private final String songName;
    private final ChatColor color;
    private final Material material;
    private final Sound song;
    private final String structureName;
    private final String structureKey;
    private final String flavor;
    private final String abilityText;
    private final Map<Material, Integer> recipe;
    private final List<PotionEffect> heldEffects;

    DiscType(int number, String theme, String songName, ChatColor color, Material material, Sound song,
             String structureName, String structureKey, String flavor, String abilityText,
             Map<Material, Integer> recipe, List<PotionEffect> heldEffects) {
        this.number = number;
        this.theme = theme;
        this.songName = songName;
        this.color = color;
        this.material = material;
        this.song = song;
        this.structureName = structureName;
        this.structureKey = structureKey;
        this.flavor = flavor;
        this.abilityText = abilityText;
        this.recipe = recipe;
        this.heldEffects = heldEffects;
    }

    public int getNumber() { return number; }
    public String getTheme() { return theme; }
    public String getSongName() { return songName; }
    public ChatColor getColor() { return color; }
    public Material getMaterial() { return material; }
    public Sound getSong() { return song; }
    public String getStructureName() { return structureName; }
    public String getStructureKey() { return structureKey; }
    public String getFlavor() { return flavor; }
    public String getAbilityText() { return abilityText; }
    public Map<Material, Integer> getRecipe() { return recipe; }
    public List<PotionEffect> getHeldEffects() { return heldEffects; }

    public String getDisplayName() {
        return color + "" + ChatColor.BOLD + theme + ChatColor.RESET + color + " (" + songName + ")";
    }

    public static DiscType byId(String id) {
        for (DiscType t : values()) {
            if (t.name().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    private static Map<Material, Integer> recipe(Object... pairs) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Material) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    private static PotionEffect effect(PotionEffectType type, int amplifier) {
        // refreshed every 2s by the ability task; no particles and no HUD icon - sneaky
        return new PotionEffect(type, 220, amplifier, false, false, false);
    }

    private static List<PotionEffect> effects(PotionEffect... e) {
        return Arrays.asList(e);
    }
}
