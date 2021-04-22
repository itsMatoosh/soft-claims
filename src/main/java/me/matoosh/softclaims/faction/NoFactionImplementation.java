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
     * @param factionName The name of the faction.
     * @return The faction if found.
     */
    @Override
    public Faction getFaction(String factionName) {
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
     * Checks whether the player can break blocks
     * in the faction at chunk.
     *
     * @param player       The player.
     * @param factionChunk The chunk to be checked.
     * @return Whether the player can break blocks in the faction.
     */
    @Override
    public boolean canPlayerDestroyInFaction(Player player, Chunk factionChunk) {
        return true;
    }

    /**
     * Lists all chunks claimed by factions.
     *
     * @param factionName The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    @Override
    public List<Chunk> getAllFactionChunks(String factionName) {
        return Collections.emptyList();
    }
}
