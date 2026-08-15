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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game configuration menus built on the Paper 1.21.6+ Dialog API.
 *
 * <p>Admins open the menu with {@code /ffa config}: a main menu lists the
 * config sections, and each section opens its own dialog with the editable
 * options prefilled from the current values. Hitting "Save &amp; Apply"
 * writes the response straight back into {@code config.yml}, saves it, and
 * pushes the change through {@link FFACore#applyConfig()} so it takes effect
 * in realtime without a restart.
 *
 * <p>Every dialog is built defensively: slider values are clamped into their
 * ranges, string defaults are truncated to the input limit, and any failure
 * while opening or saving is shown to the player instead of being silently
 * swallowed by the click callback. Saving always applies the new values to
 * every subsystem immediately - there is no separate refresh step.
 */
public final class ConfigMenu {

    private static final int BUTTON_WIDTH = 200;
    private static final int BODY_WIDTH = 400;
    private static final int MAX_STRING_LENGTH = 256;

    // Weapon section themes.
    private static final TextColor NICHIRIN = TextColor.color(0xFF8C42);
    private static final TextColor KOKUSHIBO = TextColor.color(0xB06BE8);

    private final FFACore plugin;
    private final List<Section> sections;

    /**
     * Creates the config menu and defines the editable sections.
     *
     * @param plugin owning FFACore plugin
     */
    public ConfigMenu(final FFACore plugin) {
        this.plugin = plugin;
        this.sections = defineSections();
    }

    // ------------------------------------------------------------------
    //  Public entry point
    // ------------------------------------------------------------------

    /**
     * Opens the top-level configuration dialog for a player.
     *
     * @param player the player to show the menu to
     */
    public void openMainMenu(final Player player) {
        if (!player.hasPermission("ffacore.admin")) {
            Messages.raw(player, "&cYou don't have permission for that.");
            return;
        }
        try {
            openSectionList(player);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to build the config dialog", ex);
            Messages.raw(player, "&cFailed to open the config menu: &f" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  The section list (main menu)
    // ------------------------------------------------------------------

    private void openSectionList(final Player player) {
        final List<ActionButton> buttons = new ArrayList<>();
        for (final Section section : sections) {
            buttons.add(button(section.title(), tooltip(section.description()),
                    click((view, audience) -> {
                        if (audience instanceof Player target) {
                            onMainThread(() -> openSectionOrSubmenu(target, section));
                        }
                    }), section.color()));
        }
        final ActionButton close = closeButton();

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("FFACore Configuration",
                        NamedTextColor.WHITE, TextDecoration.BOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Pick a section to configure. Changes apply automatically when you save.",
                                NamedTextColor.GRAY), BODY_WIDTH)))
                        .afterAction(DialogAfterAction.CLOSE)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons, close, 1)));

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    //  Section dialogs
    // ------------------------------------------------------------------

    /**
     * Opens a section dialog, converting any failure into a visible message
     * so a broken input can never make a click appear to do nothing.
     *
     * @param player  the player
     * @param section the section to open
     */
    private void openSectionSafely(final Player player, final Section section) {
        try {
            openSection(player, section);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to open config section " + section.id(), ex);
            Messages.raw(player, "&cFailed to open the section: &f" + ex.getMessage());
        }
    }

    private void openSection(final Player player, final Section section) {
        final List<DialogInput> inputs = new ArrayList<>();
        for (final ConfigOption option : section.options()) {
            inputs.add(buildInput(option));
        }

        final ActionButton save = button("Save & Apply",
                tooltip("Write the new values to config.yml and apply them now."),
                click((view, audience) -> {
                    if (audience instanceof Player target) {
                        onMainThread(() -> safeApply(target, section, view));
                    }
                }));
        final ActionButton back = backButton();

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(section.title(),
                        section.color() == null ? NamedTextColor.WHITE : section.color(),
                        TextDecoration.BOLD))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text(section.description(), NamedTextColor.GRAY),
                                BODY_WIDTH)))
                        .inputs(inputs)
                        .afterAction(DialogAfterAction.CLOSE)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.of(save, back), back, 2)));

        player.showDialog(dialog);
    }

    /**
     * Routes a section click to either the option dialog or a submenu listing
     * its child sections.
     *
     * @param player  the player
     * @param section the clicked section
     */
    private void openSectionOrSubmenu(final Player player, final Section section) {
        if (section.children().isEmpty()) {
            openSectionSafely(player, section);
        } else {
            openSubmenuSafely(player, section);
        }
    }

    /**
     * Opens a submenu, converting any failure into a visible message.
     *
     * @param player  the player
     * @param section the section whose children are listed
     */
    private void openSubmenuSafely(final Player player, final Section section) {
        try {
            openSubmenu(player, section);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to open config submenu " + section.id(), ex);
            Messages.raw(player, "&cFailed to open the submenu: &f" + ex.getMessage());
        }
    }

    /**
     * Lists a section's child sections as buttons; clicking one opens that
     * child's option dialog.
     *
     * @param player  the player
     * @param section the parent section
     */
    private void openSubmenu(final Player player, final Section section) {
        final List<ActionButton> buttons = new ArrayList<>();
        for (final Section child : section.children()) {
            buttons.add(button(child.title(), tooltip(child.description()),
                    click((view, audience) -> {
                        if (audience instanceof Player target) {
                            onMainThread(() -> openSectionSafely(target, child));
                        }
                    }), child.color()));
        }
        final ActionButton back = backButton();

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(section.title(),
                        NamedTextColor.WHITE, TextDecoration.BOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                section.description(), NamedTextColor.GRAY), BODY_WIDTH)))
                        .afterAction(DialogAfterAction.CLOSE)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons, back, 1)));

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
            case DECIMAL -> DialogInput.numberRange(option.key(), label(option),
                    option.min(), option.max())
                    .initial(clamp(((Number) current).floatValue(), option.min(), option.max()))
                    .step(option.step())
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
     * Applies the dialog response for one section, telling the player what
     * happened - even when something goes wrong, so failures are never silent.
     *
     * @param player  the player who clicked
     * @param section the section being edited
     * @param view    the dialog response view
     */
    private void safeApply(final Player player, final Section section,
                           final DialogResponseView view) {
        try {
            final FileConfiguration config = plugin.getConfig();
            for (final ConfigOption option : section.options()) {
                final Object value = readValue(option, view);
                if (value != null) {
                    config.set(option.path(), value);
                }
            }
            plugin.saveConfig();
            plugin.applyConfig();
            confirmSaved(player, section);
        } catch (final RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to apply config changes", ex);
            Messages.raw(player, "&cFailed to apply changes: &f" + ex.getMessage());
        }
    }

    private void confirmSaved(final Player player, final Section section) {
        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Saved",
                        NamedTextColor.GREEN, TextDecoration.BOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                section.title() + " updated and applied in realtime.",
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
            case DECIMAL -> {
                final Float value = view.getFloat(option.key());
                yield value == null ? null : Math.round(value * 10.0f) / 10.0d;
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
    //  Sections
    // ------------------------------------------------------------------

    private List<Section> defineSections() {
        return List.of(
                new Section("general", "General",
                        "Plugin prefix, arena GUI layout and schedule checks.",
                        List.of(
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
                                        "&8Arena Management")
                        )),
                new Section("regeneration", "Regeneration",
                        "How arenas restore their blocks after a fight.",
                        List.of(
                                ConfigOption.enumOption("regeneration.default-mode",
                                        "Default mode",
                                        "Restoration algorithm used for new arenas.",
                                        List.of("STANDARD", "PHASED", "SELECTIVE", "WAVE", "WORLD_EDIT"),
                                        "STANDARD"),
                                ConfigOption.integer("regeneration.max-concurrent", "Max concurrent",
                                        "How many arenas may regenerate at the same time.",
                                        1, 16, 2),
                                ConfigOption.integer("regeneration.tick-budget", "Tick budget",
                                        "Max milliseconds of block placement per tick.",
                                        1, 100, 15),
                                ConfigOption.integer("regeneration.batch-size", "Batch size",
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
                                        "Restore WAVE mode from the far side instead.", false)
                        )),
                new Section("killtoken", "Kill Token",
                        "The PvP currency: drops, cooldowns and killstreaks.",
                        List.of(
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
                                        "Highest streak token multiplier.", 1, 5, 5)
                        )),
                new Section("afk", "AFK Zones",
                        "AFK Shards earned by idle players inside a zone.",
                        List.of(
                                ConfigOption.bool("afk.enabled", "AFK rewards",
                                        "Whether the AFK shard reward loop runs.", true),
                                ConfigOption.integer("afk.reward-interval-seconds", "Reward interval",
                                        "How often the reward loop runs, in seconds.", 5, 3600, 30),
                                ConfigOption.integer("afk.shards-per-interval", "Shards per reward",
                                        "Shards awarded per reward when the player qualifies.",
                                        1, 64, 1),
                                ConfigOption.integer("afk.min-idle-seconds", "Minimum idle time",
                                        "Seconds a player must be idle before earning shards.",
                                        0, 3600, 60),
                                ConfigOption.integer("afk.max-shards-per-hour", "Hourly cap",
                                        "Hard cap on shards per player per hour. -1 disables it.",
                                        -1, 10000, 100),
                                ConfigOption.bool("afk.notify-on-earn", "Notify on earn",
                                        "Tell the player when they earn shards.", true),
                                ConfigOption.string("afk.earn-message", "Earn message",
                                        "Message sent when the player earns shards.",
                                        "&b+1 &3AFK Shard &7(the deep rewards patience)")
                        )),
                new Section("customweapons", "Custom Weapons",
                        "Tune the Nichirin Blade and Kokoshibos Sword abilities.",
                        List.of(), List.of(nichirinSection(), kokushiboSection())),
                new Section("performance", "Storage & Performance",
                        "Snapshot compression, caching and async I/O.",
                        List.of(
                                ConfigOption.bool("performance.use-async-save", "Async snapshot saves",
                                        "Write block snapshots off the main thread.", true),
                                ConfigOption.bool("performance.use-async-load", "Async snapshot loads",
                                        "Read block snapshots off the main thread.", true),
                                ConfigOption.bool("performance.cache-snapshots", "Cache snapshots",
                                        "Keep recently used snapshots in memory.", true),
                                ConfigOption.integer("performance.max-cached-snapshots", "Cache size",
                                        "Maximum snapshot lists kept in memory before eviction.",
                                        1, 50, 10),
                                ConfigOption.bool("performance.compress-snapshots", "Compress snapshots",
                                        "GZIP-compress snapshots on disk.", true),
                                ConfigOption.integer("storage.save-interval-minutes", "Auto-save interval",
                                        "Minutes between automatic arena metadata saves.",
                                        1, 1440, 30)
                        ))
        );
    }

    private Section nichirinSection() {
        return new Section("customweapons-nichirin", "Nichirin Blade",
                "Flame Combo passive and the two flame abilities.",
                List.of(
                        ConfigOption.integer("nichirin.combo-hits", "Combo hits",
                                "Hits landed without taking damage to trigger Flame Combo.",
                                1, 10, 4).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.combo-strength-duration-seconds",
                                "Strength duration",
                                "Seconds of Strength II granted when the combo completes.",
                                1, 60, 6).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.clear-blue-sky.cooldown-seconds",
                                "Clear Blue Sky cooldown",
                                "Cooldown in seconds for the full-circle slash.",
                                1, 600, 50).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.clear-blue-sky.damage-hearts",
                                "Clear Blue Sky damage",
                                "True damage in hearts (1 heart = 2 HP).",
                                1, 20, 2).withColor(NICHIRIN),
                        ConfigOption.decimal("nichirin.clear-blue-sky.radius",
                                "Clear Blue Sky radius",
                                "Full-circle reach in blocks.",
                                1f, 15f, 0.5f, 5.0).withColor(NICHIRIN),
                        ConfigOption.decimal("nichirin.clear-blue-sky.boost-power",
                                "Clear Blue Sky boost",
                                "Upward launch velocity on cast.",
                                0f, 4f, 0.1f, 1.4).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.clear-blue-sky.fire-seconds",
                                "Searing fire seconds",
                                "Seconds targets burn with Fire-Resistance-bypassing fire.",
                                1, 30, 3).withColor(NICHIRIN),
                        ConfigOption.decimal("nichirin.clear-blue-sky.sear-hearts",
                                "Searing damage",
                                "Hearts of searing damage per second while burning.",
                                0.5f, 10f, 0.5f, 1.0).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.clear-blue-sky.vfx-ticks",
                                "Clear Blue Sky animation",
                                "Ticks the ring animation plays (lower = snappier).",
                                5, 60, 12).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.enbu.cooldown-seconds",
                                "Dancing Flash cooldown",
                                "Cooldown in seconds for the dash.",
                                1, 600, 70).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.enbu.damage-hearts", "Dancing Flash damage",
                                "True damage in hearts.",
                                1, 20, 2).withColor(NICHIRIN),
                        ConfigOption.decimal("nichirin.enbu.radius", "Dancing Flash radius",
                                "Radius of the dash in blocks.",
                                1f, 10f, 0.1f, 3.0).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.enbu.absorption-lock-seconds",
                                "Absorption lock",
                                "Seconds targets cannot gain absorption.",
                                1, 60, 15).withColor(NICHIRIN),
                        ConfigOption.integer("nichirin.enbu.vfx-ticks",
                                "Dancing Flash animation",
                                "Ticks the dash animation plays.",
                                5, 60, 20).withColor(NICHIRIN)
                ), NICHIRIN);
    }

    private Section kokushiboSection() {
        return new Section("customweapons-kokushibo", "Kokoshibos Sword",
                "Upper Moon One passive and the moon abilities.",
                List.of(
                        ConfigOption.decimal("kokushibo.upper-moon-one.proc-chance",
                                "Proc chance",
                                "Chance a melee hit unleashes crescent blades.",
                                0.05f, 1f, 0.05f, 1.0).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.upper-moon-one.proc-cooldown-seconds",
                                "Proc cooldown",
                                "Minimum seconds between crescent volleys.",
                                0, 30, 2).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.upper-moon-one.crescent-count",
                                "Crescent count",
                                "Crescent blades fired per volley.",
                                1, 12, 3).withColor(KOKUSHIBO),
                        ConfigOption.decimal("kokushibo.upper-moon-one.damage-hearts",
                                "Crescent damage",
                                "True damage per crescent in hearts.",
                                0.5f, 20f, 0.5f, 1.0).withColor(KOKUSHIBO),
                        ConfigOption.decimal("kokushibo.upper-moon-one.crescent-speed",
                                "Crescent speed",
                                "Flight speed multiplier for passive crescents.",
                                0.5f, 3f, 0.1f, 1.0).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.catastrophe.cooldown-seconds",
                                "Catastrophe cooldown",
                                "Cooldown in seconds for the expanding vortex.",
                                1, 600, 70).withColor(KOKUSHIBO),
                        ConfigOption.decimal("kokushibo.catastrophe.damage-hearts",
                                "Catastrophe damage",
                                "True damage per target in hearts.",
                                1f, 20f, 0.5f, 3.0).withColor(KOKUSHIBO),
                        ConfigOption.decimal("kokushibo.catastrophe.max-radius",
                                "Vortex reach",
                                "Maximum radius the vortex expands to, in blocks.",
                                2f, 20f, 0.5f, 9.0).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.catastrophe.crescents",
                                "Crescents",
                                "Number of crescent blades in the vortex.",
                                4, 32, 12).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.catastrophe.vfx-ticks",
                                "Vortex duration",
                                "Ticks the vortex expands for (higher = slower).",
                                5, 60, 24).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.moonbow.cooldown-seconds",
                                "Moonbow cooldown",
                                "Cooldown in seconds for the six-crescent strike.",
                                1, 600, 80).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.moonbow.crescents", "Crescents",
                                "Number of crescents striking in a line.",
                                1, 12, 6).withColor(KOKUSHIBO),
                        ConfigOption.decimal("kokushibo.moonbow.spacing",
                                "Crescent spacing",
                                "Distance between each crescent in blocks.",
                                0.5f, 5f, 0.1f, 1.6).withColor(KOKUSHIBO),
                        ConfigOption.decimal("kokushibo.moonbow.strike-radius",
                                "Strike radius",
                                "Hit radius around each crescent in blocks.",
                                0.5f, 5f, 0.1f, 1.2).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.moonbow.damage-hearts",
                                "Moonbow damage",
                                "True damage per crescent in hearts.",
                                1, 20, 3).withColor(KOKUSHIBO),
                        ConfigOption.integer("kokushibo.moonbow.vfx-ticks",
                                "Moonbow animation",
                                "Ticks each strike crescent grows for.",
                                5, 40, 10).withColor(KOKUSHIBO)
                ), KOKUSHIBO);
    }

    private record Section(String id, String title, String description,
                           List<ConfigOption> options, List<Section> children,
                           TextColor color) {

        private Section(final String id, final String title, final String description,
                        final List<ConfigOption> options) {
            this(id, title, description, options, List.of(), null);
        }

        private Section(final String id, final String title, final String description,
                        final List<ConfigOption> options, final TextColor color) {
            this(id, title, description, options, List.of(), color);
        }

        private Section(final String id, final String title, final String description,
                        final List<ConfigOption> options, final List<Section> children) {
            this(id, title, description, options, children, null);
        }
    }

    // ------------------------------------------------------------------
    //  Small builders
    // ------------------------------------------------------------------

    private ActionButton button(final String text, final Component tooltip,
                                final DialogAction action) {
        return button(text, tooltip, action, null);
    }

    private ActionButton button(final String text, final Component tooltip,
                                final DialogAction action, final TextColor color) {
        return ActionButton.builder(Component.text(text,
                        color == null ? NamedTextColor.WHITE : color))
                .tooltip(tooltip)
                .width(BUTTON_WIDTH)
                .action(action)
                .build();
    }

    private Component tooltip(final String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private Component label(final ConfigOption option) {
        return Component.text(option.label(),
                option.color() == null ? NamedTextColor.WHITE : option.color());
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

    private ActionButton backButton() {
        return button("Back", tooltip("Return to the configuration menu."),
                click((view, audience) -> {
                    if (audience instanceof Player target) {
                        openMainMenu(target);
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
