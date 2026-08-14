package dev.ro161012.ffacore.afk;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.selection.Selection;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executor and tab-completer for {@code /ffa afk}.
 *
 * <ul>
 *   <li>{@code /ffa afk wand} &mdash; receive the selection wand.</li>
 *   <li>{@code /ffa afk create <name>} &mdash; create a zone from a selection.</li>
 *   <li>{@code /ffa afk delete <name>} &mdash; delete a zone.</li>
 *   <li>{@code /ffa afk list} &mdash; list every zone.</li>
 *   <li>{@code /ffa afk info [name]} &mdash; zone details, or your own status.</li>
 *   <li>{@code /ffa afk give [player] [amount]} &mdash; hand out AFK Shards.</li>
 *   <li>{@code /ffa afk reload} &mdash; reload the configuration.</li>
 * </ul>
 */
public final class AfkCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "wand", "create", "delete", "list", "info", "give", "reload");
    private static final List<String> AMOUNTS = List.of("1", "16", "64");
    private static final int MAX_GIVE = 2304;

    private final FFACore plugin;
    private final Messages messages;

    /**
     * Creates the command handler.
     *
     * @param plugin owning plugin
     */
    public AfkCommand(final FFACore plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> cmdWand(sender);
            case "create" -> cmdCreate(sender, args);
            case "delete", "remove" -> cmdDelete(sender, args);
            case "list" -> cmdList(sender);
            case "info" -> cmdInfo(sender, args);
            case "give" -> cmdGive(sender, args);
            case "reload" -> cmdReload(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(final CommandSender sender, final String label) {
        if (!checkPerm(sender, "ffacore.afk.use")) {
            return;
        }
        messages.raw(sender, "&3&lFFACore &7AFK Zones");
        messages.raw(sender, "&8&m-------------------------------");
        messages.raw(sender, "&f/" + label + " create <name> &8- &7create a zone from your selection");
        messages.raw(sender, "&f/" + label + " delete <name> &8- &7delete a zone");
        messages.raw(sender, "&f/" + label + " list &8- &7list all zones");
        messages.raw(sender, "&f/" + label + " info [name] &8- &7zone details or your status");
        messages.raw(sender, "&f/" + label + " wand &8- &7receive the selection wand");
        messages.raw(sender, "&f/" + label + " give [player] [amount] &8- &7hand out AFK Shards");
        messages.raw(sender, "&f/" + label + " reload &8- &7reload the configuration");
    }

    private void cmdWand(final CommandSender sender) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.afk.create")) {
            return;
        }
        plugin.getSelectionManager().giveWand((Player) sender);
    }

    private void cmdCreate(final CommandSender sender, final String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.afk.create")) {
            return;
        }
        final Player player = (Player) sender;
        if (args.length < 2) {
            messages.msg(player, "&cUsage: /ffa afk create <name>");
            return;
        }
        final String name = args[1];
        if (plugin.getAfkManager().zoneExists(name)) {
            messages.msg(player, "&cA zone named &f" + name + "&c already exists.");
            return;
        }
        final Selection sel = plugin.getSelectionManager().getSelection(player);
        if (sel == null || !sel.isComplete()) {
            messages.msg(player, "&cMake a selection first with &f/ffa afk wand&c.");
            return;
        }
        final AfkZone zone = plugin.getAfkManager().createZone(
                name, sel.getPos1(), sel.getPos2());
        messages.msg(player, "&aAFK zone &f" + zone.getName() + "&a created (&f"
                + zone.getBlockCount() + "&a blocks).");
        plugin.getSelectionManager().clearSelection(player);
    }

    private void cmdDelete(final CommandSender sender, final String[] args) {
        if (!checkPerm(sender, "ffacore.afk.delete")) {
            return;
        }
        if (args.length < 2) {
            messages.msg(sender, "&cUsage: /ffa afk delete <name>");
            return;
        }
        if (plugin.getAfkManager().deleteZone(args[1])) {
            messages.msg(sender, "&aAFK zone &f" + args[1] + "&a deleted.");
        } else {
            messages.msg(sender, "&cZone not found: &f" + args[1]);
        }
    }

    private void cmdList(final CommandSender sender) {
        if (!checkPerm(sender, "ffacore.afk.use")) {
            return;
        }
        final var zones = plugin.getAfkManager().getZones();
        if (zones.isEmpty()) {
            messages.msg(sender, "&7No AFK zones yet. Create one with &f/ffa afk create <name>&7.");
            return;
        }
        messages.raw(sender, "&3&lAFK Zones &7(" + zones.size() + ")");
        for (final AfkZone zone : zones) {
            messages.raw(sender, " &8- &f" + zone.getName() + " &7("
                    + zone.getWorldName() + ", " + zone.getBlockCount() + " blocks)");
        }
    }

    private void cmdInfo(final CommandSender sender, final String[] args) {
        if (!checkPerm(sender, "ffacore.afk.use")) {
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player player) {
                showStatus(player);
            } else {
                messages.msg(sender, "&cUsage: /ffa afk info <name>");
            }
            return;
        }
        final AfkZone zone = plugin.getAfkManager().getZone(args[1]);
        if (zone == null) {
            messages.msg(sender, "&cZone not found: &f" + args[1]);
            return;
        }
        messages.raw(sender, "&3&lAFK Zone: &f" + zone.getName());
        messages.raw(sender, "&7World: &f" + zone.getWorldName());
        messages.raw(sender, "&7Size: &f" + zone.getBlockCount() + " blocks");
        messages.raw(sender, "&7Corner 1: &f" + zone.getPos1().getBlockX() + ", "
                + zone.getPos1().getBlockY() + ", " + zone.getPos1().getBlockZ());
        messages.raw(sender, "&7Corner 2: &f" + zone.getPos2().getBlockX() + ", "
                + zone.getPos2().getBlockY() + ", " + zone.getPos2().getBlockZ());
    }

    private void showStatus(final Player player) {
        final AfkManager.Session session = plugin.getAfkManager().getSession(player.getUniqueId());
        if (session == null) {
            messages.msg(player, "&7You are not inside an AFK zone.");
            return;
        }
        messages.raw(player, "&3&lAFK Status");
        messages.raw(player, "&7Zone: &f" + session.getZone().getName());
        messages.raw(player, "&7Idle for: &f" + session.getIdleSeconds() + "s");
        messages.raw(player, "&7Earned this session: &f" + session.getTotalEarned() + " shard(s)");
    }

    private void cmdGive(final CommandSender sender, final String[] args) {
        if (!checkPerm(sender, "ffacore.afk.give")) {
            return;
        }
        final Player target;
        int amount = 1;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.msg(sender, "&cPlayer &f" + args[1] + "&c is not online.");
                return;
            }
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    messages.msg(sender, "&cAmount must be a whole number.");
                    return;
                }
                if (amount < 1 || amount > MAX_GIVE) {
                    messages.msg(sender, "&cAmount must be between &f1&c and &f" + MAX_GIVE + "&c.");
                    return;
                }
            }
        } else {
            if (!(sender instanceof Player player)) {
                messages.msg(sender, "&cUsage: /ffa afk give <player> [amount]");
                return;
            }
            target = player;
        }
        final ItemStack shards = AfkShard.create(amount);
        final Map<Integer, ItemStack> leftover = target.getInventory().addItem(shards);
        for (final ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        messages.msg(sender, "&aGave &f" + amount + " AFK Shard"
                + (amount == 1 ? "" : "s") + "&a to &f" + target.getName() + "&a.");
    }

    private void cmdReload(final CommandSender sender) {
        if (!checkPerm(sender, "ffacore.afk.reload")) {
            return;
        }
        plugin.reloadConfig();
        messages.msg(sender, "&aFFACore configuration reloaded.");
    }

    private boolean checkPlayer(final CommandSender sender) {
        if (!(sender instanceof Player)) {
            messages.msg(sender, "&cThis command can only be used by players.");
            return false;
        }
        return true;
    }

    private boolean checkPerm(final CommandSender sender, final String perm) {
        if (!sender.hasPermission(perm) && !sender.hasPermission("ffacore.afk.admin")) {
            messages.msg(sender, "&cYou don't have permission for that.");
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("info"))) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getAfkManager().getZoneNames().stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return AMOUNTS;
        }
        return List.of();
    }
}
