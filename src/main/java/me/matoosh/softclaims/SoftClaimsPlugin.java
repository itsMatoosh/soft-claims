package me.matoosh.softclaims;

import co.aikar.commands.BukkitCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.matoosh.softclaims.commands.SoftClaimsCommand;
import me.matoosh.softclaims.durability.BlockDurabilityService;
import me.matoosh.softclaims.durability.BlockRepairService;
import me.matoosh.softclaims.durability.CommunicationService;
import me.matoosh.softclaims.events.ChunkLoadHandler;
import me.matoosh.softclaims.events.DiggersHandler;
import me.matoosh.softclaims.events.ExplosionHandler;
import me.matoosh.softclaims.events.RightClickHandler;
import me.matoosh.softclaims.faction.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.java.annotation.dependency.Dependency;
import org.bukkit.plugin.java.annotation.dependency.SoftDependency;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

import java.io.File;

@Plugin(name = "SoftClaims", version = "1.6")
@ApiVersion(ApiVersion.Target.v1_15)
@Description("Blocks in other factions' land are much harder to break.")
@Author("itsMatoosh")
@Website("juulcraft.csrv.pl")
@Dependency("ProtocolLib")
@SoftDependency("FactionsX")
public class SoftClaimsPlugin extends JavaPlugin {

    private ProtocolManager protocolManager;
    private DiggersHandler diggersHandler;
    private final BlockDurabilityService blockDurabilityService = new BlockDurabilityService(this);
    private final CommunicationService communicationService = new CommunicationService();
    private final FactionService factionService = new FactionService(this);
    private final BlockRepairService blockRepairService = new BlockRepairService(this);

    public SoftClaimsPlugin() {
        super();
    }

    protected SoftClaimsPlugin(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
    }

    @Override
    public void onEnable() {
        // save config
        this.saveDefaultConfig();

        // register commands
        registerCommands();

        // register events
        registerEvents();

        // init faction service
        this.factionService.initialize();

        // init block repair service
        this.blockRepairService.initialize();

        // load data
        for (World world :
                Bukkit.getWorlds()) {
            for(Chunk chunk : world.getLoadedChunks()) {
                getBlockDurabilityService().loadChunk(chunk);
            }
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
        getLogger().info("Saving block info...");
        getBlockDurabilityService().persistAll();

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
        Bukkit.getPluginManager().registerEvents(new ChunkLoadHandler(this), this);
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
