package com.example.discsmp.listeners;

import com.example.discsmp.DataStore;
import com.example.discsmp.DiscSMPPlugin;
import com.example.discsmp.managers.RitualManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.Set;

/**
 * Server rules: two totem uses per player (ever, silently), no netherite
 * armor, no netherite tools or weapons except the pickaxe, and at most five
 * maces in the world.
 */
public class RulesListener implements Listener {

    private static final Set<Material> NETHERITE_ARMOR = EnumSet.of(
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);

    /** Everything netherite that is banned (pickaxe stays legal). */
    private static final Set<Material> NETHERITE_BANNED = EnumSet.of(
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD, Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);

    private final DiscSMPPlugin plugin;
    private final DataStore data;

    public RulesListener(DiscSMPPlugin plugin) {
        this.plugin = plugin;
        this.data = plugin.getDataStore();
    }

    // ---- Totems: two resurrections per player, ever. After that they just... don't. ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getHand() == null) return; // not a totem resurrection
        if (data.getTotemUses(p.getUniqueId()) >= 2) {
            event.setCancelled(true); // silently - the totem simply fails
        } else {
            data.incrementTotemUses(p.getUniqueId());
        }
    }

    // ---- Netherite bans ----

    @EventHandler(ignoreCancelled = true)
    public void onSmith(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result != null && NETHERITE_BANNED.contains(result.getType())) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorEquipClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) return;
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0
                ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if ((cursor != null && NETHERITE_ARMOR.contains(cursor.getType()))
                || (hotbar != null && NETHERITE_ARMOR.contains(hotbar.getType()))
                || (event.isShiftClick() && event.getCurrentItem() != null
                    && NETHERITE_ARMOR.contains(event.getCurrentItem().getType()))) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED + "Netherite armor is banned on the Disc SMP.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorEquipInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && NETHERITE_ARMOR.contains(item.getType())
                && (event.getAction().name().contains("RIGHT_CLICK"))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Netherite armor is banned on the Disc SMP.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorDispense(BlockDispenseArmorEvent event) {
        if (NETHERITE_ARMOR.contains(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    /** Safety net, run from the ability tick: strips netherite armor that slipped through. */
    public void sweepNetheriteArmor() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            ItemStack[] armor = inv.getArmorContents();
            boolean changed = false;
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null && NETHERITE_ARMOR.contains(armor[i].getType())) {
                    ItemStack piece = armor[i];
                    armor[i] = null;
                    changed = true;
                    inv.addItem(piece).values()
                            .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                }
            }
            if (changed) {
                inv.setArmorContents(armor);
                p.sendMessage(ChatColor.RED + "Netherite armor is banned on the Disc SMP.");
            }
        }
    }

    // ---- Five maces, ever ----

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;
        if (RitualManager.isRitualWeapon(result)) return;
        if (data.getMaceCount() >= 5) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED
                    + "All 5 maces of this world have already been forged.");
        } else {
            data.incrementMaceCount();
            int left = 5 - data.getMaceCount();
            Bukkit.broadcastMessage(ChatColor.GOLD + "⚒ A mace has been forged. "
                    + ChatColor.GRAY + left + " remain" + (left == 1 ? "s" : "") + " craftable in this world.");
        }
    }
}
