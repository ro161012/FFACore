package dev.ro161012.ffacore.selection;

import org.bukkit.Location;

public class Selection {

    private Location pos1;
    private Location pos2;

    public Location getPos1() { return pos1 != null ? pos1.clone() : null; }
    public void setPos1(Location pos) { this.pos1 = pos.clone(); }

    public Location getPos2() { return pos2 != null ? pos2.clone() : null; }
    public void setPos2(Location pos) { this.pos2 = pos.clone(); }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld().equals(pos2.getWorld());
    }

    public long getBlockCount() {
        if (!isComplete()) return 0;
        int dx = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int dy = Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
        int dz = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        return (long) dx * dy * dz;
    }
}
