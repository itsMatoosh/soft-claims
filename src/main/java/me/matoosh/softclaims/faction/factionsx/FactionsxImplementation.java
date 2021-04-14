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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    public List<String> getFactions() {
        return null;
    }

    @Override
    public boolean chargeFaction(String factionName, double price) {
        Faction faction = FactionManager.INSTANCE.getFaction(factionName);
        if (faction == null) return false;
        Faction.Bank bank = faction.getBank();
        if (bank.getAmount() < price) return false;
        double account = bank.getAmount();
        account -= price;
        bank.setAmount(account);
        return true;
    }

    @Override
    public boolean isInFactionLand(Chunk factionChunk) {
        return !getFactionByChunk(factionChunk).isSystemFaction();
    }

    @Override
    public boolean canPlayerDestroyInFaction(UUID uuid, Chunk factionChunk) {
        Faction faction = getFactionByChunk(factionChunk);
        Faction userFaction = getFactionByPlayer(uuid);
        // check if user has a faction
        if (userFaction == null) return false;
        // check if user is member of this faction
        if (faction == userFaction) return true;
        // check what the faction relation is
        Relation relation = faction.getRelationTo(userFaction);
        // check if the relation has build permissions
        return faction.getRelationPerms()
                .getPermForRelation(relation, PlayerAction.BREAK_BLOCK);
    }

    @Override
    public List<Chunk> getAllFactionChunks() {
        List<Chunk> chunks = new ArrayList<>();
        for (Faction faction : FactionManager.INSTANCE.getFactions()) {
            if (faction.isSystemFaction()) continue;
            chunks.addAll(GridManager.INSTANCE.getAllClaims(faction)
                    .stream().map(FLocation::getChunk).collect(Collectors.toList()));
        }
        return chunks;
    }

    private Faction getFactionByChunk(Chunk factionChunk) {
        return GridManager.INSTANCE.getFactionAt(factionChunk);
    }

    private Faction getFactionByPlayer(UUID player) {
        FPlayer fPlayer = PlayerManager.INSTANCE.getFPlayer(player);
        if (fPlayer == null) {
            return null;
        }
        return fPlayer.getFaction();
    }
}
