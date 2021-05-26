package me.matoosh.softclaims.faction;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class NoFactionImplementation implements IFactionImplementation {
    /**
     * Registers the events of the faction implementation.
     */
    @Override
    public void registerEvents() {}

    /**
     * Gets the list of factions on the server.
     *
     * @return The list of factions.
     */
    @Override
    public List<Faction> getAllFactions() {
        return Collections.emptyList();
    }

    /**
     * Gets faction by name.
     *
     * @param factionId The name of the faction.
     * @return The faction if found.
     */
    @Override
    public Faction getFaction(String factionId) {
        return null;
    }

    /**
     * Gets faction by chunk.
     *
     * @param factionChunk A chunk that belongs to the faction.
     * @return The faction if found.
     */
    @Override
    public Faction getFaction(Chunk factionChunk) {
        return null;
    }

    /**
     * Gets the faction which the specified player is a member of.
     *
     * @param factionMember The player.
     * @return The faction if found.
     */
    @Override
    public Faction getFaction(Player factionMember) {
        return null;
    }

    /**
     * Checks whether the player has a specific permission in the faction.
     *
     * @param player      The player.
     * @param factionName The name of the faction.
     * @param permission  The permission.
     * @return Whether the player has the specified permission in the faction.
     */
    @Override
    public boolean hasPlayerPermission(String factionName, Player player, FactionPermission permission) {
        return true;
    }

    /**
     * Lists all chunks claimed by factions.
     *
     * @param factionId The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    @Override
    public List<Chunk> getAllFactionChunks(String factionId) {
        return Collections.emptyList();
    }

    /**
     * Claims a chunk for a faction.
     *
     * @param factionId The id of the faction.
     * @param chunk     The chunk.
     */
    @Override
    public void claimChunk(String factionId, Chunk chunk) {
    }

    /**
     * Unclaims a chunk for a faction.
     *
     * @param factionId The id of the faction.
     * @param chunk     The chunk.
     */
    @Override
    public void unclaimChunk(String factionId, Chunk chunk) {
    }
}
