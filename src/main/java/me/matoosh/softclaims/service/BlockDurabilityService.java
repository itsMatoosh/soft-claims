package me.matoosh.softclaims.service;

import lombok.Data;
import lombok.Getter;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Getter
public class BlockDurabilityService {

    private final SoftClaimsPlugin plugin;

    /**
     * Reference to the durabilities storage service.
     */
    private final BlockMetadataStorage<Double> durabilityStorage;

    public BlockDurabilityService(SoftClaimsPlugin plugin) {
        // save plugin reference
        this.plugin = plugin;

        // get data folder
        Path durabilitiesDataDir = plugin.getDataFolder().toPath()
                .resolve("data").resolve("durabilities");

        // create durabilities storage
        this.durabilityStorage = new BlockMetadataStorage<>(plugin, durabilitiesDataDir);
    }

    /**
     * Set durability of a block.
     * @param block The block.
     * @param durability The new durability.
     */
    public void setDurabilityAbsolute(Block block, int durability)
            throws ChunkBusyException, ChunkNotLoadedException {
        setDurabilityRelative(block, getDurabilityRelative(block, durability));
    }

    /**
     * Set durability percentage of a block.
     * @param block The block.
     * @param durability Durability between 0 and 1.
     */
    public void setDurabilityRelative(Block block, double durability)
            throws ChunkBusyException, ChunkNotLoadedException {
        // check if value is correct
        if (!Double.isFinite(durability)) return;

        // set durability to be in 0 - 1 range.
        if (durability <= 0 || durability >= 1) {
            clearDurability(block);
        } else {
            durabilityStorage.setMetadata(block, durability);
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
            throws ChunkBusyException, ChunkNotLoadedException {
        // check if delta is correct
        if (!Double.isFinite(delta)) return Collections.emptyList();

        // check if there are any durabilities in chunk
        if (!durabilityStorage.hasMetadataForChunk(chunk)) {
            return Collections.emptyList();
        }

        // get chunk inner map
        Map<String, Double> durabilities = durabilityStorage.modifyMetadataInChunk(chunk);

        // modify all durabilities
        List<String> modified = new ArrayList<>(durabilities.entrySet().size());
        Iterator<Map.Entry<String, Double>> iter = durabilities.entrySet().iterator();
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
     * Get durability of a block.
     * @param block The block.
     * @return Current durability of the block.
     */
    public int getDurabilityAbsolute(Block block)
            throws ChunkBusyException, ChunkNotLoadedException {
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
    public double getDurabilityRelative(Block block)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (block == null) return 0d;

        // check if block has durability
        if (!hasDurability(block)) {
            return 0d;
        }

        // get block durability
        Double durability = durabilityStorage.getMetadata(block);
        if (durability == null) {
            // no data stored for the block
            // default to total durability
            return 1d;
        }
        return durability;
    }

    /**
     * Gets whether the block has durability.
     * @param block The block.
     * @return Whether the block has durability.
     */
    public boolean hasDurability(Block block) {
        // check if this world is disabled
        if (plugin.getWorldService().isWorldDisabled(block.getWorld())) {
            return false;
        }
        // check if block has durability set
        if (!plugin.getConfig().contains("blocks." + block.getType().name() + ".durability")) {
            return false;
        }
        // check if block is in a faction chunk
        return plugin.getFactionService().isInFactionLand(block.getChunk());
    }

    /**
     * Clears all durability data in a chunk.
     * @param chunk The chunk.
     */
    public void clearDurabilitiesInChunk(Chunk chunk)
            throws ChunkBusyException, ChunkNotLoadedException {
        durabilityStorage.removeMetadataForChunk(chunk);
    }

    /**
     * Clears durability of a block.
     * @param block The block.
     */
    public void clearDurability(Block block)
            throws ChunkBusyException, ChunkNotLoadedException {
        durabilityStorage.removeMetadata(block);
    }

    /**
     * Gets a list of damaged blocks within a chunk.
     * @param chunk The chunk.
     * @return A list of damaged blocks within the chunk.
     * @throws ChunkBusyException Thrown if the chunks is busy.
     */
    public List<DamagedBlock> getDamagedBlocksInChunk(Chunk chunk)
            throws ChunkBusyException, ChunkNotLoadedException {
        // check if there are durabilities in chunk
        if (durabilityStorage.hasMetadataForChunk(chunk)) {
            return Collections.emptyList();
        }
        // get damaged blocks in chunk
        return durabilityStorage.getMetadataInChunk(chunk)
            .entrySet().parallelStream().map((entry) -> {
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
     * Probability function deciding whether a block with
     * the given relative durability is "healthy" or not.
     * Healthy blocks will drop when mined, and won't break
     * down after a faction core is destroyed.
     * @param relativeDurability The relative durability of the block.
     * @return Whether the block is healthy.
     */
    public static boolean isBlockHealthy(double relativeDurability) {
        double probability = Math.exp(-3 * (1d - relativeDurability));
        return Math.random() < probability;
    }

    /**
     * Damaged block information.
     */
    @Data
    public static class DamagedBlock {
        private final Block block;
        private final double durability;
    }
}
