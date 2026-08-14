package dev.ro161012.ffacore.schedule;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.regeneration.RegenerationMode;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScheduleManager {

    private final FFACore plugin;
    private final Map<UUID, ScheduledRegen> schedules = new ConcurrentHashMap<>();
    private BukkitTask checkerTask;
    private final int checkInterval;

    public ScheduleManager(FFACore plugin) {
        this.plugin = plugin;
        this.checkInterval = plugin.getConfig().getInt("schedule.check-interval-seconds", 1);
        startChecker();
    }

    /**
     * Schedule automatic regeneration for an arena.
     * Format: "5s", "10m", "2h", "1d", "1w"
     */
    public boolean schedule(Arena arena, String timeStr) {
        long intervalSeconds = parseTime(timeStr);
        if (intervalSeconds <= 0) return false;

        ScheduledRegen scheduled = new ScheduledRegen();
        scheduled.arenaId = arena.getId();
        scheduled.arenaName = arena.getName();
        scheduled.intervalSeconds = intervalSeconds;
        scheduled.nextRegen = System.currentTimeMillis() / 1000 + intervalSeconds;
        scheduled.mode = RegenerationMode.fromString(arena.getRegenerationMode());

        schedules.put(arena.getId(), scheduled);
        arena.setSchedule(timeStr);

        return true;
    }

    /**
     * Cancel scheduled regeneration for an arena.
     */
    public boolean cancelSchedule(Arena arena) {
        arena.setSchedule(null);
        return schedules.remove(arena.getId()) != null;
    }

    /**
     * Check if an arena is scheduled.
     */
    public boolean isScheduled(UUID arenaId) {
        return schedules.containsKey(arenaId);
    }

    /**
     * Get the next regeneration timestamp (epoch seconds).
     */
    public long getNextRegen(UUID arenaId) {
        ScheduledRegen s = schedules.get(arenaId);
        return s != null ? s.nextRegen : -1;
    }

    /**
     * Get time until next regeneration (seconds).
     */
    public long getTimeUntilNext(UUID arenaId) {
        ScheduledRegen s = schedules.get(arenaId);
        if (s == null) return -1;
        long now = System.currentTimeMillis() / 1000;
        return Math.max(0, s.nextRegen - now);
    }

    public void shutdown() {
        if (checkerTask != null) {
            checkerTask.cancel();
        }
        schedules.clear();
    }

    private void startChecker() {
        long intervalTicks = Math.max(1, checkInterval * 20L);
        checkerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis() / 1000;
                for (Map.Entry<UUID, ScheduledRegen> entry : schedules.entrySet()) {
                    ScheduledRegen s = entry.getValue();
                    if (now >= s.nextRegen) {
                        Arena arena = plugin.getArenaManager().getArenaById(entry.getKey());
                        if (arena != null && !plugin.getRegenerationManager().isRegenerating(arena.getId())) {
                            plugin.getRegenerationManager().regenerate(arena, s.mode);
                        }
                        // Reschedule for next interval
                        s.nextRegen = now + s.intervalSeconds;
                    }
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    /**
     * Parse time string like "5s", "10m", "2h", "1d", "1w"
     */
    public static long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;

        try {
            timeStr = timeStr.trim().toLowerCase();
            char unit = timeStr.charAt(timeStr.length() - 1);
            String numStr = timeStr.substring(0, timeStr.length() - 1);
            long value = Long.parseLong(numStr);

            return switch (unit) {
                case 's' -> value;
                case 'm' -> value * 60;
                case 'h' -> value * 3600;
                case 'd' -> value * 86400;
                case 'w' -> value * 604800;
                default -> -1;
            };
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static class ScheduledRegen {
        UUID arenaId;
        String arenaName;
        long intervalSeconds;
        long nextRegen;
        RegenerationMode mode;
    }
}
