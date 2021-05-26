package me.matoosh.softclaims.service;

import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.faction.Faction;
import me.matoosh.softclaims.faction.FactionPermission;
import me.matoosh.softclaims.faction.IFactionImplementation;
import me.matoosh.softclaims.faction.NoFactionImplementation;
import me.matoosh.softclaims.faction.factionsx.FactionsxImplementation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.List;

@RequiredArgsConstructor
public class FactionService {

    private final SoftClaimsPlugin plugin;

    private IFactionImplementation factionImplementation;

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
     * Gets faction by name.
     * @param factionId The name of the faction.
     * @return The faction if found.
     */
    public Faction getFaction(String factionId) throws FactionDoesntExistException {
        return factionImplementation.getFaction(factionId);
    }

    /**
     * Gets faction by chunk.
     * @param factionChunk A chunk that belongs to the faction.
     * @return The faction if found.
     */
    public Faction getFaction(Chunk factionChunk) {
        return factionImplementation.getFaction(factionChunk);
    }

    /**
     * Gets the faction which the specified player is a member of.
     * @param factionMember The player.
     * @return The faction if found.
     */
    public Faction getFaction(Player factionMember) {
        return factionImplementation.getFaction(factionMember);
    }

    /**
     * Checks whether the given chunk is in faction land.
     * @param chunk The chunk.
     * @return Whether the chunk is in faction land.
     */
    public boolean isInFactionLand(Chunk chunk) {
        Faction faction = getFaction(chunk);
        return faction != null;
    }

    /**
     * Checks whether the player can break blocks
     * in the faction at chunk.
     * @param player The player.
     * @param factionChunk The chunk to be checked.
     * @return Whether the player can break blocks in the faction.
     */
    public boolean canPlayerDestroyInFaction(Player player, Chunk factionChunk) {
        // get faction at chunk
        Faction faction = getFaction(factionChunk);
        if (faction == null) {
            return true;
        }

        // get player's permission to destroy
        try {
            return hasPlayerPermission(faction.getId(), player, FactionPermission.BREAK_BLOCK);
        } catch (FactionDoesntExistException e) {
            return true;
        }
    }

    /**
     * Checks whether the player has a specific permission in the faction.
     * @param player The player.
     * @param factionId The name of the faction.
     * @param permission The permission.
     * @return Whether the player has the specified permission in the faction.
     */
    public boolean hasPlayerPermission(String factionId, Player player, FactionPermission permission)
            throws FactionDoesntExistException {
        return factionImplementation.hasPlayerPermission(factionId, player, permission);
    }

    /**
     * Gets the list of factions on the server.
     * @return The list of factions.
     */
    public List<Faction> getAllFactions() {
        return factionImplementation.getAllFactions();
    }

    /**
     * Lists all chunks claimed by factions.
     * @param factionId The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    public List<Chunk> getAllFactionChunks(String factionId) {
        return factionImplementation.getAllFactionChunks(factionId);
    }

    /**
     * Claims a chunk for a faction.
     * @param chunk The chunk.
     * @param factionId The name of the faction.
     */
    public void claimChunk(String factionId, Chunk chunk)
            throws FactionDoesntExistException {
        factionImplementation.claimChunk(factionId, chunk);
    }

    /**
     * Unclaims a chunk for a faction.
     * @param chunk The chunk.
     * @param factionId The name of the faction.
     */
    public void unclaimChunk(String factionId, Chunk chunk)
            throws FactionDoesntExistException {
        factionImplementation.unclaimChunk(factionId, chunk);
    }
}
