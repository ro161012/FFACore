package dev.ro161012.ffacore.nichirin;

import dev.ro161012.ffacore.util.ItemUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the Nichirin Blade, a Demon Slayer themed FFA weapon.
 *
 * <p>The blade is a {@link Material#NETHERITE_SWORD} carrying custom model
 * data {@code 1603} (the animated nichirin blade model from the resource
 * pack), the {@code altarsmp:nichirin_sword} tooltip style (a fiery
 * orange/red gradient border), and the {@code ffacore:nichirin_blade} tag so
 * the ability listener can recognise it without string matching.
 */
public final class NichirinBlade {

    /** Display name of the blade. */
    public static final String DISPLAY_NAME = "Nichirin Blade";

    /** Custom model data selecting the nichirin blade model. */
    public static final int MODEL_DATA = 1603;

    /** NBT tag identifying a Nichirin Blade. */
    public static final NamespacedKey KEY = new NamespacedKey("ffacore", "nichirin_blade");

    /** Tooltip style: the fiery orange/red gradient background and frame. */
    public static final Key TOOLTIP_STYLE = Key.key("altarsmp", "nichirin_sword");

    // Fire palette (matches the tooltip border, deep ember -> bright flame).
    private static final int FIRE_FROM = 0xCC3300;
    private static final int FIRE_TO = 0xFFA64D;
    private static final TextColor HEADER = TextColor.color(0xFF6B35);
    private static final TextColor TAG = TextColor.color(0x9E9E9E);
    private static final TextColor BODY = TextColor.color(0xD6D6D6);

    private NichirinBlade() {
        // Utility class.
    }

    /**
     * Creates a fresh Nichirin Blade item with the exact tooltip layout used
     * by the reference plugin: enchants, flavour lore, then the passive and
     * two active abilities with their trigger tags and cooldowns.
     *
     * @return the blade item
     */
    public static ItemStack create() {
        final ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.displayName(ItemUtils.gradient(DISPLAY_NAME, FIRE_FROM, FIRE_TO)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.setCustomModelData(MODEL_DATA);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(List.of(
                body("Forged from sunlight. The demons fear it for a reason."),
                Component.empty(),
                header("PASSIVE I FLAME COMBO"),
                tag("[Passive]"),
                Component.empty(),
                body("Land 4 hits without taking damage to gain Strength II."),
                body("Taking damage resets the combo immediately."),
                Component.empty(),
                header("HINOKAHI KAGURA: CLEAR BLUE SKY"),
                tag("[Offhand]"),
                Component.empty(),
                body("A wide horizontal fan slash dealing 2\u2764 true damage"),
                body("to all entities caught in the arc."),
                body("50s cooldown."),
                Component.empty(),
                header("HINOKAHI KAGURA ENBU"),
                tag("[Offhand + Crouch]"),
                Component.empty(),
                body("A spinning dance of flame dealing 2\u2764 true damage."),
                body("Locks targets from gaining absorption for 15s."),
                body("70s cooldown.")));
        stack.setItemMeta(meta);

        tagBlade(stack);
        ItemUtils.applyTooltipStyle(stack, TOOLTIP_STYLE);
        ItemUtils.hideEnchantmentGlint(stack);
        return stack;
    }

    /**
     * Creates a blade with the given stack size.
     *
     * @param amount stack size (clamped to at least 1)
     * @return the blade item
     */
    public static ItemStack create(final int amount) {
        final ItemStack stack = create();
        stack.setAmount(Math.max(1, amount));
        return stack;
    }

    /**
     * Tags an item as a Nichirin Blade so the ability listener recognises it.
     *
     * @param stack the item to tag
     */
    public static void tagBlade(final ItemStack stack) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
    }

    /**
     * Returns whether the item is tagged as a Nichirin Blade.
     *
     * @param stack the item to check, may be null
     * @return true when tagged
     */
    public static boolean isNichirinBlade(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .has(KEY, PersistentDataType.BYTE)
                || ItemUtils.hasCustomModelData(stack, MODEL_DATA);
    }

    /** A bold, fire-coloured ability header line. */
    private static Component header(final String text) {
        return Component.text(text, HEADER)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** A dim trigger tag line, e.g. {@code [Offhand]}. */
    private static Component tag(final String text) {
        return Component.text(text, TAG).decoration(TextDecoration.ITALIC, false);
    }

    /** A plain body line. */
    private static Component body(final String text) {
        return Component.text(text, BODY).decoration(TextDecoration.ITALIC, false);
    }
}
