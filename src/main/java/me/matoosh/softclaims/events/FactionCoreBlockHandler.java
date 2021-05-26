package me.matoosh.softclaims.events;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldBlockedException;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.exception.faction.FactionPermissionsDeniedException;
import me.matoosh.softclaims.exception.faction.NotEnoughPowerException;
import me.matoosh.softclaims.exception.faction.core.ChunkClaimedByOtherFactionException;
import me.matoosh.softclaims.exception.faction.core.MultipleCoresInChunkException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import me.matoosh.softclaims.service.FactionCoreService;
import me.matoosh.softclaims.service.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

@RequiredArgsConstructor
public class FactionCoreBlockHandler implements Listener {

    private final WorldService worldService;
    private final FactionCoreService factionCoreService;
    private final SoftClaimsPlugin plugin;

    @EventHandler
    public void onPlaceFactionCore(BlockPlaceEvent event) {
        // check if a faction core is being placed
        if (event.getBlockPlaced().getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        // check if the world is disabled
        if (worldService.isWorldDisabled(event.getBlockPlaced().getWorld())) {
            return;
        }

        try {
            // attempt to create a faction core
            factionCoreService.createCore(event.getBlockPlaced(), event.getPlayer())
            .exceptionally((e) -> {
                if (e instanceof NotEnoughPowerException) {
                    // faction doesnt have enough power to claim more land
                    MSG.send(event.getPlayer(), "&cYour faction doesn't have enough power to take over more land!");
                    Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                } else if (e instanceof ChunkClaimedByOtherFactionException) {
                    // the chunk where the core was placed is already claimed by another faction
                    MSG.send(event.getPlayer(), "&cYou can't place a core in another faction's territory!");
                    Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                }
                return null;
            });
        } catch (FactionDoesntExistException e) {
            MSG.send(event.getPlayer(), "&cYou must be part of a faction to be able to place a faction core!" +
                    "\nCreate a faction with &e/f create");
            event.setCancelled(true);
        } catch (FactionPermissionsDeniedException e) {
            MSG.send(event.getPlayer(), "&cYour faction doesn't allow you to claim land!");
            event.setCancelled(true);
        } catch (MultipleCoresInChunkException e) {
            // there is already a core in this chunk
            MSG.send(event.getPlayer(), "&cThere is already a faction core in this chunk!");
            event.setCancelled(true);
        } catch (InvalidCoreBlockException | WorldBlockedException | ChunkBusyException | ChunkNotLoadedException e) {
            // should not happen
            event.setCancelled(true);
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onBreakFactionCore(BlockPlaceEvent event) {
        // check if a faction core is being broken
        if (event.getBlockPlaced().getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        // check if the world is disabled
        if (worldService.isWorldDisabled(event.getBlockPlaced().getWorld())) {
            return;
        }

        try {
            // attempt to remove a faction core
            factionCoreService.removeCore(event.getBlockPlaced(), event.getPlayer());
        } catch (FactionDoesntExistException e) {
            MSG.send(event.getPlayer(), "&cYou must be part of a faction to be able to place a faction core!" +
                    "\nCreate a faction with &e/f create");
            event.setCancelled(true);
        } catch (FactionPermissionsDeniedException e) {
            MSG.send(event.getPlayer(), "&cYour faction doesn't allow you to claim land!");
            event.setCancelled(true);
        }catch (InvalidCoreBlockException | WorldBlockedException | ChunkBusyException | ChunkNotLoadedException e) {
            // should not happen
            e.printStackTrace();
            event.setCancelled(true);
        }
    }
}
