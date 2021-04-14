package me.matoosh.softclaims.durability;

import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.Bukkit;

public class BlockRepairService {
    /**
     * Frequency with which blocks are repaired in factions.
     */
    final static int REPAIR_FREQUENCY = 20 * 60 * 5;

    /**
     * Reference to the plugin.
     */
    private final SoftClaimsPlugin plugin;

    public BlockRepairService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the component.
     */
    public void initialize() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::repairBlocksTask, REPAIR_FREQUENCY, REPAIR_FREQUENCY);
    }

    private void repairBlocksTask() {
        plugin.ge
    }
}
