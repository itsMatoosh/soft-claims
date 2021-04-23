package me.matoosh.softclaims.events;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldBlockedException;
import me.matoosh.softclaims.exception.faction.core.FactionlessCorePlaceException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlaceHandler implements Listener {

    private final SoftClaimsPlugin plugin;

    public BlockPlaceHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlaceFactionCore(BlockPlaceEvent event) {
        // check if a faction core is being placed
        if (event.getBlockPlaced().getType() == Material.RESPAWN_ANCHOR) {
            // check if the world is disabled
            if (plugin.getWorldService().isWorldDisabled(event.getBlockPlaced().getWorld())) {
                return;
            }

            try {
                plugin.getFactionCoreService().createFactionCore(
                        event.getBlockPlaced(), event.getPlayer())
                .exceptionally((e) -> {
                    // todo: handle core creation errors
                });
            } catch (InvalidCoreBlockException | WorldBlockedException e) {
                // should not happen
                e.printStackTrace();
            } catch (FactionlessCorePlaceException e) {
                // todo: tell player to make a faction
                event.setCancelled(true);
            }
        }
    }
}
