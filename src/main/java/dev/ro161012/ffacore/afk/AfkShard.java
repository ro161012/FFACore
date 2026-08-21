package dev.ro161012.ffacore.afk;

import dev.ro161012.ffacore.util.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the AFK Shard currency item.
 *
 * <p>The shard is an {@link Material#ECHO_SHARD} carrying the
 * {@code ffacore:afk_shard} NBT tag and the {@code altarsmp:cutlass} tooltip
 * style (an animated ocean gradient background + frame from the resource
 * pack). It renders as the vanilla Echo Shard item. The item is built once
 * and cloned per award.
 */
public final class AfkShard {

    /** Display name of the shard, shown in the gradient tooltip. */
    public static final String DISPLAY_NAME = "AFK Shard";

    /** NBT key holding the shard's serial number (for future uniqueness). */
    private static final NamespacedKey SERIAL_KEY =
            new NamespacedKey("ffacore", "afk_shard_serial");

    private AfkShard() {
        // Utility class.
    }

    /**
     * Builds the AFK Shard item.
     *
     * @return a fresh shard item
     */
    public static ItemStack create() {
        final ItemStack stack = new ItemStack(Material.ECHO_SHARD);
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.displayName(ItemUtils.oceanTitle(DISPLAY_NAME));
        meta.lore(List.of(
                Component.text("Earned by resting in an AFK Zone.", NamedTextColor.AQUA),
                ItemUtils.oceanGradient("The ocean rewards those who wait.")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);

        ItemUtils.tag(stack, ItemUtils.AFK_SHARD_KEY);
        ItemUtils.applyTooltipStyle(stack, ItemUtils.OCEAN_TOOLTIP);

        final ItemMeta taggedMeta = stack.getItemMeta();
        if (taggedMeta != null) {
            taggedMeta.getPersistentDataContainer().set(
                    SERIAL_KEY, PersistentDataType.LONG, System.nanoTime());
            stack.setItemMeta(taggedMeta);
        }

        return stack;
    }

    /**
     * Returns a copy of the shard with the given stack size.
     *
     * @param amount stack size (clamped to at least 1)
     * @return the shard item
     */
    public static ItemStack create(final int amount) {
        final ItemStack stack = create();
        stack.setAmount(Math.max(1, amount));
        return stack;
    }
}
