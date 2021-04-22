package me.matoosh.softclaims.faction.factionsx;

import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.durability.BlockDurabilityService;
import net.prosavage.factionsx.event.FactionUnClaimEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class FactionsxEventHandler implements Listener {

    private final SoftClaimsPlugin plugin;

    public FactionsxEventHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkUnClaim(FactionUnClaimEvent unClaimEvent) {
        try {
            // break down damaged durable blocks
            List<BlockDurabilityService.DamagedBlock> damagedBlocks = plugin.getBlockDurabilityService()
                    .getDamagedBlocksInChunk(unClaimEvent.getFLocation().getChunk());
            damagedBlocks.forEach((block) -> {
                if (!BlockDurabilityService.isBlockHealthy(block.getDurability())) {
                    block.getBlock().breakNaturally();
                }
            });

            // clear durabilities for this chunk
            plugin.getBlockDurabilityService().clearDurabilitiesInChunk(
                    unClaimEvent.getFLocation().getChunk());
        } catch (ChunkBusyException | ChunkNotLoadedException e) {
            e.printStackTrace();
        }
    }
}
