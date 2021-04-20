package me.matoosh.softclaims.durability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.async.AsyncFiles;
import me.matoosh.softclaims.exception.ChunkBusyException;
import org.bukkit.Chunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Service managing block durability storage on disk.
 */
public class BlockDurabilityStorageService {

    /**
     * Plugin reference.
     */
    private final SoftClaimsPlugin plugin;

    /**
     * Durabilities of each block grouped by chunk.
     */
    private final Map<Chunk, Map<String, Double>> durabilities
            = new HashMap<>();

    /**
     * Regions that are currently being loaded/persisted and their load futures.
     */
    private final Map<Path, RegionTask> busyRegions = new HashMap<>();

    /**
     * Chunks that are currently being loaded/persisted.
     */
    private final Set<Chunk> busyChunks = new HashSet<>();

    /**
     * Chunks which have had their durabilities modified since they were loaded.
     */
    private Set<Chunk> dirtyChunks = new HashSet<>();

    /**
     * YAML data file mapper.
     */
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public BlockDurabilityStorageService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;

        // ensure durabilities folder exists
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
     * Checks whether there are durabilities stored for a given chunk.
     * @param chunk The chunk to check.
     * @return Whethrer there are durabilities stored for the given chunk.
     * @throws ChunkBusyException thrown if the chunk is busy.
     */
    public boolean hasDurabilitiesForChunk(Chunk chunk)
            throws ChunkBusyException {
        if (isChunkBusy(chunk)) throw new ChunkBusyException();
        return durabilities.containsKey(chunk);
    }

    /**
     * Removes all durabilities stored for a given chunk.
     * @param chunk The chunk to remove durabilities from.
     * @throws ChunkBusyException thrown if the chunk is busy.
     */
    public void removeDurabilitiesForChunk(Chunk chunk)
            throws ChunkBusyException {
        if (isChunkBusy(chunk)) throw new ChunkBusyException();
        durabilities.remove(chunk);
        dirtyChunks.add(chunk);
    }

    /**
     * Gets durabilities of blocks in a chunk to be modified.
     * Sets the chunk as dirty.
     * @param chunk The chunk in which the durabilities are.
     * @return Map of durabilities.
     * @throws ChunkBusyException thrown if the chunk is busy.
     */
    public Map<String, Double> modifyDurabilitiesInChunk(Chunk chunk)
            throws ChunkBusyException {
        if (isChunkBusy(chunk)) throw new ChunkBusyException();
        Map<String, Double> data = durabilities.computeIfAbsent(chunk, k -> new HashMap<>());
        dirtyChunks.add(chunk);
        return data;
    }

    /**
     * Gets durabilities of blocks in a chunk.
     * @param chunk The chunk in which the durabilities are.
     * @return Map of durabilities.
     * @throws ChunkBusyException thrown if the chunk is busy.
     */
    public Map<String, Double> getDurabilitiesInChunk(Chunk chunk)
            throws ChunkBusyException {
        if (isChunkBusy(chunk)) throw new ChunkBusyException();
        return durabilities.get(chunk);
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
            <Void, ? extends CompletableFuture<Void>> task) {
        // get region task
        Path regionFile = getRegionFile(chunk);
        RegionTask regionTask = busyRegions.computeIfAbsent(regionFile, k -> new RegionTask());

        // cancel last region cleanup task
        if (regionTask.getCleanupTask() != null) {
            regionTask.getCleanupTask().cancel(true);
        }

        // set this future as the latest future for the region
        // schedule operation
        CompletableFuture<Void> newTask;
        if (regionTask.getTask() != null) {
            // append future and run it on the same tread as last one
            newTask = regionTask.getTask().thenCompose(task);
        } else {
            // first future in the chain
//            plugin.getLogger().info("New session - " + regionFile.toString());
            newTask = task.apply(null);
        }

        // append clean up task after the task is done
        CompletableFuture<Boolean> cleanupTask = newTask.thenApplyAsync((t) -> {
            // delay
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        });
        CompletableFuture<Void> completeTask = cleanupTask
            .exceptionally((e) -> {
                if (e instanceof CancellationException) {
                    return false;
                }
                throw new CompletionException(e);
            })
            .thenApply((lastInChain) -> {
                // check if should continue
                if (!lastInChain) {
                    return null;
                }

                // there is no task scheduled after this one for now because this one wasnt canceled
//                plugin.getLogger().info("Commit session - " + regionFile.toString());
                busyRegions.remove(regionFile);
                // clear the region record and commit if the buffer was modified
                if (regionTask.isDirty()) {
                    return regionTask.getBuffer();
                } else {
                    return null;
                }
            })
            .thenCompose((data) -> data != null
            ? writeRegionData(regionFile, data) : CompletableFuture.completedFuture(null));

        // set new task
        regionTask.setTask(newTask);
        regionTask.setCleanupTask(cleanupTask);

        return completeTask;
    }

