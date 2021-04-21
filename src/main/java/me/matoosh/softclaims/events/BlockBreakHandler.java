package me.matoosh.softclaims.events;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.List;

public class BlockBreakHandler implements Listener {

    private final SoftClaimsPlugin plugin;

    public BlockBreakHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // get block
        Block block = event.getBlock();

        // check if block has durability
        double blockDurability;
        try {
            blockDurability = plugin.getBlockDurabilityService().getDurabilityRelative(block);
        } catch (ChunkBusyException e) {
            event.setCancelled(true);
            return;
        }
        if (blockDurability == 0 && event.isCancelled()) {
            // bypass default faction protection for non-durable blocks.
            if (plugin.getFactionService().isInFactionLand(block.getChunk())) {
                event.setCancelled(false);
            }
            return;
        } else if (blockDurability == 0) {
            // dont affect blocks outside of a faction.
            return;
        }

        // check if the block was broken without the slow breaking process
        if (!plugin.getDiggersHandler().isDigging(event.getPlayer())) {
            // check faction aspect
            if (plugin.getFactionService().canPlayerDestroyInFaction(
                    event.getPlayer().getUniqueId(), block.getChunk())) {
                // allow fast break for people with special faction perms
                event.setCancelled(false);

                // drop probability
                double probability = Math.exp(-3 * (1d - blockDurability));
                event.setDropItems(Math.random() < probability);
            } else {
                // dont allow breaking without slow down effect
                event.setCancelled(true);

                // apply fatigue to fix fast pickaxe problem
                plugin.getDiggersHandler().onStartDigging(
                        new BlockPosition(block.getX(), block.getY(), block.getZ()), event.getPlayer());
            }
        } else {
            // still in the process of breaking
            event.setCancelled(true);

            // apply fatigue to fix fast pickaxe problem
            plugin.getDiggersHandler().applyFatigue(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockDestroy(BlockDestroyEvent event) throws ChunkBusyException {
        // block durable blocks from getting destroyed by environment
        plugin.getBlockDurabilityService().clearDurability(event.getBlock());
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // block breaking durable blocks by entities
        if (event.getTo() == Material.AIR) {
            if (plugin.getBlockDurabilityService().hasDurability(event.getBlock())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) throws ChunkBusyException {
        // clear durability
        plugin.getBlockDurabilityService().clearDurability(event.getBlock());
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) throws ChunkBusyException {
        // clear durability
        plugin.getBlockDurabilityService().clearDurability(event.getBlock());
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event)
            throws ChunkBusyException {
        onBlocksMoveByPiston(event.getBlocks(), event.getDirection());
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event)
            throws ChunkBusyException {
        onBlocksMoveByPiston(event.getBlocks(), event.getDirection());
    }

    private void onBlocksMoveByPiston(List<Block> blocks, BlockFace direction) throws ChunkBusyException {
        for (Block origin : blocks) {
            // get block durability
            double durability = plugin.getBlockDurabilityService()
                    .getDurabilityRelative(origin);

            // skip if durability 0
            if (durability == 0) {
                continue;
            }

            // move durability in direction of the piston extend
            Block resulting = origin.getRelative(direction);
            plugin.getBlockDurabilityService().setDurabilityRelative(resulting, durability);

            // clear durability in origin block
            plugin.getBlockDurabilityService().clearDurability(origin);
        }
    }
}
