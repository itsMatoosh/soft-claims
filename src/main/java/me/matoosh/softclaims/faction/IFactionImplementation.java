package me.matoosh.softclaims.faction;

import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.List;

public interface IFactionImplementation {
    /**
     * Registers the events of the faction implementation.
     */
    void registerEvents();

    /**
     * Gets the list of factions on the server.
     * @return The list of factions.
     */
    List<Faction> getAllFactions();

    /**
     * Gets faction by name.
     * @param factionId The name of the faction.
     * @return The faction if found.
     */
    Faction getFaction(String factionId) throws FactionDoesntExistException;

    /**
     * Gets faction by chunk.
     * @param factionChunk A chunk that belongs to the faction.
     * @return The faction if found.
     */
    Faction getFaction(Chunk factionChunk);

    /**
     * Gets the faction which the specified player is a member of.
     * @param factionMember The player.
     * @return The faction if found.
     */
    Faction getFaction(Player factionMember);

    /**
     * Checks whether the player has a specific permission in the faction.
     * @param player The player.
     * @param factionId The name of the faction.
     * @param permission The permission.
     * @return Whether the player has the specified permission in the faction.
     */
    boolean hasPlayerPermission(String factionId, Player player, FactionPermission permission)
            throws FactionDoesntExistException;

    /**
     * Lists all chunks claimed by factions.
     * @param factionId The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    List<Chunk> getAllFactionChunks(String factionId);

    /**
     * Claims a chunk for a faction.
     * @param chunk The chunk.
     * @param factionId The id of the faction.
     */
    void claimChunk(String factionId, Chunk chunk) throws FactionDoesntExistException;

    /**
     * Unclaims a chunk for a faction.
     * @param chunk The chunk.
     * @param factionId The id of the faction.
     */
    void unclaimChunk(String factionId, Chunk chunk) throws FactionDoesntExistException;
}
