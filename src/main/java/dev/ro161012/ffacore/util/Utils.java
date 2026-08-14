package dev.ro161012.ffacore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class Utils {

    private Utils() {}

    public static String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "none";
        return loc.getWorld().getName() + "," +
               loc.getBlockX() + "," +
               loc.getBlockY() + "," +
               loc.getBlockZ();
    }

    public static Location stringToLocation(String s) {
        if (s == null || s.equals("none")) return null;
        String[] parts = s.split(",");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            float yaw = 0, pitch = 0;
            if (parts.length >= 6) {
                yaw = Float.parseFloat(parts[4]);
                pitch = Float.parseFloat(parts[5]);
            }
            return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String locationToFullString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "none";
        return loc.getWorld().getName() + "," +
               loc.getX() + "," +
               loc.getY() + "," +
               loc.getZ() + "," +
               loc.getYaw() + "," +
               loc.getPitch();
    }

    public static String blockToString(Block block) {
        return block.getWorld().getName() + "," +
               block.getX() + "," +
               block.getY() + "," +
               block.getZ();
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) return "now";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "iB";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
