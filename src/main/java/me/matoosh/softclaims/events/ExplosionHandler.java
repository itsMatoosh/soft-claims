package me.matoosh.softclaims.events;

import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.service.BlockDurabilityService;
import me.matoosh.softclaims.service.FactionService;
import me.matoosh.softclaims.service.WorldService;
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

@RequiredArgsConstructor
public class ExplosionHandler implements Listener {

    private static final int EXPLOSION_RADIUS = 3;

    private final BlockDurabilityService blockDurabilityService;
    private final FactionService factionService;
    private final WorldService worldService;
    private final SoftClaimsPlugin plugin;

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
        if (location.getWorld() == null || worldService.isWorldDisabled(location.getWorld())) {
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
            if (blockDurabilityService.isMaterialDurable(block.getType())) {
                // durable block
                // check chunk
                boolean act = actChunks.contains(block.getChunk());
                if (!act && !factionService.isInFactionLand(block.getChunk())) {
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
                        Block block = location.clone().add(x, y, z).getBlock();
                        if (block.getType().getBlastResistance() >= 1200) {
                            // block that doesnt appear in the normal explosion list
                            if (blockDurabilityService.hasDurability(block)) {
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
            List<Block> destroyedBlocks = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Void>[] futures = new CompletableFuture[durableBlocks.size()];
            for (int i = 0; i < durableBlocks.size(); i++) {
                Block b = durableBlocks.get(i);
                futures[i] = blockDurabilityService.getDurabilityAbsolute(b)
                .thenAccept((durability) -> {
                    // calculate damage to block based on distance
                    // to the center of the explosion
                    double dist = location.distance(b.getLocation().add(0.5, 0.5, 0.5)) - 1;
                    // 2 and 0.7 ensure that explosion 1 block away from the block
                    // damaged the block with the full power. Explosions inside of the
                    // block will result in double the power being exerted.
                    durability -= (int) (power * Math.exp(-dist));
                    if(durability > 0) {
                        // update durability
                        blockDurabilityService.setDurabilityAbsolute(b, durability);
                    } else {
                        // block exploded
                        blockDurabilityService.clearDurability(b);
                        destroyedBlocks.add(b);
                    }
                });
            }
            // break blocks
            CompletableFuture.allOf(futures).thenRun(() -> Bukkit.getScheduler()
                    .runTask(plugin, () -> destroyedBlocks.forEach(Block::breakNaturally)));
        });

    }
}
