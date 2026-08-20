package dev.ro161012.ffacore.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the {@code preview-items.json} catalog that ships inside the plugin
 * jar. The catalog is generated from the merged resource pack by
 * {@code tools/gen_preview_registry.js} and lists every custom item the pack
 * can render, with the exact material + model data needed to spawn it.
 */
public final class PreviewRegistry {

    private static final String RESOURCE = "preview-items.json";

    private final List<PreviewItem> items;
    private final List<String> categories;
    private final Gson gson;

    /**
     * Creates the registry, parsing the bundled catalog.
     *
     * @param plugin the owning plugin (for resource access)
     */
    public PreviewRegistry(final JavaPlugin plugin) {
        this.gson = new GsonBuilder().create();
        this.items = load(plugin);
        final Set<String> cats = new LinkedHashSet<>();
        for (final PreviewItem item : items) {
            cats.add(item.category());
        }
        this.categories = List.copyOf(cats);
    }

    private List<PreviewItem> load(final JavaPlugin plugin) {
        final List<PreviewItem> loaded = new ArrayList<>();
        final java.io.InputStream stream = plugin.getResource(RESOURCE);
        if (stream == null) {
            plugin.getLogger().warning("preview-items.json not found inside the plugin jar.");
            return loaded;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8))) {
            final JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("items")) {
                return loaded;
            }
            final JsonArray arr = root.getAsJsonArray("items");
            for (final JsonElement el : arr) {
                final JsonObject obj = el.getAsJsonObject();
                final Integer cmd = obj.has("cmd") && !obj.get("cmd").isJsonNull()
                        ? obj.get("cmd").getAsInt() : null;
                final String itemModel = obj.has("itemModel")
                        && !obj.get("itemModel").isJsonNull()
                        ? obj.get("itemModel").getAsString() : null;
                loaded.add(new PreviewItem(
                        obj.get("name").getAsString(),
                        obj.get("material").getAsString(),
                        cmd,
                        itemModel,
                        obj.has("model") ? obj.get("model").getAsString() : "",
                        obj.has("category") ? obj.get("category").getAsString() : "Misc"));
            }
        } catch (final Exception ex) {
            plugin.getLogger().warning("Failed to load preview catalog: " + ex.getMessage());
        }
        return List.copyOf(loaded);
    }

    /** All catalog items. */
    public List<PreviewItem> getItems() {
        return items;
    }

    /** Category names in first-seen order. */
    public List<String> getCategories() {
        return categories;
    }

    /** Items belonging to one category. */
    public List<PreviewItem> byCategory(final String category) {
        return items.stream()
                .filter(item -> item.category().equalsIgnoreCase(category))
                .toList();
    }

    /**
     * Finds items by a fuzzy id or display name (case-insensitive substring
     * on the display name, or exact name match).
     *
     * @param query the search text
     * @return matching entries
     */
    public List<PreviewItem> search(final String query) {
        final String q = query.toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(q)
                        || item.model().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }
}