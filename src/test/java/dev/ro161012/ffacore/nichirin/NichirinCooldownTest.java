package dev.ro161012.ffacore.nichirin;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NichirinCooldown}, the per-player ability cooldown.
 */
class NichirinCooldownTest {

    @Test
    void appliesCooldownPerPlayer() {
        final NichirinCooldown cooldown = new NichirinCooldown(50);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        cooldown.apply(a);

        assertTrue(cooldown.isOnCooldown(a), "A should be on cooldown");
        assertFalse(cooldown.isOnCooldown(b), "B should be unaffected");
    }

    @Test
    void reportsRemainingTimeWhileActive() {
        final NichirinCooldown cooldown = new NichirinCooldown(50);
        final UUID a = UUID.randomUUID();

        cooldown.apply(a);

        final long remaining = cooldown.remainingMillis(a);
        assertTrue(remaining > 0, "remaining time should be positive");
        assertTrue(remaining <= 50_000, "remaining time should not exceed the cooldown");
    }

    @Test
    void zeroCooldownExpiresImmediately() {
        final NichirinCooldown cooldown = new NichirinCooldown(0);
        final UUID a = UUID.randomUUID();

        cooldown.apply(a);

        assertFalse(cooldown.isOnCooldown(a), "a zero cooldown expires immediately");
        assertEquals(0, cooldown.remainingMillis(a), "remaining should be zero");
    }

    @Test
    void clearRemovesCooldown() {
        final NichirinCooldown cooldown = new NichirinCooldown(50);
        final UUID a = UUID.randomUUID();

        cooldown.apply(a);
        cooldown.clear(a);

        assertFalse(cooldown.isOnCooldown(a), "cleared cooldown must not be active");
    }

    @Test
    void setCooldownSecondsKeepsActiveEntries() {
        final NichirinCooldown cooldown = new NichirinCooldown(50);
        final UUID a = UUID.randomUUID();

        cooldown.apply(a);
        cooldown.setCooldownSeconds(100);

        assertTrue(cooldown.isOnCooldown(a), "active entries must survive a duration change");
    }
}
