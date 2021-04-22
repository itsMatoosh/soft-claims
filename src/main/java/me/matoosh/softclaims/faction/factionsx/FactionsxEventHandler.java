package me.matoosh.softclaims.faction.factionsx;

import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.durability.BlockDurabilityService;
import net.prosavage.factionsx.event.FactionPreClaimEvent;
import net.prosavage.factionsx.event.FactionUnClaimEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class FactionsxEventHandler implements Listener {

    private final SoftClaimsPlugin plugin;

    public FactionsxEventHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player tries to claim a chunk.
     * @param event
     */
    @EventHandler
    public void onChunkClaim(FactionPreClaimEvent event) {
        // only allow chunks to be claimed by admins/by placing a faction core
        if (!event.getClaimingAsServerAdmin()) {
            event.getFplayer();
            if (event.getFplayer().getPlayer() != null) {
                MSG.send(event.getFplayer().getPlayer(),
                        "&cYou can only claim chunks by placing down a &lFaction Core");
            }
            event.setCancelled(true);
        }
    }

    /**
     * Called when a player tries to unclaim a chunk.
     * @param event
     */
    @EventHandler
    public void onChunkUnClaim(FactionUnClaimEvent event) {
        // only allow chunks to be unclaimed by admins/by breaking a faction core
        if (!event.getUnclaimingAsServerAdmin()) {
            event.getFplayer();
            if (event.getFplayer().getPlayer() != null) {
                MSG.send(event.getFplayer().getPlayer(),
                        "&cYou can only unclaim chunks by breaking the nearby &lFaction Core");
            }
            event.setCancelled(true);
            return;
        }

        // clear durable blocks in the chunk
        try {
            // break down damaged durable blocks
            List<BlockDurabilityService.DamagedBlock> damagedBlocks = plugin.getBlockDurabilityService()
                    .getDamagedBlocksInChunk(event.getFLocation().getChunk());
            damagedBlocks.forEach((block) -> {
                // break down blocks that are not "healthy"
                if (!BlockDurabilityService.isBlockHealthy(block.getDurability())) {
                    block.getBlock().breakNaturally();
                }
            });

            // clear durabilities for this chunk
            plugin.getBlockDurabilityService().clearDurabilitiesInChunk(
                    event.getFLocation().getChunk());
        } catch (ChunkBusyException | ChunkNotLoadedException e) {
            e.printStackTrace();
        }
    }
}
