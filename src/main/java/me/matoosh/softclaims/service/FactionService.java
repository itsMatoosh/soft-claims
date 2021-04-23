package me.matoosh.softclaims.service;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.faction.Faction;
import me.matoosh.softclaims.faction.IFactionImplementation;
import me.matoosh.softclaims.faction.NoFactionImplementation;
import me.matoosh.softclaims.faction.factionsx.FactionsxImplementation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.List;

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
     * Gets faction by name.
     * @param factionName The name of the faction.
     * @return The faction if found.
     */
    public Faction getFaction(String factionName) {
        return factionImplementation.getFaction(factionName);
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
        return factionImplementation.canPlayerDestroyInFaction(player, factionChunk);
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
     * @param factionName The faction to get chunks for.
     * @return All chunks claimed by factions.
     */
    public List<Chunk> getAllFactionChunks(String factionName) {
        return factionImplementation.getAllFactionChunks(factionName);
    }

    /**
     * Claims chunks for a faction.
     * @param chunks The chunks.
     * @param player The claiming player.
     * @param factionName The name of the faction.
     */
    public void claimChunks(String factionName, Player player, List<Chunk> chunks)
            throws FactionDoesntExistException {
        factionImplementation.claimChunks(factionName, player, chunks);
    }
}
