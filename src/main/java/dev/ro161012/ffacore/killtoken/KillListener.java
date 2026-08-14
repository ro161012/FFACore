package dev.ro161012.ffacore.killtoken;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Spawns the Kill Token when a player is killed by another player, subject
 * to the pair-based anti-farming cooldown.
 */
public final class KillListener implements Listener {

    private final KillTokenManager plugin;

    /**
     * Creates the listener.
     *
     * @param plugin owning plugin instance
     */
    public KillListener(final KillTokenManager plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player deaths. A token is dropped only for player-versus-player
     * kills that are not suppressed by the pair cooldown.
     *
     * @param event the death event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final Player killer = victim.getKiller();

        // Any death ends the victim's killstreak, regardless of the cause.
        plugin.getKillstreakTracker().recordDeath(victim.getUniqueId());

        // Strict PvP requirement: a token only drops when another player
        // is responsible for the killing damage.
        if (!isPlayerKill(victim, killer)) {
            return;
        }

        final PairCooldown cooldown = plugin.getPairCooldown();

        // Anti-farming: the exact killer/victim pair (in either direction)
        // cannot generate tokens again until the cooldown expires.
        if (cooldown.isOnCooldown(killer.getUniqueId(), victim.getUniqueId())) {
            if (plugin.notifyOnCooldown()) {
                killer.sendMessage(plugin.getCooldownMessage());
            }
            return;
        }

        cooldown.apply(killer.getUniqueId(), victim.getUniqueId());

        // Consecutive PvP kills build the killer's killstreak. Starting at the
        // configured streak, the normal floor drop receives a token multiplier.
        final int streak = plugin.getKillstreakTracker().increment(killer);

        final Location deathLocation = victim.getLocation();
        victim.getWorld().dropItemNaturally(deathLocation, plugin.createToken(
                plugin.getKillstreakTokenAmount(streak)));

        final String killMessage = plugin.getKillMessage();
        if (!killMessage.isEmpty()) {
            killer.sendMessage(killMessage);
        }
    }

    /**
     * Clears the leaver's killstreak so streaks never survive a disconnect.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        plugin.getKillstreakTracker().reset(event.getPlayer().getUniqueId());
    }

    /**
     * Decides whether the death qualifies for a token drop.
     *
     * <p>Paper's {@link Player#getKiller()} returns the player responsible
     * for the killing damage, or {@code null} otherwise:
     *
     * <ul>
     *   <li>Melee or projectile kill by a player &rarr; that player (qualifies;
     *       arrows/tridents count, the shooter is resolved as the killer).</li>
     *   <li>Killed by a mob &rarr; {@code null} (no drop).</li>
     *   <li>Fall damage, lava, drowning, void, explosions, poison, etc.
     *       &rarr; {@code null} (no drop).</li>
     *   <li>Suicide ({@code /kill}) &rarr; {@code null} (no drop).</li>
     * </ul>
     *
     * <p>Self-kills are rejected explicitly as well, so a player can never
     * receive a token from their own death.
     *
     * @param victim the player who died
     * @param killer the killer reported by the server, may be null
     * @return true only if another player killed the victim
     */
    private static boolean isPlayerKill(final Player victim, final Player killer) {
        return killer != null && !killer.getUniqueId().equals(victim.getUniqueId());
    }
}
