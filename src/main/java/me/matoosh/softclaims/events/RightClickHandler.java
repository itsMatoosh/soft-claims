package me.matoosh.softclaims.events;

import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.service.BlockDurabilityService;
import me.matoosh.softclaims.service.CommunicationService;
import me.matoosh.softclaims.service.FactionCoreService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class RightClickHandler implements Listener {

    private final BlockDurabilityService blockDurabilityService;
    private final FactionCoreService factionCoreService;
    private final CommunicationService communicationService;
    private final SoftClaimsPlugin plugin;

    private final Set<Integer> cooldowns = new HashSet<>();

    /**
     * Called when a player interacts with a block.
     * @param event
     */
    @EventHandler
    public void onPlayerRightClickBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
            return;
        }

        // check durability
        if (cooldowns.contains(event.getPlayer().getEntityId())) {
            return;
        }

        // show durability or faction core info
        Block block = event.getClickedBlock();
        if (block.getType() == Material.RESPAWN_ANCHOR) {
            // show faction core info
            factionCoreService.getFactionCoreStorage().getMetadata(block).thenApply((faction) -> {
                if (faction != null) {
                    System.out.println("CORE: " + faction);
                }
                return null;
            });
        } else {
            // show block durability
            blockDurabilityService.getDurabilityAbsolute(event.getClickedBlock()).thenApply((durability) -> {
                if (durability == 0) {
                    return null;
                }

                // show durability info to player
                communicationService.showDurability(event.getPlayer(), durability,
                        blockDurabilityService.getTotalDurability(
                                event.getClickedBlock().getType()));
                return null;
            });
        }

        // do cooldown
        int entityId = event.getPlayer().getEntityId();
        cooldowns.add(entityId);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> cooldowns.remove(entityId), 10);
    }
}
