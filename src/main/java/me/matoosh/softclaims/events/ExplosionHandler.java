package me.matoosh.softclaims.events;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.ChunkBusyException;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ExplosionHandler implements Listener {

    private final SoftClaimsPlugin plugin;

    private static final int EXPLOSION_RADIUS = 3;

    public ExplosionHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        onExplosion(event.blockList(), event.getLocation(), (s) -> plugin.getConfig().getInt(
                "explosionDamage." + event.getEntityType().name(), 100));
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        onExplosion(event.blockList(), event.getBlock().getLocation(), (s) -> plugin.getConfig().getInt(
                "explosionDamage." + event.getBlock().getType().name(), 100));
    }

    private void onExplosion(List<Block> blockList, Location location,
                             Function<Void, Integer> powerFunction) {
        // check if this world is disabled
        if (plugin.getConfig().getStringList("disabledWorlds")
                .contains(location.getWorld().getName())) {
            return;
        }

        List<Block> durableBlocks = new ArrayList<>(); // durable blocks that should be processed further
        Set<Chunk> skipChunks = new HashSet<>(); // chunks not in faction land that should be skipped
        Set<Chunk> actChunks = new HashSet<>(); // chunks in faction land that should be processed
        Iterator<Block> blockIterator = blockList.iterator();
        while (blockIterator.hasNext()) {
            Block block = blockIterator.next();

            // skip origin chunks if outside faction
            if (skipChunks.contains(block.getChunk())) {
                continue;
            }

            // check if block could be durable
            if (plugin.getBlockDurabilityService().getTotalDurability(block.getType()) > 0) {
                // durable block
                // check chunk
                boolean act = actChunks.contains(block.getChunk());
                if (!act && !plugin.getFactionService().isInFactionLand(block.getChunk())) {
                    skipChunks.add(block.getChunk());
                    continue;
                } else if (!act) {
                    actChunks.add(block.getChunk());
                }

                // add block to processing
                durableBlocks.add(block);
                blockIterator.remove();
            }
        }

        // process durable block further asynchronously
        processDurableBlocksAsync(durableBlocks, location, powerFunction);
    }

    private CompletableFuture<Void> processDurableBlocksAsync(
            List<Block> durableBlocks, Location location,
            Function<Void, Integer> powerFunction) {
        return CompletableFuture.runAsync(() -> {
            // get blocks around
            for (int x = -EXPLOSION_RADIUS; x <= EXPLOSION_RADIUS; x++) {
                for (int y = -EXPLOSION_RADIUS; y <= EXPLOSION_RADIUS; y++) {
                    for (int z = -EXPLOSION_RADIUS; z <= EXPLOSION_RADIUS; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Block block = location.clone().add(x, y, z).getBlock();
                        if (block.getType().getBlastResistance() >= 1200) {
                            // block that doesnt appear in the normal explosion list
                            if (plugin.getBlockDurabilityService().hasDurability(block)) {
                                durableBlocks.add(block);
                            }
                        }
                    }
                }
            }

            // check if we found any blocks
            if (durableBlocks.size() == 0) return;

            // get power
            int power = powerFunction.apply(null);

            // apply damage to blocks
            List<Block> destroyedBlocks = new ArrayList<>();
            for (Block b : durableBlocks) {
                int durability;
                try {
                    durability = plugin.getBlockDurabilityService().getDurabilityAbsolute(b);
                } catch (ChunkBusyException e) {
                    continue;
                }

                // calculate damage to block based on distance
                // to the center of the explosion
                double dist = location.distance(b.getLocation().add(0.5, 0.5, 0.5)) - 1;
                // 2 and 0.7 ensure that explosion 1 block away from the block
                // damaged the block with the full power. Explosions inside of the
                // block will result in double the power being exerted.
                durability -= power * Math.exp(-dist);
                if(durability > 0) {
                    // update durability
                    try {
                        plugin.getBlockDurabilityService().setDurabilityAbsolute(b, durability);
                    } catch (ChunkBusyException e) {
                        e.printStackTrace();
                    }
                } else {
                    // block exploded
                    try {
                        plugin.getBlockDurabilityService().clearDurability(b);
                    } catch (ChunkBusyException ignored) {
                        // cant happen cause the chunk must be loaded for explosion
                    }
                    destroyedBlocks.add(b);
                }
            }

            // break blocks
            Bukkit.getScheduler().runTask(plugin, () -> destroyedBlocks.forEach(Block::breakNaturally));
        });

    }
}
