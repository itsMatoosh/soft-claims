package me.matoosh.softclaims.events;

import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.WorldDisabledException;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.exception.faction.FactionPermissionsDeniedException;
import me.matoosh.softclaims.exception.faction.NotEnoughPowerException;
import me.matoosh.softclaims.exception.faction.core.ChunkClaimedByOtherFactionException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import me.matoosh.softclaims.exception.faction.core.MultipleCoresInChunkException;
import me.matoosh.softclaims.service.FactionCoreService;
import me.matoosh.softclaims.service.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.concurrent.ExecutionException;

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
                if (e.getCause() instanceof NotEnoughPowerException) {
                    // faction doesnt have enough power to claim more land
                    NotEnoughPowerException powerException = (NotEnoughPowerException) e.getCause();
                    MSG.send(event.getPlayer(), "&cYour faction needs "
                            + String.format("%.2f", powerException.getPowerDeficiency())
                            + " more power to place this core!");
                    Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                } else if (e.getCause() instanceof MultipleCoresInChunkException) {
                    // there is already a core in this chunk
                    MSG.send(event.getPlayer(), "&cThere is already a faction core in this chunk!");
                    Bukkit.getScheduler().runTask(plugin, () -> event.getBlockPlaced().breakNaturally());
                } else {
                    e.printStackTrace();
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
        } catch (InvalidCoreBlockException | WorldDisabledException e) {
            // should not happen
            event.setCancelled(true);
        } catch (ChunkClaimedByOtherFactionException e) {
            // the chunk where the core was placed is already claimed by another faction
            MSG.send(event.getPlayer(), "&cYou can't place a core in another faction's territory!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreakFactionCore(BlockBreakEvent event) {
        // check if a faction core is being broken
        Block broken = event.getBlock();
        if (broken.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }

        // check if the world is disabled
        if (worldService.isWorldDisabled(broken.getWorld())) {
            return;
        }

        try {
            // attempt to remove a faction core
            factionCoreService.removeCoreAsPlayer(broken, event.getPlayer())
            .exceptionally((e) -> {
                e.printStackTrace();
                return null;
            });
        } catch (FactionPermissionsDeniedException e) {
            MSG.send(event.getPlayer(), "&cYour faction doesn't allow you to claim land!");
            // cancel event
            event.setCancelled(true);
        } catch (InvalidCoreBlockException e) {
            MSG.send(event.getPlayer(), "&cThis is an invalid faction core!");
        } catch (WorldDisabledException | ExecutionException | InterruptedException e) {
            // should not happen
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onExplodeFactionCore(EntityExplodeEvent event) {
        // ignore other explosions
        if (event.getEntityType() != EntityType.ENDER_CRYSTAL) {
            return;
        }

        // check if the world is disabled
        if (worldService.isWorldDisabled(event.getLocation().getWorld())) {
            return;
        }

        // check if this crystal is registered
        Block coreBlock = event.getEntity().getLocation()
                .subtract(0.5, 1.5, 0.5)
                .getBlock();
        factionCoreService.getFactionCoreStorage().getMetadata(coreBlock)
            .thenAccept(metadata -> {
                if (metadata != null) {
                    factionCoreService.clearAndDestroyCoresInChunk(event.getLocation().getChunk());
                }
            });
    }
}
