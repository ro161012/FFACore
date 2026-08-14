package dev.ro161012.ffacore.arena;

import dev.ro161012.ffacore.FFACore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ArenaListener implements Listener {

    private final FFACore plugin;

    public ArenaListener(FFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check on block change
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        ArenaManager manager = plugin.getArenaManager();

        // Check old location
        Arena oldArena = manager.getArenaAt(event.getFrom());
        Arena newArena = manager.getArenaAt(event.getTo());

        if (oldArena != null && oldArena != newArena) {
            oldArena.removePlayer(event.getPlayer());
        }
        if (newArena != null && newArena != oldArena) {
            newArena.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        ArenaManager manager = plugin.getArenaManager();
        // Remove from old arena tracking
        for (Arena arena : manager.getArenas()) {
            arena.removePlayer(event.getPlayer());
        }
        // Add to new arena if applicable
        Arena newArena = manager.getArenaAt(event.getTo());
        if (newArena != null) {
            newArena.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Remove player from all arenas
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            arena.removePlayer(event.getPlayer());
        }
    }
}
