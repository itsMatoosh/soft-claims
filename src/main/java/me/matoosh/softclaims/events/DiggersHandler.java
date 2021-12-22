package me.matoosh.softclaims.events;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import io.papermc.lib.PaperLib;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.service.BlockDurabilityService;
import me.matoosh.softclaims.service.CommunicationService;
import me.matoosh.softclaims.service.FactionService;
import me.matoosh.softclaims.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

@RequiredArgsConstructor
public class DiggersHandler implements PacketListener, Listener {
    /**
     * Maximum distance one can dig at.
     */
    private static final int DIG_MAX_DISTANCE = 7;

    /**
     * Effect applied to slow down player digging.
     */
    private static final PotionEffect FATIGUE_EFFECT = new PotionEffect(
            PotionEffectType.SLOW_DIGGING, 150, 3, false, false);

    private final FactionService factionService;
    private final BlockDurabilityService blockDurabilityService;
    private final CommunicationService communicationService;
    private final ProtocolManager protocolManager;
    private final SoftClaimsPlugin plugin;

    private final HashMap<Integer, DigProgress> diggers = new HashMap<>();

    @Override
    public void onPacketSending(PacketEvent packetEvent) {}

    @Override
    public void onPacketReceiving(PacketEvent packetEvent) {
        // check dig event
        if(packetEvent.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {
            // check creative
            if(packetEvent.getPlayer().getGameMode() == GameMode.CREATIVE
                    || packetEvent.getPlayer().getGameMode() == GameMode.SPECTATOR) return;

            // check dig status
            PacketContainer packet = packetEvent.getPacket();
            EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);
            if(digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
                // started digging
                onStartDigging(packet.getBlockPositionModifier().read(0), packetEvent.getPlayer());
            } else if (digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK) {
                // canceled digging
                onStopDigging(packetEvent.getPlayer());
            }
        }
    }


    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        onStopDigging(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        onStopDigging(event.getEntity());
    }

