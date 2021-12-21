package me.matoosh.softclaims.events;

import com.comphenix.protocol.wrappers.BlockPosition;
import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.service.BlockDurabilityService;
import me.matoosh.softclaims.service.FactionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
public class BlockBreakHandler implements Listener {

    private final BlockDurabilityService blockDurabilityService;
    private final FactionService factionService;
    private final DiggersHandler diggersHandler;

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) throws ExecutionException, InterruptedException {
        // get block
        Block block = event.getBlock();

        // check if block has durability
        double blockDurability = blockDurabilityService.getDurabilityRelative(block).get();

        // skip non durable blocks
        if (blockDurability <= 0) {
            // bypass default faction protection for non-durable blocks.
            if (event.isCancelled() && factionService.isInFactionLand(block.getChunk())) {
                event.setCancelled(false);
            }
            return;
        }

        // check if the block was broken without the slow breaking process
        if (diggersHandler.isDigging(event.getPlayer())) {
            // still in the process of breaking
            event.setCancelled(true);

            // apply fatigue to fix fast pickaxe problem
            diggersHandler.applyFatigue(event.getPlayer());
        } else {
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

                // use this as indication of a new digging task
                diggersHandler.onStartDigging(new BlockPosition(
                        block.getX(), block.getY(), block.getZ()), event.getPlayer());
            }
        }
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
