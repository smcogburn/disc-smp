package com.example.discsmp;

import com.example.discsmp.commands.GiveDiscCommand;
import com.example.discsmp.listeners.DiscListener;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscSMPPlugin extends JavaPlugin {
    public static NamespacedKey DISC_KEY;

    @Override
    public void onEnable() {
        DISC_KEY = new NamespacedKey(this, "disc_smp");
        if (this.getCommand("givedisc") != null) this.getCommand("givedisc").setExecutor(new GiveDiscCommand(this));
        getServer().getPluginManager().registerEvents(new DiscListener(this), this);
        getLogger().info("DiscSMP enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("DiscSMP disabled");
    }
}
