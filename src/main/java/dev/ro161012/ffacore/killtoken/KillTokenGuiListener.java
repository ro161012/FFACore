package dev.ro161012.ffacore.killtoken;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles clicks inside KillToken GUI inventories.
 * <ul>
 *   <li>Stats GUIs are read-only — all clicks are cancelled.</li>
 *   <li>Leaderboard prev/next buttons navigate pages.</li>
 *   <li>The close button closes the inventory.</li>
 * </ul>
 */
public final class KillTokenGuiListener implements Listener {

    private final KillTokenManager plugin;

    /**
     * Creates the listener.
     *
     * @param plugin owning plugin instance
     */
    public KillTokenGuiListener(final KillTokenManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KillTokenGui.GuiHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        event.setCancelled(true);

        final String tag = holder.tag();
        if (tag.startsWith("top:")) {
            final int currentPage = Integer.parseInt(tag.substring(4));
            final String name = event.getCurrentItem() != null
                    && event.getCurrentItem().hasItemMeta()
                    ? event.getCurrentItem().getItemMeta().getDisplayName()
                    : "";
            if (name.contains("Previous")) {
                KillTokenGui.openTop(viewer, currentPage - 1, plugin);
            } else if (name.contains("Next")) {
                KillTokenGui.openTop(viewer, currentPage + 1, plugin);
            } else if (name.contains("Close")) {
                viewer.closeInventory();
            }
        }
        // Stats GUIs are read-only — clicks already cancelled above
    }
}
