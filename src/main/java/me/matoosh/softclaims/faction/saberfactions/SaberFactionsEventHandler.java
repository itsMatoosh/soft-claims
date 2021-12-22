package me.matoosh.softclaims.faction.saberfactions;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.event.LandClaimEvent;
import com.massivecraft.factions.event.LandUnclaimAllEvent;
import com.massivecraft.factions.event.LandUnclaimEvent;
import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
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
    public void onChunkUnclaimAll(LandUnclaimAllEvent event) {
        // clear and destroy unhealthy blocks in all chunks
        Board.getInstance().getAllClaims(event.getFaction())
        .forEach((c) -> {
            // get core in the chunk
            plugin.getFactionCoreService().clearAndDestroyCoresInChunk(c.getChunk());

            // clean durabilities for this chunk
            plugin.getBlockDurabilityService().clearAndBreakUnhealthyBlocksInChunk(c.getChunk());
        });
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

        // clear cores in this chunk
        plugin.getFactionCoreService().clearAndDestroyCoresInChunk(event.getLocation().getChunk());

        // clear durable blocks in the chunk
        plugin.getBlockDurabilityService().clearAndBreakUnhealthyBlocksInChunk(event.getLocation().getChunk());
    }
}
