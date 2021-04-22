package me.matoosh.softclaims;

import co.aikar.commands.BukkitCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import me.matoosh.softclaims.commands.SoftClaimsCommand;
import me.matoosh.softclaims.durability.BlockDurabilityService;
import me.matoosh.softclaims.durability.BlockRepairService;
import me.matoosh.softclaims.durability.CommunicationService;
import me.matoosh.softclaims.events.BlockBreakHandler;
import me.matoosh.softclaims.events.DiggersHandler;
import me.matoosh.softclaims.events.ExplosionHandler;
import me.matoosh.softclaims.events.RightClickHandler;
import me.matoosh.softclaims.faction.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.dependency.Dependency;
import org.bukkit.plugin.java.annotation.dependency.SoftDependency;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Plugin(name = "SoftClaims", version = "1.7")
@ApiVersion(ApiVersion.Target.v1_15)
@Description("Blocks in other factions' land are much harder to break.")
@Author("itsMatoosh")
@Website("juulcraft.csrv.pl")
@Dependency("ProtocolLib")
@SoftDependency("FactionsX")
public class SoftClaimsPlugin extends JavaPlugin {

    private ProtocolManager protocolManager;
    private DiggersHandler diggersHandler;
    private BlockDurabilityService blockDurabilityService;
    private final CommunicationService communicationService = new CommunicationService();
    private final FactionService factionService = new FactionService(this);
    private final BlockRepairService blockRepairService = new BlockRepairService(this);

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
        this.blockRepairService.initialize();

        // load data
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for(Chunk chunk : world.getLoadedChunks()) {
                try {
                    tasks.add(getBlockDurabilityService().getDurabilityStorage().loadChunk(chunk));
                } catch (ChunkAlreadyLoadedException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
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
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for(Chunk chunk : world.getLoadedChunks()) {
                try {
                    tasks.add(getBlockDurabilityService().getDurabilityStorage().persistChunk(chunk, true));
                } catch (ChunkNotLoadedException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
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
        diggersHandler = new DiggersHandler(this);
        protocolManager.addPacketListener(diggersHandler);
    }

    /**
     * Registers bukkit events.
     */
    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(diggersHandler, this);
        Bukkit.getPluginManager().registerEvents(new ExplosionHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new RightClickHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockBreakHandler(this), this);
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    public BlockDurabilityService getBlockDurabilityService() {
        return blockDurabilityService;
    }

    public CommunicationService getCommunicationService() {
        return communicationService;
    }

    public FactionService getFactionService() {
        return factionService;
    }

    public DiggersHandler getDiggersHandler() {
        return diggersHandler;
    }

    public BlockRepairService getBlockRepairService() {
        return blockRepairService;
    }
}
