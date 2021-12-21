package me.matoosh.softclaims.service;

import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.ChunkCoordinates;
import me.matoosh.blockmetadata.ChunkInfo;
import me.matoosh.softclaims.exception.WorldDisabledException;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Getter
@Log
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

    /**
     * Creates a new faction core.
     * @param block The faction core block.
     * @param creator The creator of the core.
     */
    public CompletableFuture<Void> createCore(Block block, Player creator)
            throws InvalidCoreBlockException, WorldDisabledException,
            FactionDoesntExistException, FactionPermissionsDeniedException, ChunkClaimedByOtherFactionException {
        // check if a valid core block is used
        if (block.getType() != Material.RESPAWN_ANCHOR) {
            throw new InvalidCoreBlockException();
        }

        // check if the world is blocked
        if (worldService.isWorldDisabled(block.getWorld())) {
            throw new WorldDisabledException();
        }

        // get player's faction
        Faction claimingFaction = factionService.getFaction(creator);

        // check if player has claim permissions
        if (!factionService.hasPlayerPermission(
                claimingFaction.id(), creator, FactionPermission.CLAIM_CHUNK)) {
            throw new FactionPermissionsDeniedException();
        }

        // check if chunk is already claimed
        Chunk chunk = block.getChunk();
        try {
            // check if this chunk belongs to a different faction
            Faction ownerFaction = factionService.getFaction(chunk);
            if (!claimingFaction.equals(ownerFaction)) {
                // can't place a core in other faction land
                throw new ChunkClaimedByOtherFactionException();
            }
        } catch (FactionDoesntExistException ignored) {}

        // make sure that there is only 1 core per chunk
        return factionCoreStorage.getMetadataInChunk(ChunkInfo.fromChunk(chunk))
        .thenApply((metadata) -> {
            if (metadata != null) {
                // there is already a core in this chunk
                throw new CompletionException(new MultipleCoresInChunkException());
            }
            return null;
        })
        // get unclaimed chunks that should be claimed
        .thenCompose((s) -> getUnclaimedChunksAround(chunk)
            .thenApply((unclaimedChunks) -> {
                // get faction claims
                List<Chunk> claims;
                try {
                    claims = factionService.getAllFactionChunks(claimingFaction.id());
                } catch (FactionDoesntExistException e) {
                    claims = Collections.emptyList();
                }

                // ensure that the faction has enough power to claim more chunks
                System.out.println("COCK");
                if (claims.size() + unclaimedChunks.size() > claimingFaction.power()) {
                    throw new CompletionException(new NotEnoughPowerException());
                }

                // claim unclaimed
                for (Chunk c : unclaimedChunks) {
                    try {
                        factionService.claimChunk(claimingFaction.id(), c.getWorld(), c.getX(), c.getZ());
                    } catch (FactionDoesntExistException ignored) {}
                }

                return null;
            })
        )
        // add core
        .thenCompose((c) -> factionCoreStorage.setMetadata(block, claimingFaction.id()));
    }

    /**
     * Removes a faction core.
     * @param block The removed block.
     * @param remover The removing player.
     */
    public CompletableFuture<Void> removeCore(Block block, Player remover)
            throws InvalidCoreBlockException, WorldDisabledException,
            FactionPermissionsDeniedException, ExecutionException, InterruptedException {
        // check if the world is blocked
        if (worldService.isWorldDisabled(block.getWorld())) {
            throw new WorldDisabledException();
        }

        // get player's faction
        Faction faction = null;
        try {
            faction = factionService.getFaction(remover);
        } catch (FactionDoesntExistException ignored) {}

        // get core's faction
        String coreFaction = factionCoreStorage.getMetadata(block).get();
        if (coreFaction == null) {
            // not a core
            throw new InvalidCoreBlockException();
        }

        // check if core's faction == player faction
        if (faction != null && coreFaction.equals(faction.id())) {
            // check if player has unclaim perms
            try {
                if (!factionService.hasPlayerPermission(
                        faction.id(), remover, FactionPermission.UNCLAIM_CHUNK)) {
                    throw new FactionPermissionsDeniedException();
                }
            } catch (FactionDoesntExistException ignored) {
            }
        }

        // get chunks influenced by the destroyed core
        log.info("Removing a core for " + coreFaction);
        int centerChunkX = block.getChunk().getX();
        int centerChunkZ = block.getChunk().getZ();
        World world = block.getWorld();

        // calculate influences in all chunks affected
        CompletableFuture<Void>[] futures = new CompletableFuture[9];
        int f = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                // get factions influencing each chunk
                int finalI = i;
                int finalJ = j;
                futures[f] = getChunkInfluences(world, centerChunkX + i, centerChunkZ + j).thenApply(influences -> {
                    // if there are no influences, unclaim chunk
                    if (influences.containsKey(coreFaction)) {
                        int count = influences.get(coreFaction).decrementAndGet();
                        if (count == 0) {
                            influences.remove(coreFaction);
                        }
                    }
                    if (influences.size() == 0) {
                        // unclaim chunk
                        try {
                            factionService.unclaimChunk(coreFaction, world, centerChunkX + finalI, centerChunkZ + finalJ);
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
                        factionService.unclaimChunk(coreFaction, world, centerChunkX + finalI, centerChunkZ + finalJ);
                    } catch (FactionDoesntExistException e) {
                        throw new CompletionException(e);
                    }
                    try {
                        factionService.claimChunk(maxInfluence.getKey(), world, centerChunkX + finalI, centerChunkZ + finalJ);
                    } catch (FactionDoesntExistException e) {
                        throw new CompletionException(e);
                    }
                    return null;
                });
                f++;
            }
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * Calculates how many faction cores are protecting a given chunk.
     * Does not include the influence of a core inside of the chunk.
     * @return How many faction cores are protecting a given chunk.
     */
    public CompletableFuture<Map<String, AtomicInteger>> getChunkInfluences(World world, int chunkX, int chunkZ) {
        ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        CompletableFuture<Void>[] futures = new CompletableFuture[9];
        // check each neighboring chunk
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // get neighboring chunk
                ChunkInfo chunkInfo = new ChunkInfo(world.getName(), new ChunkCoordinates(chunkX + x, chunkZ + z));
                // count cores of the faction is chunk
                futures[i] = factionCoreStorage.getMetadataInChunk(chunkInfo)
                .thenAccept((map) -> {
                   if (map == null) {
                       // no faction cores in this chunk
                       return;
                   }

                   // increment faction cores
                    Collection<String> factions = map.values();
                    for (String f : factions) {
                        AtomicInteger factionCoreCount = counts
                                .computeIfAbsent(f, (s) -> new AtomicInteger());
                        factionCoreCount.incrementAndGet();
                    }
                });
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
                futures[i] = PaperLib.getChunkAtAsync(
                        core.getWorld(), centerX + x, centerZ + z)
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
    public CompletableFuture<Set<Chunk>> getUnclaimedChunksAround(Chunk chunk) {
        Set<Chunk> chunks = new HashSet<>();
        CompletableFuture[] futures = new CompletableFuture[9];
        // check each neighboring chunk
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // get neighboring chunk
                CompletableFuture<Void> countFuture = PaperLib.getChunkAtAsync(
                        chunk.getWorld(), chunk.getX() + x, chunk.getZ() + z)
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
