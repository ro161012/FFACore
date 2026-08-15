package dev.ro161012.ffacore.command;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.afk.AfkCommand;
import dev.ro161012.ffacore.killtoken.KillTokenCommand;
import dev.ro161012.ffacore.nichirin.NichirinCommand;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Root {@code /ffa} command: a compact overview of the three FFACore
 * subsystems plus a config reload. Every FFACore command lives under this
 * one namespace:
 *
 * <ul>
 *   <li>{@code /ffa arena ...} &mdash; arena regeneration management.</li>
 *   <li>{@code /ffa killtoken ...} &mdash; the Kill Token currency.</li>
 *   <li>{@code /ffa afk ...} &mdash; AFK zones and AFK Shards.</li>
 *   <li>{@code /ffa nichirin ...} &mdash; the Nichirin Blade weapon.</li>
 *   <li>{@code /ffa config} &mdash; the in-game config menu.</li>
 *   <li>{@code /ffa reload} &mdash; reload config.yml from disk.</li>
 * </ul>
 *
 * <p>There are no standalone commands: everything lives under {@code /ffa}.
 */
public final class FfaCommand implements CommandExecutor, TabCompleter {

    private final FFACore plugin;
    private final ArenaCommand arenaCommand;
    private final KillTokenCommand killTokenCommand;
    private final AfkCommand afkCommand;
    private final NichirinCommand nichirinCommand;

    /**
     * Creates the command handler.
     *
     * @param plugin           owning plugin
     * @param arenaCommand     the arena sub-command executor
     * @param killTokenCommand the kill token sub-command executor
     * @param afkCommand       the afk sub-command executor
     * @param nichirinCommand  the nichirin blade sub-command executor
     */
    public FfaCommand(final FFACore plugin, final ArenaCommand arenaCommand,
                      final KillTokenCommand killTokenCommand,
                      final AfkCommand afkCommand,
                      final NichirinCommand nichirinCommand) {
        this.plugin = plugin;
        this.arenaCommand = arenaCommand;
        this.killTokenCommand = killTokenCommand;
        this.afkCommand = afkCommand;
        this.nichirinCommand = nichirinCommand;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length > 0) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            final String[] rest = Arrays.copyOfRange(args, 1, args.length);
            switch (sub) {
                case "arena", "ar" -> {
                    return arenaCommand.onCommand(sender, command, "ffa arena", rest);
                }
                case "killtoken", "token", "kt" -> {
                    return killTokenCommand.onCommand(sender, command, "ffa killtoken", rest);
                }
                case "afk" -> {
                    return afkCommand.onCommand(sender, command, "ffa afk", rest);
                }
                case "nichirin", "blade" -> {
                    return nichirinCommand.onCommand(sender, command, "ffa nichirin", rest);
                }
                case "config" -> {
                    return openConfig(sender);
                }
                case "reload" -> {
                    return reload(sender);
                }
                default -> {
                    // fall through to the overview
                }
            }
        }

        final Messages messages = plugin.getMessages();
        messages.raw(sender, "&b&lFFACore &7v" + plugin.getPluginMeta().getVersion());
        messages.raw(sender, "&8&m--------------------------------");
        messages.raw(sender, "&bArenas &8- &7" + plugin.getArenaManager().getArenaCount()
                + " configured &8(&f/ffa arena&8)");
        messages.raw(sender, "&cKill Tokens &8- &7PvP kill currency &8(&f/ffa killtoken&8)");
        messages.raw(sender, "&3AFK Zones &8- &7" + plugin.getAfkManager().getZoneCount()
                + " zones, &f" + plugin.getAfkManager().getActiveCount()
                + "&7 player(s) inside &8(&f/ffa afk&8)");
        messages.raw(sender, "&6Nichirin Blade &8- &7Demon Slayer FFA weapon &8(&f/ffa nichirin&8)");
        messages.raw(sender, "&8&m--------------------------------");
        messages.raw(sender, "&7Use &f/ffa config &7to open the in-game config menu.");
        messages.raw(sender, "&7Use &f/ffa reload &7to reload config.yml from disk.");
        return true;
    }

    private boolean openConfig(final CommandSender sender) {
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

    private boolean reload(final CommandSender sender) {
        if (!sender.hasPermission("ffacore.admin")) {
            Messages.raw(sender, "&cYou don't have permission for that.");
            return true;
        }
        plugin.reloadConfig();
        plugin.applyConfig();
        plugin.getMessages().msg(sender, "&aFFACore configuration reloaded.");
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("arena", "killtoken", "afk", "nichirin", "config", "reload")
                    .stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length >= 2) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            final String[] rest = Arrays.copyOfRange(args, 1, args.length);
            return switch (sub) {
                case "arena", "ar" -> arenaCommand.onTabComplete(sender, command, "ffa arena", rest);
                case "killtoken", "token", "kt" ->
                        killTokenCommand.onTabComplete(sender, command, "ffa killtoken", rest);
                case "afk" -> afkCommand.onTabComplete(sender, command, "ffa afk", rest);
                case "nichirin", "blade" ->
                        nichirinCommand.onTabComplete(sender, command, "ffa nichirin", rest);
                default -> List.of();
            };
        }
        return List.of();
    }
}
