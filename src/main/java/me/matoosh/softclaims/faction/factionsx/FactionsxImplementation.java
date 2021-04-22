package me.matoosh.softclaims.faction.factionsx;

import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.faction.IFactionImplementation;
import net.prosavage.factionsx.core.FPlayer;
import net.prosavage.factionsx.core.Faction;
import net.prosavage.factionsx.manager.FactionManager;
import net.prosavage.factionsx.manager.GridManager;
import net.prosavage.factionsx.manager.PlayerManager;
import net.prosavage.factionsx.persist.data.FLocation;
import net.prosavage.factionsx.util.PlayerAction;
import net.prosavage.factionsx.util.Relation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FactionsxImplementation implements IFactionImplementation {

    private final SoftClaimsPlugin plugin;

    public FactionsxImplementation(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new FactionsxEventHandler(plugin), plugin);
    }

    @Override
    public List<me.matoosh.softclaims.faction.Faction> getAllFactions() {
        return FactionManager.INSTANCE.getFactions()
                .parallelStream()
                .filter((f) -> !f.isSystemFaction())
                .map(this::toGenericFaction)
                .collect(Collectors.toList());
    }

    /**
     * Gets faction by name.
     *
     * @param factionName The name of the faction.
     * @return The faction if found.
     */
    @Override
    public me.matoosh.softclaims.faction.Faction getFaction(String factionName) {
        Faction faction = FactionManager.INSTANCE.getFaction(factionName);
        if (faction == null) {
            return null;
        }
        return toGenericFaction(faction);
    }

    /**
     * Gets faction by chunk.
     *
     * @param factionChunk A chunk that belongs to the faction.
     * @return The faction if found.
     */
    @Override
    public me.matoosh.softclaims.faction.Faction getFaction(Chunk factionChunk) {
        Faction faction = getFactionByChunk(factionChunk);
        if (faction.isSystemFaction()) {
            return null;
        }
        return toGenericFaction(faction);
    }

    private Faction getFactionByChunk(Chunk factionChunk) {
        return GridManager.INSTANCE.getFactionAt(factionChunk);
    }

    /**
     * Gets the faction which the specified player is a member of.
     *
     * @param factionMember The player.
     * @return The faction if found.
     */
    @Override
    public me.matoosh.softclaims.faction.Faction getFaction(Player factionMember) {
        Faction faction = getFactionByPlayer(factionMember);
        if (faction.isSystemFaction()) {
            return null;
        }
        return toGenericFaction(faction);
    }

    private Faction getFactionByPlayer(Player player) {
        FPlayer fPlayer = PlayerManager.INSTANCE.getFPlayer(player);
        return fPlayer.getFaction();
    }

    @Override
    public boolean canPlayerDestroyInFaction(Player player, Chunk factionChunk) {
        Faction faction = getFactionByChunk(factionChunk);
        Faction userFaction = getFactionByPlayer(player);

        // check if faction exists
        if (faction.isSystemFaction()) return true;
        // check if player has a faction
        if (userFaction.isSystemFaction()) return false;

        // check if player is member of this faction
        if (faction.equals(userFaction)) return true;
        // check what the faction relation is
        Relation relation = faction.getRelationTo(userFaction);
        // check if the relation has build permissions
        return faction.getRelationPerms()
                .getPermForRelation(relation, PlayerAction.BREAK_BLOCK);
    }

    @Override
    public List<Chunk> getAllFactionChunks(String factionName) {
        Faction faction = FactionManager.INSTANCE.getFaction(factionName);
        if (faction == null) return Collections.emptyList();
        return GridManager.INSTANCE.getAllClaims(faction)
                .stream().map(FLocation::getChunk)
                .collect(Collectors.toList());
    }

    /**
     * Converts a FactionsX faction into a generic SoftClaims faction.
     * @param faction The FactionsX faction.
     * @return Generic faction.
     */
    public me.matoosh.softclaims.faction.Faction toGenericFaction(Faction faction) {
        return new me.matoosh.softclaims.faction.Faction(
                faction.getTag(), faction.getPower(), faction.isSystemFaction());
    }
}
