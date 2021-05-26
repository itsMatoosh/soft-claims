package me.matoosh.softclaims.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import lombok.RequiredArgsConstructor;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import me.matoosh.softclaims.service.BlockRepairService;
import me.matoosh.softclaims.service.WorldService;
import org.bukkit.command.CommandSender;

@CommandAlias("softclaims")
@RequiredArgsConstructor
public class SoftClaimsCommand extends BaseCommand {

    private final BlockRepairService blockRepairService;
    private final WorldService worldService;
    private final SoftClaimsPlugin plugin;

    @Default
    public void onDefault(CommandSender sender) {
        MSG.send(sender, "A plugin by itsMatoosh which makes blocks in other factions' land much harder to break.");
    }

    @Subcommand("reload")
    @CommandPermission("softclaims.reload")
    public void onReload(CommandSender sender) {
        // reload config
        plugin.reloadConfig();

        // reload reloadables
        blockRepairService.reload();
        worldService.reload();

        MSG.send(sender, "Plugin was reloaded successfully!");
    }
}
