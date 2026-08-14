package dev.ro161012.ffacore.selection;

import dev.ro161012.ffacore.FFACore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SelectionManager implements Listener {

    private final FFACore plugin;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Integer> previewTasks = new HashMap<>();

    private static final String WAND_NAME = ChatColor.GOLD + "Arena Selection Wand";

    public SelectionManager(FFACore plugin) {
        this.plugin = plugin;
    }

    public ItemStack getWand() {
        Material material = Material.STICK;
        try {
            material = Material.valueOf(
                plugin.getConfig().getString("selection.default-tool", "STICK").toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        ItemStack wand = new ItemStack(material);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        meta.setLore(List.of(
            ChatColor.GRAY + "Left-click: Set position 1",
            ChatColor.GRAY + "Right-click: Set position 2"
        ));
        wand.setItemMeta(meta);
        return wand;
    }

    public void giveWand(Player player) {
        player.getInventory().addItem(getWand());
        plugin.getMessages().send(player, "wand.given",
                "&aYou have received the arena selection wand!");
    }

    public Selection getSelection(Player player) {
        return selections.get(player.getUniqueId());
    }

    public void setPos1(Player player, Location loc) {
        selections.computeIfAbsent(player.getUniqueId(), k -> new Selection()).setPos1(loc);
        plugin.getMessages().sendRaw(player, "&aPosition 1 set at &e" +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    public void setPos2(Player player, Location loc) {
        selections.computeIfAbsent(player.getUniqueId(), k -> new Selection()).setPos2(loc);
        plugin.getMessages().sendRaw(player, "&aPosition 2 set at &e" +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

        Selection sel = selections.get(player.getUniqueId());
        if (sel.isComplete()) {
            plugin.getMessages().sendRaw(player, "&aSelection complete! &e" +
                    sel.getBlockCount() + " &ablocks selected.");
        }
    }

    public void startPreview(Player player, String arenaName) {
        var arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null || arena.getPos1() == null || arena.getPos2() == null) return;

        // Cancel existing preview
        cancelPreview(player);

        World world = arena.getWorld();
        if (world == null) return;

        Location min = arena.getMinCorner();
        Location max = arena.getMaxCorner();

        final Particle particle = getParticleFromConfig();

        int count = plugin.getConfig().getInt("selection.preview.particle-count", 1);
        double interval = plugin.getConfig().getDouble("selection.preview.particle-interval", 0.5);

        long period = Math.max(1, (long) (interval * 20));

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    previewTasks.remove(player.getUniqueId());
                    return;
                }
                drawBoxEdges(player, world, min, max, particle, count);
            }
        }.runTaskTimer(plugin, 0, period).getTaskId();

        previewTasks.put(player.getUniqueId(), taskId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> cancelPreview(player), 20 * 30);
    }

    private void drawBoxEdges(Player player, World world, Location min, Location max,
                               Particle particle, int count) {
        double minX = min.getBlockX();
        double minY = min.getBlockY();
        double minZ = min.getBlockZ();
        double maxX = max.getBlockX() + 1;
        double maxY = max.getBlockY() + 1;
        double maxZ = max.getBlockZ() + 1;

        // Draw vertical edges at each corner
        double[][] corners = {
            {minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}
        };

        for (double[] corner : corners) {
            for (double y = minY; y <= maxY; y += 0.5) {
                player.spawnParticle(particle, corner[0], y, corner[1], count, 0, 0, 0, 0);
            }
        }

        // Draw horizontal edges
        for (double y : new double[]{minY, maxY}) {
            for (double x = minX; x <= maxX; x += 0.5) {
                player.spawnParticle(particle, x, y, minZ, count, 0, 0, 0, 0);
                player.spawnParticle(particle, x, y, maxZ, count, 0, 0, 0, 0);
            }
            for (double z = minZ; z <= maxZ; z += 0.5) {
                player.spawnParticle(particle, minX, y, z, count, 0, 0, 0, 0);
                player.spawnParticle(particle, maxX, y, z, count, 0, 0, 0, 0);
            }
        }
    }

    public void cancelPreview(Player player) {
        Integer taskId = previewTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private Particle getParticleFromConfig() {
        try {
            return Particle.valueOf(
                plugin.getConfig().getString("selection.preview.particle-type", "HAPPY_VILLAGER").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.HAPPY_VILLAGER;
        }
    }

    public void clearSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;
        if (!WAND_NAME.equals(item.getItemMeta().getDisplayName())) return;
        if (!player.hasPermission("ffacore.arena.wand")) return;

        event.setCancelled(true);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            setPos1(player, event.getClickedBlock().getLocation());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            setPos2(player, event.getClickedBlock().getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
        cancelPreview(event.getPlayer());
    }
}
