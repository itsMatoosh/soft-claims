package me.matoosh.softclaims.service;

import lombok.Getter;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldBlockedException;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.exception.faction.NotEnoughPowerException;
import me.matoosh.softclaims.exception.faction.core.ClaimedByOtherFactionCorePlaceException;
import me.matoosh.softclaims.exception.faction.core.CoreOverlapException;
import me.matoosh.softclaims.exception.faction.core.FactionlessCorePlaceException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import me.matoosh.softclaims.faction.Faction;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class FactionCoreService {

    private final SoftClaimsPlugin plugin;

    /**
     * Faction core metadata storage.
     */
    private final BlockMetadataStorage<String> factionCoreStorage;

    public FactionCoreService(SoftClaimsPlugin plugin) {
        // save plugin reference
        this.plugin = plugin;

        // get data folder
        Path durabilitiesDataDir = plugin.getDataFolder().toPath()
                .resolve("data").resolve("cores");

        // create durabilities storage
        this.factionCoreStorage = new BlockMetadataStorage<>(plugin, durabilitiesDataDir);
    }

    /**
     * Creates a new faction core.
     * @param block The faction core block.
     * @param creator The creator of the core.
     */
    public CompletableFuture<Void> createFactionCore(Block block, Player creator)
            throws InvalidCoreBlockException, WorldBlockedException, FactionlessCorePlaceException {
        // check if a valid core block is used
        if (block.getType() != Material.RESPAWN_ANCHOR) {
            throw new InvalidCoreBlockException();
        }

        // check if the world is blocked
        if (plugin.getWorldService().isWorldDisabled(block.getWorld())) {
            throw new WorldBlockedException();
        }

        // get player's faction
        Faction faction = plugin.getFactionService().getFaction(creator);
        if (faction == null) {
            // player doesn't have a faction
            throw new FactionlessCorePlaceException();
        }

        // check if chunk is claimed
        Chunk chunk = block.getChunk();
        Faction factionClaiming = plugin.getFactionService().getFaction(chunk);
        return CompletableFuture.supplyAsync(() -> {
            if (factionClaiming != null) {
                // check if this chunk belongs to a different faction
                if (!faction.equals(factionClaiming)) {
                    // can't place a core in other faction land
                    throw new CompletionException(new ClaimedByOtherFactionCorePlaceException());
                } else {
                    // make sure that there is only 1 core per chunk
                    Map<String, String> coresInChunk;
                    try {
                        coresInChunk = plugin.getFactionCoreService()
                                .getFactionCoreStorage().getMetadataInChunk(chunk);
                    } catch (ChunkBusyException | ChunkNotLoadedException e) {
                        throw new CompletionException(e);
                    }
                    if (coresInChunk != null && coresInChunk.size() > 0) {
                        // there is already a core in this chunk
                        throw new CompletionException(new CoreOverlapException());
                    }
                }
            }
            return chunk;
        })
        // get unclaimed chunks that should be claimed
        .thenCompose(this::getUnclaimedChunksAround)
        .thenApply((unclaimedChunks) -> {
            // get faction claims
            List<Chunk> claims = plugin.getFactionService()
                    .getAllFactionChunks(factionClaiming.getName());

            // ensure that the faction has enough power to claim more chunks
            if (claims.size() + unclaimedChunks.size() > faction.getPower()) {
                throw new CompletionException(new NotEnoughPowerException());
            }

            // add core
            try {
                getFactionCoreStorage().setMetadata(block, faction.getName());
            } catch (ChunkBusyException | ChunkNotLoadedException e) {
                throw new CompletionException(e);
            }

            // claim unclaimed
            try {
                plugin.getFactionService().claimChunks(faction.getName(), creator, unclaimedChunks);
            } catch (FactionDoesntExistException e) {
                throw new CompletionException(e);
            }

            return null;
        });
    }

    /**
     * Calculates how many faction cores of a given faction are protecting a given chunk.
     * @param chunk The chunk.
     * @param faction The faction whose cores we should count.
     * @return How many faction cores are protecting a given chunk.
     */
    public CompletableFuture<Integer> getChunkProtection(Chunk chunk, String faction) {
        AtomicInteger count = new AtomicInteger();
        CompletableFuture<Void>[] futures = new CompletableFuture[8];
        // check each neighboring chunk
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // skip the chunk itself
                if (x == 0 && z == 0) {
                    continue;
                }

                // get neighboring chunk
                CompletableFuture<Void> countFuture = chunk.getWorld()
                .getChunkAtAsync(chunk.getX() + x, chunk.getZ() + z)
                .thenApply((c) -> {
                    // count cores of the faction is chunk
                    try {
                        Collection<String> factions = plugin.getFactionCoreService()
                                .getFactionCoreStorage().getMetadataInChunk(c).values();
                        for (String f : factions) {
                            if (f.equals(faction)) {
                                count.incrementAndGet();
                            }
                        }
                    } catch (ChunkBusyException | ChunkNotLoadedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
                futures[i] = countFuture;
                i++;
            }
        }

        // wait for all to complete
        return CompletableFuture.allOf(futures).thenApply((s) -> count.get());
    }

    /**
     * Calculates how many faction cores are protecting a given chunk.
     * @param chunk The chunk.
     * @return How many faction cores are protecting a given chunk.
     */
    public CompletableFuture<Map<String, AtomicInteger>> getChunkProtection(Chunk chunk) {
        ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        CompletableFuture<Void>[] futures = new CompletableFuture[8];
        // check each neighboring chunk
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // skip the chunk itself
                if (x == 0 && z == 0) {
                    continue;
                }

                // get neighboring chunk
                CompletableFuture<Void> countFuture = chunk.getWorld()
                .getChunkAtAsync(chunk.getX() + x, chunk.getZ() + z)
                .thenApply((c) -> {
                    // count cores of the faction is chunk
                    try {
                        Collection<String> factions = plugin.getFactionCoreService()
                                .getFactionCoreStorage().getMetadataInChunk(c).values();
                        for (String f : factions) {
                            AtomicInteger factionCoreCount = counts
                                    .computeIfAbsent(f, (s) -> new AtomicInteger());
                            factionCoreCount.incrementAndGet();
                        }
                    } catch (ChunkBusyException | ChunkNotLoadedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
                futures[i] = countFuture;
                i++;
            }
        }

        // wait for all to complete
        return CompletableFuture.allOf(futures).thenApply((s) -> counts);
    }

    /**
     * Finds unclaimed chunks around the given chunk.
     * @param chunk The chunk.
     * @return How many faction cores are protecting a given chunk.
     */
    public CompletableFuture<List<Chunk>> getUnclaimedChunksAround(Chunk chunk) {
        List<Chunk> chunks = new ArrayList<>();
        CompletableFuture<Void>[] futures = new CompletableFuture[8];
        // check each neighboring chunk
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // skip the chunk itself
                if (x == 0 && z == 0) {
                    continue;
                }

                // get neighboring chunk
                CompletableFuture<Void> countFuture = chunk.getWorld()
                .getChunkAtAsync(chunk.getX() + x, chunk.getZ() + z)
                .thenApply((c) -> {
                    // check if the chunk is claimed
                    if (!plugin.getFactionService().isInFactionLand(c)) {
                        chunks.add(c);
                    }
                    return null;
                });
                futures[i] = countFuture;
                i++;
            }
        }

        // wait for all to complete
        return CompletableFuture.allOf(futures).thenApply((s) -> chunks);
    }
}
