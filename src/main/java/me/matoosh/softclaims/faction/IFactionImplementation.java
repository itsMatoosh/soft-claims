package me.matoosh.softclaims.faction;

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
     * @param factionName The name of the faction.
     * @return The faction if found.
     */
    Faction getFaction(String factionName);

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
     * Checks whether the player can break blocks
     * in the faction at chunk.
     * @param player The player.
     * @param factionChunk The chunk to be checked.
     * @return Whether the player can break blocks in the faction.
     */
    boolean canPlayerDestroyInFaction(Player player, Chunk factionChunk);

    /**
     * Lists all chunks claimed by factions.
     * @param factionName The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    List<Chunk> getAllFactionChunks(String factionName);
}
