package me.matoosh.softclaims.faction;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.faction.factionsx.FactionsxImplementation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.List;
import java.util.UUID;

public class FactionService {

    private final SoftClaimsPlugin plugin;

    private IFactionImplementation factionImplementation;

    public FactionService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().isPluginEnabled("FactionsX")) {
            factionImplementation = new FactionsxImplementation(plugin);
        } else {
            factionImplementation = new NoFactionImplementation();
        }

        // register event
        factionImplementation.registerEvents();
    }

    /**
     * Checks whether a chunk is in faction land.
     * @param chunk The chunk to check.
     * @return Whether the chunk is in faction land.
     */
    public boolean isInFactionLand(Chunk chunk) {
        return factionImplementation.isInFactionLand(chunk);
    }

    /**
     * Checks whether the player can break blocks
     * in the faction at chunk.
     * @param uuid The UUID of the player.
     * @param factionChunk The chunk to be checked.
     * @return Whether the player can break blocks in the faction.
     */
    public boolean canPlayerDestroyInFaction(UUID uuid, Chunk factionChunk) {
        return factionImplementation.canPlayerDestroyInFaction(uuid, factionChunk);
    }

    /**
     * Gets the list of factions on the server.
     * @return The list of factions.
     */
    public List<String> getFactions() {
        return factionImplementation.getFactions();
    }

    /**
     * Attempts to charge a faction the given price.
     * @param factionName The name of the faction.
     * @param price The price to charge.
     * @return Whether the faction could pay the price.
     */
    public boolean chargeFaction(String factionName, double price) {
        return factionImplementation.chargeFaction(factionName, price);
    }

    /**
     * Lists all chunks claimed by factions.
     * @param factionName The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    public List<Chunk> getAllFactionChunks(String factionName) {
        return factionImplementation.getAllFactionChunks(factionName);
    }
}
