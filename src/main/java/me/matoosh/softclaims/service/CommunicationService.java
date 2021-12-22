package me.matoosh.softclaims.service;

import me.matoosh.softclaims.SoftClaimsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public class CommunicationService {

    private final SoftClaimsPlugin plugin;

    public CommunicationService(SoftClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Shows the current block durability to the player.
     * @param player
     * @param durability
     */
    public void showDurability(Player player, int durability, int totalDurability) {
        StringBuilder progressBar = new StringBuilder();

        // math
        int percent = (int)(((float) durability / (float) totalDurability) * 100f);
        int fullBars = percent / 10;
        int remainder = percent % 10;

        // full bars
        progressBar.append("█".repeat(Math.max(0, fullBars)));

        // partial bars
        int offset = 0;
        if(remainder > 5) {
            progressBar.append("▒");
            offset = 1;
        } else if (remainder > 2) {
            progressBar.append("░");
            offset = 1;
        }

        // blank space
        progressBar.append("  ".repeat(Math.max(0, 10 - fullBars - offset)));

        // action bar
        plugin.getAdventure().player(player)
                .sendActionBar(Component.join(
                Component.text(" | ", TextColor.fromHexString("#bababa")),
                Component.text("Durability: " + durability, TextColor.fromHexString("#f44e07")),
                Component.join(Component.empty(),
                        Component.text("[", TextColor.fromHexString("#777777")),
                        Component.text(progressBar.toString(), TextColor.fromHexString("#f44e07")),
                        Component.text("]", TextColor.fromHexString("#777777"))
                )
        ));
    }
}
