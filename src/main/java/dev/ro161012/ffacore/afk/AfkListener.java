package dev.ro161012.ffacore.afk;

import dev.ro161012.ffacore.FFACore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Resets a player's AFK idle timer whenever they do something that proves
 * they are not idle: move between blocks, interact, or take damage.
 *
 * <p>All handlers are cheap: they only touch the session map for players who
 * are already inside an AFK zone, so they cost nothing for everyone else.
 */
public final class AfkListener implements Listener {

    private final FFACore plugin;

    /**
     * Creates the listener.
     *
     * @param plugin owning plugin
     */
    public AfkListener(final FFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        plugin.getAfkManager().markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        plugin.getAfkManager().markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getAfkManager().markActive(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        plugin.getAfkManager().removeSession(event.getPlayer().getUniqueId());
    }
}
