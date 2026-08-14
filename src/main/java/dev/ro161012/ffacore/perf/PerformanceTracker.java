package dev.ro161012.ffacore.perf;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.Utils;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceTracker {

    private final FFACore plugin;

    // Recent regeneration timings
    private final List<RegenRecord> recentRegens = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_RECORDS = 100;

    // Overall stats
    private final AtomicLong totalRegenerations = new AtomicLong(0);
    private final AtomicLong totalBlocksRestored = new AtomicLong(0);
    private final AtomicLong totalTimeMs = new AtomicLong(0);

    // Per-arena stats
    private final Map<String, ArenaStats> arenaStats = new ConcurrentHashMap<>();

    // Cleanup task
    private BukkitTask cleanupTask;

    public PerformanceTracker(FFACore plugin) {
        this.plugin = plugin;

        // Periodic cleanup of old records
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long cutoff = System.currentTimeMillis() - 3600_000; // 1 hour
                synchronized (recentRegens) {
                    recentRegens.removeIf(r -> r.timestamp < cutoff);
                }
                arenaStats.entrySet().removeIf(e ->
                        System.currentTimeMillis() - e.getValue().lastAccess > 86400_000); // 1 day
            }
        }.runTaskTimer(plugin, 6000, 6000); // Every 5 minutes
    }

    public void recordRegeneration(String arenaName, long blockCount, long durationMs) {
        totalRegenerations.incrementAndGet();
        totalBlocksRestored.addAndGet(blockCount);
        totalTimeMs.addAndGet(durationMs);

        RegenRecord record = new RegenRecord();
        record.arenaName = arenaName;
        record.blockCount = blockCount;
        record.durationMs = durationMs;
        record.timestamp = System.currentTimeMillis();

        synchronized (recentRegens) {
            recentRegens.add(record);
            if (recentRegens.size() > MAX_RECORDS) {
                recentRegens.remove(0);
            }
        }

        ArenaStats stats = arenaStats.computeIfAbsent(arenaName, k -> new ArenaStats());
        stats.regenerations++;
        stats.totalBlocks += blockCount;
        stats.totalTimeMs += durationMs;
        stats.lastRegen = record.timestamp;
        stats.lastAccess = System.currentTimeMillis();
    }

    public long getTotalRegenerations() { return totalRegenerations.get(); }
    public long getTotalBlocksRestored() { return totalBlocksRestored.get(); }
    public long getTotalTimeMs() { return totalTimeMs.get(); }

    public double getAverageTimeMs() {
        long total = totalRegenerations.get();
        return total > 0 ? (double) totalTimeMs.get() / total : 0;
    }

    public double getAverageBlocksPerSecond() {
        long total = totalRegenerations.get();
        if (total == 0 || totalTimeMs.get() == 0) return 0;
        return (double) totalBlocksRestored.get() / (totalTimeMs.get() / 1000.0);
    }

    public List<RegenRecord> getRecentRegens(int limit) {
        synchronized (recentRegens) {
            int count = Math.min(limit, recentRegens.size());
            return new ArrayList<>(recentRegens.subList(
                    recentRegens.size() - count, recentRegens.size()));
        }
    }

    public ArenaStats getArenaStats(String arenaName) {
        return arenaStats.get(arenaName);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }

    /**
     * Get a formatted performance summary for display.
     */
    public List<String> getPerformanceSummary() {
        List<String> lines = new ArrayList<>();
        lines.add("&b=== FFACore Performance ===");
        lines.add("&7Total Regenerations: &f" + totalRegenerations.get());
        lines.add("&7Total Blocks Restored: &f" + Utils.formatBytes(totalBlocksRestored.get()));
        lines.add("&7Total Time: &f" + Utils.formatTime(totalTimeMs.get() / 1000));
        lines.add("&7Average Time: &f" + String.format("%.1f ms", getAverageTimeMs()));
        lines.add("&7Avg Blocks/sec: &f" + String.format("%.0f", getAverageBlocksPerSecond()));
        lines.add("&7Active Regen Tasks: &f" + plugin.getRegenerationManager().getActiveCount());
        lines.add("&7Queue Size: &f" + plugin.getRegenerationManager().getQueueSize());
        lines.add("&7Cached Snapshots: &f" + "N/A");
        lines.add("&7IO Threads: &f" + plugin.getConfig().getInt("performance.io-threads", 2));
        lines.add("&7Max Concurrent: &f" + plugin.getRegenerationManager().getMaxConcurrent());

        // Recent regens
        lines.add("&7--- Recent Regenerations ---");
        for (RegenRecord r : getRecentRegens(5)) {
            lines.add(String.format("&7  %s: &f%s blocks in %dms",
                    r.arenaName, Utils.formatBytes(r.blockCount), r.durationMs));
        }

        return lines;
    }

    // -- Inner classes --

    public static class RegenRecord {
        public String arenaName;
        public long blockCount;
        public long durationMs;
        public long timestamp;
    }

    public static class ArenaStats {
        public long regenerations;
        public long totalBlocks;
        public long totalTimeMs;
        public long lastRegen;
        public long lastAccess;
    }
}
