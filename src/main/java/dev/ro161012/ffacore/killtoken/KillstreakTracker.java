package dev.ro161012.ffacore.killtoken;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

/**
 * Tracks consecutive PvP kills, lifetime kills, deaths, and best streak
 * per player. Each qualifying kill broadcasts the player's current streak
 * in chat and plays a fixed-pitch sound only for that player.
 *
 * <p>A streak ends when its owner dies — from any cause — or leaves the
 * server. Lifetime kill and death counts persist until server restart.
 * All stats are kept in memory only.
 */
public final class KillstreakTracker {

    private static final float VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    private final KillTokenManager plugin;
    private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> totalKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> totalDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bestStreaks = new ConcurrentHashMap<>();

    /**
     * Creates the tracker.
     *
     * @param plugin owning plugin instance
     */
    public KillstreakTracker(final KillTokenManager plugin) {
        this.plugin = plugin;
    }

    /**
     * Increments the killer's streak and lifetime kills, announces in chat,
     * and returns the new streak length.
     *
     * @param killer the player who scored the kill
     * @return the new streak length
     */
    public int increment(final Player killer) {
        final UUID id = killer.getUniqueId();
        final int streak = streaks.merge(id, 1, Integer::sum);

        // Track lifetime kills
        totalKills.merge(id, 1, Integer::sum);

        // Track best streak
        bestStreaks.merge(id, streak, Math::max);

        announce(killer, streak);
        return streak;
    }

    /**
     * Records a death for the given player (any cause) and resets their
     * current streak.
     *
     * @param playerId the player who died
     */
    public void recordDeath(final UUID playerId) {
        totalDeaths.merge(playerId, 1, Integer::sum);
        streaks.remove(playerId);
    }

    /**
     * Shows a configured streak announcement without changing the player's
     * real streak. Used by the administrator test command.
     *
     * @param player player used in the preview
     * @param streak streak length to preview
     */
    public void preview(final Player player, final int streak) {
        announce(player, Math.max(1, streak));
    }

    /**
     * Clears a player's streak. Called whenever the player dies (from any
     * cause) or disconnects. Does not reset lifetime stats.
     *
     * @param playerId the player whose streak ends
     */
    public void reset(final UUID playerId) {
        streaks.remove(playerId);
    }

    /**
     * Returns the current streak length for a player.
     *
     * @param playerId the player
     * @return streak length, 0 if none
     */
    public int getStreak(final UUID playerId) {
        return streaks.getOrDefault(playerId, 0);
    }

    /**
     * Returns the total lifetime PvP kills for a player.
     *
     * @param playerId the player
     * @return total kills, 0 if none recorded
     */
    public int getTotalKills(final UUID playerId) {
        return totalKills.getOrDefault(playerId, 0);
    }

    /**
     * Returns the total lifetime deaths for a player.
     *
     * @param playerId the player
     * @return total deaths, 0 if none recorded
     */
    public int getTotalDeaths(final UUID playerId) {
        return totalDeaths.getOrDefault(playerId, 0);
    }

    /**
     * Returns the best (highest) streak ever achieved by a player.
     *
     * @param playerId the player
     * @return best streak, 0 if none recorded
     */
    public int getBestStreak(final UUID playerId) {
        return bestStreaks.getOrDefault(playerId, 0);
    }

    /**
     * Returns the kill/death ratio as a formatted string, or "N/A".
     *
     * @param playerId the player
     * @return KDR string
     */
    public String getKdr(final UUID playerId) {
        final int kills = getTotalKills(playerId);
        final int deaths = getTotalDeaths(playerId);
        if (deaths == 0) {
            return kills == 0 ? "N/A" : String.format("%.1f", (double) kills);
        }
        return String.format("%.2f", (double) kills / deaths);
    }

    /**
     * Broadcasts the configured streak message and plays the configured sound
     * for the streak owner alone. The pitch stays at Minecraft's normal 1.0.
     *
     * @param killer player whose streak increased
     * @param streak current streak length
     */
    public List<Map.Entry<UUID, Integer>> getTopKills() {
        return totalKills.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
    }

    private void announce(final Player killer, final int streak) {
        if (!plugin.shouldAnnounceKillstreak(streak)) {
            return;
        }

        final String message = plugin.getKillstreakMessage()
                .replace("%player%", killer.getName())
                .replace("%streak%", String.valueOf(streak));
        plugin.getServer().broadcastMessage(message);
        killer.playSound(killer.getLocation(), plugin.getKillstreakSound(), VOLUME, SOUND_PITCH);
    }
}
