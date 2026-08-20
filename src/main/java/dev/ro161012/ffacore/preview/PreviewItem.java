package dev.ro161012.ffacore.preview;

/**
 * One entry in the {@code preview-items.json} catalog: everything needed to
 * hand out a single custom item from the merged resource pack.
 *
 * <p>Two rendering mechanisms are supported:
 * <ul>
 *   <li>{@link #cmd()} &mdash; a {@code custom_model_data} value on the
 *       {@link #material()} base item (the dispatch path in the pack's
 *       {@code assets/minecraft/items/*.json}).</li>
 *   <li>{@link #itemModel()} &mdash; a {@code minecraft:item_model} component
 *       id resolved through the pack's item model definitions.</li>
 * </ul>
 * Exactly one of the two is set for any entry.
 *
 * @param name     display name shown in the preview GUI
 * @param material the base Bukkit material name
 * @param cmd      custom model data value, or {@code null}
 * @param itemModel item model component id, or {@code null}
 * @param model    the pack model path (informational)
 * @param category user-facing category name
 */
public record PreviewItem(String name, String material, Integer cmd,
                          String itemModel, String model, String category) {

    /** Convenience handle to the Bukkit material. */
    public org.bukkit.Material materialOrFallback() {
        try {
            return org.bukkit.Material.valueOf(material);
        } catch (IllegalArgumentException ex) {
            return org.bukkit.Material.NETHERITE_SWORD;
        }
    }
}