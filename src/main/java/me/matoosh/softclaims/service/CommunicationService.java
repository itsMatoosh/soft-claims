package me.matoosh.softclaims.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public class CommunicationService {
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
        for (int i = 0; i < fullBars; i++) {
            progressBar.append("█");
        }

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
        for (int i = 0; i < 10 - fullBars - offset; i++) {
            progressBar.append("  ");
        }

        // action bar
//        TextComponent durabilityComponent = new TextComponent("Durability: " + durability);
//        durabilityComponent.setColor(ChatColor.of("#f44e07"));
//        TextComponent separatorComponent = new TextComponent(" | " + durability);
//        separatorComponent.setColor(ChatColor.of("#bababa"));
//        TextComponent progressBarOpenerComponent = new TextComponent("[");
//        progressBarOpenerComponent.setColor(ChatColor.of("#777777"));
//        TextComponent progressBarCloserComponent = new TextComponent("]");
//        progressBarCloserComponent.setColor(ChatColor.of("#777777"));
//        TextComponent progressBarComponent = new TextComponent(progressBar.toString());
//        progressBarComponent.setColor(ChatColor.of("#f44e07"));
//
//        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, durabilityComponent, separatorComponent,
//                progressBarOpenerComponent, progressBarComponent, progressBarCloserComponent);

        player.sendActionBar(Component.join(
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
