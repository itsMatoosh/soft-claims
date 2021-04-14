package me.matoosh.softclaims.events;

import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkLoadHandler implements Listener {

    private final SoftClaimsPlugin plugin;

    public ChunkLoadHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if(plugin.getConfig().getStringList("disabledWorlds")
                .contains(event.getWorld().getName())) return;
        plugin.getBlockDurabilityService().loadChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if(plugin.getConfig().getStringList("disabledWorlds")
                .contains(event.getWorld().getName())) return;
        plugin.getBlockDurabilityService().persistChunk(event.getChunk(), true);
    }
}
