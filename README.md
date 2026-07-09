# Disc SMP

KNICKS IN 5 (lets go)

A Paper plugin for the Disc SMP: **ten unique music discs** hidden at shrines inside vanilla
structures. Each disc grants a power while held. Collect and play all ten, perform the ritual,
and claim one of six one-of-a-kind OP weapons.

Requires **Paper 1.21.6+** (uses the Mace, Trial Keys, and the Tears disc) and **Java 21**.

## Build & install

```bash
./gradlew build
```

Drop `build/libs/disc-smp-1.0.0.jar` into your Paper server's `plugins` folder.

## Setup (admins)

Each disc needs a shrine — a **themed altar** hidden in its structure:

1. `/discsmp locate all` — finds a matching structure scattered around the map (min ~1,500
   blocks from spawn) and builds the altar **inside** the structure's generated bounding box
   (down in the mineshaft, inside the bastion, etc.). Verify each spot and adjust if desired:
2. `/discsmp setshrine <disc>` — rebuilds the altar at the block you're looking at.

Each altar is a small indestructible structure themed to its disc (blackstone + gilded
blackstone for the Bastion, prismarine for the Monument, ...) with a floating label that says
to right-click. Right-clicking any altar block opens the offering GUI: the four required
materials, live green/red status for what you're carrying, and a forge button. Materials are
consumed straight from your inventory — nothing is stored in the altar, so nothing can be
stolen. The disc lands directly in your inventory (or drops at your feet if it's full).

Other admin commands: `/discsmp status`, `give <disc> [player]`, `reset <disc>`,
`reroll [player]`, `recipe <disc>`. Players can run `/discs` to see the state of the hunt.

The world border (15,000 × 15,000 overworld, scaled nether) is applied automatically on first boot.

## The ten discs

| # | Theme | Song | Power while held | Shrine |
|---|-------|------|------------------|--------|
| 1 | Berserker | Pigstep | Strength II | Bastion |
| 2 | Acrobat | Cat | Jump Boost III | Jungle Temple |
| 3 | Vampire | Chirp | Regeneration II | Woodland Mansion |
| 4 | Juggernaut | Blocks | Resistance II + Health Boost II | Desert Temple |
| 5 | Phoenix | Otherside | Fire Resistance | Nether Fortress |
| 6 | Pain | 11 | Curses nearby enemies: Weakness III + Blindness | Witch Hut |
| 7 | Poseidon | Wait | Dolphin's Grace II + Water Breathing | Ocean Monument |
| 8 | Mine & Craft | Stal | Haste III | Mineshaft |
| 9 | Phantom | Tears | Speed II + Invisibility | Ancient City |
| 10 | Gambling | Creator | Random Level V power, rerolled per owner (dice-roll animation) | Trial Chambers |

How they work:

- **Only one of each exists.** Approaching an unclaimed shrine triggers an omen — the world
  darkens, the shrine names itself, and the offering recipe is revealed (once per player;
  `/discsmp recipe` shows it again).
- Bring the materials and **right-click the altar** to forge the disc: lightning, soulfire,
  a challenge toast pops, the server is told — and the disc's song plays for **everyone
  online**, like the dragon's death cry.
- **Whoever holds it, has it.** If a disc burns, blows up, despawns, or falls into the void,
  it returns to its shrine and can be forged again (server-wide announcement).
- Discs **cannot be stored in ender chests**.

## The ritual of the ten songs

1. Play each of the ten discs in a jukebox at least once (attunement, tracked per player).
2. With **all ten discs in your inventory**, sneak-right-click a jukebox.
3. The discs rise, spiral together, and are destroyed — they return to their shrines,
   craftable again.
4. Choose one of six weapons (each claimable **once, ever**) and type its name in chat:

   Sword (Sharpness VII…), Axe, Bow (Power VII, Infinity…), Mace (Density VII, Wind Burst IV…),
   Spear (Impaling VII, Loyalty IV, Channeling), Trident (Riptide IV…).

   The weapon is unbreakable, fire-proof, never despawns, and the void throws it back to spawn.

## House rules (enforced)

- **Totems:** each player gets 2 totem saves, ever. After that they silently stop working.
- **Netherite armor:** banned — can't smith it, equip it, or dispense it onto yourself.
- **Netherite tools/weapons:** banned, **except the pickaxe**. (Ritual weapons are exempt.)
- **Maces:** only 5 may ever be crafted in the world.
- No x-ray, cheats, chunk banning, or lag machines — that one's on you, not the plugin.
