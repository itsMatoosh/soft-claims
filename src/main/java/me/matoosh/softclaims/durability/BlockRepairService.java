package me.matoosh.softclaims.durability;

import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class BlockRepairService {
    /**
     * List of recently healed blocks.
     */
    private final ConcurrentMap<Chunk, Queue<int[]>> healedBlocks = new ConcurrentHashMap<>();

    /**
     * Reference to the plugin.
     */
    private final SoftClaimsPlugin plugin;

    private static final int ANIMATIONS_PER_TICK = 15;

    public BlockRepairService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the component.
     */
    public void initialize() {
        int frequency = 20 * plugin.getConfig().getInt("repair.repairFrequency", 300);
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::repairBlocksTask, frequency, frequency);
        Bukkit.getScheduler().runTaskTimer(plugin, this::repairTick, 10, 10);
    }

    /**
     * Runs asynchronously and heals faction blocks.
     */
    private void repairBlocksTask() {
        // get repair delta
        int repairDelta = plugin.getConfig().getInt("repair.repairDelta", 0);
        double repairCost = plugin.getConfig().getInt("repair.repairCost", 0);
        if (repairDelta == 0) return;

        // repair blocks in every faction
        List<String> factions = plugin.getFactionService().getFactions();
        for (String faction : factions) {
            for (Chunk factionChunk :
                    plugin.getFactionService().getAllFactionChunks(faction)) {
                try {
                    // check if faction can afford
                    int blocksToRepairInChunk = plugin.getBlockDurabilityService()
                            .countDamagedInChunk(factionChunk);
                    double cost = blocksToRepairInChunk * repairCost;
                    if (!plugin.getFactionService().chargeFaction(faction, cost)) {
                        break;
                    }

                    // heal blocks
                    healedBlocks.put(
                        factionChunk,
                        plugin.getBlockDurabilityService()
                        .modifyDurabilitiesInChunk(factionChunk, repairDelta)
                        .stream().map((s) -> {
                            String[] posUnparsed = s.split(",");
                            return new int[]{
                                Integer.parseInt(posUnparsed[0]),
                                Integer.parseInt(posUnparsed[1]),
                                Integer.parseInt(posUnparsed[2])
                            };
                        }).collect(Collectors.toCollection(LinkedBlockingQueue::new)));
                } catch (ChunkBusyException | ChunkNotLoadedException ignored) {}
            }
        }
    }

    /**
     * Called every 10 ticks.
     * Shows block animations for healed blocks.
     */
    public void repairTick() {
        // check if healed blocks empty
        if (healedBlocks.size() == 0) {
            return;
        }

        // animate blocks
        int i = 0;
        Iterator<Map.Entry<Chunk, Queue<int[]>>> iter = healedBlocks.entrySet().iterator();
        while (iter.hasNext() && i < ANIMATIONS_PER_TICK) {
            // get blocks in chunk to animate
            Map.Entry<Chunk, Queue<int[]>> entry = iter.next();
            Queue<int[]> blocks = entry.getValue();

            // spawn particles
            while (blocks.size() > 0 && i < ANIMATIONS_PER_TICK) {
                doHealAnimation(entry.getKey(), blocks.remove());
                i++;
            }

            // remove handled chunks
            if (blocks.size() == 0) {
                iter.remove();
            }
        }
    }

    /**
     * Do a heal animation at block.
     * @param chunk The chunk to do the heal animation in.
     * @param position The position of the block in the chunk to animate.
     */
    private void doHealAnimation(Chunk chunk, int[] position) {
        Block b = chunk.getBlock(position[0], position[1], position[2]);
        for (int i = 0; i < 6; i++) {
            Block adjacent = b.getRelative(blockFaceFromInt(i));
            if (adjacent.isEmpty()) {
                adjacent.getWorld().spawnParticle(Particle.COMPOSTER, adjacent.getLocation(), 1);
            }
        }
    }

    /**
     * Gets a block face from the block face number.
     * @param i block face number.
     * @return The block face.
     */
    private BlockFace blockFaceFromInt(int i) {
        switch(i) {
            default:
                return BlockFace.UP;
            case 1:
                return BlockFace.DOWN;
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.EAST;
            case 5:
                return BlockFace.WEST;
        }
    }
}
