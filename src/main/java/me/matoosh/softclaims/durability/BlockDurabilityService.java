package me.matoosh.softclaims.durability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.ChunkBusyException;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.javaync.io.AsyncFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class BlockDurabilityService {

    private final SoftClaimsPlugin plugin;

    /**
     * Durabilities of each block grouped by chunk.
     */
    private final ConcurrentMap<Chunk, Map<String, Double>> durabilities
            = new ConcurrentHashMap<>();
    /**
     * Chunks that are currently being loaded/persisted.
     */
    private final Set<Chunk> busyChunks =
            Collections.synchronizedSet(new HashSet<>());
    /**
     * Regions that are currently being loaded/persisted and their load futures.
     */
    private final ConcurrentMap<String, CompletableFuture<Void>> busyRegions
            = new ConcurrentHashMap<>();

    /**
     * Chain of futures used for loading/persisting regions.
     */
    private final Set<CompletableFuture<Void>> regionFutureChain =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * YAML data file mapper.
     */
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public BlockDurabilityService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    /**
     * Initialize the data.
     */
    private void initialize() {
        Path dataPath = getDurabilitiesFolder();
        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectory(dataPath);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Set durability of a block.
     * @param block The block.
     * @param durability The new durability.
     */
    public void setDurabilityAbsolute(Block block, int durability)
            throws ChunkBusyException {
        setDurabilityRelative(block, getDurabilityRelative(block, durability));
    }

    /**
     * Set durability percentage of a block.
     * @param block The block.
     * @param durability Durability between 0 and 1.
     */
    public void setDurabilityRelative(Block block, double durability)
            throws ChunkBusyException {
        // check if value is correct
        if (!Double.isFinite(durability)) return;

        // check if chunk busy
        if (isChunkBusy(block.getChunk())) {
            throw new ChunkBusyException();
        }

        // set durability to be in 0 - 1 range.
        if (durability <= 0 || durability >= 1) {
            clearDurability(block);
        } else {
            // check if block durable
            if (!hasDurability(block)) {
                clearDurability(block);
                return;
            }

            durabilities.computeIfAbsent(
                    block.getChunk(), k -> new HashMap<>())
                    .put(getBlockKeyInChunk(block), durability);
        }
    }

    /**
     * Modifies all durabilities in a chunk by a delta.
     * Relatively expensive operation.
     * @param chunk The chunk.
     * @param delta The change in durabilities.
     * @return List of modified durability keys.
     */
    public List<String> modifyDurabilitiesInChunk(Chunk chunk, int delta)
            throws ChunkBusyException {
        // check if delta is correct
        if (!Double.isFinite(delta)) return Collections.emptyList();

        // check if chunk busy
        if (isChunkBusy(chunk)) {
            throw new ChunkBusyException();
        }

        // get chunk inner map
        Map<String, Double> innerMap = durabilities.get(chunk);
        if (innerMap == null) return Collections.emptyList();

        // modify all durabilities
        List<String> modified = new ArrayList<>(innerMap.entrySet().size());
        Iterator<Map.Entry<String, Double>> iter = innerMap.entrySet().iterator();
        while (iter.hasNext()) {
            // get current durability
            Map.Entry<String, Double> entry = iter.next();
            String[] posUnparsed = entry.getKey().split(",");
            Block block = chunk.getBlock(
                    Integer.parseInt(posUnparsed[0]),
                    Integer.parseInt(posUnparsed[1]),
                    Integer.parseInt(posUnparsed[2])
            );
            int durability = getAbsoluteDurability(block, entry.getValue());

            // get new durability
            double newDurability = getDurabilityRelative(block, durability + delta);

            // clear if outside of range
            if (newDurability <= 0 || newDurability >= 1) {
                iter.remove();
                continue;
            }

            // update durability
            entry.setValue(newDurability);
            modified.add(entry.getKey());
        }

        return modified;
    }

    /**
     * Counts damaged blocks in chunk.
     * @param chunk The chunk.
     * @return The number of damaged blocks in the chunk.
     * @throws ChunkBusyException thrown if the chunk is busy.
     */
    public int countDamagedInChunk(Chunk chunk) throws ChunkBusyException {
        // check if chunk busy
        if (isChunkBusy(chunk)) {
            throw new ChunkBusyException();
        }

        // get chunk inner map
        Map<String, Double> innerMap = durabilities.get(chunk);
        if (innerMap == null) {
            return 0;
        } else {
            return innerMap.size();
        }
    }

    /**
     * Get durability of a block.
     * @param block The block.
     * @return Current durability of the block.
     */
    public int getDurabilityAbsolute(Block block) throws ChunkBusyException {
        double durability = getDurabilityRelative(block);
        if (durability == 0) {
            return 0;
        } else {
            return getAbsoluteDurability(block, durability);
        }
    }

    /**
     * Get durability of a block.
     * @param block The block.
     * @return Current durability of the block.
     */
    public double getDurabilityRelative(Block block) throws ChunkBusyException {
        if (block == null) return 0d;

        // check if block has durability
        if (!hasDurability(block)) {
            return 0d;
        }

        // check if chunk busy
        if (isChunkBusy(block.getChunk())) {
            throw new ChunkBusyException();
        }

        // get chunk
        Chunk chunk = block.getChunk();
        Map<String, Double> innerMap = durabilities.get(chunk);
        if (innerMap == null) {
            // no data stored for the chunk
            // default to total durability
            return 1d;
        }

        // get position in the inner map
        Double durability = innerMap.get(getBlockKeyInChunk(block));
        if (durability == null) {
            // no data stored for the block
            // default to total durability
            return 1d;
        }
        return durability;
    }

    /**
     * Gets relative durability of a block
     * given its absolute durability.
     * @param block The block.
     * @param durability The absolute durability of the block.
     * @return The relative durability of the block.
     */
    public double getDurabilityRelative(Block block, int durability) {
        return (double) durability /
                (double) getTotalDurability(block.getType());
    }

    /**
     * Gets absolute durability of a block
     * given its relative durability.
     * @param block The block.
     * @param durability The relative durability of the block, from 0 to 1.
     * @return The absolute durability of a block.
     */
    public int getAbsoluteDurability(Block block, double durability) {
        return (int) (durability * getTotalDurability(block.getType()));
    }

    /**
     * Gets the total durability of a material.
     * Total durability is the maximum durability of the material defined in the config.
     * @param material The block.
     * @return The total durability of a block.
     */
    public int getTotalDurability(Material material) {
        if (material == null) return 0;
        return plugin.getConfig().getInt("blocks."
                + material.name() + ".durability", 0);
    }

    /**
     * Gets whether the given durable block should drop when broken.
     * @param block The block.
     * @return Whether the block should drop when broken.
     */
    public boolean getBlockDrops(Block block) {
        return plugin.getConfig().getBoolean("blocks."
                + block.getType().name() + ".drop", false);
    }

    /**
     * Gets whether the block has durability.
     * @param block The block.
     * @return Whether the block has durability.
     */
    public boolean hasDurability(Block block) {
        // check if this world is disabled
        if (plugin.getConfig().getStringList("disabledWorlds")
                .contains(block.getWorld().getName())) {
            return false;
        }
        // check if block has durability set
        if (!plugin.getConfig().contains("blocks." + block.getType().name() + ".durability")) {
            return false;
        }
        // check if block is in a faction chunk
        if (!plugin.getFactionService().isInFactionLand(block.getChunk())) {
            return false;
        }

        return true;
    }

    /**
     * Clears durability of a block.
     * @param block The block.
     */
    public void clearDurability(Block block) {
        // get chunk
        Chunk chunk = block.getChunk();
        Map<String, Double> innerMap = durabilities.get(chunk);
        if (innerMap == null) return; // not present

        // get position in inner map
        innerMap.remove(getBlockKeyInChunk(block));

        // check if last value
        if (innerMap.size() < 1) {
            // remove inner map
            durabilities.remove(chunk);
        }
    }

    /**
     * Schedules a region task.
     * Region tasks are executed asynchronously,
     * but only 1 task can run for a single region
     * at any time.
     * @param chunk Chunk which is in the region which we want to schedule a task for.
     * @param task The task to be scheduled.
     * @return Future of the task.
     */
    private CompletableFuture<Void> scheduleRegionTask(Chunk chunk, java.util.function.Function
            <Void, ? extends java.util.concurrent.CompletableFuture<Void>> task) {
        // check if region is busy
        String regionKey = getRegionKey(chunk);
        CompletableFuture<Void> lastFuture = busyRegions.get(regionKey);

        // schedule operation
        CompletableFuture<Void> newFuture;
        if (lastFuture != null) {
            // append future
            newFuture = lastFuture.thenComposeAsync(task);

            // add last future as chained
            regionFutureChain.add(lastFuture);
        } else {
            // first future in the chain
            newFuture = task.apply(null);
        }
        // set this future as the latest future for the region
        busyRegions.put(regionKey, newFuture);

        // append clean up task after the task is done
        CompletableFuture<Void> finalNewFuture = newFuture;
        newFuture.thenRunAsync(() -> {
            if (regionFutureChain.contains(finalNewFuture)) {
                // there is the next future chained after this one
                // only remove chain reference
                regionFutureChain.remove(finalNewFuture);
            } else {
                // there is no task scheduled after this one for now
                // clear the region record
                busyRegions.remove(regionKey);
            }
        });

        return newFuture;
    }

    /**
     * Reads region durability data.
     * @param regionFile Path to the region file.
     * @return Map of region durability data.
     */
    private CompletableFuture<Map<String, Map<String, Double>>> readRegionData(Path regionFile) {
        // read file text
        return AsyncFiles.readAll(regionFile).thenApplyAsync(content -> {
            // parse file
            try {
                return mapper.readValue(
                        content, new TypeReference<Map<String, Map<String, Double>>>(){});
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Reads region durability data.
     * @param regionFile Path to the region file.
     * @return Map of region durability data.
     */
    private CompletableFuture<Integer> writeRegionData(
            Path regionFile, Map<String, Map<String, Double>> data) {
        // remove old file to overwrite
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.deleteIfExists(regionFile);
            } catch (IOException ignored) {
                return false;
            }
        }).thenApplyAsync((s) -> {
            // serialize to yaml
            if (data == null) return null;
            try {
                return mapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                throw new CompletionException(e);
            }
        })
        .thenCompose((content) -> content != null
                ? AsyncFiles.writeBytes(regionFile, content.getBytes(),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
                : CompletableFuture.completedFuture(0));
    }

    /**
     * Persists all loaded block data.
     */
    public CompletableFuture<Void> persistAll() {
        return CompletableFuture.allOf(
                durabilities.keySet().stream()
                        .map((chunk -> persistChunk(chunk, false)))
                        .toArray(CompletableFuture[]::new)
        );
    }

    /**
     * Persists chunk durability data on disk.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    public CompletableFuture<Void> persistChunk(Chunk chunk, boolean unload) {
        // save chunk asynchronously
        return scheduleRegionTask(chunk, (s) -> persistChunkAsync(chunk, unload));
    }

    /**
     * Persists chunk durability data on disk asynchronously.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    private CompletableFuture<Void> persistChunkAsync(Chunk chunk, boolean unload) {
        // set busy
        busyChunks.add(chunk);

        // get region path
        Path regionFilePath = getRegionFile(chunk);

        // read current region data
        return readRegionData(regionFilePath)
        .exceptionallyAsync((e) ->{
            // error reading file
            // possibly doesnt exist
            return null;
        })
        .thenApplyAsync((data) -> {
            // get chunk data
            Map<String, Double> chunkDurabilities;
            if(unload) {
                chunkDurabilities = durabilities.remove(chunk);
            } else {
                chunkDurabilities = durabilities.get(chunk);
            }

            // update data
            String chunkSection = getChunkKey(chunk);
            if (data != null) {
                // durabilities already stored for this region, append
                if (chunkDurabilities == null) {
                    // no durabilities to save for this chunk, remove entry from region
                    data.remove(chunkSection);
                } else {
                    // update durabilities for this chunk
                    data.put(chunkSection, chunkDurabilities);
                }
            } else if (chunkDurabilities != null) {
                // no durabilities stored for this region yet, create new map
                data = new HashMap<>();
                data.put(chunkSection, chunkDurabilities);
            }

            // delete empty region files
            if (data != null && data.size() == 0) {
                data = null;
            }

            return data;
        }).thenCompose((data) -> writeRegionData(regionFilePath, data))
        .exceptionallyAsync((e) -> {
            // error writing chunk data
            // not busy
            e.printStackTrace();
            return null;
        })
        .thenAcceptAsync((data) -> {
            // not busy
            busyChunks.remove(chunk);
        });
    }

    /**
     * Loads chunk durability data into memory.
     * @param chunk The chunk.
     */
    public CompletableFuture<Void> loadChunk(Chunk chunk) {
        // load chunk asynchronously
        return scheduleRegionTask(chunk, (s) -> loadChunkAsync(chunk));
    }

    /**
     * Loads chunk durability data into memory synchronously.
     * @param chunk The chunk.
     */
    private CompletableFuture<Void> loadChunkAsync(Chunk chunk) {
        return CompletableFuture.supplyAsync(() ->{
            // set busy
            busyChunks.add(chunk);

            // get appropriate file
            return getRegionFile(chunk);
        })
        .thenCompose(this::readRegionData)
        .exceptionallyAsync((e) -> {
            // error loading chunk data
            // not busy
            busyChunks.remove(chunk);
            return null;
        })
        .thenAcceptAsync((data) -> {
            // check if load was successful
            if (data == null) return;

            // load
            String chunkSection = getChunkKey(chunk);
            Map<String, Double> chunkData = data.get(chunkSection);
            if (chunkData != null) {
                durabilities.put(chunk, chunkData);
            }

            // not busy
            busyChunks.remove(chunk);
        });
    }

    /**
     * Gets a list of damaged blocks within a chunk.
     * @param chunk The chunk.
     * @return A list of damaged blocks within the chunk.
     * @throws ChunkBusyException Thrown if the chunks is busy.
     */
    public List<DamagedBlock> getDamagedBlocksInChunk(Chunk chunk)
            throws ChunkBusyException {
        // check if chunk is busy
        if (isChunkBusy(chunk)) throw new ChunkBusyException();
        // get damaged blocks in chunk
        Map<String, Double> damagedInChunk = durabilities.get(chunk);
        if (damagedInChunk == null) return Collections.emptyList();
        // get a list of damaged blocks
        return damagedInChunk.entrySet().parallelStream().map((entry) -> {
            String[] position = entry.getKey().split(",");
            return new DamagedBlock(
                    chunk.getBlock(Integer.parseInt(position[0]),
                            Integer.parseInt(position[1]),
                            Integer.parseInt(position[2])),
                    entry.getValue()
            );
        }).collect(Collectors.toList());
    }

    /**
     * Damaged block information.
     */
    public class DamagedBlock {
        private final Block block;
        private final double durability;

        public DamagedBlock(Block block, double durability) {
            this.block = block;
            this.durability = durability;
        }

        public Block getBlock() {
            return block;
        }

        public double getDurability() {
            return durability;
        }
    }

    /**
     * Clears all durability data in a chunk.
     * @param chunk The chunk.
     */
    public void clearDurabilitiesInChunk(Chunk chunk)
            throws ChunkBusyException {
        // check if chunk is busy
        if (isChunkBusy(chunk)) throw new ChunkBusyException();
        durabilities.remove(chunk);
    }

    /**
     * Gets a key unique for a block in a chunk.
     * @param block The block for which to get the key.
     * @return The key.
     */
    public String getBlockKeyInChunk(Block block) {
        Chunk chunk = block.getChunk();
        return getBlockKeyInChunk(
                block.getX() - chunk.getX() * 16,
                block.getY(),
                block.getZ() - chunk.getZ() * 16);
    }

    /**
     * Gets a key unique for a block in a chunk.
     * @param x X coordinate of the block relative to the chunk.
     * @param y Y coordinate of the block.
     * @param z Z coordinate of the block relative to the chunk.
     * @return The key.
     */
    public String getBlockKeyInChunk(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Get a key unique to a region in which a chunk is located.
     * @param chunk The chunk in the region.
     * @return The region key.
     */
    public String getRegionKey(Chunk chunk) {
        return (chunk.getX() / 16) + "_" + (chunk.getZ() / 16);
    }

    /**
     * Get a key unique to a chunk within a durabilities region file.
     * @param chunk The chunk.
     * @return The chunk key.
     */
    public String getChunkKey(Chunk chunk) {
        return chunk.getX() + "," + chunk.getZ();
    }

    /**
     * Checks whether the specified chunk is busy.
     * A chunk is busy if it's being loaded/unloaded.
     * @param chunk The chunk.
     * @return Whether the chunk is busy.
     */
    public boolean isChunkBusy(Chunk chunk) {
        return busyChunks.contains(chunk);
    }

    /**
     * Get file name under which a region file should be saved.
     * @param chunk The chunk in the saved region.
     * @return The file name.
     */
    public Path getRegionFile(Chunk chunk) {
        return getDurabilitiesFolder().resolve(chunk.getWorld().getName() + "_" + getRegionKey(chunk) + ".yml");
    }

    /**
     * Get path to the durabilities folder.
     * @return
     */
    public Path getDurabilitiesFolder() {
        return plugin.getDataFolder().toPath().resolve("data");
    }
}
