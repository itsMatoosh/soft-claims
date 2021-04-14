package me.matoosh.softclaims.faction;

import org.bukkit.Chunk;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class NoFactionImplementation implements IFactionImplementation {
    @Override
    public void registerEvents() {}

    @Override
    public List<String> getFactions() {
        return Collections.emptyList();
    }

    @Override
    public boolean chargeFaction(String factionName, double price) {
        return false;
    }

    @Override
    public boolean isInFactionLand(Chunk factionChunk) {
        return false;
    }

    @Override
    public boolean canPlayerDestroyInFaction(UUID uuid, Chunk factionChunk) {
        return false;
    }

    @Override
    public List<Chunk> getAllFactionChunks() {
        return Collections.emptyList();
    }
}
