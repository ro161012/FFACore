package dev.ro161012.ffacore.nichirin;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NichirinCombo}, the Flame Combo passive tracker.
 */
class NichirinComboTest {

    @Test
    void completesAfterRequiredHits() {
        final NichirinCombo combo = new NichirinCombo(4);
        final UUID id = UUID.randomUUID();

        assertFalse(combo.registerHit(id));
        assertFalse(combo.registerHit(id));
        assertFalse(combo.registerHit(id));
        assertTrue(combo.registerHit(id), "the fourth hit completes the combo");
    }

    @Test
    void resetsAfterCompletion() {
        final NichirinCombo combo = new NichirinCombo(4);
        final UUID id = UUID.randomUUID();

        combo.registerHit(id);
        combo.registerHit(id);
        combo.registerHit(id);
        combo.registerHit(id);

        assertEquals(0, combo.getHits(id), "the combo resets after completing");
    }

    @Test
    void resetClearsProgress() {
        final NichirinCombo combo = new NichirinCombo(4);
        final UUID id = UUID.randomUUID();

        combo.registerHit(id);
        combo.registerHit(id);
        combo.reset(id);

        assertEquals(0, combo.getHits(id), "reset must clear progress");
    }

    @Test
    void hitsAreTrackedPerPlayer() {
        final NichirinCombo combo = new NichirinCombo(4);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        combo.registerHit(a);

        assertEquals(1, combo.getHits(a));
        assertEquals(0, combo.getHits(b), "other players must be unaffected");
    }
}
