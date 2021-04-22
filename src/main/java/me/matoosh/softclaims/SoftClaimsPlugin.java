package me.matoosh.softclaims;

import co.aikar.commands.BukkitCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import lombok.Getter;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
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

import java.util.concurrent.ExecutionException;

@Plugin(name = "SoftClaims", version = "1.8.0")
@ApiVersion(ApiVersion.Target.v1_15)
@Description("Blocks in other factions' land are much harder to break.")
@Author("itsMatoosh")
@Website("juulcraft.csrv.pl")
@Dependency("ProtocolLib")
@SoftDependency("FactionsX")
@Getter
public class SoftClaimsPlugin extends JavaPlugin {

    private final DiggersHandler diggersHandler = new DiggersHandler(this);
    private final CommunicationService communicationService = new CommunicationService();
    private final FactionService factionService = new FactionService(this);
    private final BlockRepairService blockRepairService = new BlockRepairService(this);
    private final FactionCoreService factionCoreService = new FactionCoreService(this);
    private final WorldService worldService = new WorldService(this);

    private ProtocolManager protocolManager;
    private BlockDurabilityService blockDurabilityService;

    @Override
    public void onEnable() {
        // save config
        this.saveDefaultConfig();

        // register commands
        registerCommands();

        // register events
        registerEvents();

        // init block durability service
        blockDurabilityService = new BlockDurabilityService(this);

        // init faction service
        this.factionService.initialize();

        // init block repair service
        this.blockRepairService.reload();

        // init world service
        this.worldService.reload();

        // load data
        try {
            this.worldService.loadMetadataForLoadedChunks().get();
        } catch (InterruptedException | ExecutionException | ChunkAlreadyLoadedException e) {
            e.printStackTrace();
        }

        getLogger().info("Soft Claims enabled!");
    }

    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();

        // register events
        registerProtocolEvents();
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling SoftClaims...");
        getLogger().info("Saving durability info...");
        // save remaining loaded chunks
        try {
            this.worldService.saveMetadataForLoadedChunks().get();
        } catch (InterruptedException | ExecutionException | ChunkNotLoadedException e) {
            e.printStackTrace();
        }
        getLogger().info("Soft Claims disabled!");
    }

    /**
     * Registers commands.
     */
    private void registerCommands() {
        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new SoftClaimsCommand(this));
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
        Bukkit.getPluginManager().registerEvents(new BlockPlaceHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockBreakHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new ExplosionHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new RightClickHandler(this), this);
    }
}
