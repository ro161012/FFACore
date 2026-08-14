package dev.ro161012.ffacore.killtoken;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks anti-farming cooldowns between unordered pairs of players.
 *
 * <p>When player A kills player B, a cooldown is applied to exactly that
 * pair. While it is active, neither A killing B nor B killing A produces a
 * token. Kills involving any other player are unaffected.
 *
 * <p>This class is thread-safe.
 */
public final class PairCooldown {

    private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();
    private volatile long cooldownMillis;

    /**
     * Creates a new tracker.
     *
     * @param cooldownSeconds cooldown length in seconds
     */
    public PairCooldown(final long cooldownSeconds) {
        setCooldownSeconds(cooldownSeconds);
    }

    /**
     * Builds a symmetric key so that {@code (a, b)} and {@code (b, a)} map
     * to the same pair.
     */
    private static String pairKey(final UUID a, final UUID b) {
        final String first = a.toString();
        final String second = b.toString();
        return first.compareTo(second) <= 0
                ? first + '|' + second
                : second + '|' + first;
    }

    /**
     * Checks whether the given pair is currently on cooldown.
     *
     * @param a first player
     * @param b second player
     * @return true while the pair cooldown is active
     */
    public boolean isOnCooldown(final UUID a, final UUID b) {
        final Long until = expiryAt.get(pairKey(a, b));
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            expiryAt.remove(pairKey(a, b), until);
            return false;
        }
        return true;
    }

    /**
     * Starts (or restarts) the cooldown for the given pair.
     *
     * @param a first player
     * @param b second player
     */
    public void apply(final UUID a, final UUID b) {
        expiryAt.put(pairKey(a, b), System.currentTimeMillis() + cooldownMillis);
    }

    /**
     * Returns the remaining cooldown for the pair in milliseconds.
     *
     * @param a first player
     * @param b second player
     * @return remaining time in milliseconds, or 0 if not on cooldown
     */
    public long remainingMillis(final UUID a, final UUID b) {
        final Long until = expiryAt.get(pairKey(a, b));
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /**
     * Updates the cooldown length for future kills. Active cooldowns keep
     * their original expiry time.
     *
     * @param seconds new cooldown length in seconds
     */
    public void setCooldownSeconds(final long seconds) {
        this.cooldownMillis = Math.max(0L, seconds) * 1000L;
    }

    /**
     * Removes all expired entries. Called opportunistically; the map stays
     * small in practice because entries are dropped on read.
     */
    public void purgeExpired() {
        final long now = System.currentTimeMillis();
        expiryAt.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
