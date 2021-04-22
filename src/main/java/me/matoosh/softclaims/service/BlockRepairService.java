package me.matoosh.softclaims.service;

import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.faction.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Manages automatic repair of blocks based on the faction power.
 */
public class BlockRepairService implements IReloadable {

    private final SoftClaimsPlugin plugin;

    /**
     * Number of healed blocks that should be animated per each animation tick.
     */
    private static final int ANIMATIONS_PER_TICK = 15;

    /**
     * List of recently healed blocks for
     * which a heal animation is still due.
     */
    private final ConcurrentMap<Chunk, Queue<int[]>> healedBlocks = new ConcurrentHashMap<>();

    /**
     * The amount of durability that should be restored with each heal.
     */
    private int repairDelta;

    /**
     * Async task for repairing blocks in factions.
     */
    private BukkitTask repairTask;
    /**
     * Task for playing block heal animations.
     */
    private BukkitTask repairAnimationTask;

    public BlockRepairService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void reload() {
        // cancel previous tasks
        if (repairTask != null) {
            repairTask.cancel();
            repairTask = null;
        }
        if (repairAnimationTask != null) {
            repairAnimationTask.cancel();
            repairAnimationTask = null;
        }

        // set heal delta
        this.repairDelta = plugin.getConfig().getInt("repair.repairDelta", 0);

        // set the heal frequency
        int frequency = 20 * plugin.getConfig().getInt("repair.repairFrequency", 300);
        this.repairTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::repairBlocksTask, frequency, frequency);

        // run the repair animations every 10 ticks
        this.repairAnimationTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::repairTick, 10, 10);
    }

    /**
     * Runs asynchronously and heals faction blocks.
     */
    private void repairBlocksTask() {
        // get repair delta
        if (repairDelta == 0) {
            return;
        }

        // repair blocks for every faction
        List<Faction> factions = plugin.getFactionService().getAllFactions();
        for (Faction faction : factions) {
            // get faction chunks
            List<Chunk> factionChunks = plugin.getFactionService()
                    .getAllFactionChunks(faction.getName());

            // calculate faction chunk influence
            int chunkInfluence = (int) (faction.getPower() - factionChunks.size());

            // calculate repair delta
            int repairDeltaAdjusted;
            if (chunkInfluence >= 0) {
                repairDeltaAdjusted = repairDelta;
            } else {
                repairDeltaAdjusted = (int) (repairDelta *
                        ((double) (factionChunks.size() + repairDelta) / (double) factionChunks.size()));
            }

            // skip if no repair
            if (repairDeltaAdjusted <= 0) {
                return;
            }

            // heal blocks
            for (Chunk factionChunk : factionChunks) {
                try {
                    healedBlocks.put(
                        factionChunk,
                        plugin.getBlockDurabilityService()
                        .modifyDurabilitiesInChunk(factionChunk, repairDeltaAdjusted)
                        .stream().map((s) -> {
                            String[] posUnparsed = s.split(",");
                            return new int[]{
                                Integer.parseInt(posUnparsed[0]),
                                Integer.parseInt(posUnparsed[1]),
                                Integer.parseInt(posUnparsed[2])
                            };
                        }).collect(Collectors.toCollection(LinkedList::new)));
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
                adjacent.getWorld().spawnParticle(
                        Particle.COMPOSTER, adjacent.getLocation(), 1);
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
