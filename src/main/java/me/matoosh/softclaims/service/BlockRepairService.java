package me.matoosh.softclaims.service;

import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.faction.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Manages automatic repair of blocks based on the faction power.
 */
@RequiredArgsConstructor
public class BlockRepairService implements IReloadable {

    /**
     * Vectors for each side of the block.
     */
    private static final Vector[] SIDE_VECTORS = {
        new Vector(0.65, 0, 0),
        new Vector(-0.65, 0, 0),
        new Vector(0, 0.65, 0),
        new Vector(0, -0.65, 0),
        new Vector(0, 0, 0.65),
        new Vector(0, 0, -0.65),
    };

    private final FactionService factionService;
    private final BlockDurabilityService blockDurabilityService;
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
                plugin, this::repairTick, frequency, frequency);

        // run the repair animations every 10 ticks
        this.repairAnimationTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::repairAnimationTick, 10, 10);
    }

    /**
     * Runs asynchronously and heals faction blocks.
     */
    private void repairTick() {
        // get repair delta
        if (repairDelta <= 0) {
            return;
        }

        // repair blocks for every faction
        List<Faction> factions = factionService.getAllFactions();
        for (Faction faction : factions) {
            // get faction chunks
            List<Chunk> factionChunks = null;
            try {
                factionChunks = factionService.getAllFactionChunks(faction.id());
            } catch (FactionDoesntExistException ignored) {
            }

            // dont repair if not enough power
            if (faction.power() < factionChunks.size()) {
                return;
            }

            // heal blocks
            for (Chunk factionChunk : factionChunks) {
                blockDurabilityService.modifyDurabilitiesInChunk(factionChunk, repairDelta)
                .thenApply((modified) -> {
                    healedBlocks.put(factionChunk, modified.stream().map((s) -> {
                        String[] posUnparsed = s.split(",");
                        return new int[]{
                                Integer.parseInt(posUnparsed[0]),
                                Integer.parseInt(posUnparsed[1]),
                                Integer.parseInt(posUnparsed[2])
                        };
                    }).collect(Collectors.toCollection(LinkedList::new)));
                    return null;
                });
            }
        }
    }

    /**
     * Called every 10 ticks.
     * Shows block animations for healed blocks.
     */
    public void repairAnimationTick() {
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
        // get block center location
        Location centerLocation = new Location(
                chunk.getWorld(), position[0], position[1], position[2])
                .add(0.5, 0.5, 0.5);

        // spawn particles on each side
        for (Vector sideVector : SIDE_VECTORS) {
            Location loc = centerLocation.add(sideVector);
            chunk.getWorld().spawnParticle(Particle.COMPOSTER, loc, 2);
        }
    }
}
