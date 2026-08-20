package dev.ro161012.ffacore.customitem;

import dev.ro161012.ffacore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Command handler for distributing the screenshot-based custom items. */
public final class CustomItemCommand implements TabExecutor {

    private static final String PERMISSION = "ffacore.customitems";

    private final CustomItemManager manager;

    /**
     * Creates the command handler.
     *
     * @param manager custom item manager
     */
    public CustomItemCommand(final CustomItemManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            Messages.raw(sender, "&cYou don't have permission for that.");
            return true;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            list(sender);
            return true;
        }
        if (!"give".equalsIgnoreCase(args[0])) {
            Messages.raw(sender, "&7Usage: &f/ffa customitems give <item> [player] [amount]");
            return true;
        }
        if (args.length < 2 || args.length > 4 || args[1].isBlank()) {
            Messages.raw(sender, "&7Usage: &f/ffa customitems give <item> [player] [amount]");
            return true;
        }

        final CustomItemType type = CustomItemType.fromKey(args[1]);
        if (type == null) {
            Messages.raw(sender, "&cUnknown custom item: &f" + args[1]);
            return true;
        }

        Player target = sender instanceof Player player ? player : null;
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = clampAmount(Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
                if (args[2].matches("[+-]?\\d+")) {
                    Messages.raw(sender, "&cAmount must be a whole number.");
                    return true;
                }
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    Messages.raw(sender, "&cPlayer not found: &f" + args[2]);
                    return true;
                }
            }
        }
        if (args.length >= 4) {
            try {
                amount = clampAmount(Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                Messages.raw(sender, "&cAmount must be a whole number.");
                return true;
            }
        }
        if (target == null) {
            Messages.raw(sender, "&cConsole must specify a player.");
            return true;
        }

        manager.give(target, type, amount);
        Messages.raw(sender, "&7Gave &f" + type.displayName() + " &7to &f"
                + target.getName() + " &7x" + amount + ".");
        if (sender != target) {
            Messages.raw(target, "&7You received &f" + type.displayName()
                    + " &7x" + amount + ".");
        }
        return true;
    }

    private void list(final CommandSender sender) {
        Messages.raw(sender, "&eCustom items:");
        for (final CustomItemType type : manager.types()) {
            Messages.raw(sender, " &8• &f" + type.key() + " &8- &7" + type.displayName());
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        final String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (final String value : List.of("give", "list")) {
                if (value.startsWith(prefix)) {
                    result.add(value);
                }
            }
        } else if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            for (final CustomItemType type : manager.types()) {
                if (type.key().startsWith(prefix)) {
                    result.add(type.key());
                }
            }
        } else if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(player.getName());
                }
            }
        }
        return result;
    }

    private int clampAmount(final int amount) {
        return Math.max(1, Math.min(64, amount));
    }
}
