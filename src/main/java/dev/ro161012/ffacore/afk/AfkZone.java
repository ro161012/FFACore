package dev.ro161012.ffacore.afk;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * A designated AFK region. Players who stand idle inside the region earn
 * AFK Shards over time.
 */
public final class AfkZone {

    private final UUID id;
    private final String name;
    private final String worldName;
    private final Location pos1;
    private final Location pos2;
    private long createdAt;

    /**
     * Creates an AFK zone from two opposite corners.
     *
     * @param name zone name (unique, case-insensitive)
     * @param pos1 first corner
     * @param pos2 opposite corner
     */
    public AfkZone(final String name, final Location pos1, final Location pos2) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.worldName = pos1.getWorld().getName();
        this.pos1 = pos1.clone();
        this.pos2 = pos2.clone();
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getPos1() {
        return pos1.clone();
    }

    public Location getPos2() {
        return pos2.clone();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns whether a block location lies inside the zone's cuboid.
     *
     * @param loc the location to test
     * @return true when inside
     */
    public boolean contains(final Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        final int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        final int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        final int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        final int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        final int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        final int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    /**
     * Returns the total block volume of the zone.
     *
     * @return block count
     */
    public long getBlockCount() {
        final int dx = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        final int dy = Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
        final int dz = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        return (long) dx * dy * dz;
    }
}
