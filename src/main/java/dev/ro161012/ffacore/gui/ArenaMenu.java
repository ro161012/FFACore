package dev.ro161012.ffacore.gui;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArenaMenu implements Listener {

    private final FFACore plugin;
    private final Map<UUID, Integer> openMenus = new HashMap<>(); // player -> current page

    public ArenaMenu(FFACore plugin) {
        this.plugin = plugin;
    }

    // ==================== MAIN MENU ====================

    public void openMainMenu(Player player) {
        openMainMenu(player, 0);
    }

    private void openMainMenu(Player player, int page) {
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("gui.title", "&8Arena Management"));
        int rows = plugin.getConfig().getInt("gui.rows", 6);
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(new MenuHolder("main:" + page), size, title);

        List<Arena> arenas = new ArrayList<>();
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (arena.getParent() == null) {
                arenas.add(arena);
            }
        }

        int itemsPerPage = size - 9; // Leave bottom row for navigation
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, arenas.size());

        for (int i = startIndex; i < endIndex; i++) {
            Arena arena = arenas.get(i);
            inv.addItem(createArenaItem(arena));
        }

        // Navigation row
        int navRow = size - 9;
        if (page > 0) {
            inv.setItem(navRow, createNavItem(Material.ARROW, "&aPrevious Page"));
        }
        if (endIndex < arenas.size()) {
            inv.setItem(navRow + 8, createNavItem(Material.ARROW, "&aNext Page"));
        }

        inv.setItem(navRow + 4, createInfoItem());

        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), page);
    }

    // ==================== ARENA DETAILS ====================

    public void openArenaDetails(Player player, Arena arena) {
        String title = ChatColor.translateAlternateColorCodes('&',
                "&8Arena: &e" + arena.getName());
        int size = 27;

        Inventory inv = Bukkit.createInventory(new MenuHolder("detail:" + arena.getName()), size, title);

        // Info item
        inv.setItem(4, createArenaItem(arena));

        // Regenerate button
        inv.setItem(11, createButton(Material.EMERALD, "&aRegenerate",
                "&7Click to regenerate this arena",
                "&7Mode: &e" + arena.getRegenerationMode(),
                "&7Regenerating: &e" + (plugin.getRegenerationManager().isRegenerating(arena.getId()) ? "Yes" : "No")));

        // Schedule button
        long timeUntil = plugin.getScheduleManager().getTimeUntilNext(arena.getId());
        inv.setItem(13, createButton(Material.CLOCK, "&eSchedule",
                "&7Schedule: &e" + (arena.getSchedule() != null ? arena.getSchedule() : "None"),
                "&7Next: &e" + (timeUntil >= 0 ? Utils.formatTime(timeUntil) : "N/A")));

        // Settings button
        inv.setItem(15, createButton(Material.COMPARATOR, "&bSettings",
                "&7Mode: &f" + arena.getRegenerationMode(),
                "&7Locked: &f" + arena.isLocked(),
                "&7Players: &f" + arena.getPlayerCount()));

        // Preview button
        inv.setItem(21, createButton(Material.ENDER_EYE, "&dPreview Borders",
                "&7Click to preview arena borders"));

        // Back button
        inv.setItem(22, createButton(Material.BARRIER, "&cBack",
                "&7Return to arena list"));

        // Sub-arena info
        if (arena.hasSubArenas()) {
            inv.setItem(23, createButton(Material.CHEST, "&6Sub Arenas",
                    "&7" + arena.getSubArenas().size() + " sub-arena(s)"));
        }

        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), -1);
    }

    // ==================== EVENT HANDLING ====================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String tag = holder.tag;

        if (tag.startsWith("main:")) {
            int page = Integer.parseInt(tag.substring(5));
            handleMainClick(player, item, page);
        } else if (tag.startsWith("detail:")) {
            String arenaName = tag.substring(7);
            handleDetailClick(player, item, arenaName);
        }
    }

    private void handleMainClick(Player player, ItemStack item, int page) {
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        if (name == null) return;

        if (name.equals("Previous Page")) {
            openMainMenu(player, page - 1);
        } else if (name.equals("Next Page")) {
            openMainMenu(player, page + 1);
        } else {
            // Arena item clicked
            Arena arena = plugin.getArenaManager().getArena(name);
            if (arena != null) {
                openArenaDetails(player, arena);
            }
        }
    }

    private void handleDetailClick(Player player, ItemStack item, String arenaName) {
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) return;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (displayName == null) return;

        switch (displayName) {
            case "Regenerate" -> {
                player.closeInventory();
                plugin.getRegenerationManager().regenerate(arena).thenRun(() -> {
                    plugin.getMessages().send(player, "regen.complete",
                            "&aArena regenerated!");
                });
            }
            case "Schedule" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7Current schedule: &e" + (arena.getSchedule() != null ? arena.getSchedule() : "None")));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7Use &e/ffa arena schedule " + arena.getName() + " <time> &7to set"));
            }
            case "Settings" -> {
                player.closeInventory();
                player.chat("/ffa arena settings " + arena.getName());
            }
            case "Preview Borders" -> {
                player.closeInventory();
                plugin.getSelectionManager().startPreview(player, arena.getName());
                plugin.getMessages().send(player, "preview.success",
                        "&aShowing borders for 30 seconds");
            }
            case "Back" -> openMainMenu(player);
            case "Sub Arenas" -> {
                // Could open sub-arena list
                player.closeInventory();
                player.chat("/ffa arena subarena " + arena.getName() + " list");
            }
            default -> plugin.getLogger().warning("Unknown arena detail action: " + displayName);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            openMenus.remove(event.getPlayer().getUniqueId());
        }
    }

    // ==================== ITEM CREATION ====================

    private ItemStack createArenaItem(Arena arena) {
        boolean regen = plugin.getRegenerationManager().isRegenerating(arena.getId());
        Material mat = regen ? Material.RED_TERRACOTTA :
                arena.isLocked() ? Material.ORANGE_TERRACOTTA : Material.LIME_TERRACOTTA;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + arena.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + arena.getWorldName());
        lore.add(ChatColor.GRAY + "Size: " + ChatColor.WHITE + Utils.formatBytes(arena.getBlockCount()));
        lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + arena.getRegenerationMode());
        lore.add(ChatColor.GRAY + "Status: " + (regen ? ChatColor.RED + "Regenerating" :
                arena.isLocked() ? ChatColor.GOLD + "Locked" : ChatColor.GREEN + "Ready"));

        if (arena.getSchedule() != null) {
            long until = plugin.getScheduleManager().getTimeUntilNext(arena.getId());
            if (until >= 0) {
                lore.add(ChatColor.GRAY + "Next Regen: " + ChatColor.WHITE + Utils.formatTime(until));
            }
        }

        lore.add(ChatColor.GRAY + "Players: " + ChatColor.WHITE + arena.getPlayerCount());

        if (arena.hasSubArenas()) {
            lore.add(ChatColor.GOLD + "" + arena.getSubArenas().size() + " sub-arena(s)");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bFFACore"));
        meta.setLore(List.of(
                ChatColor.GRAY + "Arenas: " + ChatColor.WHITE + plugin.getArenaManager().getArenaCount(),
                ChatColor.GRAY + "Active: " + ChatColor.WHITE + plugin.getRegenerationManager().getActiveCount(),
                ChatColor.GRAY + "Queue: " + ChatColor.WHITE + plugin.getRegenerationManager().getQueueSize()
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== MENU HOLDER ====================

    private record MenuHolder(String tag) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
