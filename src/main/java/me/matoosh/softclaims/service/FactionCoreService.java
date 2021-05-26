package me.matoosh.softclaims.service;

import lombok.Getter;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldBlockedException;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.exception.faction.FactionPermissionsDeniedException;
import me.matoosh.softclaims.exception.faction.NotEnoughPowerException;
import me.matoosh.softclaims.exception.faction.core.ChunkClaimedByOtherFactionException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import me.matoosh.softclaims.exception.faction.core.MultipleCoresInChunkException;
import me.matoosh.softclaims.faction.Faction;
import me.matoosh.softclaims.faction.FactionPermission;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class FactionCoreService {

    /**
     * Reference to the faction service.
     */
    private final FactionService factionService;
    /**
     * Reference to the world service.
     */
    private final WorldService worldService;

    /**
     * Faction core metadata storage.
     */
    private final BlockMetadataStorage<String> factionCoreStorage;

    public FactionCoreService(SoftClaimsPlugin plugin, FactionService factionService, WorldService worldService) {
        // set references
        this.factionService = factionService;
        this.worldService = worldService;

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
    public CompletableFuture<Void> createCore(Block block, Player creator)
            throws InvalidCoreBlockException, WorldBlockedException, FactionDoesntExistException,
            FactionPermissionsDeniedException, ChunkNotLoadedException, ChunkBusyException,
            MultipleCoresInChunkException {
        // check if a valid core block is used
        if (block.getType() != Material.RESPAWN_ANCHOR) {
            throw new InvalidCoreBlockException();
        }

        // check if the world is blocked
        if (worldService.isWorldDisabled(block.getWorld())) {
            throw new WorldBlockedException();
        }

        // get player's faction
        Faction faction = factionService.getFaction(creator);
        if (faction == null) {
            // player doesn't have a faction
            throw new FactionDoesntExistException();
        }

        // check if player has claim permissions
        if (!factionService.hasPlayerPermission(
                faction.getId(), creator, FactionPermission.CLAIM_CHUNK)) {
            throw new FactionPermissionsDeniedException();
        }

        // check if chunk is already claimed
        Chunk chunk = block.getChunk();
        Faction factionClaiming = factionService.getFaction(chunk);
        if (factionClaiming != null) {
            // check if this chunk belongs to a different faction
            if (!faction.equals(factionClaiming)) {
                // can't place a core in other faction land
                throw new CompletionException(new ChunkClaimedByOtherFactionException());
            } else {
                // make sure that there is only 1 core per chunk
                Map<String, String> coresInChunk = getFactionCoreStorage().getMetadataInChunk(chunk);
                if (coresInChunk != null && coresInChunk.size() > 0) {
                    // there is already a core in this chunk
                    throw new MultipleCoresInChunkException();
                }
            }
        }

        // claim chunk
        factionService.claimChunk(faction.getId(), chunk);

        // get unclaimed chunks that should be claimed
        return this.getUnclaimedChunksAround(chunk)
        .thenApply((unclaimedChunks) -> {
            // get faction claims
            List<Chunk> claims = factionService.getAllFactionChunks(faction.getId());

            // ensure that the faction has enough power to claim more chunks
            if (claims.size() + unclaimedChunks.size() > faction.getPower()) {
                throw new CompletionException(new NotEnoughPowerException());
            }

            // add core
            try {
                getFactionCoreStorage().setMetadata(block, faction.getId());
            } catch (ChunkBusyException | ChunkNotLoadedException e) {
                throw new CompletionException(e);
            }

            // claim unclaimed
            for (Chunk c :
                    unclaimedChunks) {
                try {
                    factionService.claimChunk(faction.getId(), c);
                } catch (FactionDoesntExistException e) {
                    throw new CompletionException(e);
                }
            }

            return null;
        });
    }

    /**
     * Removes a faction core.
     * @param block The removed block.
     * @param remover The removing player.
     */
    public CompletableFuture<Void> removeCore(Block block, Player remover)
            throws InvalidCoreBlockException, WorldBlockedException,
            FactionDoesntExistException, ChunkNotLoadedException, ChunkBusyException,
            FactionPermissionsDeniedException {
        // check block type
        if (block.getType() != Material.RESPAWN_ANCHOR) {
            throw new InvalidCoreBlockException();
        }

        // check if the world is blocked
        if (worldService.isWorldDisabled(block.getWorld())) {
            throw new WorldBlockedException();
        }

        // get player's faction
        Faction faction = factionService.getFaction(remover);

        // get core's faction
        String coreFaction = factionCoreStorage.getMetadata(block);
        if (coreFaction == null) {
            // not a core
            throw new InvalidCoreBlockException();
        }

        // check if core's faction == player faction
        if (faction != null && coreFaction.equals(faction.getId())) {
            // check if player has unclaim perms
            if (!factionService.hasPlayerPermission(
                    faction.getId(), remover, FactionPermission.UNCLAIM_CHUNK)) {
                throw new FactionPermissionsDeniedException();
            }
        }

        // get chunks influenced by the destroyed core
        return getInfluencedChunks(block).thenApply(chunks -> {
            for (Chunk c : chunks) {
                // get factions influencing each chunk
                getChunkInfluences(c).thenApply(influences -> {
                    // if there are no influences, unclaim chunk
                    if (influences.size() == 0) {
                        // unclaim chunk
                        try {
                            factionService.unclaimChunk(coreFaction, c);
                        } catch (FactionDoesntExistException e) {
                            throw new CompletionException(e);
                        }
                        return null;
                    }

                    // find the new faction to influence this chunk
                    Map.Entry<String, AtomicInteger> maxInfluence = null;
                    for (Map.Entry<String, AtomicInteger> influence : influences.entrySet()) {
                        // set initial influence
                        if (maxInfluence == null) {
                            maxInfluence = influence;
                            continue;
                        }

                        // set the max influence
                        if (influence.getValue().get() > maxInfluence.getValue().get()) {
                            maxInfluence = influence;
                        }
                    }

                    // if the most influential faction is the current faction
                    // leave the chunk be
                    if (maxInfluence.getKey().equals(coreFaction)) {
                        return null;
                    }

                    // make the most influential faction take the chunk
                    // unclaim chunk
                    try {
                        factionService.unclaimChunk(coreFaction, c);
                    } catch (FactionDoesntExistException e) {
                        throw new CompletionException(e);
                    }
                    try {
                        factionService.claimChunk(maxInfluence.getKey(), c);
                    } catch (FactionDoesntExistException e) {
                        throw new CompletionException(e);
                    }
                    return null;
                });
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
    public CompletableFuture<Integer> getChunkInfluencesOfFaction(Chunk chunk, String faction) {
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
                        Collection<String> factions = getFactionCoreStorage().getMetadataInChunk(c).values();
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
     * Does not include the influence of a core inside of the chunk.
     * @param chunk The chunk.
     * @return How many faction cores are protecting a given chunk.
     */
    public CompletableFuture<Map<String, AtomicInteger>> getChunkInfluences(Chunk chunk) {
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
                        Collection<String> factions = getFactionCoreStorage().getMetadataInChunk(c).values();
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
     * Get chunks influenced by the given core.
     * @param core The core block.
     * @return List of 9 chunks influenced by the core.
     */
    public CompletableFuture<Chunk[]> getInfluencedChunks(Block core) {
        // get center chunk coords
        int centerX = core.getX() / 16;
        int centerZ = core.getZ() / 16;

        // get chunks around center
        Chunk[] chunks = new Chunk[9];
        CompletableFuture<Void>[] futures = new CompletableFuture[9];
        AtomicInteger chunkArrIndex = new AtomicInteger();
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                futures[i] = core.getWorld().getChunkAtAsync(centerX + x, centerZ + z)
                .thenApply((c) -> {
                    chunks[chunkArrIndex.get()] = c;
                    chunkArrIndex.getAndIncrement();
                    return null;
                });
                i++;
            }
        }
        return CompletableFuture.allOf(futures)
                .thenApply((s) -> chunks);
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
                    if (!factionService.isInFactionLand(c)) {
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
