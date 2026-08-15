package dev.ro161012.ffacore.weapon;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a countdown boss bar for every custom-weapon ability a player puts
 * on cooldown.
 *
 * <p>Each ability keeps its own bar keyed by player and ability id, so a
 * player with both Nichirin Blade actives (or both Kokoshibos Sword actives)
 * on cooldown sees one bar per ability. Bars drain in real time and are
 * removed automatically the moment the cooldown expires; there are no chat
 * or action-bar indicators, only the boss bar.
 */
public final class AbilityBossBars {

    /** How often, in ticks, the bars are re-progressed. */
    private static final long TICK_INTERVAL = 2L;

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Active>> active = new ConcurrentHashMap<>();

    /**
     * Creates the boss bar tracker and starts its update task.
     *
     * @param plugin owning plugin (for the scheduler)
     */
    public AbilityBossBars(final JavaPlugin plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    /**
     * Shows a draining cooldown bar for one ability. Re-triggering the same
     * ability id replaces any existing bar for that ability.
     *
     * @param player         the player to show the bar to
     * @param abilityId      stable id for the ability
     * @param title          the bar's title text
     * @param color          the themed bar colour
     * @param cooldownMillis cooldown length in milliseconds
     */
    public void start(final Player player, final String abilityId, final String title,
                      final BarColor color, final long cooldownMillis) {
        final UUID id = player.getUniqueId();
        final Map<String, Active> byAbility = active.computeIfAbsent(
                id, ignored -> new ConcurrentHashMap<>());
        final Active previous = byAbility.put(abilityId,
                new Active(title, color, cooldownMillis));
        if (previous != null) {
            previous.bar.removeAll();
        }
        byAbility.get(abilityId).bar.addPlayer(player);
    }

    /**
     * Removes every bar belonging to a player (used on quit and disable).
     *
     * @param playerId the player id
     */
    public void clear(final UUID playerId) {
        final Map<String, Active> byAbility = active.remove(playerId);
        if (byAbility != null) {
            byAbility.values().forEach(bar -> bar.bar.removeAll());
        }
    }

    /**
     * Removes every bar from every player.
     */
    public void close() {
        active.values().forEach(byAbility ->
                byAbility.values().forEach(bar -> bar.bar.removeAll()));
        active.clear();
    }

    /**
     * Advances every bar and drops any that have expired.
     */
    private void tick() {
        final long now = System.currentTimeMillis();
        active.forEach((playerId, byAbility) -> {
            byAbility.entrySet().removeIf(entry -> {
                final Active current = entry.getValue();
                final long remaining = current.expiresAt - now;
                if (remaining <= 0L) {
                    current.bar.removeAll();
                    return true;
                }
                final double progress = Math.max(0.0,
                        Math.min(1.0, (double) remaining / current.totalMillis));
                current.bar.setProgress(progress);
                final long seconds = (long) Math.ceil(remaining / 1000.0);
                current.bar.setTitle(current.title + "  §r§7" + seconds + "s");
                return false;
            });
            if (byAbility.isEmpty()) {
                active.remove(playerId, byAbility);
            }
        });
    }

    /**
     * A single in-flight cooldown bar.
     */
    private static final class Active {
        private final BossBar bar;
        private final String title;
        private final long expiresAt;
        private final long totalMillis;

        Active(final String title, final BarColor color, final long cooldownMillis) {
            this.bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            this.title = title;
            this.totalMillis = Math.max(1L, cooldownMillis);
            this.expiresAt = System.currentTimeMillis() + totalMillis;
        }
    }
}
