package dev.ro161012.ffacore.config;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game configuration menu built on the Paper 1.21.6+ Dialog API.
 *
 * <p>Admins open the menu with {@code /ffa config}: a single dialog lists
 * every editable option at once, prefilled with the current values. Hitting
 * "Save &amp; Apply" writes the response straight back into {@code config.yml},
 * saves it, and pushes the change through {@link FFACore#applyConfig()} so it
 * takes effect in realtime without a restart.
 */
public final class ConfigMenu {

    private static final int BUTTON_WIDTH = 200;
    private static final int BODY_WIDTH = 400;
    private static final int MAX_STRING_LENGTH = 256;

    private final FFACore plugin;
    private final List<ConfigOption> options;

    /**
     * Creates the config menu and defines the editable options.
     *
     * @param plugin owning FFACore plugin
     */
    public ConfigMenu(final FFACore plugin) {
        this.plugin = plugin;
        this.options = defineOptions();
    }

    // ------------------------------------------------------------------
    //  Public entry point
    // ------------------------------------------------------------------

    /**
     * Opens the configuration dialog for a player.
     *
     * @param player the player to show the menu to
     */
    public void openMainMenu(final Player player) {
        if (!player.hasPermission("ffacore.admin")) {
            Messages.raw(player, "&cYou don't have permission for that.");
            return;
        }
        try {
            openConfigDialog(player);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to build the config dialog", ex);
            Messages.raw(player, "&cFailed to open the config dialog: &f" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  The single configuration dialog
    // ------------------------------------------------------------------

    private void openConfigDialog(final Player player) {
        final List<DialogInput> inputs = new ArrayList<>();
        for (final ConfigOption option : options) {
            inputs.add(buildInput(option));
        }

        final ActionButton save = button("Save & Apply",
                tooltip("Write every new value to config.yml and apply it right now."),
                click((view, audience) -> {
                    if (audience instanceof Player target) {
                        onMainThread(() -> safeApply(target, view));
                    }
                }));
        final ActionButton reload = button("Reload from disk",
                tooltip("Discard unsaved edits and re-read config.yml."),
                click((view, audience) -> {
                    if (audience instanceof Player target) {
                        onMainThread(() -> safeReload(target));
                    }
                }));
        final ActionButton close = closeButton();

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("FFACore Configuration",
                        NamedTextColor.WHITE, TextDecoration.BOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Edit any value below - changes apply in realtime when you hit Save & Apply.",
                                NamedTextColor.GRAY), BODY_WIDTH)))
                        .inputs(inputs)
                        .afterAction(DialogAfterAction.CLOSE)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.of(save, reload, close), close, 1)));

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    //  Inputs
    // ------------------------------------------------------------------

    private DialogInput buildInput(final ConfigOption option) {
        final Object current = option.currentValue(plugin.getConfig());
        return switch (option.kind()) {
            case BOOLEAN -> DialogInput.bool(option.key(), label(option))
                    .initial((Boolean) current)
                    .onTrue("Enabled")
                    .onFalse("Disabled")
                    .build();
            case INTEGER -> DialogInput.numberRange(option.key(), label(option),
                    option.min(), option.max())
                    .initial(clamp(((Number) current).floatValue(), option.min(), option.max()))
                    .step(stepFor(option))
                    .width(BODY_WIDTH)
                    .build();
            case STRING -> {
                final String value = (String) current;
                yield DialogInput.text(option.key(), label(option))
                        .initial(value.length() > MAX_STRING_LENGTH
                                ? value.substring(0, MAX_STRING_LENGTH) : value)
                        .maxLength(MAX_STRING_LENGTH)
                        .width(BODY_WIDTH)
                        .build();
            }
            case ENUM -> {
                final String selected = ((String) current).toUpperCase(Locale.ROOT);
                final List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                for (final String choice : option.choices()) {
                    entries.add(SingleOptionDialogInput.OptionEntry.create(choice,
                            label(choice), choice.equals(selected)));
                }
                yield DialogInput.singleOption(option.key(), label(option), entries)
                        .width(BODY_WIDTH)
                        .build();
            }
        };
    }

    /**
     * Picks a sensible slider step so the whole range stays navigable.
     *
     * @param option the integer option
     * @return the step
     */
    private float stepFor(final ConfigOption option) {
        final float span = option.max() - option.min();
        if (span <= 10f) {
            return 1f;
        }
        if (span <= 100f) {
            return 5f;
        }
        if (span <= 1000f) {
            return 25f;
        }
        return 100f;
    }

    private static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    //  Saving and reloading
    // ------------------------------------------------------------------

    /**
     * Applies the dialog response, telling the player what happened - even
     * when something goes wrong, so failures are never silent.
     *
     * @param player the player who clicked
     * @param view   the dialog response view
     */
    private void safeApply(final Player player, final DialogResponseView view) {
        try {
            final FileConfiguration config = plugin.getConfig();
            for (final ConfigOption option : options) {
                final Object value = readValue(option, view);
                if (value != null) {
                    config.set(option.path(), value);
                }
            }
            plugin.saveConfig();
            plugin.applyConfig();
            confirmSaved(player);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to apply config changes", ex);
            Messages.raw(player, "&cFailed to apply changes: &f" + ex.getMessage());
        }
    }

    private void safeReload(final Player player) {
        try {
            plugin.reloadConfig();
            plugin.applyConfig();
            confirmReloaded(player);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to reload config", ex);
            Messages.raw(player, "&cFailed to reload config: &f" + ex.getMessage());
        }
    }

    private void confirmSaved(final Player player) {
        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Saved", NamedTextColor.GREEN))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Configuration updated and applied in realtime.",
                                NamedTextColor.GRAY), BODY_WIDTH)))
                        .afterAction(DialogAfterAction.CLOSE)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.notice(backToMenuButton())));

        player.showDialog(dialog);
    }

    private void confirmReloaded(final Player player) {
        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Reloaded", NamedTextColor.GREEN))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "config.yml reloaded from disk and applied.",
                                NamedTextColor.GRAY), BODY_WIDTH)))
                        .afterAction(DialogAfterAction.CLOSE)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.notice(backToMenuButton())));

        player.showDialog(dialog);
    }

    /**
     * Reads one option back from the dialog response view, validating enum
     * choices. Returns null when the player left the input unchanged.
     *
     * @param option the option definition
     * @param view   the response view
     * @return the parsed value, or null
     */
    private Object readValue(final ConfigOption option, final DialogResponseView view) {
        return switch (option.kind()) {
            case BOOLEAN -> view.getBoolean(option.key());
            case INTEGER -> {
                final Float value = view.getFloat(option.key());
                yield value == null ? null : Math.round(value);
            }
            case STRING -> view.getText(option.key());
            case ENUM -> {
                final String raw = view.getText(option.key());
                if (raw == null) {
                    yield null;
                }
                final String value = raw.toUpperCase(Locale.ROOT);
                yield option.choices().contains(value) ? value : null;
            }
        };
    }

    // ------------------------------------------------------------------
    //  Options
    // ------------------------------------------------------------------

    private List<ConfigOption> defineOptions() {
        return List.of(
                ConfigOption.string("general.prefix", "Chat prefix",
                        "Prefix used on FFACore chat messages.",
                        "&8[&bFFACore&8]&r"),
                ConfigOption.bool("general.auto-load-on-startup",
                        "Load arenas on startup",
                        "Load saved arenas when the server starts.", true),
                ConfigOption.integer("schedule.check-interval-seconds",
                        "Schedule check interval",
                        "How often scheduled regenerations are checked, in seconds.",
                        1, 60, 1),
                ConfigOption.integer("gui.rows", "Arena GUI rows",
                        "Height of the arena management GUI (1-6 rows).",
                        1, 6, 6),
                ConfigOption.string("gui.title", "Arena GUI title",
                        "Title of the arena management GUI.",
                        "&8Arena Management"),
                ConfigOption.enumOption("regeneration.default-mode", "Regen default mode",
                        "Restoration algorithm used for new arenas.",
                        List.of("STANDARD", "PHASED", "SELECTIVE", "WAVE", "WORLD_EDIT"),
                        "STANDARD"),
                ConfigOption.integer("regeneration.max-concurrent", "Regen max concurrent",
                        "How many arenas may regenerate at the same time.",
                        1, 16, 2),
                ConfigOption.integer("regeneration.tick-budget", "Regen tick budget",
                        "Max milliseconds of block placement per tick.",
                        1, 100, 15),
                ConfigOption.integer("regeneration.batch-size", "Regen batch size",
                        "Blocks placed per tick in STANDARD/SELECTIVE modes.",
                        1, 100000, 1000),
                ConfigOption.bool("regeneration.teleport-players-to-spawn",
                        "Teleport players out",
                        "Teleport players inside an arena to its spawn before restoring.",
                        true),
                ConfigOption.integer("regeneration.phased.blocks-per-second",
                        "Phased rate",
                        "Blocks restored per second in PHASED mode.",
                        1, 1000000, 50000),
                ConfigOption.integer("regeneration.phased.delay-between-phases",
                        "Phased delay",
                        "Ticks between PHASED mode phases.", 0, 60, 2),
                ConfigOption.integer("regeneration.wave.wave-speed", "Wave speed",
                        "Blocks restored per second in WAVE mode.",
                        1, 1000000, 10000),
                ConfigOption.bool("regeneration.wave.reverse-order", "Reverse wave",
                        "Restore WAVE mode from the far side instead.", false),
                ConfigOption.integer("tokens-per-kill", "Tokens per kill",
                        "Tokens dropped per qualifying kill before the streak multiplier.",
                        1, 64, 1),
                ConfigOption.integer("cooldown-seconds", "Pair cooldown",
                        "Seconds a killer-victim pair waits before another token can drop.",
                        0, 3600, 60),
                ConfigOption.bool("notify-on-cooldown", "Notify on cooldown",
                        "Warn the killer when the pair cooldown suppresses a drop.",
                        true),
                ConfigOption.string("kill-message", "Kill message",
                        "Message sent to the killer when a token drops. Empty disables it.",
                        "&4&l+1 &cKill Token"),
                ConfigOption.bool("killstreak.enabled", "Killstreaks",
                        "Enable killstreak announcements and token multipliers.", true),
                ConfigOption.integer("killstreak.announcement-minimum",
                        "Announce at streak",
                        "Streak length from which announcements are broadcast.",
                        1, 50, 2),
                ConfigOption.integer("killstreak.reward-start", "Multiplier starts at",
                        "Streak where the token multiplier kicks in.", 1, 50, 3),
                ConfigOption.integer("killstreak.reward-step", "Multiplier step",
                        "Kills between multiplier increases.", 1, 50, 3),
                ConfigOption.integer("killstreak.max-token-multiplier", "Max multiplier",
                        "Highest streak token multiplier.", 1, 5, 5),
                ConfigOption.bool("afk.enabled", "AFK rewards",
                        "Whether the AFK shard reward loop runs.", true),
                ConfigOption.integer("afk.reward-interval-seconds", "AFK reward interval",
                        "How often the reward loop runs, in seconds.", 5, 3600, 30),
                ConfigOption.integer("afk.shards-per-interval", "AFK shards per reward",
                        "Shards awarded per reward when the player qualifies.",
                        1, 64, 1),
                ConfigOption.integer("afk.min-idle-seconds", "AFK minimum idle",
                        "Seconds a player must be idle before earning shards.",
                        0, 3600, 60),
                ConfigOption.integer("afk.max-shards-per-hour", "AFK hourly cap",
                        "Hard cap on shards per player per hour. -1 disables it.",
                        -1, 10000, 100),
                ConfigOption.bool("afk.notify-on-earn", "AFK notify on earn",
                        "Tell the player when they earn shards.", true),
                ConfigOption.string("afk.earn-message", "AFK earn message",
                        "Message sent when the player earns shards.",
                        "&b+1 &3AFK Shard &7(the deep rewards patience)"),
                ConfigOption.bool("performance.use-async-save", "Async snapshot saves",
                        "Write block snapshots off the main thread.", true),
                ConfigOption.bool("performance.use-async-load", "Async snapshot loads",
                        "Read block snapshots off the main thread.", true),
                ConfigOption.bool("performance.cache-snapshots", "Cache snapshots",
                        "Keep recently used snapshots in memory.", true),
                ConfigOption.integer("performance.max-cached-snapshots", "Snapshot cache size",
                        "Maximum snapshot lists kept in memory before eviction.",
                        1, 50, 10),
                ConfigOption.bool("performance.compress-snapshots", "Compress snapshots",
                        "GZIP-compress snapshots on disk.", true),
                ConfigOption.integer("storage.save-interval-minutes", "Auto-save interval",
                        "Minutes between automatic arena metadata saves.",
                        1, 1440, 30)
        );
    }

    // ------------------------------------------------------------------
    //  Small builders
    // ------------------------------------------------------------------

    private ActionButton button(final String text, final Component tooltip,
                                final DialogAction action) {
        return ActionButton.builder(Component.text(text, NamedTextColor.WHITE))
                .tooltip(tooltip)
                .width(BUTTON_WIDTH)
                .action(action)
                .build();
    }

    private Component tooltip(final String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private Component label(final ConfigOption option) {
        return Component.text(option.label(), NamedTextColor.WHITE);
    }

    private Component label(final String text) {
        return Component.text(text, NamedTextColor.WHITE);
    }

    private ActionButton closeButton() {
        return button("Close", tooltip("Close the configuration menu."),
                click((view, audience) -> {
                    if (audience instanceof Player target) {
                        target.closeDialog();
                    }
                }));
    }

    private ActionButton backToMenuButton() {
        return button("Back to menu", tooltip("Return to the configuration menu."),
                click((view, audience) -> {
                    if (audience instanceof Player target) {
                        openMainMenu(target);
                    }
                }));
    }

    /**
     * Wraps a response handler in the Paper click-callback plumbing: one use
     * and the default lifetime.
     *
     * @param handler the handler receiving the response view and audience
     * @return the click action
     */
    private DialogAction click(final DialogActionCallback handler) {
        return DialogAction.customClick(handler,
                ClickCallback.Options.builder()
                        .uses(1)
                        .lifetime(ClickCallback.DEFAULT_LIFETIME)
                        .build());
    }

    /**
     * Runs a task on the server thread, hopping over when the callback fired
     * on an I/O thread.
     *
     * @param task the task to run
     */
    private void onMainThread(final Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
