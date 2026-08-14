package dev.ro161012.ffacore.killtoken;

import java.util.List;
import java.util.Locale;

import dev.ro161012.ffacore.util.ItemUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Storage block for the Kill Token currency.
 *
 * <p>A single Compressed Kill Token Block holds {@value #COMPRESS_RATIO}
 * Kill Tokens. It uses a quartz-style material so it reads as
 * "token-like" at a glance, and its lore states the stored value.
 *
 * <p>No crafting recipes are provided: servers wire the block into their
 * own trading systems (e.g. custom villager trades).
 *
 * <p>The item template is built once and cloned per use, so command paths
 * and listeners never rebuild item metadata.
 */
public final class CompressedBlockManager {

    /** Tokens stored in one Compressed Kill Token Block. */
    public static final int COMPRESS_RATIO = 64;

    /** Plain display name of the compressed block (no color codes). */
    public static final String DISPLAY_NAME = "Compressed Kill Token Block";

    /** Configuration section for the compressed block feature. */
    public static final String CONFIG_SECTION = "compressed-blocks";

    private static final Material DEFAULT_MATERIAL = Material.QUARTZ_BLOCK;

    private final KillTokenManager plugin;

    private Material material = DEFAULT_MATERIAL;
    private ItemStack template;

    /**
     * Creates the manager for the owning plugin.
     *
     * @param plugin owning plugin instance
     */
    public CompressedBlockManager(final KillTokenManager plugin) {
        this.plugin = plugin;
    }

    /**
     * Re-reads the configured material and rebuilds the item template.
     * Called on enable and on every config reload.
     */
    public void refresh() {
        this.material = resolveMaterial("compressed-block-material", DEFAULT_MATERIAL);
        this.template = build(this.material);
    }

    /**
     * Creates a Compressed Kill Token Block holding
     * {@value #COMPRESS_RATIO} tokens.
     *
     * @return the compressed block item
     */
    public ItemStack createCompressedBlock() {
        if (template == null) {
            refresh();
        }
        return template.clone();
    }

    /**
     * Builds the item template: a quartz-style block with the enchanted
     * glint and a lore stating the stored value.
     *
     * @param material the configured block material
     * @return the finished item
     */
    private ItemStack build(final Material material) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(KillTokenManager.color("&c" + DISPLAY_NAME));
            meta.setLore(List.of(
                    KillTokenManager.color("&7A compact block of Kill Tokens."),
                    KillTokenManager.color("&8Value: &c" + COMPRESS_RATIO + " &fKill Tokens")));
            // Hidden enchantment for the enchanted glint, without showing
            // an enchantment line on the tooltip.
            meta.addEnchant(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking")), 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        // Same ember gradient background as the Kill Token.
        ItemUtils.applyTooltipStyle(stack, ItemUtils.EMBER_TOOLTIP);
        return stack;
    }

    /**
     * Resolves a configurable material name, falling back to the given
     * default and warning on invalid values.
     *
     * @param path configuration path below {@value #CONFIG_SECTION}
     * @param fallback material used when the value is missing or unknown
     * @return the configured material
     */
    private Material resolveMaterial(final String path, final Material fallback) {
        final String name = plugin.getConfig()
                .getString(CONFIG_SECTION + "." + path, fallback.name());
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material '" + name
                    + "' for " + CONFIG_SECTION + "." + path + ", using "
                    + fallback.name() + ".");
            return fallback;
        }
    }
}