    @EventHandler
    public void onPlayerEnterBed(PlayerBedEnterEvent event) {
        onStopDigging(event.getPlayer());
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        onStopDigging(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // check if the player is digging
        DigProgress progress = diggers.get(event.getPlayer().getEntityId());
        if (progress != null) {
            // check distance from the player to the block
            int dx = event.getTo().getBlockX() - progress.getBlock().getX();
            int dy = event.getTo().getBlockY() - progress.getBlock().getY();
            int dz = event.getTo().getBlockZ() - progress.getBlock().getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > DIG_MAX_DISTANCE) {
                onStopDigging(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        // cancel dig task for leaving player
        onStopDigging(event.getPlayer());
    }

    /**
     * Called when a player starts digging.
     */
    @SneakyThrows
    public void onStartDigging(BlockPosition position, Player player) {
        // make sure no duplicate digging tasks are running
        onStopDigging(player);

        // get block
        Block block = player.getWorld().getBlockAt(position.getX(), position.getY(), position.getZ());

        // allow fast-break if the player has appropriate faction perms
        if (factionService.canPlayerDestroyInFaction(player, block.getChunk())) {
            return;
        }

        // get tool, its digging power, and enchantments
        ItemStack tool = player.getInventory().getItemInMainHand();
        final int toolPower = getToolPower(block, tool);
        final int toolEfficiency = tool.getAmount() > 0 ? tool.removeEnchantment(Enchantment.DIG_SPEED) : 0;

        // get block durability
        blockDurabilityService.getDurabilityAbsolute(block)
        .thenAccept((blockDurability) -> {
            // start digging task
            DigProgress digProgress = new DigProgress(plugin, block, position, tool,
                    toolPower, toolEfficiency, blockDurability);
            applyFatigue(player);
            digProgress.setTask(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                () -> playerDigTask(digProgress, tool, player), 20, 20));
            diggers.put(player.getEntityId(), digProgress);
        });
    }

    /**
     * Get the tool's power on a block.
     * @param block The block.
     * @param tool The tool.
     * @return The tool's power on a block.
     */
    private int getToolPower(Block block, ItemStack tool) {
        if (tool.getAmount() > 0) {
            if (PaperLib.isPaper()) {
                // use actual block destroy speed
                Method method;
                try {
                    method = Block.class.getMethod("getDestroySpeed", ItemStack.class, boolean.class);
                    float pow = (Float) method.invoke(block, tool, true);
                    return (int) pow;
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return 1;
    }

    /**
     * Called every 20 ticks, performs operation at each swing of the player digging,
     * @param digProgress
     * @param tool
     * @param player
     */
    private void playerDigTask(DigProgress digProgress, ItemStack tool, Player player) {
        // do swing
        int swingsSinceSave = digProgress.onSwing();
        if (swingsSinceSave > 5) {
            // apply fatigue
            applyFatigue(player);

            // save current dig progress
            digProgress.save();

            // update dig animation
            setDigAnimation(player,
                    digProgress.getPosition(),
                    digProgress.getAnimationProgress());
        }

        if (digProgress.getCurrentDurability() <= 0) {
            // cancel task
            onStopDigging(player);

            // break block
            digProgress.getBlock().breakNaturally(tool);
        } else {
            // update progress
            communicationService.showDurability(
                    player,
                    digProgress.getCurrentDurability(),
                    digProgress.getTotalDurability());
        }
    }

    /**
     * Stops a digging task for a player.
     * @param player
     */
    public void onStopDigging(Player player) {
        if (isDigging(player)) {
            // retrieve the dig progress
            DigProgress digProgress = diggers.remove(player.getEntityId());
            Bukkit.getScheduler().cancelTask(digProgress.getTask());

            // save block durability and damage tool
            digProgress.save();

            // restore player's enchantments
            if (digProgress.getTool().getAmount() > 0
                    && digProgress.getToolEfficiency() > 0) {
                ItemMeta meta = digProgress.getTool().getItemMeta();
                meta.addEnchant(Enchantment.DIG_SPEED,
                        digProgress.getToolEfficiency(), false);
                digProgress.getTool().setItemMeta(meta);
            }

            // clear fatigue effect
            Bukkit.getScheduler().runTask(
                    plugin, () -> clearFatigue(player));

            // clear animation
            setDigAnimation(player, digProgress.getPosition(), -1);
        }
    }

    @Override
    public ListeningWhitelist getSendingWhitelist() {
        return ListeningWhitelist.EMPTY_WHITELIST;
    }

    @Override
    public ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist.newBuilder()
                .gamePhase(GamePhase.PLAYING)
                .types(PacketType.Play.Client.BLOCK_DIG)
                .build();
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Applies mining fatigue for player.
     * @param player
     */
    public void applyFatigue(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.addPotionEffect(FATIGUE_EFFECT);
        });
    }

    /**
     * Clears mining fatigue from player.
     * @param player
     */
    public void clearFatigue(Player player) {
        player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
    }

    /**
     * Sets a player's dig progress.
     * Progress is from 0-9
     * @param player
     * @param progress
     */
    private void setDigAnimation(Player player, BlockPosition blockPosition, int progress) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        // set entity id
        packetContainer.getIntegers().write(0,
                player.getEntityId() + Bukkit.getOnlinePlayers().size() + 1);
        // set block position
        packetContainer.getBlockPositionModifier().write(0, blockPosition);
        // set progress
        packetContainer.getIntegers().write(1, progress);

        // send progress
        Bukkit.getScheduler().runTask(plugin,
                () -> LocationUtil.getPlayersAroundPoint(blockPosition.toLocation(player.getWorld()), 1)
        .forEach(p -> {
            // send
            try {
                protocolManager.sendServerPacket(p, packetContainer);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }));
    }

    /**
     * Checks whether the player is digging a durable block.
     * @param player The player.
     * @return Whether the player is digging a durable block.
     */
    public boolean isDigging(Player player) {
        return diggers.containsKey(player.getEntityId());
    }

    /**
     * Keeps track of dig progress.
     */
    static class DigProgress {
        private final SoftClaimsPlugin plugin;

        private final Block block;
        private final BlockPosition position;

        private final int toolPower;
        private final int toolEfficiency;
        private final ItemStack tool;
        private final double toolDamageModifier;
        private final ItemMeta toolMeta;

        private final int totalDurability;
        private int swingsSinceSave = 0;
        private int lastDurability;

        private int task;

        public DigProgress(SoftClaimsPlugin plugin,
                           Block block,
                           BlockPosition position,
                           ItemStack tool,
                           int toolPower,
                           int toolEfficiency,
                           int startDurability) {
            this.plugin = plugin;
            this.block = block;
            this.position = position;
            this.tool = tool;
            this.toolPower = toolPower;
            this.toolEfficiency = toolEfficiency;
            this.lastDurability = startDurability;
            this.totalDurability = plugin.getBlockDurabilityService()
                    .getTotalDurability(block.getType());

            // get tool meta only if tool is damageable
            ItemMeta toolMeta = this.tool.getItemMeta();
            if (toolMeta instanceof Damageable) {
                this.toolDamageModifier = plugin.getConfig().getDouble("toolDamageModifier", 0.2d);
                this.toolMeta = toolMeta;
            } else {
                this.toolDamageModifier = 0;
                this.toolMeta = null;
            }
        }

        public Block getBlock() {
            return block;
        }

        public BlockPosition getPosition() {
            return position;
        }

        public int getCurrentDurability() {
            return lastDurability - swingsSinceSave * toolPower;
        }

        public int onSwing() {
            return ++swingsSinceSave;
        }

        @SneakyThrows
        public void save() {
            // damage tool
            if (toolDamageModifier > 0 && toolMeta != null) {
                Damageable damageable = (Damageable) toolMeta;
                int damageToDeal = (int) ((double) toolPower * toolDamageModifier * (double) swingsSinceSave);
                damageable.setDamage(damageable.getDamage() + damageToDeal);
                tool.setItemMeta(toolMeta);
            }

            // damage blocks
            lastDurability = plugin.getBlockDurabilityService().getDurabilityAbsolute(block).get();
            lastDurability -= swingsSinceSave * toolPower;
            plugin.getBlockDurabilityService().setDurabilityAbsolute(block, lastDurability);
            swingsSinceSave = 0;
        }

        public int getTask() {
            return task;
        }

        public void setTask(int task) {
            this.task = task;
        }

        public int getTotalDurability() {
            return totalDurability;
        }

        public int getAnimationProgress() {
            return (int) (9f * (1f - (float) getCurrentDurability()) / ((float) getTotalDurability()));
        }

        public int getToolEfficiency() {
            return toolEfficiency;
        }

        public ItemStack getTool() {
            return tool;
        }
    }
}
