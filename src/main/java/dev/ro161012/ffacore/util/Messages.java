package dev.ro161012.ffacore.util;

import dev.ro161012.ffacore.FFACore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Messages {

    private final FFACore plugin;
    private String prefix;

    public Messages(FFACore plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Re-reads the chat prefix from {@code config.yml} so prefix changes made
     * through the config menu apply immediately.
     */
    public void reload() {
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("general.prefix", "&8[&bFFACore&8]&r"));
    }

    public String prefixed(String key, String def) {
        String raw = plugin.getConfig().getString("messages." + key, def);
        return prefix + " " + ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void send(CommandSender sender, String key, String def) {
        deliver(sender, prefixed(key, def));
    }

    public void sendRaw(CommandSender sender, String msg) {
        raw(sender, msg);
    }

    /**
     * Sends a colourised message prefixed with the plugin prefix.
     *
     * @param sender the recipient
     * @param msg    raw {@code &}-style message
     */
    public void msg(CommandSender sender, String msg) {
        deliver(sender, prefix + " " + color(msg));
    }

    /**
     * Sends a colourised message without the plugin prefix.
     *
     * @param sender the recipient
     * @param msg    raw {@code &}-style message
     */
    public static void raw(final CommandSender sender, final String msg) {
        deliver(sender, color(msg));
    }

    private static void deliver(final CommandSender sender, final String colored) {
        if (sender instanceof Player) {
            sender.sendMessage(deserialize(colored));
        } else {
            sender.sendMessage(ChatColor.stripColor(colored));
        }
    }

    /**
     * Translates {@code &}-style colour codes in the given string.
     *
     * @param value raw string, may be null
     * @return colourised string, never null
     */
    public static String color(final String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    /**
     * Deserialises an {@code &}-style legacy string into an Adventure
     * component.
     *
     * @param legacy the legacy string
     * @return the component
     */
    public static Component deserialize(final String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }
}
