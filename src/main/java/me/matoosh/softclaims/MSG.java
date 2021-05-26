package me.matoosh.softclaims;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Manages player communication.
 */
public class MSG {
    public static String PLUGIN_CHAT_PREFIX = ChatColor.translateAlternateColorCodes(
            '&', "&0&l[&f&lSoft &b&lClaims&0&l] &f");

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(constructMessage(message));
    }

    /**
     * Constructs a plugin message with plugin prefix.
     * @param message
     * @return
     */
    public static String constructMessage(String message) {
        return PLUGIN_CHAT_PREFIX + ChatColor
                .translateAlternateColorCodes('&', message);
    }
}
