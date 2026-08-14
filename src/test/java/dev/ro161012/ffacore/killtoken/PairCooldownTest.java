package dev.ro161012.ffacore.killtoken;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PairCooldown}, the unordered-pair anti-farming tracker.
 */
class PairCooldownTest {

    @Test
    void appliesCooldownSymmetrically() {
        final PairCooldown cooldown = new PairCooldown(60);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        cooldown.apply(a, b);

        assertTrue(cooldown.isOnCooldown(a, b), "A->B should be on cooldown");
        assertTrue(cooldown.isOnCooldown(b, a), "B->A should share the cooldown");
    }

    @Test
    void otherPairsAreUnaffected() {
        final PairCooldown cooldown = new PairCooldown(60);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final UUID c = UUID.randomUUID();

        cooldown.apply(a, b);

        assertFalse(cooldown.isOnCooldown(a, c), "unrelated pairs must stay clear");
        assertFalse(cooldown.isOnCooldown(b, c), "unrelated pairs must stay clear");
    }

    @Test
    void reportsRemainingTimeWhileActive() {
        final PairCooldown cooldown = new PairCooldown(60);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        cooldown.apply(a, b);

        final long remaining = cooldown.remainingMillis(a, b);
        assertTrue(remaining > 0, "remaining time should be positive");
        assertTrue(remaining <= 60_000, "remaining time should not exceed the cooldown");
    }

    @Test
    void zeroCooldownExpiresImmediately() {
        final PairCooldown cooldown = new PairCooldown(0);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        cooldown.apply(a, b);

        assertEquals(0, cooldown.remainingMillis(a, b), "a zero cooldown expires immediately");
    }

    @Test
    void purgeRemovesExpiredEntries() {
        final PairCooldown cooldown = new PairCooldown(0);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        cooldown.apply(a, b);
        cooldown.purgeExpired();

        assertFalse(cooldown.isOnCooldown(a, b), "purged pairs must not be on cooldown");
    }
}
