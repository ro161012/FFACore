package dev.ro161012.ffacore.command;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * Root {@code /ffa} command: a compact overview of the three FFACore
 * subsystems plus a config reload.
 */
public final class FfaCommand implements CommandExecutor, TabCompleter {

    private final FFACore plugin;

    /**
     * Creates the command handler.
     *
     * @param plugin owning plugin
     */
    public FfaCommand(final FFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("config")) {
            if (!sender.hasPermission("ffacore.admin")) {
                Messages.raw(sender, "&cYou don't have permission for that.");
                return true;
            }
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                Messages.raw(sender, "&cOnly players can open the config menu.");
                return true;
            }
            plugin.getConfigMenu().openMainMenu(player);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ffacore.admin")) {
                Messages.raw(sender, "&cYou don't have permission for that.");
                return true;
            }
            plugin.reloadConfig();
            plugin.applyConfig();
            plugin.getMessages().msg(sender, "&aFFACore configuration reloaded.");
            return true;
        }

        final Messages messages = plugin.getMessages();
        messages.raw(sender, "&b&lFFACore &7v" + plugin.getPluginMeta().getVersion());
        messages.raw(sender, "&8&m--------------------------------");
        messages.raw(sender, "&bArenas &8- &7" + plugin.getArenaManager().getArenaCount()
                + " configured &8(&f/arena&8)");
        messages.raw(sender, "&cKill Tokens &8- &7PvP kill currency &8(&f/killtoken&8)");
        messages.raw(sender, "&3AFK Zones &8- &7" + plugin.getAfkManager().getZoneCount()
                + " zones, &f" + plugin.getAfkManager().getActiveCount()
                + "&7 player(s) inside &8(&f/afk&8)");
        messages.raw(sender, "&8&m--------------------------------");
        messages.raw(sender, "&7Use &f/ffa config &7to open the in-game config menu.");
        messages.raw(sender, "&7Use &f/ffa reload &7to reload config.yml from disk.");
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("config", "reload").stream()
                    .filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
