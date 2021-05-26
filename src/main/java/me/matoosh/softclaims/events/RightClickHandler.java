package me.matoosh.softclaims.events;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.service.BlockDurabilityService;
import me.matoosh.softclaims.service.CommunicationService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class RightClickHandler implements Listener {

    private final BlockDurabilityService blockDurabilityService;
    private final CommunicationService communicationService;
    private final SoftClaimsPlugin plugin;

    private final Set<Integer> cooldowns = new HashSet<>();

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
                durability = blockDurabilityService.getDurabilityAbsolute(event.getClickedBlock());
            } catch (ChunkBusyException | ChunkNotLoadedException e) {
                return;
            }
            if(durability == 0) return;

            // show durability info to player
            communicationService.showDurability(event.getPlayer(), durability,
                    blockDurabilityService.getTotalDurability(
                            event.getClickedBlock().getType()));

            // do cooldown
            int entityId = event.getPlayer().getEntityId();
            cooldowns.add(entityId);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> cooldowns.remove(entityId), 15);
        }
    }
}
