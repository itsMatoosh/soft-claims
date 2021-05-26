package me.matoosh.softclaims.faction.factionsx;

import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.faction.FactionPermission;
import me.matoosh.softclaims.faction.IFactionImplementation;
import net.prosavage.factionsx.core.FPlayer;
import net.prosavage.factionsx.core.Faction;
import net.prosavage.factionsx.manager.FactionManager;
import net.prosavage.factionsx.manager.GridManager;
import net.prosavage.factionsx.manager.PlayerManager;
import net.prosavage.factionsx.persist.data.FLocation;
import net.prosavage.factionsx.util.MemberAction;
import net.prosavage.factionsx.util.PlayerAction;
import net.prosavage.factionsx.util.Relation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class FactionsxImplementation implements IFactionImplementation {

    private final SoftClaimsPlugin plugin;

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
     * @param factionId The name of the faction.
     * @return The faction if found.
     */
    @Override
    public me.matoosh.softclaims.faction.Faction getFaction(String factionId)
            throws FactionDoesntExistException {
        Faction faction = getFactionById(factionId);
        if (faction == null) {
            throw new FactionDoesntExistException();
        }
        return toGenericFaction(faction);
    }

    private Faction getFactionById(String factionId) {
        long id = Long.parseLong(factionId);
        Faction faction = FactionManager.INSTANCE.getFaction(id);
        if (faction.isSystemFaction()) {
            return null;
        } else {
            return faction;
        }
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

    /**
     * Checks whether the player has a specific permission in the faction.
     *
     * @param player      The player.
     * @param factionId The name of the faction.
     * @param permission  The permission.
     * @return Whether the player has the specified permission in the faction.
     */
    @Override
    public boolean hasPlayerPermission(String factionId, Player player, FactionPermission permission)
            throws FactionDoesntExistException {
        // get faction
        Faction faction = getFactionById(factionId);
        // check if faction exists
        if (faction == null) {
            throw new FactionDoesntExistException();
        }

        // get player's faction
        Faction playerFaction = getFactionByPlayer(player);

        // check if player has a faction
        if (playerFaction.isSystemFaction()) return false;

        // check if player is member of this faction
        Enum fXAction = getAction(permission);
        if (faction.equals(playerFaction)) {
            // get fplayer
            FPlayer fPlayer = PlayerManager.INSTANCE.getFPlayer(player);
            if (fXAction instanceof PlayerAction) {
                return fPlayer.getRole().canDoPlayerAction((PlayerAction) fXAction);
            } else if (fXAction instanceof MemberAction) {
                return fPlayer.getRole().canDoMemberAction((MemberAction) fXAction);
            }
        } else if (fXAction instanceof PlayerAction) {
            // check what the faction relation is
            Relation relation = faction.getRelationTo(playerFaction);
            // check if the relation has build permissions
            return faction.getRelationPerms().getPermForRelation(
                    relation, (PlayerAction) fXAction);
        }
        return false;
    }

    private Faction getFactionByPlayer(Player player) {
        FPlayer fPlayer = PlayerManager.INSTANCE.getFPlayer(player);
        return fPlayer.getFaction();
    }

    @Override
    public List<Chunk> getAllFactionChunks(String factionId) {
        Faction faction = getFactionById(factionId);
        if (faction == null) {
            return Collections.emptyList();
        }
        return GridManager.INSTANCE.getAllClaims(faction)
                .stream().map(FLocation::getChunk)
                .collect(Collectors.toList());
    }

    /**
     * Claims a chunk for a faction.
     *
     * @param factionId The id of the faction.
     * @param chunk     The chunk.
     */
    @Override
    public void claimChunk(String factionId, Chunk chunk) throws FactionDoesntExistException {
        Faction faction = getFactionById(factionId);
        if (faction == null) {
            throw new FactionDoesntExistException();
        }
        GridManager.INSTANCE.claim(faction,
                new FLocation(chunk.getX(), chunk.getZ(), chunk.getWorld().getName()));
    }

    /**
     * Unclaims a chunk for a faction.
     *
     * @param factionId The id of the faction.
     * @param chunk     The chunk.
     */
    @Override
    public void unclaimChunk(String factionId, Chunk chunk) throws FactionDoesntExistException {
        Faction faction = getFactionById(factionId);
        if (faction == null) {
            throw new FactionDoesntExistException();
        }
        GridManager.INSTANCE.unclaim(faction,
                new FLocation(chunk.getX(), chunk.getZ(), chunk.getWorld().getName()));
    }

    /**
     * Converts a FactionsX faction into a generic SoftClaims faction.
     * @param faction The FactionsX faction.
     * @return Generic faction.
     */
    public me.matoosh.softclaims.faction.Faction toGenericFaction(Faction faction) {
        return new me.matoosh.softclaims.faction.Faction(
                faction.getId() + "", faction.getTag(),
                faction.getPower(), faction.isSystemFaction());
    }

    /**
     * Converts generic faction permission to FactionsX action.
     * @param permission Generic permission.
     * @return FactionsX action.
     */
    public Enum getAction(FactionPermission permission) {
        switch (permission) {
            case BREAK_BLOCK:
                return PlayerAction.BREAK_BLOCK;
            case CLAIM_CHUNK:
                return MemberAction.CLAIM;
            case UNCLAIM_CHUNK:
                return MemberAction.UNCLAIM;
        }
        return null;
    }
}
