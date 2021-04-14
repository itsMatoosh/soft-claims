package me.matoosh.softclaims.events;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.ChunkBusyException;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;

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
        int blockDurability;
        try {
            blockDurability = plugin.getBlockDurabilityService().getDurabilityAbsolute(block);
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

                // cancel drop
                event.setCancelled(false);
                event.setDropItems(plugin.getBlockDurabilityService().getBlockDrops(block));
            } else {
                // dont allow breaking without slow down effect
                event.setCancelled(true);
            }
        } else {
            // still in the process of breaking
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockDestroy(BlockDestroyEvent event) {
        // clear durability
        plugin.getBlockDurabilityService().clearDurability(event.getBlock());
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        // clear durability
        plugin.getBlockDurabilityService().clearDurability(event.getBlock());
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
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
