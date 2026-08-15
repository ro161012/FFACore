package dev.ro161012.ffacore.nichirin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the Flame Combo passive: consecutive landed hits without taking
 * damage.
 *
 * <p>Each hit increments the combo. When it reaches the required count the
 * combo completes (granting Strength II) and resets to zero. Taking damage
 * resets the combo immediately. This class is thread-safe.
 */
public final class NichirinCombo {

    private final Map<UUID, Integer> hits = new ConcurrentHashMap<>();
    private final int hitsRequired;

    /**
     * Creates a combo tracker.
     *
     * @param hitsRequired hits needed to complete the combo, at least one
     */
    public NichirinCombo(final int hitsRequired) {
        this.hitsRequired = Math.max(1, hitsRequired);
    }

    /**
     * Registers a landed hit for the player.
     *
     * @param id the player id
     * @return true when this hit completed the combo (and it has reset)
     */
    public boolean registerHit(final UUID id) {
        final int next = hits.merge(id, 1, Integer::sum);
        if (next >= hitsRequired) {
            hits.remove(id);
            return true;
        }
        return false;
    }

    /**
     * Resets the combo (used when the player takes damage or quits).
     *
     * @param id the player id
     */
    public void reset(final UUID id) {
        hits.remove(id);
    }

    /**
     * Returns the current combo count.
     *
     * @param id the player id
     * @return number of landed hits, at least zero
     */
    public int getHits(final UUID id) {
        return hits.getOrDefault(id, 0);
    }
}
