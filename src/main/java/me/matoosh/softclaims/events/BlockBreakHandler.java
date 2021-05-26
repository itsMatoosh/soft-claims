package me.matoosh.softclaims.events;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.service.BlockDurabilityService;
import me.matoosh.softclaims.service.FactionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

@RequiredArgsConstructor
public class BlockBreakHandler implements Listener {

    private final BlockDurabilityService blockDurabilityService;
    private final FactionService factionService;
    private final DiggersHandler diggersHandler;

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // get block
        Block block = event.getBlock();

        // check if block has durability
        double blockDurability;
        try {
            blockDurability = blockDurabilityService.getDurabilityRelative(block);
        } catch (ChunkBusyException | ChunkNotLoadedException e) {
            event.setCancelled(true);
            return;
        }
        if (blockDurability == 0 && event.isCancelled()) {
            // bypass default faction protection for non-durable blocks.
            if (factionService.isInFactionLand(block.getChunk())) {
                event.setCancelled(false);
            }
            return;
        } else if (blockDurability == 0) {
            // dont affect blocks outside of a faction.
            return;
        }

        // check if the block was broken without the slow breaking process
        if (!diggersHandler.isDigging(event.getPlayer())) {
            // check faction aspect
            if (factionService.canPlayerDestroyInFaction(
                    event.getPlayer(), block.getChunk())) {
                // allow fast break for people with special faction perms
                event.setCancelled(false);

                // drop if block is healthy
                event.setDropItems(BlockDurabilityService.isBlockHealthy(blockDurability));
            } else {
                // dont allow breaking without slow down effect
                event.setCancelled(true);

                // apply fatigue to fix fast pickaxe problem
                diggersHandler.onStartDigging(new BlockPosition(
                        block.getX(), block.getY(), block.getZ()), event.getPlayer());
            }
        } else {
            // still in the process of breaking
            event.setCancelled(true);

            // apply fatigue to fix fast pickaxe problem
            diggersHandler.applyFatigue(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockDestroy(BlockDestroyEvent event)
            throws ChunkBusyException, ChunkNotLoadedException {
        // block durable blocks from getting destroyed by environment
        blockDurabilityService.clearDurability(event.getBlock());
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // block breaking durable blocks by entities
        if (event.getTo() == Material.AIR) {
            if (blockDurabilityService.hasDurability(event.getBlock())) {
                event.setCancelled(true);
            }
        }
    }
}
