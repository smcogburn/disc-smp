package com.example.discsmp;

import com.example.discsmp.commands.DiscSMPCommand;
import com.example.discsmp.commands.DiscsCommand;
import com.example.discsmp.listeners.DiscListener;
import com.example.discsmp.listeners.RulesListener;
import com.example.discsmp.managers.AbilityManager;
import com.example.discsmp.managers.RitualManager;
import com.example.discsmp.managers.ShrineManager;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscSMPPlugin extends JavaPlugin {

    private static final double BORDER_SIZE = 15000;

    private DataStore dataStore;
    private ShrineManager shrineManager;
    private AbilityManager abilityManager;
    private RitualManager ritualManager;
    private RulesListener rulesListener;

    @Override
    public void onEnable() {
        DiscItems.init(this);
        dataStore = new DataStore(this);
        shrineManager = new ShrineManager(this, dataStore);
        abilityManager = new AbilityManager(this, dataStore);
        ritualManager = new RitualManager(this, dataStore);
        rulesListener = new RulesListener(this);

        getServer().getPluginManager().registerEvents(new DiscListener(this), this);
        getServer().getPluginManager().registerEvents(rulesListener, this);
        getServer().getPluginManager().registerEvents(ritualManager, this);

        DiscSMPCommand admin = new DiscSMPCommand(this);
        getCommand("discsmp").setExecutor(admin);
        getCommand("discsmp").setTabCompleter(admin);
        getCommand("discs").setExecutor(new DiscsCommand(this));

        // held-disc powers + netherite armor safety net, every 2s
        getServer().getScheduler().runTaskTimer(this, () -> {
            abilityManager.tick();
            rulesListener.sweepNetheriteArmor();
        }, 40L, 40L);
        // shrine omens, every 3s
        getServer().getScheduler().runTaskTimer(this, shrineManager::tickOmens, 60L, 60L);

        setupWorldBorder();
        getLogger().info("Disc SMP enabled. Ten discs. One world. Good luck.");
    }

    /** 15k x 15k overworld border (nether scaled 1:8), set once. */
    private void setupWorldBorder() {
        if (dataStore.isBorderSet()) return;
        for (World world : getServer().getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            if (world.getEnvironment() == World.Environment.NORMAL) {
                border.setSize(BORDER_SIZE);
            } else if (world.getEnvironment() == World.Environment.NETHER) {
                border.setSize(BORDER_SIZE / 8);
            }
        }
        dataStore.markBorderSet();
        getLogger().info("World border set to " + (int) BORDER_SIZE + " x " + (int) BORDER_SIZE + ".");
    }

    @Override
    public void onDisable() {
        if (dataStore != null) dataStore.save();
        getLogger().info("Disc SMP disabled.");
    }

    public DataStore getDataStore() { return dataStore; }
    public ShrineManager getShrineManager() { return shrineManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public RitualManager getRitualManager() { return ritualManager; }
}
