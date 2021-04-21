package me.matoosh.softclaims.events;

import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;

public class RightClickHandler implements Listener {

    private final SoftClaimsPlugin plugin;
    private final Set<Integer> cooldowns = new HashSet<>();

    public RightClickHandler(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player interacts with a block.
     * @param event
     */
    @EventHandler
    public void onPlayerRightClickBlock(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.hasBlock()) {
            // check durability
            if (event.getClickedBlock() == null) return;
            if (cooldowns.contains(event.getPlayer().getEntityId())) return;

            // check if block has durability
            int durability;
            try {
                durability = plugin.getBlockDurabilityService().getDurabilityAbsolute(event.getClickedBlock());
            } catch (ChunkBusyException e) {
                return;
            }
            if(durability == 0) return;

            // show durability info to player
            plugin.getCommunicationService().showDurability(event.getPlayer(), durability,
                    plugin.getBlockDurabilityService().getTotalDurability(
                            event.getClickedBlock().getType()));

            // do cooldown
            int entityId = event.getPlayer().getEntityId();
            cooldowns.add(entityId);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> cooldowns.remove(entityId), 15);
        }
    }
}
