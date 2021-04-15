package me.matoosh.softclaims.faction;

import org.bukkit.Chunk;

import java.util.List;
import java.util.UUID;

public interface IFactionImplementation {
    /**
     * Registers the events of the faction implementation.
     */
    void registerEvents();

    /**
     * Gets the list of factions on the server.
     * @return The list of factions.
     */
    List<String> getFactions();

    /**
     * Attempts to charge a faction the given price.
     * @param factionName The name of the faction.
     * @param price The price to charge.
     * @return Whether the faction could pay the price.
     */
    boolean chargeFaction(String factionName, double price);

    /**
     * Checks whether a chunk is in faction land.
     * @param chunk The chunk to check.
     * @return Whether the chunk is in faction land.
     */
    boolean isInFactionLand(Chunk chunk);

    /**
     * Checks whether the player can break blocks
     * in the faction at chunk.
     * @param uuid The UUID of the player.
     * @param factionChunk The chunk to be checked.
     * @return Whether the player can break blocks in the faction.
     */
    boolean canPlayerDestroyInFaction(UUID uuid, Chunk factionChunk);

    /**
     * Lists all chunks claimed by factions.
     * @param factionName The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    List<Chunk> getAllFactionChunks(String factionName);
}
