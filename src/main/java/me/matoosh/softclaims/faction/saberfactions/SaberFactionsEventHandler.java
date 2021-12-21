package me.matoosh.softclaims.faction.saberfactions;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.event.LandClaimEvent;
import com.massivecraft.factions.event.LandUnclaimEvent;
import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.service.BlockDurabilityService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public class SaberFactionsEventHandler implements Listener {
    private final SoftClaimsPlugin plugin;

    @EventHandler
    public void onChunkClaim(LandClaimEvent event) {
        // only allow chunks to be unclaimed by admins/by breaking a faction core
        FPlayer fPlayer = event.getfPlayer();

        // only allow chunks to be claimed by admins/by placing a faction core
        if (fPlayer != null && !fPlayer.isAdminBypassing()) {
            MSG.send(event.getfPlayer().getPlayer(),
                    "&cYou can only claim chunks by placing down a &lFaction Core");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkUnclaim(LandUnclaimEvent event) {
        // only allow chunks to be unclaimed by admins/by breaking a faction core
        FPlayer fPlayer = event.getfPlayer();

        // check admin bypass
        if (fPlayer != null && !fPlayer.isAdminBypassing()) {
            MSG.send(fPlayer.getPlayer(), "&cYou can only unclaim chunks by breaking the nearby &lFaction Core");
            event.setCancelled(true);
            return;
        }

        // clear durable blocks in the chunk
        plugin.getBlockDurabilityService().getDamagedBlocksInChunk(event.getLocation().getChunk()).thenApply((damagedBlocks -> {
            damagedBlocks.forEach((block) -> {
                // break down blocks that are not "healthy"
                if (!BlockDurabilityService.isBlockHealthy(block.getDurability())) {
                    Bukkit.getScheduler().runTask(plugin, () -> block.getBlock().breakNaturally());
                }
            });
            return null;
        }));

        // clear durabilities for this chunk
        plugin.getBlockDurabilityService().clearDurabilitiesInChunk(
                event.getLocation().getChunk());
    }
}
