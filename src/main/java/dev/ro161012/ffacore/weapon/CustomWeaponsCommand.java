package dev.ro161012.ffacore.weapon;

import dev.ro161012.ffacore.kokushibo.KokushiboSword;
import dev.ro161012.ffacore.nichirin.NichirinBlade;
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
 * The {@code /ffa customweapons} command, the single entry point for handing
 * out FFACore custom weapons.
 *
 * <p>Usage: {@code /ffa customweapons give <nichirin|kokushibo> [player] [amount]}.
 * No other command hands out weapons.
 */
public final class CustomWeaponsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> WEAPON_IDS = List.of("nichirin", "kokushibo");

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("Custom weapons: nichirin, kokushibo",
                    NamedTextColor.GRAY));
            return true;
        }

        if (!args[0].equalsIgnoreCase("give")) {
            showUsage(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ffa customweapons give <nichirin|kokushibo> [player] [amount]",
                    NamedTextColor.GRAY));
            return true;
        }

        final ItemStack weapon = weaponFor(args[1]);
        if (weapon == null) {
            sender.sendMessage(Component.text("Unknown weapon: " + args[1]
                    + ". Use nichirin or kokushibo.", NamedTextColor.RED));
            return true;
        }

        final Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
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
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }

        giveWeapon(target, weapon, amount);
        sender.sendMessage(Component.text("Gave " + amount + "x " + weaponName(args[1])
                + " to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                      final String alias, final String[] args) {
        if (args.length == 1) {
            return List.of("give", "list");
        }
        if (args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            final List<String> matches = new ArrayList<>();
            for (final String id : WEAPON_IDS) {
                if (id.startsWith(prefix)) {
                    matches.add(id);
                }
            }
            return matches;
        }
        if (args.length == 3) {
            final String prefix = args[2].toLowerCase(Locale.ROOT);
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
     * Resolves a weapon id (and common aliases) to a fresh item, or null when
     * unknown.
     */
    private static ItemStack weaponFor(final String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "nichirin", "blade", "nichirinblade" -> NichirinBlade.create();
            case "kokushibo", "koku", "kokoshibo", "kokushibosword", "moon" ->
                    KokushiboSword.create();
            default -> null;
        };
    }

    private static String weaponName(final String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "kokushibo", "koku", "kokoshibo", "kokushibosword", "moon" ->
                    "Kokoshibos Sword";
            default -> "Nichirin Blade";
        };
    }

    /**
     * Hands the weapon to a player, one copy per requested amount so the
     * unstackable sword is never forced into an invalid stack.
     */
    private static void giveWeapon(final Player player, final ItemStack template,
                                   final int amount) {
        for (int i = 0; i < amount; i++) {
            final ItemStack copy = template.clone();
            copy.setAmount(1);
            if (!player.getInventory().addItem(copy).isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), copy);
            }
        }
    }

    private static void showUsage(final CommandSender sender) {
        sender.sendMessage(Component.text(
                "Usage: /ffa customweapons give <nichirin|kokushibo> [player] [amount]",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Weapons: nichirin, kokushibo", NamedTextColor.GRAY));
    }
}
