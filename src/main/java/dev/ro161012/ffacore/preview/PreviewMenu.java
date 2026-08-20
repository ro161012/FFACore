package dev.ro161012.ffacore.preview;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paged chest GUI for browsing every custom item in the merged resource pack.
 *
 * <p>The main menu lists the pack categories; clicking one opens a paged
 * list of 45 items with previous/next navigation. Left-clicking an item
 * gives one copy, shift-click hands out a full stack, so the admin can
 * preview anything the pack can render without knowing a single id.
 */
public final class PreviewMenu implements Listener {

    private static final int PAGES_SIZE = 54;
    private static final int ITEMS_PER_PAGE = PAGES_SIZE - 9;
    private static final int NAV_ROW = PAGES_SIZE - 9;

    private final FFACore plugin;

    /**
     * Creates the menu.
     *
     * @param plugin owning plugin
     */
    public PreviewMenu(final FFACore plugin) {
        this.plugin = plugin;
    }

    /** Opens the category overview. */
    public void openMain(final Player player) {
        final Inventory inv = Bukkit.createInventory(
                new PreviewHolder("main:0"), 45, ChatColor.translateAlternateColorCodes('&',
                        "&7Preview &8\u00bb &fItems"));

        final PreviewRegistry registry = plugin.getPreviewRegistry();
        for (final String category : registry.getCategories()) {
            final List<PreviewItem> in = registry.byCategory(category);
            final ItemStack icon = new ItemStack(Material.BOOK);
            final ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    "&e" + category));
            meta.setLore(List.of(
                    ChatColor.GRAY + "" + in.size() + " item(s)",
                    ChatColor.DARK_GRAY + "Click to browse"));
            icon.setItemMeta(meta);
            inv.addItem(icon);
        }

        player.openInventory(inv);
    }

    /**
     * Opens a paged list of one category.
     *
     * @param player   the viewer
     * @param category category key
     * @param page     zero-based page
     */
    public void openCategory(final Player player, final String category,
                             final int page) {
        final List<PreviewItem> items = plugin.getPreviewRegistry().byCategory(category);
        final int pages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        final int safePage = Math.max(0, Math.min(page, pages - 1));

        final Inventory inv = Bukkit.createInventory(new PreviewHolder(
                "cat:" + category.toLowerCase(Locale.ROOT) + ":" + safePage),
                PAGES_SIZE,
                ChatColor.translateAlternateColorCodes('&',
                        "&7Preview &8\u00bb &e" + category
                                + " &8(&f" + (safePage + 1) + "&7/&f" + pages + "&8)"));

        final int start = safePage * ITEMS_PER_PAGE;
        for (int i = start; i < Math.min(start + ITEMS_PER_PAGE, items.size()); i++) {
            inv.setItem(i - start, createPreviewItem(items.get(i)));
        }

        if (safePage > 0) {
            inv.setItem(NAV_ROW, navItem(Material.ARROW, "&aPrevious Page"));
        }
        if (safePage + 1 < pages) {
            inv.setItem(NAV_ROW + 8, navItem(Material.ARROW, "&aNext Page"));
        }
        inv.setItem(NAV_ROW + 4, navItem(Material.BARRIER, "&cBack to categories"));

        player.openInventory(inv);
    }

    private ItemStack createPreviewItem(final PreviewItem item) {
        final ItemStack stack = new ItemStack(item.materialOrFallback());
        final ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                "&f" + item.name()));

        final List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + item.model());
        if (item.cmd() != null) {
            lore.add(ChatColor.GRAY + "Custom model data: &f" + item.cmd());
        }
        if (item.itemModel() != null) {
            lore.add(ChatColor.GRAY + "Item model: &f" + item.itemModel());
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click &7to take 1");
        lore.add(ChatColor.YELLOW + "Shift-click &7to take 64");
        meta.setLore(lore);
        stack.setItemMeta(meta);

        final PreviewItemBuilder builder = new PreviewItemBuilder(stack);
        if (item.cmd() != null) {
            builder.withCustomModelData(item.cmd());
        } else if (item.itemModel() != null) {
            builder.withItemModel(item.itemModel());
        }
        return builder.build();
    }

    private ItemStack navItem(final Material mat, final String name) {
        final ItemStack item = new ItemStack(mat);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    //  Click handling
    // ------------------------------------------------------------------

    /**
     * Handles clicks in any preview inventory.
     *
     * @param event the click event
     */
    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PreviewHolder holder)) {
            return;
        }
        event.setCancelled(true);
        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        final String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (holder.tag().startsWith("main:")) {
            if (name != null) {
                openCategory(player, name, 0);
            }
            return;
        }

        // Category pages: "cat:<category>:<page>"
        final String[] parts = holder.tag().split(":");
        final String category = parts[1];
        final int page = Integer.parseInt(parts[2]);

        if (name == null) {
            return;
        }
        if (name.equals("Previous Page")) {
            openCategory(player, category, page - 1);
        } else if (name.equals("Next Page")) {
            openCategory(player, category, page + 1);
        } else if (name.equals("Back to categories")) {
            openMain(player);
        } else {
            // Item click: find the registry entry that produced this stack.
            final int amount = event.isShiftClick() ? 64 : 1;
            final int slot = event.getSlot();
            final List<PreviewItem> items =
                    plugin.getPreviewRegistry().byCategory(category);
            final int index = page * ITEMS_PER_PAGE + slot;
            if (index < 0 || index >= items.size()) {
                return;
            }
            give(player, items.get(index), amount);
        }
    }

    /**
     * Builds the actual item stack for a catalog entry and drops it into
     * the player's inventory (overflow drops at their feet).
     *
     * @param player recipient
     * @param item   catalog entry
     * @param amount stack size
     */
    public void give(final Player player, final PreviewItem item, final int amount) {
        final ItemStack stack = new PreviewItemBuilder(
                new ItemStack(item.materialOrFallback()))
                .withAmount(amount)
                .withCustomModelData(item.cmd())
                .withItemModel(item.itemModel())
                .build();
        final Map<Integer, ItemStack> leftover =
                player.getInventory().addItem(stack);
        for (final ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        Messages.raw(player, "&7Gave you &f" + item.name()
                + (amount > 1 ? " &7x" + amount : "") + "&7.");
    }

    /**
     * Tag carry-object for preview inventories.
     *
     * @param tag holder tag
     */
    private record PreviewHolder(String tag) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /**
     * Small fluent helper that applies the model data components to a stack.
     */
    private static final class PreviewItemBuilder {

        private final ItemStack stack;

        private PreviewItemBuilder(final ItemStack stack) {
            this.stack = stack;
        }

        private PreviewItemBuilder withAmount(final int amount) {
            stack.setAmount(amount);
            return this;
        }

        private PreviewItemBuilder withCustomModelData(final Integer cmd) {
            if (cmd == null) {
                return this;
            }
            final ItemMeta meta = stack.getItemMeta();
            meta.setCustomModelData(cmd);
            stack.setItemMeta(meta);
            return this;
        }

        private PreviewItemBuilder withItemModel(final String model) {
            if (model == null || model.isEmpty()) {
                return this;
            }
            final ItemMeta meta = stack.getItemMeta();
            final String[] parts = model.split(":", 2);
            final String namespace = parts.length == 2 ? parts[0] : "minecraft";
            final String key = parts.length == 2 ? parts[1] : parts[0];
            meta.setItemModel(NamespacedKey.fromString(namespace + ":" + key));
            stack.setItemMeta(meta);
            return this;
        }

        private ItemStack build() {
            return stack;
        }
    }
}