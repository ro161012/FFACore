package dev.ro161012.ffacore.killtoken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Red-themed inventory GUIs for player stats and kill leaderboards.
 *
 * <p>Colour palette:
 * <ul>
 *   <li>{@code &4} (dark red) — headers and branding</li>
 *   <li>{@code &c} (bright red) — player names and highlight values</li>
 *   <li>{@code &7} (gray) — stat labels and secondary text</li>
 *   <li>{@code &f} (white) — raw data values</li>
 * </ul>
 */
public final class KillTokenGui {

    private static final Material BORDER = Material.RED_STAINED_GLASS_PANE;

    private KillTokenGui() { /* utility class */ }

    // ──────────────────────────────────────────────
    //  Stats GUI
    // ──────────────────────────────────────────────

    /**
     * Opens a 3-row stats GUI for the given target, shown to the viewer.
     *
     * @param viewer the player requesting the GUI
     * @param target the player whose stats are displayed
     * @param plugin the owning plugin instance
     */
    public static void openStats(final Player viewer, final Player target,
                                 final KillTokenManager plugin) {
        final var tracker = plugin.getKillstreakTracker();
        final UUID id = target.getUniqueId();
        final Inventory inv = Bukkit.createInventory(
                new GuiHolder("stats:" + target.getName()), 27,
                color("&4&l" + target.getName() + " &8| &7Stats"));

        // Border
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 18, 19, 20, 21, 22, 23, 24, 25, 26}) {
            inv.setItem(i, pane());
        }

        // Head (slot 13)
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(target);
            skullMeta.setDisplayName(color("&c&l" + target.getName()));
            skullMeta.setLore(List.of(
                    color("&7KDR: &f" + tracker.getKdr(id)),
                    color("&7Best Streak: &f" + tracker.getBestStreak(id))));
            head.setItemMeta(skullMeta);
        }
        inv.setItem(13, head);

        // Kills (slot 10)
        inv.setItem(10, statItem(Material.IRON_SWORD,
                "&cKills", tracker.getTotalKills(id),
                "&7Lifetime PvP kills"));

        // Deaths (slot 12)
        inv.setItem(12, statItem(Material.SKELETON_SKULL,
                "&cDeaths", tracker.getTotalDeaths(id),
                "&7Lifetime deaths (all causes)"));

        // KDR (slot 14)
        inv.setItem(14, statItem(Material.GOLDEN_SWORD,
                "&cKDR", tracker.getKdr(id),
                "&7Kill / Death ratio"));

        // Current Streak (slot 16)
        inv.setItem(16, statItem(Material.FIRE_CHARGE,
                "&cStreak", tracker.getStreak(id),
                "&7Current killstreak",
                "&7Best: &f" + tracker.getBestStreak(id)));

        viewer.openInventory(inv);
    }

    // ──────────────────────────────────────────────
    //  Leaderboard GUI
    // ──────────────────────────────────────────────

    /** Players per leaderboard page. */
    private static final int PER_PAGE = 28;

    /**
     * Opens a paginated 5-row kill leaderboard GUI.
     *
     * @param viewer the player opening the GUI
     * @param page   1-based page number (clamped)
     * @param plugin the owning plugin instance
     */
    public static void openTop(final Player viewer, final int page,
                               final KillTokenManager plugin) {
        final var tracker = plugin.getKillstreakTracker();
        final List<Map.Entry<UUID, Integer>> sorted = tracker.getTopKills();
        final int totalPages = Math.max(1, (sorted.size() + PER_PAGE - 1) / PER_PAGE);
        final int p = Math.max(1, Math.min(page, totalPages));
        final int start = (p - 1) * PER_PAGE;
        final int end = Math.min(start + PER_PAGE, sorted.size());

        final Inventory inv = Bukkit.createInventory(
                new GuiHolder("top:" + p), 54,
                color("&4&lKill Leaderboard &8| &7Page " + p + "/" + totalPages));

        // Border
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, pane());
            }
        }

        // Title skull (slot 4)
        final ItemStack titleSkull = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta tMeta = (SkullMeta) titleSkull.getItemMeta();
        if (tMeta != null) {
            tMeta.setOwningPlayer(viewer);
            tMeta.setDisplayName(color("&c&lTop Killers"));
            tMeta.setLore(List.of(
                    color("&7Sorted by lifetime PvP kills"),
                    color("&7Page &f" + p + " &7/ &f" + totalPages)));
            titleSkull.setItemMeta(tMeta);
        }
        inv.setItem(4, titleSkull);

        // Player slots (start at row 1, skip the border columns)
        final int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        int slotIdx = 0;
        for (int i = start; i < end && slotIdx < slots.length; i++) {
            final var entry = sorted.get(i);
            final UUID uuid = entry.getKey();
            final int kills = entry.getValue();
            final int deaths = tracker.getTotalDeaths(uuid);
            final OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(uuid);
            final String name = offPlayer.getName() != null ? offPlayer.getName() : "Unknown";

            final int rank = i + 1;
            final ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            final SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(offPlayer);
                meta.setDisplayName(color("&c#" + rank + " &f" + name));
                meta.setLore(List.of(
                        color("&7Kills: &f" + kills),
                        color("&7Deaths: &f" + deaths),
                        color("&7KDR: &f" + tracker.getKdr(uuid)),
                        color("&7Best Streak: &f" + tracker.getBestStreak(uuid))));
                skull.setItemMeta(meta);
            }
            inv.setItem(slots[slotIdx++], skull);
        }

        // Navigation
        if (p > 1) {
            inv.setItem(48, navItem(Material.ARROW, "&cPrevious Page"));
        }
        inv.setItem(49, navItem(Material.BARRIER, "&cClose"));
        if (p < totalPages) {
            inv.setItem(50, navItem(Material.ARROW, "&cNext Page"));
        }

        viewer.openInventory(inv);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private static ItemStack pane() {
        final ItemStack item = new ItemStack(BORDER);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack statItem(final Material material, final String label,
                                       final Object valueObject, final String... loreLines) {
        final String value = String.valueOf(valueObject);
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&4" + label + "&8: &f" + value));
            final List<String> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(color(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack navItem(final Material material, final String name) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    static String color(final String s) {
        return KillTokenManager.color(s);
    }

    // ──────────────────────────────────────────────

    /** Simple tag-based holder to identify GUI clicks. */
    public record GuiHolder(String tag) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
