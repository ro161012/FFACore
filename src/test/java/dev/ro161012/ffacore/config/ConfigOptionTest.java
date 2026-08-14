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

        assertEquals("regeneration_phased_delay_between_phases", option.key());
    }

    @Test
    void dialogKeysAreAlwaysValidInputNames() {
        // Minecraft's dialog inputs reject any key that is not lowercase
        // alphanumerics and underscores ("key must be a valid input name").
        final java.util.regex.Pattern valid =
                java.util.regex.Pattern.compile("^[a-z0-9_]+$");
        for (final String path : java.util.List.of(
                "general.prefix",
                "general.auto-load-on-startup",
                "schedule.check-interval-seconds",
                "gui.rows",
                "gui.title",
                "regeneration.default-mode",
                "regeneration.max-concurrent",
                "regeneration.tick-budget",
                "regeneration.batch-size",
                "regeneration.teleport-players-to-spawn",
                "regeneration.phased.blocks-per-second",
                "regeneration.phased.delay-between-phases",
                "regeneration.wave.wave-speed",
                "regeneration.wave.reverse-order",
                "tokens-per-kill",
                "cooldown-seconds",
                "notify-on-cooldown",
                "kill-message",
                "killstreak.enabled",
                "killstreak.announcement-minimum",
                "killstreak.reward-start",
                "killstreak.reward-step",
                "killstreak.max-token-multiplier",
                "afk.enabled",
                "afk.reward-interval-seconds",
                "afk.shards-per-interval",
                "afk.min-idle-seconds",
                "afk.max-shards-per-hour",
                "afk.notify-on-earn",
                "afk.earn-message",
                "performance.use-async-save",
                "performance.use-async-load",
                "performance.cache-snapshots",
                "performance.max-cached-snapshots",
                "performance.compress-snapshots",
                "storage.save-interval-minutes")) {
            final ConfigOption option = ConfigOption.bool(path, "x", "y", true);
            assertTrue(valid.matcher(option.key()).matches(),
                    "invalid dialog key: " + option.key());
        }
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
