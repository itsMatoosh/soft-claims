package me.matoosh.softclaims.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import me.matoosh.softclaims.MSG;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.command.CommandSender;

@CommandAlias("softclaims")
public class SoftClaimsCommand extends BaseCommand {
    private final SoftClaimsPlugin plugin;

    public SoftClaimsCommand(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onDefault(CommandSender sender) {
        MSG.send(sender, "A plugin by itsMatoosh which makes blocks in other factions' land much harder to break.");
    }

    @Subcommand("reload")
    @CommandPermission("softclaims.reload")
    public void onReload(CommandSender sender) {
        this.plugin.reloadConfig();
        MSG.send(sender, "Plugin was reloaded successfully!");
    }
}
