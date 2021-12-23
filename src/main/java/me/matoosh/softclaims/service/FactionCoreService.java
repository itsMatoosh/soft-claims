package me.matoosh.softclaims.service;

import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.ChunkCoordinates;
import me.matoosh.blockmetadata.ChunkInfo;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldDisabledException;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.exception.faction.FactionPermissionsDeniedException;
import me.matoosh.softclaims.exception.faction.NotEnoughPowerException;
import me.matoosh.softclaims.exception.faction.core.ChunkClaimedByOtherFactionException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import me.matoosh.softclaims.exception.faction.core.MultipleCoresInChunkException;
import me.matoosh.softclaims.faction.Faction;
import me.matoosh.softclaims.faction.FactionPermission;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Getter
@Log
public class FactionCoreService {
    private final SoftClaimsPlugin plugin;
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
        // claim chunks around the core
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
                double powerDeficiency = claims.size() + unclaimedChunks.size() - claimingFaction.power();
                if (powerDeficiency > 0) {
                    throw new CompletionException(new NotEnoughPowerException(powerDeficiency));
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
        .thenCompose((c) -> factionCoreStorage.setMetadata(block, claimingFaction.id()))
        // spawn crystal and do effects
        .thenAccept((c) -> {
            // get location
            Location location = block.getLocation();
            World world = location.getWorld();

            // run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // spawn crystal
                EnderCrystal crystal = (EnderCrystal) world.spawnEntity(
                        location.add(0.5, 1.5, 0.5), EntityType.ENDER_CRYSTAL);
                crystal.setShowingBottom(false);
                crystal.setPersistent(true);
                crystal.setCustomName(claimingFaction.name() + "'s Core");

                // TODO: spawn particles
            });

        });
    }

    /**
     * Removes a faction core as a player.
     * @param block The core block.
     * @param remover The removing player.
     */
    public CompletableFuture<Void> removeCoreAsPlayer(Block block, Player remover)
            throws InvalidCoreBlockException, FactionPermissionsDeniedException,
            WorldDisabledException, ExecutionException, InterruptedException {
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

        // remove core
        return removeCore(block);
    }

    /**
     * Removes a faction core.
     * @param block The core block.
     */
    public CompletableFuture<Void> removeCore(Block block)
            throws InvalidCoreBlockException, ExecutionException, InterruptedException {
        // get core's faction
        String coreFaction = factionCoreStorage.getMetadata(block).get();
        if (coreFaction == null) {
            // not a core
            throw new InvalidCoreBlockException();
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
                ChunkInfo chunkInfo = new ChunkInfo(world.getName(),
                        new ChunkCoordinates(centerChunkX + i, centerChunkZ + j));
                CompletableFuture<Map<String, AtomicInteger>> influencesFuture = getChunkInfluences(chunkInfo);
                CompletableFuture<ChunkCoordinates> coordinatesFuture
                        = CompletableFuture.completedFuture(chunkInfo.getCoordinates());

                // update faction claims
                futures[f] = influencesFuture.thenAcceptBoth(coordinatesFuture, (influences, coordinates) -> {
                    // if there are no influences, unclaim chunk
                    if (influences.containsKey(coreFaction)) {
                        // remove one point of influence from this chunk bc we are removing a core
                        int count = influences.get(coreFaction).decrementAndGet();
                        if (count == 0) {
                            influences.remove(coreFaction);
                        }
                    }
                    if (influences.size() == 0) {
                        // unclaim chunk
                        try {
                            factionService.unclaimChunk(coreFaction, world, coordinates.getX(), coordinates.getZ());
                        } catch (FactionDoesntExistException e) {
                            throw new CompletionException(e);
                        }
                        return;
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
                        return;
                    }

                    // make the most influential faction take the chunk
                    // unclaim chunk
                    try {
                        factionService.unclaimChunk(coreFaction, world, coordinates.getX(), coordinates.getZ());
                    } catch (FactionDoesntExistException e) {
                        throw new CompletionException(e);
                    }
                    try {
                        factionService.claimChunk(maxInfluence.getKey(), world, coordinates.getX(), coordinates.getZ());
                    } catch (FactionDoesntExistException e) {
                        throw new CompletionException(e);
                    }
                });
                f++;
            }
        }
        // wait for all the claims and metadata to be removed
        return CompletableFuture.allOf(futures)
        // ensure that the core metadata is removed
        .thenCompose(s -> factionCoreStorage.removeMetadata(block))
        // remove crystal and show effects
        .thenAccept((c) -> {
            // get location
            Location location = block.getLocation();

            // run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // remove the crystal
                world.getNearbyEntities(location, 1, 2, 1)
                    .stream().filter(entity -> entity instanceof EnderCrystal)
                    .forEach(Entity::remove);

                // remove core block
                block.breakNaturally();
            });
        });
    }

    /**
     * Calculates how many faction cores are protecting a given chunk.
     * Includes the core in the chunk.
     * @return How many faction cores are protecting a given chunk.
     */
    public CompletableFuture<Map<String, AtomicInteger>> getChunkInfluences(ChunkInfo chunkInfo) {
        ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        CompletableFuture<Void>[] futures = new CompletableFuture[9];
        // check each neighboring chunk
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // get neighboring chunk
                ChunkInfo neighboringInfo = new ChunkInfo(chunkInfo.getWorld(),
                        new ChunkCoordinates(chunkInfo.getCoordinates().getX() + x, chunkInfo.getCoordinates().getZ() + z));
                // count cores of the faction is chunk
                futures[i] = factionCoreStorage.getMetadataInChunk(neighboringInfo)
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
    public ChunkInfo[] getInfluencedChunks(Block core) {
        // get center chunk coords
        int centerX = core.getX() / 16;
        int centerZ = core.getZ() / 16;

        // get chunks around center
        ChunkInfo[] chunks = new ChunkInfo[9];
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                chunks[i] = new ChunkInfo(core.getWorld().getName(),
                        new ChunkCoordinates(centerX + x, centerZ + z));
                i++;
            }
        }
        return chunks;
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

    /**
     * Clears and destroys faction cores in a chunk.
     * @param chunk
     * @return
     */
    public CompletableFuture<Void> clearAndDestroyCoresInChunk(Chunk chunk) {
        return factionCoreStorage.getMetadataInChunk(ChunkInfo.fromChunk(chunk))
        .thenAccept(cores -> cores.keySet().forEach(key -> {
            String[] position = key.split(",");

            // break down blocks
            Block block = chunk.getBlock(Integer.parseInt(position[0]),
                    Integer.parseInt(position[1]),
                    Integer.parseInt(position[2]));

            // remove core
            try {
                removeCore(block);
            } catch (InvalidCoreBlockException | ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }
}
