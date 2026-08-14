package dev.ro161012.ffacore.killtoken;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Prevents the Compressed Kill Token Block from being placed in the
 * world. The block is a currency item for trading, not a building
 * material; placement is cancelled and the player is notified.
 */
public final class CompressedBlockListener implements Listener {

    /** Pre-colourised placement rejection message (translated once). */
    private static final String PLACE_REJECTED = KillTokenManager.color(
            "&cCompressed Kill Token Blocks cannot be placed - use them for trading.");

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        if (!ChatColor.stripColor(meta.getDisplayName()).equals(CompressedBlockManager.DISPLAY_NAME)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(PLACE_REJECTED);
    }
}
