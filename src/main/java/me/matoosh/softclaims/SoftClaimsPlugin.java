package me.matoosh.softclaims;

import co.aikar.commands.BukkitCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import lombok.Getter;
import me.matoosh.softclaims.commands.SoftClaimsCommand;
import me.matoosh.softclaims.events.*;
import me.matoosh.softclaims.service.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.dependency.Dependency;
import org.bukkit.plugin.java.annotation.dependency.SoftDependency;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

@Plugin(name = "SoftClaims", version = "1.8.0")
@ApiVersion(ApiVersion.Target.v1_15)
@Description("Blocks in other factions' land are much harder to break.")
@Author("itsMatoosh")
@Website("juulcraft.csrv.pl")
@Dependency("ProtocolLib")
@SoftDependency("FactionsX")
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

    @Override
    public void onEnable() {
        // save config
        this.saveDefaultConfig();

        // create services
        communicationService = new CommunicationService();
        factionService = new FactionService(this);
        worldService = new WorldService(this);
        blockDurabilityService = new BlockDurabilityService(worldService, factionService, this);
        blockRepairService = new BlockRepairService(factionService, blockDurabilityService, this);
        factionCoreService = new FactionCoreService(this, factionService, worldService);

        // create handlers
        diggersHandler = new DiggersHandler(factionService, blockDurabilityService,
                communicationService, protocolManager, this);

        // register commands
        registerCommands();

        // register events
        registerEvents();

        // init faction service
        this.factionService.initialize();

        // init block repair service
        this.blockRepairService.reload();

        // init world service
        this.worldService.reload();

        getLogger().info("Soft Claims enabled!");
    }

    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();

        // register events
        registerProtocolEvents();
    }

    @Override
    public void onDisable() {
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
                blockDurabilityService, communicationService, this), this);
    }
}
