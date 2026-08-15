package dev.ro161012.ffacore.kokushibo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /ffa kokushibo} sub-command, currently limited to handing out
 * the Kokoshibos Sword.
 */
public final class KokushiboCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /ffa kokushibo give [player] [amount]",
                    NamedTextColor.GRAY));
            return true;
        }

        final Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }

        giveSword(target, amount);
        sender.sendMessage(Component.text("Gave " + amount + "x Kokoshibos Sword to "
                + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (args.length == 1) {
            return List.of("give");
        }
        if (args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            final List<String> names = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }

    /**
     * Hands the sword to a player, adding to their inventory or dropping it
     * at their feet when their inventory is full.
     */
    private static void giveSword(final Player player, final int amount) {
        final ItemStack sword = KokushiboSword.create(amount);
        if (player.getInventory().addItem(sword).isEmpty()) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), sword);
    }
}
