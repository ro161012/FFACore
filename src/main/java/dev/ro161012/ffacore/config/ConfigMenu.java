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
    private static final List<String> CUSTOM_ITEM_THEME_CHOICES = List.of(
            "GLOBAL", "UNIFIED_PURPLE", "ITEM", "EMBER", "FROST", "EARTH", "GOLD", "VOID");

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
        final boolean submenu = !section.children().isEmpty();
        try {
            if (submenu) {
                openSubmenu(player, section);
            } else {
                openSection(player, section);
            }
        } catch (final RuntimeException ex) {
            final String kind = submenu ? "submenu" : "section";
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to open config " + kind + " " + section.id(), ex);
            Messages.raw(player, "&cFailed to open the " + kind + ": &f" + ex.getMessage());
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
                            onMainThread(() -> openSectionOrSubmenu(target, child));
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
        if (!Float.isFinite(value)) {
            return min;
        }
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
                yield value == null ? null : Math.round(clamp(value, option.min(), option.max()));
            }
            case DECIMAL -> {
                final Float value = view.getFloat(option.key());
                if (value == null) {
                    yield null;
                }
                final float clamped = clamp(value, option.min(), option.max());
                final float step = option.step();
                final double snapped = step <= 0f
                        ? clamped : Math.round(clamped / step) * (double) step;
                yield Math.max(option.min(), Math.min(option.max(), snapped));
            }
            case STRING -> {
                final String value = view.getText(option.key());
                yield value == null || value.length() <= MAX_STRING_LENGTH
                        ? value : value.substring(0, MAX_STRING_LENGTH);
            }
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

    private Section customItemsSection() {
        return new Section("custom-items", "Custom Items",
                "Configure every ability, bind, cooldown, balance value and tooltip theme.",
                List.of(), List.of(
                        new Section("custom-items-global", "Global",
                                "Shared switches and the unified resource-pack tooltip theme. Active abilities use the swap-hands key; move weapons manually in the inventory when you want them offhand.",
                                List.of(
                                        ConfigOption.bool("custom-items.enabled", "Enable custom items",
                                                "Enable custom item abilities and passive effects.", true),
                                        ConfigOption.enumOption("custom-items.tooltip-theme", "Tooltip theme",
                                                "Gradient background and title palette used by every custom item.",
                                                List.of("UNIFIED_PURPLE", "ITEM", "EMBER", "FROST",
                                                        "EARTH", "GOLD", "VOID"), "UNIFIED_PURPLE")
                                ), TextColor.color(0xC56BFF)),
                        customItemsGroup("custom-items.swords", "Swords",
                                "Swords and their passive or active powers.", List.of(
                                        customItem("custom-items.dash-sword", "Dash Sword",
                                                List.of(
                                                        ConfigOption.integer("custom-items.dash-sword.cooldown-seconds", "Cooldown", "Dash cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.decimal("custom-items.dash-sword.distance", "Distance", "Blocks travelled by the dash.", 0.5f, 30f, 0.5f, 8.0),
                                                        ConfigOption.integer("custom-items.dash-sword.duration-ticks", "Duration", "Dash duration in ticks.", 1, 40, 6)
                                                ), 0xA78BFA),
                                        customItem("custom-items.frost-sword", "Frost Sword",
                                                List.of(
                                                        ConfigOption.decimal("custom-items.frost-sword.proc-chance", "Freeze chance", "Chance to freeze a hit target.", 0f, 1f, 0.01f, 0.20),
                                                        ConfigOption.decimal("custom-items.frost-sword.freeze-seconds", "Freeze seconds", "Freeze duration in seconds.", 0.1f, 30f, 0.1f, 3.0)
                                                ), 0x7DD3FC),
                                        customItem("custom-items.strike-sword", "Strike Sword",
                                                List.of(ConfigOption.decimal("custom-items.strike-sword.proc-chance", "Lightning chance", "Chance to call damaging lightning on a hit.", 0f, 1f, 0.01f, 0.15)), 0xF4D34A),
                                        customItem("custom-items.lifestealer-sword", "Lifestealer Sword",
                                                List.of(
                                                        ConfigOption.decimal("custom-items.lifestealer-sword.proc-chance", "Lifesteal chance", "Chance to heal on a hit.", 0f, 1f, 0.01f, 0.20),
                                                        ConfigOption.decimal("custom-items.lifestealer-sword.heal-amount", "Heal amount", "Health restored on a proc.", 0.5f, 20f, 0.5f, 4.0)
                                                ), 0xFF6B78),
                                        customItem("custom-items.adrenaline-blade", "Adrenaline Blade",
                                                List.of(
                                                        ConfigOption.integer("custom-items.adrenaline-blade.cooldown-seconds", "Cooldown", "Second-chance cooldown in seconds.", 0, 600, 70),
                                                        ConfigOption.decimal("custom-items.adrenaline-blade.second-chance-seconds", "Second-chance seconds", "Duration of the second chance.", 0.1f, 30f, 0.1f, 4.0),
                                                        ConfigOption.integer("custom-items.adrenaline-blade.absorption-hearts", "Absorption hearts", "Absorption hearts granted after the lethal hit.", 1, 40, 14)
                                                ), 0x48D7FF),
                                        customItem("custom-items.flux-sword", "Flux Sword",
                                                List.of(
                                                        ConfigOption.integer("custom-items.flux-sword.cooldown-seconds", "Cooldown", "Beam cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.decimal("custom-items.flux-sword.damage", "Damage", "Damage dealt once per target.", 0f, 100f, 0.5f, 24.0),
                                                        ConfigOption.decimal("custom-items.flux-sword.range", "Range", "Beam range in blocks.", 1f, 64f, 0.5f, 24.0),
                                                        ConfigOption.decimal("custom-items.flux-sword.radius", "Hit radius", "Beam hit radius.", 0.25f, 5f, 0.05f, 1.35)
                                                ), 0x55B7FF),
                                        customItem("custom-items.pigxaliur", "Pigxaliur",
                                                List.of(
                                                        ConfigOption.decimal("custom-items.pigxaliur.proc-chance", "Hoglin chance", "Chance to summon hoglins on hit.", 0f, 1f, 0.01f, 0.20),
                                                        ConfigOption.integer("custom-items.pigxaliur.cooldown-seconds", "Cooldown", "Hoglin proc cooldown in seconds.", 0, 300, 30),
                                                        ConfigOption.integer("custom-items.pigxaliur.hoglin-count", "Hoglin count", "Hoglins summoned per proc.", 1, 10, 3),
                                                        ConfigOption.decimal("custom-items.pigxaliur.hoglin-lifetime-seconds", "Hoglin lifetime", "How long summoned hoglins survive.", 1f, 120f, 1f, 20.0)
                                                ), 0x58E36B)
                                ), 0xA78BFA),
                        customItemsGroup("custom-items.spears", "Spears",
                                "Rocket, venom, dash and vault spear tuning.", List.of(
                                        customItem("custom-items.rocket-spear", "Rocket Spear",
                                                List.of(
                                                        ConfigOption.integer("custom-items.rocket-spear.cooldown-seconds", "Cooldown", "Rocket cooldown in seconds.", 0, 300, 30),
                                                        ConfigOption.decimal("custom-items.rocket-spear.launch-seconds", "Launch seconds", "Time spent rising.", 0.1f, 5f, 0.05f, 1.0),
                                                        ConfigOption.decimal("custom-items.rocket-spear.maximum-air-seconds", "Maximum air seconds", "Safety timeout before the slam.", 1f, 20f, 0.5f, 4.0),
                                                        ConfigOption.decimal("custom-items.rocket-spear.launch-velocity", "Launch velocity", "Initial upward velocity.", 0.1f, 4f, 0.05f, 1.45),
                                                        ConfigOption.decimal("custom-items.rocket-spear.slam-velocity", "Slam velocity", "Downward slam velocity.", 0.1f, 6f, 0.05f, 2.2),
                                                        ConfigOption.decimal("custom-items.rocket-spear.impact-radius", "Impact radius", "Radius of the landing hit.", 1f, 20f, 0.5f, 5.0),
                                                        ConfigOption.decimal("custom-items.rocket-spear.impact-damage", "Impact damage", "Damage dealt on landing.", 0f, 100f, 0.5f, 12.0)
                                                ), 0xFF805A),
                                        customItem("custom-items.venom-spear", "Venom Spear",
                                                List.of(
                                                        ConfigOption.integer("custom-items.venom-spear.cooldown-seconds", "Cooldown", "Dash cooldown in seconds.", 0, 300, 12),
                                                        ConfigOption.decimal("custom-items.venom-spear.distance", "Distance", "Blocks travelled by the dash.", 0.5f, 30f, 0.5f, 8.0),
                                                        ConfigOption.integer("custom-items.venom-spear.duration-ticks", "Duration", "Dash duration in ticks.", 1, 40, 6),
                                                        ConfigOption.decimal("custom-items.venom-spear.proc-chance", "Venom chance", "Chance to poison and blind on dash.", 0f, 1f, 0.01f, 0.20),
                                                        ConfigOption.decimal("custom-items.venom-spear.effect-radius", "Effect radius", "Radius of the venom effect.", 1f, 50f, 0.5f, 20.0),
                                                        ConfigOption.decimal("custom-items.venom-spear.poison-seconds", "Poison seconds", "Poison duration.", 0.1f, 30f, 0.1f, 5.0),
                                                        ConfigOption.decimal("custom-items.venom-spear.blind-seconds", "Blind seconds", "Blindness duration.", 0.1f, 30f, 0.1f, 3.0)
                                                ), 0x70E85A),
                                        customItem("custom-items.dash-spear", "Dash Spear",
                                                List.of(
                                                        ConfigOption.integer("custom-items.dash-spear.cooldown-seconds", "Cooldown", "Dash cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.decimal("custom-items.dash-spear.distance", "Distance", "Blocks travelled by the dash.", 0.5f, 30f, 0.5f, 9.0),
                                                        ConfigOption.integer("custom-items.dash-spear.duration-ticks", "Duration", "Dash duration in ticks.", 1, 40, 6)
                                                ), 0xFF78C8),
                                        customItem("custom-items.vault-spear", "Vault Spear",
                                                List.of(
                                                        ConfigOption.integer("custom-items.vault-spear.cooldown-seconds", "Cooldown", "Vault cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.decimal("custom-items.vault-spear.forward-velocity", "Forward velocity", "Forward vault velocity.", 0f, 3f, 0.05f, 0.55),
                                                        ConfigOption.decimal("custom-items.vault-spear.vertical-velocity", "Vertical velocity", "Upward vault velocity.", 0f, 3f, 0.05f, 1.35)
                                                ), 0x81C8FF)
                                ), 0xFF78C8),
                        customItemsGroup("custom-items.axes", "Axes",
                                "Earthquakes, magma, cobweb and whirlwind axe powers.", List.of(
                                        customItem("custom-items.paxe", "Paxe",
                                                List.of(
                                                        ConfigOption.integer("custom-items.paxe.cooldown-seconds", "Cooldown", "Glass prison cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.decimal("custom-items.paxe.duration-seconds", "Duration", "Glass prison duration.", 1f, 120f, 1f, 20.0)
                                                ), 0xFFB347),
                                        customItem("custom-items.seismic-axe", "Seismic Axe",
                                                List.of(
                                                        ConfigOption.integer("custom-items.seismic-axe.cooldown-seconds", "Cooldown", "Earthquake cooldown in seconds.", 0, 300, 25),
                                                        ConfigOption.decimal("custom-items.seismic-axe.launch-seconds", "Launch seconds", "Time spent rising.", 0.1f, 5f, 0.05f, 0.75),
                                                        ConfigOption.decimal("custom-items.seismic-axe.maximum-air-seconds", "Maximum air seconds", "Safety timeout before impact.", 1f, 20f, 0.5f, 3.0),
                                                        ConfigOption.decimal("custom-items.seismic-axe.launch-velocity", "Launch velocity", "Initial upward velocity.", 0.1f, 4f, 0.05f, 1.15),
                                                        ConfigOption.decimal("custom-items.seismic-axe.slam-velocity", "Slam velocity", "Downward slam velocity.", 0.1f, 6f, 0.05f, 1.8),
                                                        ConfigOption.decimal("custom-items.seismic-axe.impact-radius", "Impact radius", "Radius of the earthquake.", 1f, 24f, 0.5f, 8.0),
                                                        ConfigOption.decimal("custom-items.seismic-axe.impact-damage", "Impact damage", "Damage dealt by the earthquake.", 0f, 100f, 0.5f, 24.0),
                                                        ConfigOption.decimal("custom-items.seismic-axe.knockback", "Knockback", "Horizontal launch strength.", 0f, 5f, 0.05f, 1.35),
                                                        ConfigOption.decimal("custom-items.seismic-axe.vertical-knockback", "Vertical knockback", "Vertical launch strength.", 0f, 3f, 0.05f, 0.85)
                                                ), 0xD89224),
                                        customItem("custom-items.cob-axe", "Cob Axe",
                                                List.of(
                                                        ConfigOption.integer("custom-items.cob-axe.cooldown-seconds", "Cooldown", "Cobweb shred cooldown in seconds.", 0, 300, 30),
                                                        ConfigOption.integer("custom-items.cob-axe.radius", "Radius", "Cobweb removal radius.", 1, 20, 7)
                                                ), 0xE6E6E6),
                                        customItem("custom-items.magma-axe", "Magma Axe",
                                                List.of(
                                                        ConfigOption.integer("custom-items.magma-axe.cooldown-seconds", "Cooldown", "Magma ring cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.integer("custom-items.magma-axe.radius", "Radius", "Magma ring and damage radius.", 1, 20, 6),
                                                        ConfigOption.decimal("custom-items.magma-axe.damage", "Damage", "Damage dealt by the magma ring.", 0f, 100f, 0.5f, 14.0),
                                                        ConfigOption.decimal("custom-items.magma-axe.fire-seconds", "Fire seconds", "Ignition duration.", 0.1f, 30f, 0.1f, 4.0),
                                                        ConfigOption.decimal("custom-items.magma-axe.visual-seconds", "Visual seconds", "How long magma displays remain.", 0.1f, 10f, 0.1f, 2.0)
                                                ), 0xFF8A3D),
                                        customItem("custom-items.whirl-axe", "Whirl Axe",
                                                List.of(
                                                        ConfigOption.integer("custom-items.whirl-axe.cooldown-seconds", "Cooldown", "Whirlwind cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.integer("custom-items.whirl-axe.radius", "Radius", "Whirlwind radius.", 1, 20, 6),
                                                        ConfigOption.decimal("custom-items.whirl-axe.damage", "Damage", "Damage dealt by the whirlwind.", 0f, 100f, 0.5f, 8.0),
                                                        ConfigOption.decimal("custom-items.whirl-axe.knockback", "Knockback", "Horizontal launch strength.", 0f, 5f, 0.05f, 1.4),
                                                        ConfigOption.decimal("custom-items.whirl-axe.vertical-knockback", "Vertical knockback", "Vertical launch strength.", 0f, 3f, 0.05f, 0.65)
                                                ), 0xBFF7EE)
                                ), 0xFFB347),
                        customItemsGroup("custom-items.bows", "Bows",
                                "Grapple movement and volley arrow tuning.", List.of(
                                        customItem("custom-items.grapple-bow", "Grapple Bow",
                                                List.of(ConfigOption.decimal("custom-items.grapple-bow.dash-velocity", "Dash velocity", "Velocity applied in the shot direction.", 0.1f, 4f, 0.05f, 1.35)), 0xB886FF),
                                        customItem("custom-items.volley-bow", "Volley Bow",
                                                List.of(
                                                        ConfigOption.integer("custom-items.volley-bow.cooldown-seconds", "Cooldown", "Extra-arrow cooldown in seconds.", 0, 300, 8),
                                                        ConfigOption.decimal("custom-items.volley-bow.arrow-speed", "Arrow speed", "Speed of the two extra arrows.", 0.5f, 6f, 0.05f, 3.0),
                                                        ConfigOption.decimal("custom-items.volley-bow.spread-degrees", "Spread degrees", "Angle of the two extra arrows.", 0f, 45f, 0.5f, 7.0)
                                                ), 0xFFE070)
                                ), 0xFFE070),
                        customItemsGroup("custom-items.maces", "Maces",
                                "Impact, cobweb, wither and dash mace powers.", List.of(
                                        customItem("custom-items.earthquake-mace", "Earthquake",
                                                List.of(
                                                        ConfigOption.integer("custom-items.earthquake-mace.cooldown-seconds", "Cooldown", "Earthquake cooldown in seconds.", 0, 300, 30),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.launch-seconds", "Launch seconds", "Time spent rising.", 0.1f, 5f, 0.05f, 0.75),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.maximum-air-seconds", "Maximum air seconds", "Safety timeout before impact.", 1f, 20f, 0.5f, 3.0),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.launch-velocity", "Launch velocity", "Initial upward velocity.", 0.1f, 4f, 0.05f, 1.15),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.slam-velocity", "Slam velocity", "Downward slam velocity.", 0.1f, 6f, 0.05f, 1.8),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.impact-radius", "Impact radius", "Radius of the earthquake.", 1f, 24f, 0.5f, 8.0),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.impact-damage", "Impact damage", "Damage dealt by the earthquake.", 0f, 100f, 0.5f, 12.0),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.knockback", "Knockback", "Horizontal launch strength.", 0f, 5f, 0.05f, 1.35),
                                                        ConfigOption.decimal("custom-items.earthquake-mace.vertical-knockback", "Vertical knockback", "Vertical launch strength.", 0f, 3f, 0.05f, 0.85)
                                                ), 0xE0A51A),
                                        customItem("custom-items.cob-mace", "Cob Mace",
                                                List.of(
                                                        ConfigOption.integer("custom-items.cob-mace.cooldown-seconds", "Cooldown", "Cobweb shred cooldown in seconds.", 0, 300, 30),
                                                        ConfigOption.integer("custom-items.cob-mace.radius", "Radius", "Cobweb removal radius.", 1, 20, 7)
                                                ), 0xE9E9E9),
                                        customItem("custom-items.wither-mace", "Wither Mace",
                                                List.of(
                                                        ConfigOption.decimal("custom-items.wither-mace.proc-chance", "Wither chance", "Chance to inflict Wither II.", 0f, 1f, 0.01f, 0.20),
                                                        ConfigOption.integer("custom-items.wither-mace.cooldown-seconds", "Cooldown", "Wither proc cooldown in seconds.", 0, 300, 30),
                                                        ConfigOption.decimal("custom-items.wither-mace.wither-seconds", "Wither seconds", "Wither II duration.", 0.1f, 30f, 0.1f, 10.0)
                                                ), 0xA68B99),
                                        customItem("custom-items.dash-mace", "Dash Mace",
                                                List.of(
                                                        ConfigOption.integer("custom-items.dash-mace.cooldown-seconds", "Cooldown", "Dash cooldown in seconds.", 0, 300, 20),
                                                        ConfigOption.decimal("custom-items.dash-mace.dash-velocity", "Dash velocity", "Velocity in the look direction.", 0.1f, 4f, 0.05f, 1.35)
                                                ), 0x69E86A)
                                ), 0x9B8CFF)
                ));
    }

    private Section customItemsGroup(final String id, final String title,
                                     final String description, final List<Section> children,
                                     final int color) {
        return new Section(id, title, description, List.of(), children,
                TextColor.color(color));
    }

    private Section customItem(final String id, final String title,
                               final List<ConfigOption> options, final int color) {
        final TextColor tint = TextColor.color(color);
        final List<ConfigOption> themedOptions = new ArrayList<>();
        for (final ConfigOption option : options) {
            themedOptions.add(option.withColor(tint));
        }
        themedOptions.add(ConfigOption.enumOption(id + ".tooltip-theme", "Tooltip theme",
                "Background and title gradient for this item; GLOBAL follows the shared theme.",
                CUSTOM_ITEM_THEME_CHOICES, "GLOBAL").withColor(tint));
        return new Section(id, title, "Tune this item's ability, balance values and theme.",
                themedOptions, tint);
    }

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
                customItemsSection(),
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
