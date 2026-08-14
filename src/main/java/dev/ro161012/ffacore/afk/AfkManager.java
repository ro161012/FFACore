package dev.ro161012.ffacore.afk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the AFK zone feature.
 *
 * <p>A single repeating task scans online players for zone membership and
 * awards AFK Shards to those who have been idle inside a zone long enough.
 * Zone membership and idle state are cached in per-player sessions so the hot
 * path never iterates the whole world or re-parses configuration.
 */
public final class AfkManager {

    private static final String ZONES_FILE = "afk-zones.json";
    private static final long HOUR_MS = 3_600_000L;

    private final FFACore plugin;
    private final Map<String, AfkZone> zones = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final long rewardIntervalSeconds;
    private final int shardsPerInterval;
    private final long minIdleSeconds;
    private final int maxShardsPerHour;
    private final boolean notifyOnEarn;
    private final String earnMessage;

    private BukkitTask rewardTask;

    /**
     * Creates the AFK subsystem and starts the reward loop.
     *
     * @param plugin owning FFACore plugin
     */
    public AfkManager(final FFACore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("afk.enabled", true);
        this.rewardIntervalSeconds = Math.max(5,
                plugin.getConfig().getLong("afk.reward-interval-seconds", 30L));
        this.shardsPerInterval = Math.max(1,
                plugin.getConfig().getInt("afk.shards-per-interval", 1));
        this.minIdleSeconds = Math.max(0,
                plugin.getConfig().getLong("afk.min-idle-seconds", 60L));
        this.maxShardsPerHour = plugin.getConfig().getInt("afk.max-shards-per-hour", 100);
        this.notifyOnEarn = plugin.getConfig().getBoolean("afk.notify-on-earn", true);
        this.earnMessage = Messages.color(plugin.getConfig().getString(
                "afk.earn-message", "&b+1 &3AFK Shard &7(the deep rewards patience)"));

        loadZones();
        if (enabled) {
            startRewardTask();
        }
    }

    // ------------------------------------------------------------------
    //  Zone management
    // ------------------------------------------------------------------

    /**
     * Creates an AFK zone from two corners.
     *
     * @param name zone name
     * @param pos1 first corner
     * @param pos2 opposite corner
     * @return the new zone
     */
    public AfkZone createZone(final String name, final Location pos1, final Location pos2) {
        final AfkZone zone = new AfkZone(name, pos1, pos2);
        zones.put(name.toLowerCase(Locale.ROOT), zone);
        saveZones();
        return zone;
    }

