package dev.ro161012.ffacore.nichirin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cooldown tracker for the Nichirin Blade active abilities.
 *
 * <p>Each ability (Clear Blue Sky and Enbu) keeps its own tracker so the two
 * cooldowns never interfere. Entries are expired lazily on read and can be
 * purged in bulk. This class is thread-safe.
 */
public final class NichirinCooldown {

    private final Map<UUID, Long> expiryAt = new ConcurrentHashMap<>();
    private volatile long cooldownMillis;

    /**
     * Creates a tracker with a fixed cooldown length.
     *
     * @param cooldownSeconds cooldown length in seconds
     */
    public NichirinCooldown(final long cooldownSeconds) {
        this.cooldownMillis = Math.max(0L, cooldownSeconds) * 1000L;
    }

    /**
     * Returns whether the player is currently on cooldown, dropping the entry
     * if it has already expired.
     *
     * @param id the player id
     * @return true while the cooldown is active
     */
    public boolean isOnCooldown(final UUID id) {
        final Long until = expiryAt.get(id);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            expiryAt.remove(id, until);
            return false;
        }
        return true;
    }

    /**
     * Returns the remaining cooldown in milliseconds.
     *
     * @param id the player id
     * @return remaining milliseconds, or 0 when not on cooldown
     */
    public long remainingMillis(final UUID id) {
        final Long until = expiryAt.get(id);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /**
     * Starts the cooldown for the player.
     *
     * @param id the player id
     */
    public void apply(final UUID id) {
        expiryAt.put(id, System.currentTimeMillis() + cooldownMillis);
    }

    /**
     * Clears any active cooldown for the player.
     *
     * @param id the player id
     */
    public void clear(final UUID id) {
        expiryAt.remove(id);
    }

    /**
     * Updates the cooldown length for future activations. Active cooldowns
     * keep their original expiry time.
     *
     * @param seconds new cooldown length in seconds
     */
    public void setCooldownSeconds(final long seconds) {
        this.cooldownMillis = Math.max(0L, seconds) * 1000L;
    }

    /**
     * Removes every expired entry.
     */
    public void purgeExpired() {
        final long now = System.currentTimeMillis();
        expiryAt.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
