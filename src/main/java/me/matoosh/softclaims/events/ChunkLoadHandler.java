package me.matoosh.softclaims.events;

import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Bukkit;
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
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.getBlockDurabilityService().getDurabilityStorage().loadChunk(event.getChunk()));
        } else {
            plugin.getBlockDurabilityService().getDurabilityStorage().loadChunk(event.getChunk());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.getBlockDurabilityService().getDurabilityStorage().persistChunk(event.getChunk(), true));
        } else {
            plugin.getBlockDurabilityService().getDurabilityStorage().persistChunk(event.getChunk(), true);
        }
    }
}
