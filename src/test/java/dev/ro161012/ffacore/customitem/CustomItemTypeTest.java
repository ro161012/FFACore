package dev.ro161012.ffacore.customitem;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Regression tests for the custom-item command key resolver. */
class CustomItemTypeTest {

    @Test
    void acceptsCaseSpacesAndDashes() {
        assertEquals(CustomItemType.DASH_SWORD, CustomItemType.fromKey("DASH-SWORD"));
        assertEquals(CustomItemType.FROST_SWORD, CustomItemType.fromKey(" frost sword "));
    }

    @Test
    void isLocaleIndependentForPlayerInput() {
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(CustomItemType.PIGXALIUR,
                    CustomItemType.fromKey("PIGXALIUR"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsEmptyAndUnknownKeys() {
        assertNull(CustomItemType.fromKey(null));
        assertNull(CustomItemType.fromKey("   "));
        assertNull(CustomItemType.fromKey("not-an-item"));
    }
}
