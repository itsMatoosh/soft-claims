package me.matoosh.softclaims.events;

import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldBlockedException;
import me.matoosh.softclaims.exception.faction.NotEnoughPowerException;
import me.matoosh.softclaims.exception.faction.core.ClaimedByOtherFactionCorePlaceException;
import me.matoosh.softclaims.exception.faction.core.CoreOverlapException;
import me.matoosh.softclaims.exception.faction.core.FactionlessCorePlaceException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import org.bukkit.Bukkit;
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
                // attempt to create a faction core
                plugin.getFactionCoreService().createFactionCore(
                        event.getBlockPlaced(), event.getPlayer())
                .exceptionally((e) -> {
                    if (e instanceof NotEnoughPowerException) {
                        // faction doesnt have enough power to claim more land
                        MSG.send(event.getPlayer(), "&cYour faction doens't have enough power to take over more land!");
                        Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                    } else if (e instanceof ClaimedByOtherFactionCorePlaceException) {
                        // the chunk where the core was placed is already claimed by another faction
                        MSG.send(event.getPlayer(), "&cYou can't place a core in another faction's territory!");
                        Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                    } else if (e instanceof CoreOverlapException) {
                        // there is already a core in this chunk
                        MSG.send(event.getPlayer(), "&cThere is already a faction core in this chunk!");
                        Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                    }
                    return null;
                });
            } catch (FactionlessCorePlaceException e) {
                MSG.send(event.getPlayer(), "&cYou must be part of a faction to be able to place a faction core!" +
                        "\nCreate a faction with &e/f create");
                event.setCancelled(true);
            } catch (InvalidCoreBlockException | WorldBlockedException e) {
                // should not happen
                e.printStackTrace();
            }
        }
    }
}
