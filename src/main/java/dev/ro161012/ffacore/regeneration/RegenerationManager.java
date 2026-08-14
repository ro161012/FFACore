package dev.ro161012.ffacore.regeneration;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.storage.BlockSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RegenerationManager {

    private final FFACore plugin;
    private int maxConcurrent;
    private int tickBudget;
    private int defaultBatchSize;
    private int phasedBlocksPerSecond;
    private int phasedDelay;
    private int waveSpeed;

    private final Map<UUID, RegenerationTask> activeRegenerations = new ConcurrentHashMap<>();
    private final Queue<PendingRegen> queue = new ConcurrentLinkedQueue<>();
    private int currentConcurrent = 0;
    private BukkitTask processTask;

    public RegenerationManager(FFACore plugin) {
        this.plugin = plugin;
        applyConfig();

        // Start processing queue
        startProcessor();
    }

    /**
     * Re-reads the regeneration limits from {@code config.yml} so changes
     * made through the config menu apply to the next restoration immediately.
     */
    public void applyConfig() {
        this.maxConcurrent = Math.max(1,
                plugin.getConfig().getInt("regeneration.max-concurrent", 2));
        this.tickBudget = Math.max(1,
                plugin.getConfig().getInt("regeneration.tick-budget", 15));
        this.defaultBatchSize = Math.max(1,
                plugin.getConfig().getInt("regeneration.batch-size", 1000));
        this.phasedBlocksPerSecond = Math.max(1,
                plugin.getConfig().getInt("regeneration.phased.blocks-per-second", 50000));
        this.phasedDelay = Math.max(0,
                plugin.getConfig().getInt("regeneration.phased.delay-between-phases", 2));
        this.waveSpeed = Math.max(1,
                plugin.getConfig().getInt("regeneration.wave.wave-speed", 10000));
    }

    /**
     * Regenerate an arena using its configured mode.
     */
    public CompletableFuture<Void> regenerate(Arena arena, RegenerationMode mode) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!canStartNew()) {
            queue.add(new PendingRegen(arena, mode, future));
            return future;
        }

        startRegeneration(arena, mode, future);
        return future;
    }

    /**
     * Regenerate an arena using its saved mode setting.
     */
    public CompletableFuture<Void> regenerate(Arena arena) {
        return regenerate(arena, RegenerationMode.fromString(arena.getRegenerationMode()));
    }

    /**
     * Save a snapshot of an arena's current state.
     */
    public CompletableFuture<Void> saveSnapshot(Arena arena) {
        return plugin.getArenaStorage().saveSnapshotAsync(arena);
    }

    public boolean isRegenerating(UUID arenaId) {
        return activeRegenerations.containsKey(arenaId);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getActiveCount() {
        return activeRegenerations.size();
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void shutdown() {
        if (processTask != null) {
            processTask.cancel();
        }
        activeRegenerations.values().forEach(t -> {
            if (t.task != null) t.task.cancel();
        });
        activeRegenerations.clear();
        queue.clear();
    }

    // -- Internal --

    private boolean canStartNew() {
        return currentConcurrent < maxConcurrent;
    }

    private void startRegeneration(Arena arena, RegenerationMode mode, CompletableFuture<Void> future) {
        currentConcurrent++;

        RegenerationTask task = new RegenerationTask();
        task.arena = arena;
        task.mode = mode;
        task.future = future;

        plugin.getArenaStorage().loadSnapshotAsync(arena).thenAccept(snapshots -> {
            if (snapshots.isEmpty()) {
                plugin.getLogger().warning("No snapshot found for arena: " + arena.getName());
                completeTask(task);
                return;
            }

            Location origin = arena.getMinCorner();
            if (origin == null) {
                plugin.getLogger().warning("No origin for arena: " + arena.getName());
                completeTask(task);
                return;
            }

            arena.setLocked(true);

            // Teleport players inside the arena back to spawn before restoring blocks
            if (plugin.getConfig().getBoolean("regeneration.teleport-players-to-spawn", true)) {
                Bukkit.getScheduler().runTask(plugin, () -> teleportPlayersToSpawn(arena));
            }

            switch (mode) {
                case STANDARD -> runStandard(arena, snapshots, origin, task);
                case PHASED -> runPhased(arena, snapshots, origin, task);
                case SELECTIVE -> runSelective(arena, snapshots, origin, task);
                case WAVE -> runWave(arena, snapshots, origin, task);
                case WORLD_EDIT -> runWorldEdit(arena, snapshots, origin, task);
                default -> throw new IllegalStateException("Unknown regeneration mode: " + mode);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load snapshot for arena " + arena.getName() + ": " + ex.getMessage());
            completeTask(task);
            return null;
        });

        activeRegenerations.put(arena.getId(), task);
    }

    /**
     * STANDARD mode: Place all blocks immediately (batched across ticks if needed).
     */
    private void runStandard(Arena arena, List<BlockSnapshot> snapshots, Location origin, RegenerationTask task) {
        long startTime = System.currentTimeMillis();
        task.totalBlocks = snapshots.size();
        int[] index = {0};
        int batchSize = defaultBatchSize;

        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                long tickStart = System.nanoTime();
                int processed = 0;

                while (index[0] < snapshots.size() && processed < batchSize) {
                    snapshots.get(index[0]).restore(origin);
                    index[0]++;
                    processed++;

                    long elapsed = (System.nanoTime() - tickStart) / 1_000_000;
                    if (elapsed >= tickBudget) break;
                }
                task.processedBlocks = index[0];

                if (index[0] >= snapshots.size()) {
                    finishRegeneration(arena, task, startTime);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);

        task.task = bukkitTask;
        task.startTime = startTime;
    }

    /**
     * PHASED mode: Restore blocks in multiple timed phases.
     */
    private void runPhased(Arena arena, List<BlockSnapshot> snapshots, Location origin, RegenerationTask task) {
        long startTime = System.currentTimeMillis();
        task.totalBlocks = snapshots.size();

        int blocksPerTick = phasedBlocksPerSecond / 20;
        int[] index = {0};
        int phaseSize = Math.max(1, blocksPerTick);

        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                long tickStart = System.nanoTime();
                int processed = 0;
                int end = Math.min(index[0] + phaseSize, snapshots.size());

                while (index[0] < end) {
                    snapshots.get(index[0]).restore(origin);
                    index[0]++;
                    processed++;
                }
                task.processedBlocks = index[0];

                if (index[0] >= snapshots.size()) {
                    finishRegeneration(arena, task, startTime);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);

        task.task = bukkitTask;
        task.startTime = startTime;
    }

    /**
     * SELECTIVE mode: Only restore blocks that differ from the snapshot.
     */
    private void runSelective(Arena arena, List<BlockSnapshot> snapshots, Location origin, RegenerationTask task) {
        long startTime = System.currentTimeMillis();
        task.totalBlocks = snapshots.size();
        int[] index = {0};
        int batchSize = defaultBatchSize;

        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                long tickStart = System.nanoTime();
                int processed = 0;

                while (index[0] < snapshots.size() && processed < batchSize) {
                    BlockSnapshot snap = snapshots.get(index[0]);
                    Location loc = origin.clone().add(snap.getRelX(), snap.getRelY(), snap.getRelZ());
                    Block current = loc.getBlock();

                    if (!current.getType().equals(snap.getMaterial())
                            || !current.getBlockData().getAsString().equals(snap.getBlockData())) {
                        snap.restore(origin);
                        processed++;
                    }
                    index[0]++;

                    long elapsed = (System.nanoTime() - tickStart) / 1_000_000;
                    if (elapsed >= tickBudget) break;
                }
                task.processedBlocks = index[0];

                if (index[0] >= snapshots.size()) {
                    finishRegeneration(arena, task, startTime);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);

        task.task = bukkitTask;
        task.startTime = startTime;
    }

    /**
     * WAVE mode: Restore blocks in a wave-like pattern from one side to another.
     */
    private void runWave(Arena arena, List<BlockSnapshot> snapshots, Location origin, RegenerationTask task) {
        long startTime = System.currentTimeMillis();

        Map<Integer, List<BlockSnapshot>> byX = new TreeMap<>();
        for (BlockSnapshot snap : snapshots) {
            byX.computeIfAbsent(snap.getRelX(), k -> new ArrayList<>()).add(snap);
        }

        List<Integer> xCoords = new ArrayList<>(byX.keySet());
        Collections.sort(xCoords);

        if (plugin.getConfig().getBoolean("regeneration.wave.reverse-order", false)) {
            Collections.reverse(xCoords);
        }

        task.totalBlocks = snapshots.size();
        int[] xIndex = {0};
        int blocksPerTick = waveSpeed / 20;

        BukkitTask bukkitTask = new BukkitRunnable() {
            int blocksPlaced = 0;

            @Override
            public void run() {
                long tickStart = System.nanoTime();
                int processed = 0;

                while (xIndex[0] < xCoords.size() && processed < blocksPerTick) {
                    List<BlockSnapshot> column = byX.get(xCoords.get(xIndex[0]));
                    for (BlockSnapshot snap : column) {
                        snap.restore(origin);
                    }
                    blocksPlaced += column.size();
                    processed += column.size();
                    xIndex[0]++;

                    long elapsed = (System.nanoTime() - tickStart) / 1_000_000;
                    if (elapsed >= tickBudget) break;
                }
                task.processedBlocks = blocksPlaced;

                if (xIndex[0] >= xCoords.size()) {
                    finishRegeneration(arena, task, startTime);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);

        task.task = bukkitTask;
        task.startTime = startTime;
    }

    /**
     * WORLD_EDIT mode: Use WorldEdit's fast block placement API.
     */
    private void runWorldEdit(Arena arena, List<BlockSnapshot> snapshots, Location origin, RegenerationTask task) {
        long startTime = System.currentTimeMillis();
        task.totalBlocks = snapshots.size();

        var weHook = plugin.getWorldEditHook();
        if (weHook != null && weHook.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = weHook.restoreFast(arena, snapshots, origin);
                if (success) {
                    task.processedBlocks = snapshots.size();
                    finishRegeneration(arena, task, startTime);
                } else {
                    runStandard(arena, snapshots, origin, task);
                }
            });
            task.task = null;
            task.startTime = startTime;
            return;
        }

        runStandard(arena, snapshots, origin, task);
    }

    /**
     * Teleports every online player currently inside the arena to its spawn
     * point. If no spawn is set, falls back to the arena centre elevated by
     * two blocks. Offline players in the tracking set are silently skipped.
     */
    private void teleportPlayersToSpawn(Arena arena) {
        Location spawn = arena.getSpawn();
        if (spawn == null && arena.getPos1() != null && arena.getPos2() != null) {
            spawn = new Location(
                    arena.getWorld(),
                    (arena.getPos1().getBlockX() + arena.getPos2().getBlockX()) / 2.0 + 0.5,
                    Math.max(arena.getPos1().getBlockY(), arena.getPos2().getBlockY()) + 2,
                    (arena.getPos1().getBlockZ() + arena.getPos2().getBlockZ()) / 2.0 + 0.5);
        }
        if (spawn == null) return;

        for (UUID playerId : arena.getPlayersInside()) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.teleport(spawn);
            }
        }
    }

    private void finishRegeneration(Arena arena, RegenerationTask task, long startTime) {
        arena.setLocked(false);
        arena.setLastRegenerated(System.currentTimeMillis());

        long duration = System.currentTimeMillis() - startTime;
        plugin.getPerformanceTracker().recordRegeneration(arena.getName(), arena.getBlockCount(), duration);

        completeTask(task);
    }

    private void completeTask(RegenerationTask task) {
        activeRegenerations.remove(task.arena.getId());
        currentConcurrent--;
        if (task.future != null) {
            task.future.complete(null);
        }
        processQueue();
    }

    private void processQueue() {
        PendingRegen pending = queue.poll();
        if (pending != null) {
            startRegeneration(pending.arena, pending.mode, pending.future);
        }
    }

    private void startProcessor() {
        processTask = new BukkitRunnable() {
            @Override
            public void run() {
                while (canStartNew()) {
                    PendingRegen pending = queue.poll();
                    if (pending == null) break;
                    startRegeneration(pending.arena, pending.mode, pending.future);
                }
            }
        }.runTaskTimer(plugin, 20, 20); // Check every second
    }

    // -- Inner classes --

    public static class RegenerationProgress {
        public final int processedBlocks;
        public final int totalBlocks;
        public final long elapsedMs;

        RegenerationProgress(int processedBlocks, int totalBlocks, long elapsedMs) {
            this.processedBlocks = processedBlocks;
            this.totalBlocks = totalBlocks;
            this.elapsedMs = elapsedMs;
        }

        public int getPercent() {
            if (totalBlocks <= 0) return 0;
            return Math.min(100, (processedBlocks * 100) / totalBlocks);
        }

        public long getEtaMs() {
            if (processedBlocks <= 0 || totalBlocks <= 0) return -1;
            double rate = (double) processedBlocks / Math.max(1, elapsedMs);
            long remaining = totalBlocks - processedBlocks;
            return (long) (remaining / Math.max(0.001, rate));
        }
    }

    /**
     * Cancel an in-progress regeneration for the given arena.
     *
     * @param arenaId the arena to cancel
     * @return true if a regeneration was cancelled
     */
    public boolean cancel(UUID arenaId) {
        RegenerationTask task = activeRegenerations.remove(arenaId);
        if (task == null) return false;
        if (task.task != null) task.task.cancel();
        task.arena.setLocked(false);
        currentConcurrent--;
        if (task.future != null) task.future.cancel(true);
        processQueue();
        return true;
    }

    /**
     * Returns progress information for an active regeneration, or null
     * if the arena is not currently regenerating.
     */
    public RegenerationProgress getProgress(UUID arenaId) {
        RegenerationTask task = activeRegenerations.get(arenaId);
        if (task == null) return null;
        long elapsed = System.currentTimeMillis() - task.startTime;
        return new RegenerationProgress(task.processedBlocks, task.totalBlocks, elapsed);
    }

    private static class RegenerationTask {
        Arena arena;
        RegenerationMode mode;
        BukkitTask task;
        CompletableFuture<Void> future;
        long startTime;
        int totalBlocks;
        int processedBlocks;
    }

    private static class PendingRegen {
        final Arena arena;
        final RegenerationMode mode;
        final CompletableFuture<Void> future;

        PendingRegen(Arena arena, RegenerationMode mode, CompletableFuture<Void> future) {
            this.arena = arena;
            this.mode = mode;
            this.future = future;
        }
    }
}
