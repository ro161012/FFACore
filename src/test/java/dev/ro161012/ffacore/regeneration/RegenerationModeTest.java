package dev.ro161012.ffacore.regeneration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link RegenerationMode#fromString(String)}.
 */
class RegenerationModeTest {

    @Test
    void parsesCaseInsensitively() {
        assertEquals(RegenerationMode.STANDARD, RegenerationMode.fromString("standard"));
        assertEquals(RegenerationMode.PHASED, RegenerationMode.fromString("PHASED"));
        assertEquals(RegenerationMode.SELECTIVE, RegenerationMode.fromString("Selective"));
    }

    @Test
    void parsesEveryMode() {
        assertEquals(RegenerationMode.WAVE, RegenerationMode.fromString("WAVE"));
        assertEquals(RegenerationMode.WORLD_EDIT, RegenerationMode.fromString("world_edit"));
    }

    @Test
    void unknownValuesFallBackToStandard() {
        assertEquals(RegenerationMode.STANDARD, RegenerationMode.fromString("garbage"));
        assertEquals(RegenerationMode.STANDARD, RegenerationMode.fromString(null));
    }
}
