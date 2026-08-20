package dev.ro161012.ffacore.preview;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Executor and tab-completer for {@code /ffa preview}.
 *
 * <ul>
 *   <li>{@code /ffa preview} &mdash; opens the preview GUI (categories of
 *       every custom item in the merged resource pack).</li>
 *   <li>{@code /ffa preview <category>} &mdash; jumps straight into one
 *       category page.</li>
 *   <li>{@code /ffa preview give <name> [amount]} &mdash; hands out one item
 *       by display name, e.g. {@code /ffa preview give kanabo}.</li>
 *   <li>{@code /ffa preview find <text>} &mdash; lists catalog entries
 *       matching text without opening the GUI.</li>
 * </ul>
 *
 * <p>Guarded by {@code ffacore.preview} (op by default).
 */
public final class PreviewCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("give", "find", "list");

    private final FFACore plugin;
    private final PreviewMenu menu;

    /**
     * Creates the command handler.
     *
     * @param plugin owning plugin
     * @param menu   the preview GUI
     */
    public PreviewCommand(final FFACore plugin, final PreviewMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (!sender.hasPermission("ffacore.preview")) {
            Messages.raw(sender, "&cYou don't have permission for that.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Messages.raw(sender, "&cOnly players can use the preview.");
            return true;
        }

        if (args.length == 0) {
            menu.openMain(player);
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> handleGive(player, args);
            case "find", "search" -> handleFind(player, args);
            case "list" -> handleList(player, args);
            default -> {
                // Treat a bare argument as a category short-cut.
                final String cat = matchCategory(args[0]);
                if (cat != null) {
                    menu.openCategory(player, cat, 0);
                } else {
                    Messages.raw(player, "&cUnknown category or preview action: &f" + args[0]);
                    Messages.raw(player, "&7Try &f/ffa preview &7to browse everything.");
                }
            }
        }
        return true;
    }

    private void handleGive(final Player player, final String[] args) {
        if (args.length < 2 || args.length > 3 || args[1].isBlank()) {
            Messages.raw(player, "&cUsage: &f/ffa preview give <name> [amount]");
            return;
        }
        final int amount;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                Messages.raw(player, "&cAmount must be a whole number.");
                return;
            }
        } else {
            amount = 1;
        }

        final List<PreviewItem> matches = plugin.getPreviewRegistry().search(args[1]);
        if (matches.isEmpty()) {
            Messages.raw(player, "&cNo preview item matches &f" + args[1] + "&c.");
            return;
        }
        final PreviewItem target = matches.size() == 1 ? matches.get(0)
                : matches.stream().min((a, b) ->
                        Integer.compare(a.name().length(), b.name().length())).orElse(matches.get(0));
        menu.give(player, target, amount);
    }

    private void handleFind(final Player player, final String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            Messages.raw(player, "&cUsage: &f/ffa preview find <text>");
            return;
        }
        final List<PreviewItem> matches = plugin.getPreviewRegistry().search(args[1]);
        if (matches.isEmpty()) {
            Messages.raw(player, "&cNo preview items match &f" + args[1] + "&c.");
            return;
        }
        Messages.raw(player, "&e" + matches.size() + " &7match(es) for &f" + args[1] + "&7:");
        for (int i = 0; i < Math.min(20, matches.size()); i++) {
            final PreviewItem item = matches.get(i);
            Messages.raw(player, " &8\u2022 &f" + item.name()
                    + " &8(&7" + item.category() + "&8)");
        }
    }

    private void handleList(final Player player, final String[] args) {
        if (args.length == 1) {
            Messages.raw(player, "&ePreview categories:");
            for (final String category : plugin.getPreviewRegistry().getCategories()) {
                Messages.raw(player, " &8\u2022 &f" + category + " &8("
                        + plugin.getPreviewRegistry().byCategory(category).size() + ")");
            }
            return;
        }
        final String cat = matchCategory(args[1]);
        if (cat == null) {
            Messages.raw(player, "&cUnknown category: &f" + args[1]);
            return;
        }
        menu.openCategory(player, cat, 0);
    }

    /**
     * Case-insensitive category match with partial names allowed.
     *
     * @param input the raw input
     * @return the canonical category name, or {@code null}
     */
    private String matchCategory(final String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        final String q = input.toLowerCase(Locale.ROOT);
        for (final String category : plugin.getPreviewRegistry().getCategories()) {
            if (category.equalsIgnoreCase(q) || category.toLowerCase(Locale.ROOT).startsWith(q)) {
                return category;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (!sender.hasPermission("ffacore.preview") || args.length == 0) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        final String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (final String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            for (final String category : plugin.getPreviewRegistry().getCategories()) {
                if (category.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(category);
                }
            }
        } else if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            final List<PreviewItem> matches = plugin.getPreviewRegistry().search(prefix);
            for (final PreviewItem item : matches) {
                final String words = item.name().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (out.size() < 30 && !out.contains(words)) {
                    out.add(words);
                }
            }
        }
        return out;
    }
}