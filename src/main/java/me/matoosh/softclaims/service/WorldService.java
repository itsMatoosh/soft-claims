package me.matoosh.softclaims.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Getter
@AllArgsConstructor
public class WorldService implements IReloadable {

    private final SoftClaimsPlugin plugin;

    /**
     * Set of all disabled worlds' names.
     */
    private final Set<String> disabledWorlds = new HashSet<>();

    @Override
    public void reload() {
        // reload disabled worlds
        disabledWorlds.clear();
        disabledWorlds.addAll(this.plugin.getConfig()
                .getStringList("disabledWorlds"));
    }

    /**
     * Checks whether a given world is disabled.
     * @param world The world.
     * @return Whether the given world is disabled.
     */
    public boolean isWorldDisabled (World world) {
        return disabledWorlds.contains(world.getName());
    }

    /**
     * Loads block metadata for the currently loaded chunks.
     * @return The load future.
     */
    public CompletableFuture<Void> loadMetadataForLoadedChunks()
            throws ChunkAlreadyLoadedException {
        // get chunks to load
        List<Chunk> chunksToLoad = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isWorldDisabled(world)) {
                continue;
            }
            Collections.addAll(chunksToLoad, world.getLoadedChunks());
        }
        Chunk[] chunks = chunksToLoad.toArray(new Chunk[0]);

        // load durabilities
        CompletableFuture<Void> durabilityLoad = this.plugin.getBlockDurabilityService()
                .getDurabilityStorage().loadChunks(chunks);

        // load faction cores
        CompletableFuture<Void> coresLoad = this.plugin.getFactionCoreService()
                .getFactionCoreStorage().loadChunks(chunks);

        // wait for all
        return CompletableFuture.allOf(durabilityLoad, coresLoad);
    }

    /**
     * Saves block metadata for all the currently loaded chunks.
     * @return The save future.
     */
    public CompletableFuture<Void> saveMetadataForLoadedChunks() throws ChunkNotLoadedException {
        // get chunks to load
        List<Chunk> chunksToLoad = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isWorldDisabled(world)) {
                continue;
            }
            Collections.addAll(chunksToLoad, world.getLoadedChunks());
        }
        Chunk[] chunks = chunksToLoad.toArray(new Chunk[0]);

        // save durabilities
        CompletableFuture<Void> durabilityLoad = this.plugin.getBlockDurabilityService()
                .getDurabilityStorage().persistChunks(true, chunks);

        // save faction cores
        CompletableFuture<Void> coresLoad = this.plugin.getFactionCoreService()
                .getFactionCoreStorage().persistChunks(true, chunks);

        // wait for all
        return CompletableFuture.allOf(durabilityLoad, coresLoad);
    }
}
