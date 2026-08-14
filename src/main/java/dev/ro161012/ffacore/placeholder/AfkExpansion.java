package dev.ro161012.ffacore.placeholder;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.afk.AfkManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for the AFK subsystem.
 *
 * <h3>Placeholders</h3>
 * <table>
 *   <tr><th>Placeholder</th><th>Returns</th></tr>
 *   <tr><td>{@code %afk_zone%}</td><td>current AFK zone name, or empty</td></tr>
 *   <tr><td>{@code %afk_idle_seconds%}</td><td>seconds since last activity</td></tr>
 *   <tr><td>{@code %afk_earned%}</td><td>shards earned this session</td></tr>
 *   <tr><td>{@code %afk_zone_count%}</td><td>total configured zones</td></tr>
 *   <tr><td>{@code %afk_players%}</td><td>players currently inside a zone</td></tr>
 * </table>
 */
public final class AfkExpansion extends PlaceholderExpansion {

    private final FFACore plugin;

    /**
     * Creates the expansion.
     *
     * @param plugin owning plugin
     */
    public AfkExpansion(final FFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "afk";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ro161012";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(final Player player,
                                                 @NotNull final String identifier) {
        if (player == null) {
            return handleGlobal(identifier);
        }
        final AfkManager.Session session = plugin.getAfkManager().getSession(player.getUniqueId());
        return switch (identifier.toLowerCase(java.util.Locale.ROOT)) {
            case "zone" -> session == null ? "" : session.getZone().getName();
            case "idle_seconds" -> session == null ? "0" : String.valueOf(session.getIdleSeconds());
            case "earned" -> session == null ? "0" : String.valueOf(session.getTotalEarned());
            default -> handleGlobal(identifier);
        };
    }

    private String handleGlobal(final String identifier) {
        return switch (identifier.toLowerCase(java.util.Locale.ROOT)) {
            case "zone_count" -> String.valueOf(plugin.getAfkManager().getZoneCount());
            case "players" -> String.valueOf(plugin.getAfkManager().getActiveCount());
            default -> null;
        };
    }
}
