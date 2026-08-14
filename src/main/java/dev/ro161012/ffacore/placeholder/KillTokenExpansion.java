package dev.ro161012.ffacore.placeholder;

import dev.ro161012.ffacore.killtoken.KillTokenManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for KillToken.
 *
 * <h3>Placeholders</h3>
 * <table>
 *   <caption>Player-specific</caption>
 *   <tr><th>Placeholder</th><th>Description</th></tr>
 *   <tr><td>{@code %killtoken_streak%}</td><td>current PvP killstreak</td></tr>
 *   <tr><td>{@code %killtoken_kills%}</td><td>lifetime PvP kills</td></tr>
 *   <tr><td>{@code %killtoken_deaths%}</td><td>lifetime deaths</td></tr>
 *   <tr><td>{@code %killtoken_best_streak%}</td><td>highest streak ever</td></tr>
 *   <tr><td>{@code %killtoken_kdr%}</td><td>kill/death ratio</td></tr>
 *   <tr><td>{@code %killtoken_tokens%}</td><td>Kill Tokens in inventory</td></tr>
 *   <tr><td>{@code %killtoken_token_blocks%}</td><td>compressed blocks in inventory</td></tr>
 *   <tr><td>{@code %killtoken_cooldown_remaining%}</td>
 *     <td>seconds until pair cooldown clears with last killer</td></tr>
 * </table>
 */
public final class KillTokenExpansion extends PlaceholderExpansion {

    private final KillTokenManager plugin;

    /**
     * Creates the expansion.
     *
     * @param plugin owning plugin instance
     */
    public KillTokenExpansion(final KillTokenManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "killtoken";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ro161012";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(final Player player,
                                                  @NotNull final String identifier) {
        if (player == null) {
            return null;
        }

        return switch (identifier.toLowerCase(java.util.Locale.ROOT)) {
            case "streak" -> String.valueOf(plugin.getKillstreakTracker()
                    .getStreak(player.getUniqueId()));
            case "kills" -> String.valueOf(plugin.getKillstreakTracker()
                    .getTotalKills(player.getUniqueId()));
            case "deaths" -> String.valueOf(plugin.getKillstreakTracker()
                    .getTotalDeaths(player.getUniqueId()));
            case "best_streak" -> String.valueOf(plugin.getKillstreakTracker()
                    .getBestStreak(player.getUniqueId()));
            case "kdr" -> plugin.getKillstreakTracker()
                    .getKdr(player.getUniqueId());
            case "tokens" -> String.valueOf(countInventoryTokens(player));
            case "token_blocks" -> String.valueOf(countCompressedBlocks(player));
            default -> null;
        };
    }

    /**
     * Counts how many Kill Token items the player has in their inventory.
     * Stacks are summed by their actual amount.
     *
     * @param player the player
     * @return total token count across all inventory slots
     */
    private int countInventoryTokens(final Player player) {
        final ItemStack currency = plugin.getCurrencyItem();
        int total = 0;
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && currency.isSimilar(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Counts compressed Kill Token blocks in the player's inventory.
     *
     * @param player the player
     * @return total compressed block count
     */
    private int countCompressedBlocks(final Player player) {
        final ItemStack block = plugin.getCompressedBlockManager().createCompressedBlock();
        int total = 0;
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && block.isSimilar(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
