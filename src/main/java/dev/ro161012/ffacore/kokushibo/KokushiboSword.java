package dev.ro161012.ffacore.kokushibo;

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
 * Builds the Kokoshibos Sword, the Upper Moon One FFA weapon.
 *
 * <p>The sword is a {@link Material#NETHERITE_SWORD} carrying custom model
 * data {@code 1605} (the kokushibo model), the {@code altarsmp:kokoshibo_sword}
 * tooltip style (an animated purple gradient border), and the
 * {@code ffacore:kokushibo_sword} tag so the ability listener recognises it.
 */
public final class KokushiboSword {

    /** Display name of the sword. */
    public static final String DISPLAY_NAME = "Kokoshibos Sword";

    /** Custom model data selecting the kokushibo model. */
    public static final int MODEL_DATA = 1605;

    /** Custom model data of the moon crescent projectile item. */
    public static final int CRESCENT_MODEL_DATA = 2;

    /** NBT tag identifying a Kokoshibos Sword. */
    public static final NamespacedKey KEY = new NamespacedKey("ffacore", "kokushibo_sword");

    /** Tooltip style: the animated purple gradient background and frame. */
    public static final Key TOOLTIP_STYLE = Key.key("altarsmp", "kokoshibo_sword");

    // Moon palette (soft muted violet, matching the recoloured tooltip border).
    private static final int MOON_FROM = 0x3A2A5C;
    private static final int MOON_TO = 0xA98BC8;
    private static final TextColor HEADER = TextColor.color(0xA78BC8);
    private static final TextColor TAG = TextColor.color(0x9E9E9E);
    private static final TextColor BODY = TextColor.color(0xD6D6D6);

    private KokushiboSword() {
        // Utility class.
    }

    /**
     * Creates a fresh Kokoshibos Sword with the exact tooltip layout used by
     * the reference plugin: enchants, flavour lore, the passive and the two
     * active abilities.
     *
     * @return the sword item
     */
    public static ItemStack create() {
        final ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.displayName(ItemUtils.gradient(DISPLAY_NAME, MOON_FROM, MOON_TO)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.LOOTING, 3, true);
        meta.setCustomModelData(MODEL_DATA);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(List.of(
                body("A living blade perfected by Upper Moon One."),
                Component.empty(),
                header("PASSIVE | UPPER MOON ONE"),
                Component.empty(),
                body("10s cooldown."),
                Component.empty(),
                header("CRESCENT THROW"),
                tag("[Offhand]"),
                Component.empty(),
                body("Open a 10s Lunar Eclipse window."),
                body("Each left-click releases the original slowing star."),
                body("One projectile may be fired each second."),
                body("70s cooldown."),
                Component.empty(),
                header("SIXTEENTH FORM | MOONBOW, HALF MOON"),
                tag("[Offhand + Crouch]"),
                Component.empty(),
                body("Face a direction, then call down six moon crescents."),
                body("They strike in a straight line from near to far."),
                body("Each target takes up to 3 hearts true damage."),
                body("80s cooldown.")));
        stack.setItemMeta(meta);

        tagSword(stack);
        ItemUtils.applyTooltipStyle(stack, TOOLTIP_STYLE);
        return stack;
    }

    /**
     * Creates a sword with the given stack size.
     *
     * @param amount stack size (clamped to at least 1)
     * @return the sword item
     */
    public static ItemStack create(final int amount) {
        final ItemStack stack = create();
        stack.setAmount(Math.max(1, amount));
        return stack;
    }

    /**
     * Builds the moon crescent item used as the projectile and strike visual:
     * a {@link Material#NETHER_STAR} with custom model data {@code 2}.
     *
     * @return the crescent item
     */
    public static ItemStack crescentItem() {
        final ItemStack stack = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(CRESCENT_MODEL_DATA);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Tags an item as a Kokoshibos Sword so the ability listener recognises it.
     *
     * @param stack the item to tag
     */
    public static void tagSword(final ItemStack stack) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
    }

    /**
     * Returns whether the item is tagged as a Kokoshibos Sword.
     *
     * @param stack the item to check, may be null
     * @return true when tagged
     */
    public static boolean isKokushiboSword(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .has(KEY, PersistentDataType.BYTE);
    }

    /** A bold, moon-coloured ability header line. */
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
