package me.matoosh.softclaims.service;

import lombok.Getter;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.softclaims.SoftClaimsPlugin;

import java.nio.file.Path;

@Getter
public class FactionCoreService {

    private final SoftClaimsPlugin plugin;

    /**
     * Faction core metadata storage.
     */
    private final BlockMetadataStorage<String> factionCoreStorage;

    public FactionCoreService(SoftClaimsPlugin plugin) {
        // save plugin reference
        this.plugin = plugin;

        // get data folder
        Path durabilitiesDataDir = plugin.getDataFolder().toPath()
                .resolve("data").resolve("cores");

        // create durabilities storage
        this.factionCoreStorage = new BlockMetadataStorage<>(plugin, durabilitiesDataDir);
    }
}
