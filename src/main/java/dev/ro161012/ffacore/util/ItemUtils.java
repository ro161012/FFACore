package dev.ro161012.ffacore.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Shared item-building helpers for the FFACore currency items.
 *
 * <p>The Kill Token and AFK Shard use a smooth per-character gradient for
 * their display name, a custom {@code minecraft:tooltip_style} that renders
 * the ocean/ember gradient background drawn by the companion resource pack,
 * and a {@code NamespacedKey} tag so the items can be identified reliably
 * without string matching.
 */
public final class ItemUtils {

    /** Custom model data used by the Kill Token texture override. */
    public static final int KILL_TOKEN_MODEL_DATA = 1001;

    /** Custom model data used by the AFK Shard texture override. */
    public static final int AFK_SHARD_MODEL_DATA = 1002;

    /** NBT tag identifying a Kill Token currency item. */
    public static final NamespacedKey KILL_TOKEN_KEY =
            new NamespacedKey("ffacore", "kill_token");

    /** NBT tag identifying an AFK Shard currency item. */
    public static final NamespacedKey AFK_SHARD_KEY =
            new NamespacedKey("ffacore", "afk_shard");

    /** Tooltip style (gradient background + frame) for the AFK Shard. */
    public static final Key OCEAN_TOOLTIP = Key.key("ffacore", "ocean");

    /** Tooltip style (gradient background + frame) for the Kill Token. */
    public static final Key EMBER_TOOLTIP = Key.key("ffacore", "ember");

    // Ocean palette (AFK Shard).
    private static final int OCEAN_FROM = 0x0A2A6B;
    private static final int OCEAN_TO = 0x58C7F3;

    // Ember palette (Kill Token).
    private static final int EMBER_FROM = 0x7A0B0B;
    private static final int EMBER_TO = 0xFF5C5C;

    private ItemUtils() {
        // Utility class.
    }

    /**
     * Returns a smooth per-character gradient component between two packed RGB
     * colours.
     *
     * @param text the text to gradient
     * @param from packed RGB start colour
     * @param to   packed RGB end colour
     * @return gradient component rendered in the default font
     */
    public static Component gradient(final String text, final int from, final int to) {
        final TextComponent.Builder builder = Component.text();
        final int length = text.length();
        for (int i = 0; i < length; i++) {
            final double t = length <= 1 ? 0.0 : (double) i / (length - 1);
            builder.append(Component.text(text.charAt(i)).color(TextColor.color(
                    lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t),
                    lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t),
                    lerp(from & 0xFF, to & 0xFF, t))));
        }
        return builder.build();
    }

    /**
     * Ocean-palette gradient text (deep blue to light cyan), bold.
     *
     * @param text the title
     * @return the gradient component
     */
    public static Component oceanTitle(final String text) {
        return gradient(text, OCEAN_FROM, OCEAN_TO).decorate(TextDecoration.BOLD);
    }

    /**
     * Ember-palette gradient text (dark red to bright red-orange), bold.
     *
     * @param text the title
     * @return the gradient component
     */
    public static Component emberTitle(final String text) {
        return gradient(text, EMBER_FROM, EMBER_TO).decorate(TextDecoration.BOLD);
    }

    /**
     * Ocean-palette gradient text without bold.
     *
     * @param text the text
     * @return gradient component
     */
    public static Component oceanGradient(final String text) {
        return gradient(text, OCEAN_FROM, OCEAN_TO);
    }

    /**
     * Applies a {@code minecraft:tooltip_style} to the item so the client
     * renders the matching gradient background and frame from the resource
     * pack.
     *
     * @param stack the item
     * @param style tooltip style key, e.g. {@code ffacore:ocean}
     */
    public static void applyTooltipStyle(final ItemStack stack, final Key style) {
        try {
            stack.setData(DataComponentTypes.TOOLTIP_STYLE, style);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            // Tooltip styles are cosmetic; ignore when the runtime does not
            // expose the minecraft:tooltip_style data component.
        }
    }

    /**
     * Returns whether the item is tagged as a Kill Token.
     *
     * @param stack the item to check
     * @return true when tagged
     */
    public static boolean isKillToken(final ItemStack stack) {
        return hasTag(stack, KILL_TOKEN_KEY);
    }

    /**
     * Returns whether the item is tagged as an AFK Shard.
     *
     * @param stack the item to check
     * @return true when tagged
     */
    public static boolean isAfkShard(final ItemStack stack) {
        return hasTag(stack, AFK_SHARD_KEY);
    }

    /**
     * Tags an item with a byte marker under the given key.
     *
     * @param stack the item to tag
     * @param key   the tag key
     */
    public static void tag(final ItemStack stack, final NamespacedKey key) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
    }

    private static boolean hasTag(final ItemStack stack, final NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static int lerp(final int a, final int b, final double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
