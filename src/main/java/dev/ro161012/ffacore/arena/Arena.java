package dev.ro161012.ffacore.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Arena {

    private final UUID id;
    private String name;
    private String worldName;
    private Location pos1;
    private Location pos2;
    private Location spawn;
    private UUID creator;
    private String creatorName;
    private long createdAt;
    private boolean locked;
    private String regenerationMode;
    private String schedule;
    private long lastRegenerated;

    // Sub arenas
    private Arena parent;
    private final List<Arena> subArenas = new ArrayList<>();

    // Players currently inside the arena
    private final Set<UUID> playersInside = new HashSet<>();

    // Settings map
    private final Map<String, Object> settings = new HashMap<>();

    public Arena(String name, Location pos1, Location pos2, UUID creator, String creatorName) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.worldName = pos1.getWorld().getName();
        this.pos1 = pos1.clone();
        this.pos2 = pos2.clone();
        this.creator = creator;
        this.creatorName = creatorName;
        this.createdAt = System.currentTimeMillis();
        this.locked = false;
        this.regenerationMode = "STANDARD";
        this.schedule = null;
        this.lastRegenerated = 0;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorldName() { return worldName; }
    public World getWorld() { return Bukkit.getWorld(worldName); }

    public Location getPos1() { return pos1 != null ? pos1.clone() : null; }
    public Location getPos2() { return pos2 != null ? pos2.clone() : null; }

    public void setPos1(Location pos) {
        this.pos1 = pos.clone();
        this.worldName = pos.getWorld().getName();
    }

    public void setPos2(Location pos) {
        this.pos2 = pos.clone();
    }

    public void setWorldName(String name) { this.worldName = name; }

    public Location getSpawn() { return spawn != null ? spawn.clone() : null; }
    public void setSpawn(Location spawn) { this.spawn = spawn != null ? spawn.clone() : null; }

    public UUID getCreator() { return creator; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String name) { this.creatorName = name; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long time) { this.createdAt = time; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getRegenerationMode() { return regenerationMode; }
    public void setRegenerationMode(String mode) { this.regenerationMode = mode; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public long getLastRegenerated() { return lastRegenerated; }
    public void setLastRegenerated(long time) { this.lastRegenerated = time; }

    // Sub-arena management
    public Arena getParent() { return parent; }
    public void setParent(Arena parent) { this.parent = parent; }

    public List<Arena> getSubArenas() { return Collections.unmodifiableList(subArenas); }
    public void addSubArena(Arena sub) {
        sub.setParent(this);
        subArenas.add(sub);
    }
    public void removeSubArena(Arena sub) {
        sub.setParent(null);
        subArenas.remove(sub);
    }
    public boolean hasSubArenas() { return !subArenas.isEmpty(); }

    // Player tracking
    public Set<UUID> getPlayersInside() { return playersInside; }
    public void addPlayer(Player player) { playersInside.add(player.getUniqueId()); }
    public void removePlayer(Player player) { playersInside.remove(player.getUniqueId()); }
    public int getPlayerCount() { return playersInside.size(); }
    public boolean hasPlayers() { return !playersInside.isEmpty(); }

    // Settings
    public Map<String, Object> getSettings() { return settings; }
    public Object getSetting(String key) { return settings.get(key); }
    public void setSetting(String key, Object value) { settings.put(key, value); }

    /**
     * Check if a block location is inside the arena region.
     */
    public boolean contains(Location loc) {
        if (pos1 == null || pos2 == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
            && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
            && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    /**
     * Get the total block count of this arena.
     */
    public long getBlockCount() {
        if (pos1 == null || pos2 == null) return 0;
        int dx = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int dy = Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
        int dz = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        return (long) dx * dy * dz;
    }

    /**
     * Get the minimum corner.
     */
    public Location getMinCorner() {
        if (pos1 == null || pos2 == null) return null;
        return new Location(
            pos1.getWorld(),
            Math.min(pos1.getBlockX(), pos2.getBlockX()),
            Math.min(pos1.getBlockY(), pos2.getBlockY()),
            Math.min(pos1.getBlockZ(), pos2.getBlockZ())
        );
    }

    /**
     * Get the maximum corner.
     */
    public Location getMaxCorner() {
        if (pos1 == null || pos2 == null) return null;
        return new Location(
            pos1.getWorld(),
            Math.max(pos1.getBlockX(), pos2.getBlockX()),
            Math.max(pos1.getBlockY(), pos2.getBlockY()),
            Math.max(pos1.getBlockZ(), pos2.getBlockZ())
        );
    }

    @Override
    public String toString() {
        return "Arena{name=" + name + ", world=" + worldName +
               ", size=" + getBlockCount() + ", mode=" + regenerationMode + "}";
    }
}
