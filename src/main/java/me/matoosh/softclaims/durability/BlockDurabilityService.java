package me.matoosh.softclaims.durability;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.ChunkBusyException;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BlockDurabilityService {

    private final SoftClaimsPlugin plugin;

    /**
     * Durabilities of each block grouped by chunk.
     */
    private final HashMap<Chunk, HashMap<String, Double>> durabilities = new HashMap<>();
    /**
     * Chunks that are currently being loaded/persisted.
     */
    private final Set<Chunk> busyChunks =
            Collections.synchronizedSet(new HashSet<>());
    /**
     * Regions that are currently being loaded/persisted and their load futures.
     */
    private final Map<String, Deque<CompletableFuture<Void>>> busyRegions
            = Collections.synchronizedMap(new HashMap<>());

    public BlockDurabilityService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
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
        HashMap<String, Double> innerMap = durabilities.get(chunk);
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
        HashMap<String, Double> innerMap = durabilities.get(chunk);
        if(innerMap == null) return; // not present

        // check if last value
        if (innerMap.size() <= 1) {
            // remove inner map
            durabilities.remove(chunk);
            return;
        }

        // get position in inner map
        innerMap.remove(getBlockKeyInChunk(block));
    }

    /**
     * Schedules a region task.
     * Region tasks are executed asynchronously,
     * but only 1 task can run for a single region
     * at any time.
     * @param chunk Chunk which is in the region which we want to schedule a task for.
     * @param runnable The task to be scheduled.
     */
    private void scheduleRegionTask(Chunk chunk, Runnable runnable) {
        // check if region is busy
        String regionKey = getRegionKey(chunk);
        Deque<CompletableFuture<Void>> lastFutures = busyRegions.computeIfAbsent(
                regionKey, (k) -> new LinkedList<>());

        // schedule operation
        CompletableFuture<Void> newFuture;
        try {
            // append future
            CompletableFuture<Void> lastFuture = lastFutures.getLast();
            newFuture = lastFuture.thenRunAsync(runnable);
        } catch (NoSuchElementException exception) {
            // new future
            newFuture = CompletableFuture.runAsync(runnable);
        }
        lastFutures.add(newFuture);

        // clean up after task is done
        CompletableFuture<Void> finalNewFuture = newFuture;
        newFuture.thenRun(() -> {
            Deque<CompletableFuture<Void>> futures = busyRegions.get(regionKey);
            if (futures != null && futures.size() > 0) {
                futures.remove(finalNewFuture);
                if (futures.size() == 0) {
                    // remove future queue
                    busyRegions.remove(regionKey);
                }
            }
        });
    }

    /**
     * Persists all loaded block data.
     */
    public void persistAll() {
        for (Chunk chunk : durabilities.keySet()) {
            persistChunk(chunk, false);
        }
    }

    /**
     * Persists chunk durability data on disk.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    public void persistChunk(Chunk chunk, boolean unload) {
        // save chunk asynchronously
        scheduleRegionTask(chunk, () -> persistChunkSync(chunk, unload));
    }

    /**
     * Persists chunk durability data on disk synchronously.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    private void persistChunkSync(Chunk chunk, boolean unload) {
        // set busy
        busyChunks.add(chunk);

        // get chunk data
        Map<String, Double> innerMap;
        if(unload) {
            innerMap = durabilities.remove(chunk);
        } else {
            innerMap = durabilities.get(chunk);
        }

        // get appropriate file
        String regionFileName = getRegionFileName(chunk);
        File chunkConfigFile = new File(plugin.getDataFolder(), regionFileName);

        // create if not present
        YamlConfiguration chunkConfig;
        if (!chunkConfigFile.exists()) {
            if(innerMap == null) {
                // not busy
                markChunkNotBusy(chunk);
                return;
            }
            chunkConfig = new YamlConfiguration();
        } else {
            // load chunk's config
            chunkConfig = new YamlConfiguration();
            try {
                chunkConfig.load(chunkConfigFile);
            } catch (IOException | InvalidConfigurationException e) {
                // failed to load chunk data
                e.printStackTrace();
                // not busy
                markChunkNotBusy(chunk);
                return;
            }
        }

        // create new entry
        String chunkSection = getChunkKey(chunk);
        if(innerMap != null) {
            chunkConfig.createSection(chunkSection, innerMap);
        } else {
            chunkConfig.set(chunkSection, null);
        }

        // remove file if empty
        if(chunkConfig.getValues(false).size() == 0) {
            chunkConfigFile.delete();
        } else {
            // save
            try {
                chunkConfig.save(chunkConfigFile);
            } catch (IOException e) {
                // error saving chunk data
                e.printStackTrace();

                // not busy
                markChunkNotBusy(chunk);

                return;
            }
        }

        // not busy
        markChunkNotBusy(chunk);
    }

    /**
     * Loads chunk durability data into memory.
     * @param chunk The chunk.
     */
    public void loadChunk(Chunk chunk) {
        // load chunk asynchronously
        scheduleRegionTask(chunk, () -> loadChunkSync(chunk));
    }

    /**
     * Loads chunk durability data into memory synchronously.
     * @param chunk The chunk.
     */
    private void loadChunkSync(Chunk chunk) {
        // set busy
        busyChunks.add(chunk);

        // get appropriate file
        String regionFileName = getRegionFileName(chunk);

        // skip inexistent
        File chunkConfigFile = new File(plugin.getDataFolder(), regionFileName);
        if (!chunkConfigFile.exists()) {
            // not busy
            markChunkNotBusy(chunk);
            return;
        }

        // load chunks config
        YamlConfiguration chunkConfig = new YamlConfiguration();
        try {
            chunkConfig.load(chunkConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            // not busy
            markChunkNotBusy(chunk);
            return;
        }

        // load
        String chunkSection = getChunkKey(chunk);
        ConfigurationSection chunkSectionLoaded = chunkConfig.getConfigurationSection(chunkSection);
        if(chunkSectionLoaded != null) {
            HashMap<String, Double> innerMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : chunkSectionLoaded.getValues(false).entrySet()) {
                innerMap.put(entry.getKey(), (double)(entry.getValue()));
            }
            plugin.getLogger().info("Loaded " + innerMap.size() + " durabilities at: " + chunk.toString());
            durabilities.put(chunk, innerMap);
        }

        // not busy
        markChunkNotBusy(chunk);
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
     * Marks a chunk as no longer busy.
     * @param chunk The chunk.
     */
    private void markChunkNotBusy(Chunk chunk) {
        busyChunks.remove(chunk);
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
     * Get file name under which a region file should be saved.
     * @param chunk The chunk in the saved region.
     * @return The file name.
     */
    public String getRegionFileName(Chunk chunk) {
        return chunk.getWorld().getName() + "_" + getRegionKey(chunk) + ".yml";
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
}
