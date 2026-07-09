package com.example.discsmp.listeners;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscItems;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.DiscType;
import com.example.discsmp.managers.RitualManager;
import com.example.discsmp.managers.ShrineManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Jukebox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Disc interactions and protections: shrine altar clicks, jukebox attunement
 * and the ritual trigger, the ender chest ban, ownership tracking, and the
 * destroyed-disc-returns-to-its-shrine rule. Also keeps ritual weapons alive.
 */
public class DiscListener implements Listener {

    private final DiscSMPPlugin plugin;
    private final DataStore data;

    public DiscListener(DiscSMPPlugin plugin) {
        this.plugin = plugin;
        this.data = plugin.getDataStore();
    }

    // ---- Shrine altar + jukebox ----

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player p = event.getPlayer();

        DiscType altar = plugin.getShrineManager().altarAt(block.getLocation());
        if (altar != null) {
            event.setCancelled(true);
            plugin.getShrineManager().openAltarGui(p, altar);
            return;
        }

        if (block.getType() == Material.JUKEBOX) {
            if (p.isSneaking()) {
                event.setCancelled(true);
                plugin.getRitualManager().handleSneakClick(p);
                return;
            }
            boolean hasRecord = ((Jukebox) block.getBlockData()).hasRecord();
            DiscType inHand = DiscItems.getDiscType(event.getItem());
            if (!hasRecord && inHand != null) {
                // vanilla inserts the disc; the ritual tracker verifies a tick later
                plugin.getRitualManager().onDiscInserted(p, inHand, block);
            } else if (hasRecord) {
                // ejecting whatever was playing
                plugin.getRitualManager().onJukeboxDisturbed(block.getLocation());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onJukeboxBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.JUKEBOX) {
            plugin.getRitualManager().onJukeboxDisturbed(event.getBlock().getLocation());
        }
    }

    // ---- Altar GUI: display-only, plus the forge button ----

    @EventHandler
    public void onAltarGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof ShrineManager.AltarHolder holder)) return;
        event.setCancelled(true); // nothing can be put in or taken out
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() == event.getView().getTopInventory()
                && event.getSlot() == ShrineManager.FORGE_SLOT) {
            plugin.getShrineManager().tryForge(p, holder.type);
        }
    }

    @EventHandler
    public void onAltarGuiDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShrineManager.AltarHolder) {
            event.setCancelled(true);
        }
    }

    // ---- Altars are indestructible ----

    @EventHandler(ignoreCancelled = true)
    public void onAltarBreak(BlockBreakEvent event) {
        if (plugin.getShrineManager().altarAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.DARK_GRAY
                    + "The altar does not yield to mortal tools.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAltarExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getShrineManager().altarAt(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAltarBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getShrineManager().altarAt(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAltarBurn(BlockBurnEvent event) {
        if (plugin.getShrineManager().altarAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAltarPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getShrineManager().altarAt(b.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAltarPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getShrineManager().altarAt(b.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---- Ownership: whoever holds a disc, has it ----

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        DiscType type = DiscItems.getDiscType(event.getItem().getItemStack());
        if (type != null) {
            plugin.getAbilityManager().onDiscAcquired(p, type);
        }
    }

    // ---- No discs in ender chests ----

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.ENDER_CHEST) return;

        ItemStack moving = null;
        if (event.isShiftClick() && event.getClickedInventory() != top) {
            moving = event.getCurrentItem();
        } else if (event.getClickedInventory() == top) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                moving = event.getView().getBottomInventory().getItem(event.getHotbarButton());
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                moving = event.getWhoClicked().getInventory().getItemInOffHand();
            } else {
                moving = event.getCursor();
            }
        }
        if (DiscItems.getDiscType(moving) != null) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED
                    + "The disc refuses to enter the ender chest.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (DiscItems.getDiscType(event.getOldCursor()) == null) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED
                        + "The disc refuses to enter the ender chest.");
                return;
            }
        }
    }

    // ---- Destruction: the disc returns to its shrine ----

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        if (RitualManager.isRitualWeapon(event.getEntity().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        DiscType type = DiscItems.getDiscType(event.getEntity().getItemStack());
        if (type != null) {
            discDestroyed(type, "faded away");
        }
    }

    @EventHandler
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;

        if (RitualManager.isRitualWeapon(item.getItemStack())) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                // the weapon cannot be destroyed: the void spits it back out at spawn
                Location spawn = item.getWorld().getSpawnLocation();
                item.teleport(spawn.add(0.5, 1.0, 0.5));
                item.setVelocity(new org.bukkit.util.Vector(0, 0.2, 0));
                Bukkit.broadcastMessage(ChatColor.GOLD
                        + "⚔ A weapon of legend has returned to spawn from the void.");
            }
            return;
        }

        DiscType type = DiscItems.getDiscType(item.getItemStack());
        if (type != null) {
            String fate = switch (event.getCause()) {
                case VOID -> "fell into the void";
                case FIRE, FIRE_TICK, LAVA -> "burned away";
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "was blown apart";
                default -> "was destroyed";
            };
            item.remove();
            discDestroyed(type, fate);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (RitualManager.isRitualWeapon(event.getEntity().getItemStack())) {
            event.getEntity().setInvulnerable(true);
            event.getEntity().setPersistent(true);
        }
    }

    private void discDestroyed(DiscType type, String fate) {
        if (!data.isClaimed(type)) return;
        data.setUnclaimed(type);
        if (type == DiscType.GAMBLING) data.setGamble(null, null);
        plugin.getShrineManager().updateDisplay(type);
        Bukkit.broadcastMessage(type.getColor() + "♫ " + type.getDisplayName() + type.getColor()
                + " " + fate + "! " + ChatColor.GRAY
                + "It has returned to its shrine in " + type.getStructureName()
                + ", waiting to be forged again.");
    }
}
