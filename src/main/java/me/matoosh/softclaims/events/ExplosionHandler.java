package me.matoosh.softclaims.events;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.ChunkBusyException;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ExplosionHandler implements Listener {

    private final SoftClaimsPlugin plugin;

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

        Set<Block> durableBlocks = new HashSet<>(); // durable blocks that should be processed further
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
        if (durableBlocks.size() == 0) return;

        // get power
        int power = powerFunction.apply(null);

        // apply damage to blocks
        for (Block b : durableBlocks) {
            int durability;
            try {
                durability = plugin.getBlockDurabilityService().getDurabilityAbsolute(b);
            } catch (ChunkBusyException e) {
                continue;
            }

            // calculate damage to block based on distance
            // to the center of the explosion
            double dist = location.distanceSquared(b.getLocation());
            // 2 and 0.7 ensure that explosion 1 block away from the block
            // damaged the block with the full power. Explosions inside of the
            // block will result in double the power being exerted.
            durability -= 2 * power + Math.exp(-0.7d * dist);
            if(durability > 0) {
                // update durability
                try {
                    plugin.getBlockDurabilityService().setDurabilityAbsolute(b, durability);
                } catch (ChunkBusyException e) {
                    e.printStackTrace();
                }
            } else {
                // block exploded
                plugin.getBlockDurabilityService().clearDurability(b);
                blockList.add(b);
            }
        }
    }
}
