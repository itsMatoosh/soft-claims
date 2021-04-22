package me.matoosh.softclaims.events;

import me.matoosh.softclaims.SoftClaimsPlugin;
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
            // check if the world is blocked
            if (plugin.getConfig().getStringList("disabledWorlds")
                    .contains(event.getBlockPlaced().getLocation().getWorld().getName())) {
                return;
            }

            // get player's faction
        }
    }
}
