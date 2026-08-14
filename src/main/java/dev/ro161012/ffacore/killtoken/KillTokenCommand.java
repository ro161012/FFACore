package dev.ro161012.ffacore.killtoken;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Executor and tab-completer for {@code /killtoken}.
 *
 * <ul>
 *   <li>{@code /killtoken set} &mdash; the item in the sender's main hand
 *       becomes the new Kill Token currency item.</li>
 *   <li>{@code /killtoken give [player] [amount]} &mdash; grants tokens
 *       directly, e.g. for rewards or manual payouts.</li>
 *   <li>{@code /killtoken giveblock [player] [amount]} &mdash; grants
 *       compressed Kill Token blocks (each worth 64 tokens).</li>
 *   <li>{@code /killtoken reload} &mdash; reloads {@code config.yml}.</li>
 *   <li>{@code /killtoken test} &mdash; previews the configured killstreak
 *       announcements, sound, and multiplier token drop.</li>
 * </ul>
 *
 * <p>Subcommands are guarded by the fine-grained permissions
 * {@code ffacore.killtoken.set}, {@code ffacore.killtoken.give}, {@code ffacore.killtoken.reload}, and
 * {@code ffacore.killtoken.test}, all children of {@code ffacore.killtoken.admin}.
 */
public final class KillTokenCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "set", "give", "giveblock", "reload", "test", "stats", "top");
    private static final List<String> AMOUNT_SUGGESTIONS = List.of("1", "16", "64");

    /** Hard cap for a single {@code /killtoken give} payout (36 stacks). */
    static final int MAX_GIVE_AMOUNT = 2304;

    /** Hard cap for a single {@code /killtoken giveblock} payout (9 stacks). */
    static final int MAX_BLOCK_GIVE_AMOUNT = 576;

    private final KillTokenManager plugin;

    /**
     * Creates the command handler.
     *
     * @param plugin owning plugin instance
     */
    public KillTokenCommand(final KillTokenManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender);
            case "give" -> handleGive(sender, args);
            case "giveblock" -> handleGiveBlock(sender, args);
            case "reload" -> handleReload(sender);
            case "test" -> handleTest(sender);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(final CommandSender sender, final String label) {
        sender.sendMessage(KillTokenManager.color("&4&lKillToken &8| &7Commands:"));
        sender.sendMessage(KillTokenManager.color("&f/" + label + " set &8- &7use your main-hand item as the token"));
        sender.sendMessage(KillTokenManager.color(
                "&f/" + label + " give [player] [amount] &8- &7hand out tokens"));
        sender.sendMessage(KillTokenManager.color(
                "&f/" + label + " giveblock [player] [amount] &8- &7hand out compressed blocks"));
        sender.sendMessage(KillTokenManager.color("&f/" + label + " reload &8- &7reload the configuration"));
        sender.sendMessage(KillTokenManager.color(
                "&f/" + label + " test &8- &7preview killstreak chat and token multiplier"));
        sender.sendMessage(KillTokenManager.color(
                "&f/" + label + " stats [player] &8- &7open stats GUI"));
        sender.sendMessage(KillTokenManager.color(
                "&f/" + label + " top [page] &8- &7kill leaderboard"));
    }

    private void handleSet(final CommandSender sender) {
        if (!sender.hasPermission("ffacore.killtoken.set")) {
            sender.sendMessage(KillTokenManager.color("&cYou do not have permission to do that."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(KillTokenManager.color("&cOnly players can set the Kill Token item."));
            return;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            player.sendMessage(KillTokenManager.color(
                    "&cHold the item you want to use as the Kill Token first."));
            return;
        }

        plugin.setCurrencyItem(held);
        player.sendMessage(KillTokenManager.color("&aKill Token currency updated to &f"
                + prettyName(held.getType()) + "&a."));
    }

    private void handleGive(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("ffacore.killtoken.give")) {
            sender.sendMessage(KillTokenManager.color("&cYou do not have permission to do that."));
            return;
        }

        final Player target;
        int amount = 1;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(KillTokenManager.color("&cPlayer &f" + args[1] + "&c is not online."));
                return;
            }
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(KillTokenManager.color("&cAmount must be a whole number."));
                    return;
                }
                if (amount < 1 || amount > MAX_GIVE_AMOUNT) {
                    sender.sendMessage(KillTokenManager.color(
                            "&cAmount must be between &f1&c and &f" + MAX_GIVE_AMOUNT + "&c."));
                    return;
                }
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(KillTokenManager.color(
                        "&cUsage: /killtoken give <player> [amount]"));
                return;
            }
            target = player;
        }

        deliver(target, amount);
        sender.sendMessage(KillTokenManager.color("&aGave &f" + amount
                + " Kill Token" + (amount == 1 ? "" : "s") + "&a to &f" + target.getName() + "&a."));
    }

    /**
     * Places the tokens into the target's inventory, merging with existing
     * stacks. Any overflow that does not fit is dropped at the target's
     * feet, so no tokens are ever lost.
     *
     * @param target receiving player
     * @param amount number of tokens to hand out
     */
    private void deliver(final Player target, final int amount) {
        final ItemStack stack = plugin.createToken(amount);
        final Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        for (final ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
    }

    private void handleGiveBlock(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("ffacore.killtoken.give")) {
            sender.sendMessage(KillTokenManager.color("&cYou do not have permission to do that."));
            return;
        }

        final Player target;
        int amount = 1;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(KillTokenManager.color("&cPlayer &f" + args[1] + "&c is not online."));
                return;
            }
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(KillTokenManager.color("&cAmount must be a whole number."));
                    return;
                }
                if (amount < 1 || amount > MAX_BLOCK_GIVE_AMOUNT) {
                    sender.sendMessage(KillTokenManager.color("&cAmount must be between &f1&c and &f"
                            + MAX_BLOCK_GIVE_AMOUNT + "&c."));
                    return;
                }
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(KillTokenManager.color(
                        "&cUsage: /killtoken giveblock <player> [amount]"));
                return;
            }
            target = player;
        }

        final ItemStack stack = plugin.getCompressedBlockManager().createCompressedBlock();
        stack.setAmount(amount);
        final Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        for (final ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        sender.sendMessage(KillTokenManager.color("&aGave &f" + amount + " Compressed Kill Token Block"
                + (amount == 1 ? "" : "s") + "&a to &f" + target.getName() + "&a."));
    }

    private void handleStats(final CommandSender sender, final String[] args) {
        final boolean viewingSelf = args.length < 2
                || (sender instanceof Player player
                    && player.getName().equalsIgnoreCase(args[1]));
        if (!viewingSelf && !sender.hasPermission("ffacore.killtoken.stats")) {
            sender.sendMessage(KillTokenManager.color(
                    "&cYou do not have permission to view other players' stats."));
            return;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(KillTokenManager.color(
                    "&cOnly players can open the stats GUI."));
            return;
        }

        final Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(KillTokenManager.color(
                        "&cPlayer &f" + args[1] + "&c is not online."));
                return;
            }
        } else {
            target = viewer;
        }

        KillTokenGui.openStats(viewer, target, plugin);
    }

    private void handleTop(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(KillTokenManager.color(
                    "&cOnly players can open the leaderboard."));
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                viewer.sendMessage(KillTokenManager.color(
                        "&cInvalid page number."));
                return;
            }
        }
        KillTokenGui.openTop(viewer, page, plugin);
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission("ffacore.killtoken.reload")) {
            sender.sendMessage(KillTokenManager.color("&cYou do not have permission to do that."));
            return;
        }
        plugin.reload();
        sender.sendMessage(KillTokenManager.color("&aKillToken configuration reloaded."));
    }

    /**
     * Runs a safe killstreak multiplier preview for an administrator.
     *
     * @param sender command sender
     */
    private void handleTest(final CommandSender sender) {
        if (!sender.hasPermission("ffacore.killtoken.test")) {
            sender.sendMessage(KillTokenManager.color("&cYou do not have permission to do that."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(KillTokenManager.color("&cOnly players can run a killstreak test."));
            return;
        }
        if (!plugin.runKillstreakTest(player)) {
            sender.sendMessage(KillTokenManager.color("&cKillstreaks are disabled in the configuration."));
            return;
        }

        final int rewardStreak = plugin.getKillstreakRewardStart();
        sender.sendMessage(KillTokenManager.color("&4KillToken &8| &7Tested chat at &c"
                + plugin.getKillstreakAnnouncementMinimum() + "&7 kills and the &c" + rewardStreak
                + "&7-kill &c" + plugin.getKillstreakTokenMultiplier(rewardStreak)
                + "x &7token drop. No real streak or cooldown was changed."));
    }

    private static String prettyName(final Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(name -> name.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("stats")
                || args[0].equalsIgnoreCase("giveblock"))) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("giveblock"))) {
            return AMOUNT_SUGGESTIONS;
        }
        return List.of();
    }
}
