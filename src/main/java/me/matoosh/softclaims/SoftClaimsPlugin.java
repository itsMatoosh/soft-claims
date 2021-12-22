package me.matoosh.softclaims;

import co.aikar.commands.BukkitCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import lombok.Getter;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.softclaims.commands.SoftClaimsCommand;
import me.matoosh.softclaims.events.*;
import me.matoosh.softclaims.service.*;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.dependency.Dependency;
import org.bukkit.plugin.java.annotation.dependency.SoftDependency;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

import java.nio.file.Path;

@Plugin(name = "SoftClaims", version = "1.8.0")
@ApiVersion(ApiVersion.Target.v1_17)
@Description("Blocks in other factions' land are much harder to break.")
@Author("itsMatoosh")
@Website("juulcraft.csrv.pl")
@Dependency("ProtocolLib")
@SoftDependency("Factions")
@Getter
public class SoftClaimsPlugin extends JavaPlugin {

    private CommunicationService communicationService;
    private FactionService factionService;
    private ProtocolManager protocolManager;
    private BlockDurabilityService blockDurabilityService;
    private FactionCoreService factionCoreService;
    private DiggersHandler diggersHandler;
    private BlockRepairService blockRepairService;
    private WorldService worldService;
    private BukkitAudiences adventure;

    @Override
    public void onEnable() {
        // save config
        this.saveDefaultConfig();

        // initialize an audiences instance for the plugin
        this.adventure = BukkitAudiences.create(this);

        // create services
        communicationService = new CommunicationService(this);
        factionService = new FactionService(this);
        worldService = new WorldService(this);

        // create durability service
        Path durabilitiesDataDir = getDataFolder().toPath().resolve("data").resolve("durabilities");
        blockDurabilityService = new BlockDurabilityService(worldService, factionService, this,
                new BlockMetadataStorage<>(this, durabilitiesDataDir));
        blockRepairService = new BlockRepairService(factionService, blockDurabilityService, this);

        // create faction core service
        Path coresDataDir = getDataFolder().toPath().resolve("data").resolve("cores");
        factionCoreService = new FactionCoreService(this, factionService, worldService,
                new BlockMetadataStorage<>(this, coresDataDir));

        // init faction service
        this.factionService.initialize();

        // init block repair service
        this.blockRepairService.reload();

        // init world service
        this.worldService.reload();

        // create protocol manager
        protocolManager = ProtocolLibrary.getProtocolManager();

        // create handlers
        diggersHandler = new DiggersHandler(factionService, blockDurabilityService,
                communicationService, protocolManager, this);

        // register commands
        registerCommands();

        // register events
        registerEvents();

        // register events
        registerProtocolEvents();

        getLogger().info("Soft Claims enabled!");
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        getLogger().info("Soft Claims disabled!");
    }

    /**
     * Registers commands.
     */
    private void registerCommands() {
        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new SoftClaimsCommand(blockRepairService, worldService, this));
    }

    /**
     * Registers protocol events.
     */
    private void registerProtocolEvents() {
        protocolManager.addPacketListener(diggersHandler);
    }

    /**
     * Registers bukkit events.
     */
    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(diggersHandler, this);
        Bukkit.getPluginManager().registerEvents(new FactionCoreBlockHandler(
                worldService, factionCoreService, this), this);
        Bukkit.getPluginManager().registerEvents(new BlockBreakHandler(
                blockDurabilityService, factionService, diggersHandler), this);
        Bukkit.getPluginManager().registerEvents(new ExplosionHandler(
                blockDurabilityService, factionService, worldService, this), this);
        Bukkit.getPluginManager().registerEvents(new RightClickHandler(
                blockDurabilityService, factionCoreService, communicationService, this), this);
    }
}