    /**
     * Reads region durability data.
     * @param regionFile Path to the region file.
     * @return Map of region durability data.
     */
    private CompletableFuture<Map<String, Map<String, Double>>> readRegionData(Path regionFile) {
        // no file to read
        if (regionFile == null) {
            return CompletableFuture.completedFuture(null);
        }

        // check if region is buffered
        RegionTask task = busyRegions.get(regionFile);
        if (task != null && task.isBuffered()) {
            return CompletableFuture.completedFuture(task.getBuffer());
        }

        // check if file exists
        if (!Files.exists(regionFile)) {
            return CompletableFuture.completedFuture(null);
        }

        // read file text
        return AsyncFiles.readAll(regionFile, 1024)
                .thenApply(content -> {
                    try {
                        // parse file
                        Map<String, Map<String, Double>> data = mapper.readValue(
                                content, new TypeReference<Map<String, Map<String, Double>>>(){});

                        // buffer data
                        if (task != null) {
                            task.setBuffer(data);
                            task.setBuffered(true);
                        }

                        return data;
                    } catch (JsonProcessingException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    /**
     * Writes the region data to the buffer.
     * Buffered region data will be written to disk at the end of each region chain.
     * @param regionFile Path to the region file.
     * @return The number of bytes that were written to disk.
     */
    private CompletableFuture<Void> bufferRegionData(
            Path regionFile, Map<String, Map<String, Double>> data) {
        RegionTask regionTask = busyRegions.get(regionFile);
        if (regionTask != null) {
            regionTask.setBuffer(data);
            regionTask.setBuffered(true);
            regionTask.setDirty(true);
            return CompletableFuture.completedFuture(null);
        } else {
            return writeRegionData(regionFile, data);
        }
    }

    /**
     * Writes region data to disk.
     * @param regionFile Path to the region file.
     * @return The number of bytes that were written to disk.
     */
    private CompletableFuture<Void> writeRegionData(
            Path regionFile, Map<String, Map<String, Double>> data) {
//        plugin.getLogger().info("Writing region data: " + regionFile);

        // remove old file to overwrite
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.deleteIfExists(regionFile);
            } catch (IOException ignored) {
                return false;
            }
        }).thenApply((s) -> {
            // serialize to yaml
            try {
                return mapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                throw new CompletionException(e);
            }
        }).thenCompose((content) -> content != null
            ? AsyncFiles.writeBytes(regionFile, content.getBytes(),
            StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).thenApply((s) -> null)
            : CompletableFuture.completedFuture(null));
    }

    /**
     * Persists chunk durability data on disk.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    public CompletableFuture<Void> persistChunk(Chunk chunk, boolean unload) {
        // check if chunk dirty
        if (!dirtyChunks.contains(chunk)) {
            return CompletableFuture.completedFuture(null);
        }
        // save chunk asynchronously
        return scheduleRegionTask(chunk, (s) -> persistChunkAsync(chunk, unload));
    }

    /**
     * Persists chunk durability data on disk asynchronously.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    private CompletableFuture<Void> persistChunkAsync(Chunk chunk, boolean unload) {
        return CompletableFuture.supplyAsync(() -> {
            // set busy
            busyChunks.add(chunk);

            // get appropriate file
            return getRegionFile(chunk);
        })
            .thenCompose(this::readRegionData)
            .exceptionally((e) ->{
                // error reading file
                // possibly doesnt exist
                e.printStackTrace();
                return null;
            })
            .thenApply((data) -> {
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
                    if (chunkDurabilities == null || chunkDurabilities.size() == 0) {
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
            })
            .thenCompose((data) -> bufferRegionData(getRegionFile(chunk), data))
            .thenRun(() -> {
                // not busy
                busyChunks.remove(chunk);
                // no dirty
                dirtyChunks.remove(chunk);
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
                .exceptionally((e) -> {
                    // error loading chunk data
                    // not busy
                    e.printStackTrace();
                    return null;
                })
                .thenAccept((data) -> {
                    // check if load was successful
                    if (data == null) {
                        busyChunks.remove(chunk);
                        return;
                    }

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
     * @return Path to the durabilities folder.
     */
    public Path getDurabilitiesFolder() {
        return plugin.getDataFolder().toPath().resolve("data");
    }

    /**
     * Represents a busy region.
     */
    public static class RegionTask {
        private CompletableFuture<Void> task;
        private CompletableFuture<Boolean> cleanupTask;
        private Map<String, Map<String, Double>> buffer;
        private boolean dirty;
        private boolean buffered;

        public RegionTask() {
            this.buffered = false;
        }

        public CompletableFuture<Void> getTask() {
            return task;
        }

        public void setTask(CompletableFuture<Void> task) {
            this.task = task;
        }

        public Map<String, Map<String, Double>> getBuffer() {
            return buffer;
        }

        public void setBuffer(Map<String, Map<String, Double>> buffer) {
            this.buffer = buffer;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isBuffered() {
            return buffered;
        }

        public void setBuffered(boolean buffered) {
            this.buffered = buffered;
        }

        public CompletableFuture<Boolean> getCleanupTask() {
            return cleanupTask;
        }

        public void setCleanupTask(CompletableFuture<Boolean> cleanupTask) {
            this.cleanupTask = cleanupTask;
        }
    }
}
