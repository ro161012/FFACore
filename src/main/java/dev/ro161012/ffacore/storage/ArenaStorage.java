package dev.ro161012.ffacore.storage;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.arena.ArenaManager;
import dev.ro161012.ffacore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ArenaStorage {

    private final FFACore plugin;
    private final Path dataFolder;
    private final Path arenasFile;
    private final Path snapshotsFolder;
    private boolean compressSnapshots;
    private boolean useAsyncSave;
    private boolean useAsyncLoad;
    private int maxCachedSnapshots;

    // Cache of loaded snapshots: arenaId -> list of block snapshots
    private final Map<UUID, List<BlockSnapshot>> snapshotCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheAccessOrder = new LinkedHashMap<>();

    public ArenaStorage(FFACore plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.arenasFile = dataFolder.resolve("arenas.json");
        this.snapshotsFolder = dataFolder.resolve("snapshots");
        applyConfig();

        try {
            Files.createDirectories(dataFolder);
            Files.createDirectories(snapshotsFolder);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create data folders: " + e.getMessage());
        }
    }

    /**
     * Re-reads the snapshot I/O settings from {@code config.yml} so config
     * menu changes apply to the next save/load cycle immediately.
     */
    public void applyConfig() {
        this.compressSnapshots = plugin.getConfig().getBoolean("performance.compress-snapshots", true);
        this.useAsyncSave = plugin.getConfig().getBoolean("performance.use-async-save", true);
        this.useAsyncLoad = plugin.getConfig().getBoolean("performance.use-async-load", true);
        this.maxCachedSnapshots = Math.max(1,
                plugin.getConfig().getInt("performance.max-cached-snapshots", 10));
    }

    /**
     * Save all arena metadata to the arenas.json file.
     */
    public void saveAllArenas(ArenaManager manager) {
        try {
            List<Map<String, Object>> arenaList = new ArrayList<>();
            for (Arena arena : manager.getArenas()) {
                // Only save top-level arenas; sub-arenas are saved within their parent
                if (arena.getParent() != null) continue;
                arenaList.add(serializeArena(arena));
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(arenasFile, gson.toJson(arenaList));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save arenas: " + e.getMessage());
        }
    }

    /**
     * Load all arenas from arenas.json.
     */
    public void loadAllArenas(ArenaManager manager) {
        if (!Files.exists(arenasFile)) return;

        try {
            String json = Files.readString(arenasFile);
            Gson gson = new Gson();
            TypeToken<List<Map<String, Object>>> typeToken =
                    new TypeToken<List<Map<String, Object>>>() {};
            List<Map<String, Object>> arenaList = gson.fromJson(json, typeToken.getType());

            if (arenaList != null) {
                for (Map<String, Object> data : arenaList) {
                    Arena arena = deserializeArena(data);
                    if (arena != null) {
                        manager.addArena(arena);
                    }
                }
                plugin.getLogger().info("Loaded " + manager.getArenaCount() + " arenas.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load arenas: " + e.getMessage());
        }
    }

    /**
     * Save a single arena's snapshot.
     */
    public CompletableFuture<Void> saveSnapshotAsync(Arena arena) {
        List<BlockSnapshot> snapshots = captureSnapshot(arena);

        Runnable task = () -> {
            try {
                writeSnapshotToDisk(arena.getId(), snapshots);
                // Update cache
                snapshotCache.put(arena.getId(), snapshots);
                cacheAccessOrder.put(arena.getId(), System.currentTimeMillis());
                evictCacheIfNeeded();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save snapshot for arena " + arena.getName() + ": " + e.getMessage());
            }
        };

        if (useAsyncSave) {
            return CompletableFuture.runAsync(task);
        } else {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Save a snapshot synchronously (used during plugin disable).
     */
    public void saveSnapshotSync(Arena arena) {
        List<BlockSnapshot> snapshots = captureSnapshot(arena);
        try {
            writeSnapshotToDisk(arena.getId(), snapshots);
            snapshotCache.put(arena.getId(), snapshots);
            cacheAccessOrder.put(arena.getId(), System.currentTimeMillis());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save snapshot: " + e.getMessage());
        }
    }

    /**
     * Load a snapshot (from cache or disk).
     */
    public CompletableFuture<List<BlockSnapshot>> loadSnapshotAsync(Arena arena) {
        // Check cache
        List<BlockSnapshot> cached = snapshotCache.get(arena.getId());
        if (cached != null) {
            cacheAccessOrder.put(arena.getId(), System.currentTimeMillis());
            return CompletableFuture.completedFuture(new ArrayList<>(cached));
        }

        if (useAsyncLoad) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<BlockSnapshot> snapshots = readSnapshotFromDisk(arena.getId());
                    snapshotCache.put(arena.getId(), snapshots);
                    cacheAccessOrder.put(arena.getId(), System.currentTimeMillis());
                    evictCacheIfNeeded();
                    return new ArrayList<>(snapshots);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to load snapshot: " + e.getMessage());
                    return Collections.emptyList();
                }
            });
        } else {
            try {
                List<BlockSnapshot> snapshots = readSnapshotFromDisk(arena.getId());
                snapshotCache.put(arena.getId(), snapshots);
                return CompletableFuture.completedFuture(new ArrayList<>(snapshots));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load snapshot: " + e.getMessage());
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
        }
    }

    /**
     * Delete a snapshot from disk and cache.
     */
    public void deleteSnapshot(UUID arenaId) {
        snapshotCache.remove(arenaId);
        cacheAccessOrder.remove(arenaId);
        Path file = getSnapshotPath(arenaId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete snapshot file: " + e.getMessage());
        }
    }

    /**
     * Capture all blocks in the arena into snapshots.
     */
    public List<BlockSnapshot> captureSnapshot(Arena arena) {
        List<BlockSnapshot> snapshots = new ArrayList<>();
        Location origin = arena.getMinCorner();
        if (origin == null) return snapshots;

        World world = arena.getWorld();
        if (world == null) return snapshots;

        Location min = arena.getMinCorner();
        Location max = arena.getMaxCorner();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    snapshots.add(BlockSnapshot.fromBlock(block, origin));
                }
            }
        }

        return snapshots;
    }

    // -- Private methods --

    private Path getSnapshotPath(UUID arenaId) {
        String ext = compressSnapshots ? ".dat.gz" : ".dat";
        return snapshotsFolder.resolve(arenaId.toString() + ext);
    }

    private void writeSnapshotToDisk(UUID arenaId, List<BlockSnapshot> snapshots) throws IOException {
        Path file = getSnapshotPath(arenaId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // Write header
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(snapshots.size());
        for (BlockSnapshot snap : snapshots) {
            byte[] data = snap.serialize();
            dos.writeInt(data.length);
            dos.write(data);
        }

        byte[] raw = bos.toByteArray();

        if (compressSnapshots) {
            try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
                gzos.write(raw);
            }
        } else {
            Files.write(file, raw);
        }
    }

    private List<BlockSnapshot> readSnapshotFromDisk(UUID arenaId) throws IOException {
        Path file = getSnapshotPath(arenaId);
        if (!Files.exists(file)) return Collections.emptyList();

        byte[] raw;
        if (compressSnapshots) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(file.toFile()))) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = gzis.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
            }
            raw = bos.toByteArray();
        } else {
            raw = Files.readAllBytes(file);
        }

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(raw));
        int count = dis.readInt();
        List<BlockSnapshot> snapshots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int dataLen = dis.readInt();
            byte[] data = new byte[dataLen];
            dis.readFully(data);
            snapshots.add(BlockSnapshot.deserialize(data));
        }

        return snapshots;
    }

    private void evictCacheIfNeeded() {
        while (snapshotCache.size() > maxCachedSnapshots) {
            UUID oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<UUID, Long> entry : cacheAccessOrder.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestTime = entry.getValue();
                    oldest = entry.getKey();
                }
            }
            if (oldest != null) {
                snapshotCache.remove(oldest);
                cacheAccessOrder.remove(oldest);
            }
        }
    }

    // -- Arena serialization --

    private Map<String, Object> serializeArena(Arena arena) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", arena.getId().toString());
        map.put("name", arena.getName());
        map.put("world", arena.getWorldName());
        map.put("pos1", Utils.locationToString(arena.getPos1()));
        map.put("pos2", Utils.locationToString(arena.getPos2()));
        map.put("spawn", Utils.locationToFullString(arena.getSpawn()));
        map.put("creator", arena.getCreator().toString());
        map.put("creatorName", arena.getCreatorName());
        map.put("createdAt", arena.getCreatedAt());
        map.put("locked", arena.isLocked());
        map.put("regenerationMode", arena.getRegenerationMode());
        map.put("schedule", arena.getSchedule());
        map.put("lastRegenerated", arena.getLastRegenerated());
        map.put("settings", arena.getSettings());

        // Sub-arenas
        if (arena.hasSubArenas()) {
            List<Map<String, Object>> subs = new ArrayList<>();
            for (Arena sub : arena.getSubArenas()) {
                subs.add(serializeArena(sub));
            }
            map.put("subArenas", subs);
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private Arena deserializeArena(Map<String, Object> data) {
        try {
            String name = (String) data.get("name");
            UUID id = UUID.fromString((String) data.get("id"));
            String worldName = (String) data.get("world");

            Location pos1 = Utils.stringToLocation((String) data.get("pos1"));
            Location pos2 = Utils.stringToLocation((String) data.get("pos2"));
            Location spawn = Utils.stringToLocation((String) data.get("spawn"));

            UUID creator = UUID.fromString((String) data.get("creator"));
            String creatorName = (String) data.get("creatorName");

            // Create with dummy locations, then fix
            Arena arena = new Arena(name,
                    pos1 != null ? pos1 : new Location(Bukkit.getWorld(worldName), 0, 0, 0),
                    pos2 != null ? pos2 : new Location(Bukkit.getWorld(worldName), 0, 0, 0),
                    creator, creatorName);

            // Override ID
            try {
                java.lang.reflect.Field idField = Arena.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(arena, id);
            } catch (Exception ignored) {}

            arena.setSpawn(spawn);
            arena.setWorldName(worldName);
            if (data.containsKey("createdAt")) arena.setCreatedAt(((Number) data.get("createdAt")).longValue());
            if (data.containsKey("locked")) arena.setLocked((Boolean) data.get("locked"));
            if (data.containsKey("regenerationMode")) arena.setRegenerationMode((String) data.get("regenerationMode"));
            if (data.containsKey("schedule")) arena.setSchedule((String) data.get("schedule"));
            if (data.containsKey("lastRegenerated")) arena.setLastRegenerated(((Number) data.get("lastRegenerated")).longValue());

            // Settings
            if (data.containsKey("settings")) {
                Map<String, Object> settings = (Map<String, Object>) data.get("settings");
                settings.forEach(arena::setSetting);
            }

            // Sub-arenas
            if (data.containsKey("subArenas")) {
                List<Map<String, Object>> subs = (List<Map<String, Object>>) data.get("subArenas");
                for (Map<String, Object> subData : subs) {
                    Arena sub = deserializeArena(subData);
                    if (sub != null) {
                        arena.addSubArena(sub);
                    }
                }
            }

            return arena;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize arena: " + e.getMessage());
            return null;
        }
    }
}