    /**
     * Deletes an AFK zone by name.
     *
     * @param name zone name
     * @return true when a zone was removed
     */
    public boolean deleteZone(final String name) {
        final boolean removed = zones.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            saveZones();
        }
        return removed;
    }

    /**
     * Returns a zone by name, supporting a partial prefix match.
     *
     * @param name zone name or prefix
     * @return the zone, or null
     */
    public AfkZone getZone(final String name) {
        final AfkZone exact = zones.get(name.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return exact;
        }
        for (final AfkZone zone : zones.values()) {
            if (zone.getName().toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT))) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Returns the first zone containing the given location.
     *
     * @param loc the location
     * @return the containing zone, or null
     */
    public AfkZone getZoneAt(final Location loc) {
        for (final AfkZone zone : zones.values()) {
            if (zone.contains(loc)) {
                return zone;
            }
        }
        return null;
    }

    public boolean zoneExists(final String name) {
        return zones.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Collection<AfkZone> getZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public int getZoneCount() {
        return zones.size();
    }

    /**
     * Returns sorted zone names for tab completion and listings.
     *
     * @return sorted names
     */
    public List<String> getZoneNames() {
        final List<String> names = new ArrayList<>();
        for (final AfkZone zone : zones.values()) {
            names.add(zone.getName());
        }
        Collections.sort(names);
        return names;
    }

    // ------------------------------------------------------------------
    //  Session access (for placeholders and commands)
    // ------------------------------------------------------------------

    /**
     * Returns the player's AFK session, or null when they are not in a zone.
     *
     * @param playerId player UUID
     * @return session or null
     */
    public Session getSession(final UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * Returns the number of players currently inside an AFK zone.
     *
     * @return session count
     */
    public int getActiveCount() {
        return sessions.size();
    }

    // ------------------------------------------------------------------
    //  Reward loop
    // ------------------------------------------------------------------

    private void startRewardTask() {
        final long period = Math.max(20L, rewardIntervalSeconds * 20L);
        rewardTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, period, period);
    }

    private void tick() {
        final long now = System.currentTimeMillis();

        // Sync sessions with current zone membership.
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final AfkZone zone = getZoneAt(player.getLocation());
            final Session session = sessions.get(player.getUniqueId());
            if (zone == null) {
                if (session != null) {
                    sessions.remove(player.getUniqueId());
                }
                continue;
            }
            if (session == null) {
                sessions.put(player.getUniqueId(), new Session(player.getUniqueId(), zone, now));
            } else if (!session.zone.getId().equals(zone.getId())) {
                session.zone = zone;
                session.lastActivity = now;
            }
        }
        // Drop sessions for players who left without a move event firing.
        sessions.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);

        // Award shards to idle players.
        for (final Session session : sessions.values()) {
            final Player player = Bukkit.getPlayer(session.playerId);
            if (player == null) {
                continue;
            }
            if (now - session.lastActivity < minIdleSeconds * 1000L) {
                continue;
            }
            if (!canEarn(session, now)) {
                continue;
            }
            session.earnTimes.addLast(now);
            session.totalEarned += shardsPerInterval;
            awardShards(player, shardsPerInterval);
        }
    }

    /**
     * Enforces the hourly cap by pruning stale earn timestamps.
     *
     * @param session the session
     * @param now     current time in ms
     * @return true when another reward is allowed
     */
    private boolean canEarn(final Session session, final long now) {
        if (maxShardsPerHour <= 0) {
            return true;
        }
        final Deque<Long> earnTimes = session.earnTimes;
        while (!earnTimes.isEmpty() && earnTimes.peekFirst() <= now - HOUR_MS) {
            earnTimes.removeFirst();
        }
        return earnTimes.size() < maxShardsPerHour;
    }

    /**
     * Hands shards to the player, dropping overflow at their feet so nothing
     * is ever lost.
     *
     * @param player the player
     * @param amount number of shards
     */
    private void awardShards(final Player player, final int amount) {
        final ItemStack shards = AfkShard.create(amount);
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(shards);
        for (final ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        if (notifyOnEarn && !earnMessage.isEmpty()) {
            player.sendMessage(Messages.deserialize(earnMessage));
        }
    }

    /**
     * Marks a player as active. Called from the activity listener whenever a
     * player in a zone moves, interacts, or is damaged.
     *
     * @param playerId player UUID
     */
    public void markActive(final UUID playerId) {
        final Session session = sessions.get(playerId);
        if (session != null) {
            session.lastActivity = System.currentTimeMillis();
        }
    }

    /**
     * Drops a player's session, e.g. on quit.
     *
     * @param playerId player UUID
     */
    public void removeSession(final UUID playerId) {
        sessions.remove(playerId);
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------

    private Path zonesFile() {
        return plugin.getDataFolder().toPath().resolve(ZONES_FILE);
    }

    private void loadZones() {
        final Path file = zonesFile();
        if (!Files.exists(file)) {
            return;
        }
        try {
            final String json = Files.readString(file);
            final Gson gson = new Gson();
            final TypeToken<List<Map<String, Object>>> type =
                    new TypeToken<List<Map<String, Object>>>() { };
            final List<Map<String, Object>> list = gson.fromJson(json, type.getType());
            if (list == null) {
                return;
            }
            for (final Map<String, Object> data : list) {
                final AfkZone zone = deserialize(data);
                if (zone != null) {
                    zones.put(zone.getName().toLowerCase(Locale.ROOT), zone);
                }
            }
            plugin.getLogger().info("Loaded " + zones.size() + " AFK zone(s).");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load AFK zones: " + e.getMessage());
        }
    }

    private void saveZones() {
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final AfkZone zone : zones.values()) {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", zone.getId().toString());
            data.put("name", zone.getName());
            data.put("world", zone.getWorldName());
            data.put("pos1", locString(zone.getPos1()));
            data.put("pos2", locString(zone.getPos2()));
            data.put("createdAt", zone.getCreatedAt());
            list.add(data);
        }
        try {
            final Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(zonesFile(), gson.toJson(list));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save AFK zones: " + e.getMessage());
        }
    }

    private AfkZone deserialize(final Map<String, Object> data) {
        try {
            final String name = (String) data.get("name");
            final Location pos1 = parseLoc((String) data.get("pos1"));
            final Location pos2 = parseLoc((String) data.get("pos2"));
            if (pos1 == null || pos2 == null) {
                return null;
            }
            final AfkZone zone = new AfkZone(name, pos1, pos2);
            if (data.get("createdAt") instanceof Number createdAt) {
                zone.setCreatedAt(createdAt.longValue());
            }
            return zone;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize AFK zone: " + e.getMessage());
            return null;
        }
    }

    private static String locString(final Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + ","
                + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location parseLoc(final String s) {
        if (s == null) {
            return null;
        }
        final String[] parts = s.split(",");
        if (parts.length < 4) {
            return null;
        }
        final org.bukkit.World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Stops the reward loop.
     */
    public void shutdown() {
        if (rewardTask != null) {
            rewardTask.cancel();
            rewardTask = null;
        }
    }

    /**
     * Whether the AFK feature is enabled in configuration.
     *
     * @return true when enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Per-player AFK state cached while inside a zone.
     */
    public static final class Session {

        private final UUID playerId;
        private AfkZone zone;
        private long lastActivity;
        private long totalEarned;
        private final Deque<Long> earnTimes = new ArrayDeque<>();

        Session(final UUID playerId, final AfkZone zone, final long now) {
            this.playerId = playerId;
            this.zone = zone;
            this.lastActivity = now;
            this.totalEarned = 0;
        }

        /**
         * Returns the zone the player currently occupies.
         *
         * @return the zone
         */
        public AfkZone getZone() {
            return zone;
        }

        /**
         * Returns the epoch millis of the player's last activity.
         *
         * @return last activity timestamp
         */
        public long getLastActivity() {
            return lastActivity;
        }

        /**
         * Returns the seconds since the player was last active.
         *
         * @return idle seconds
         */
        public long getIdleSeconds() {
            return (System.currentTimeMillis() - lastActivity) / 1000L;
        }

        /**
         * Returns the total shards earned this session.
         *
         * @return total shards
         */
        public long getTotalEarned() {
            return totalEarned;
        }
    }
}
