package dev.ro161012.ffacore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ConfigOption} value reading and dialog key generation.
 */
class ConfigOptionTest {

    @Test
    void readsBooleanFromConfig() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("afk.enabled", false);
        final ConfigOption option =
                ConfigOption.bool("afk.enabled", "AFK rewards", "Whether rewards run.", true);

        assertEquals(false, option.currentValue(config));
    }

    @Test
    void fallsBackToDefaultWhenMissing() {
        final YamlConfiguration config = new YamlConfiguration();
        final ConfigOption option = ConfigOption.integer(
                "cooldown-seconds", "Pair cooldown", "Cooldown length.", 0, 3600, 60);

        assertEquals(60, option.currentValue(config));
    }

    @Test
    void readsIntegerAndStringValues() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("tokens-per-kill", 3);
        config.set("general.prefix", "&8[&bX&8]");

        assertEquals(3, ConfigOption.integer(
                "tokens-per-kill", "Tokens per kill", "Drop amount.", 1, 64, 1)
                .currentValue(config));
        assertEquals("&8[&bX&8]", ConfigOption.string(
                "general.prefix", "Chat prefix", "Message prefix.", "&8[&bFFACore&8]&r")
                .currentValue(config));
    }

    @Test
    void readsEnumChoice() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("regeneration.default-mode", "PHASED");
        final ConfigOption option = ConfigOption.enumOption(
                "regeneration.default-mode", "Default mode", "Restoration mode.",
                java.util.List.of("STANDARD", "PHASED"), "STANDARD");

        assertEquals("PHASED", option.currentValue(config));
    }

    @Test
    void sanitisesConfigPathsIntoDialogKeys() {
        final ConfigOption option = ConfigOption.integer(
                "regeneration.phased.delay-between-phases", "Phased delay",
                "Ticks between phases.", 0, 60, 2);

        assertEquals("regeneration_phased_delay-between-phases", option.key());
    }

    @Test
    void enumOptionRetainsChoices() {
        final ConfigOption option = ConfigOption.enumOption(
                "regeneration.default-mode", "Default mode", "Restoration mode.",
                java.util.List.of("STANDARD", "WAVE"), "STANDARD");

        assertTrue(option.choices().contains("WAVE"));
        assertFalse(option.choices().contains("NOPE"));
    }
}
