package me.matoosh.softclaims.faction.saberfactions;

import com.massivecraft.factions.*;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.faction.Faction;
import me.matoosh.softclaims.faction.FactionPermission;
import me.matoosh.softclaims.faction.IFactionImplementation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SaberFactionsImplementation implements IFactionImplementation {

    private final SoftClaimsPlugin plugin;

    @Override
    public void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new SaberFactionsEventHandler(plugin), plugin);
    }

    @Override
    public List<Faction> getAllFactions() {
        return Factions.getInstance().getAllFactions().stream()
                .map(SaberFactionsImplementation::toFaction)
                .collect(Collectors.toList());
    }

    @Override
    public Faction getFaction(String factionId) throws FactionDoesntExistException {
        com.massivecraft.factions.Faction f = Factions.getInstance().getFactionById(factionId);
        if (f == null) {
            throw new FactionDoesntExistException();
        }
        return toFaction(f);
    }

    @Override
    public Faction getFaction(Chunk factionChunk) throws FactionDoesntExistException {
        FLocation fLocation = new FLocation(factionChunk.getWorld().getName(), factionChunk.getX(), factionChunk.getZ());
        com.massivecraft.factions.Faction f = Board.getInstance().getFactionAt(fLocation);
        if (f == null || f.isSystemFaction()) {
            throw new FactionDoesntExistException();
        }
        return toFaction(f);
    }

    @Override
    public Faction getFaction(Player factionMember) throws FactionDoesntExistException {
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(factionMember);
        if (fPlayer.getFaction() == null || fPlayer.getFaction().isSystemFaction()) {
            throw new FactionDoesntExistException();
        }
        return toFaction(fPlayer.getFaction());
    }

    @Override
    public boolean hasPlayerPermission(String factionId, Player player, FactionPermission permission) throws FactionDoesntExistException {
        // get player and faction
        com.massivecraft.factions.FPlayer p = FPlayers.getInstance().getByPlayer(player);
        com.massivecraft.factions.Faction f = Factions.getInstance().getFactionById(factionId);
        if (f == null || f.isSystemFaction()) {
            throw new FactionDoesntExistException();
        }

        // check the permission
        PermissableAction action = toPermissableAction(permission);
        Access access = f.getAccess(p, action);
        return access == Access.ALLOW;
    }

    @Override
    public List<Chunk> getAllFactionChunks(String factionId) throws FactionDoesntExistException {
        com.massivecraft.factions.Faction f = Factions.getInstance().getFactionById(factionId);
        if (f == null) {
            throw new FactionDoesntExistException();
        }
        return Board.getInstance().getAllClaims(f).stream().map(FLocation::getChunk).collect(Collectors.toList());
    }

    @Override
    public void claimChunk(String factionId, World world, int chunkX, int chunkZ) throws FactionDoesntExistException {
        com.massivecraft.factions.Faction f = Factions.getInstance().getFactionById(factionId);
        FLocation location = new FLocation(world.getName(), chunkX, chunkZ);
        if (f == null) {
            throw new FactionDoesntExistException();
        }
        Board.getInstance().setFactionAt(f, location);
    }

    @Override
    public void unclaimChunk(String factionId, World world, int chunkX, int chunkZ) throws FactionDoesntExistException {
        com.massivecraft.factions.Faction f = Factions.getInstance().getFactionById(factionId);
        FLocation location = new FLocation(world.getName(), chunkX, chunkZ);
        if (f == null) {
            throw new FactionDoesntExistException();
        }
        Board.getInstance().setFactionAt(Factions.getInstance().getWilderness(), location);
    }

    /**
     * Converts a saber faction to a universal faction.
     * @param f Saber faction instance.
     * @return The universal faction object.
     */
    public static Faction toFaction(com.massivecraft.factions.Faction f) {
        return new Faction(f.getId(), f.getTag(), f.getPower());
    }

    /**
     * Converts a universal faction permission to a saber factions permission.
     * @param permission
     * @return
     */
    public static PermissableAction toPermissableAction(FactionPermission permission) {
        return switch (permission) {
            case BREAK_BLOCK -> PermissableAction.DESTROY;
            case CLAIM_CHUNK, UNCLAIM_CHUNK -> PermissableAction.TERRITORY;
        };
    }
}
