package dev.ro161012.ffacore.placeholder;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.util.Utils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArenaExpansion extends PlaceholderExpansion {

    private final FFACore plugin;

    public ArenaExpansion(FFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ffacore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ro161012";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        // Global placeholders first, e.g. %ffacore_total_arenas%.
        String global = handleGlobal(identifier);
        if (global != null) {
            return global;
        }

        // Arena-specific placeholders: %ffacore_<arena>_<placeholder>%.
        int separator = identifier.indexOf('_');
        if (separator < 0) {
            return null;
        }

        String arenaName = identifier.substring(0, separator);
        String placeholder = identifier.substring(separator + 1);

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) return null;

        return handleArenaPlaceholder(arena, placeholder, player);
    }

    private String handleGlobal(String identifier) {
        return switch (identifier.toLowerCase()) {
            case "total_arenas" -> String.valueOf(plugin.getArenaManager().getArenaCount());
            case "active_regens" -> String.valueOf(plugin.getRegenerationManager().getActiveCount());
            case "queue_size" -> String.valueOf(plugin.getRegenerationManager().getQueueSize());
            case "total_regenerations" -> String.valueOf(plugin.getPerformanceTracker().getTotalRegenerations());
            case "total_blocks_restored" -> String.valueOf(plugin.getPerformanceTracker().getTotalBlocksRestored());
            default -> null;
        };
    }

    private String handleArenaPlaceholder(Arena arena, String placeholder, Player player) {
        switch (placeholder.toLowerCase()) {
            case "status" -> {
                if (plugin.getRegenerationManager().isRegenerating(arena.getId())) return "Regenerating";
                if (arena.isLocked()) return "Locked";
                return "Ready";
            }
            case "locked" -> { return String.valueOf(arena.isLocked()); }
            case "players" -> { return String.valueOf(arena.getPlayerCount()); }
            case "size" -> { return Utils.formatBytes(arena.getBlockCount()); }
            case "block_count" -> { return String.valueOf(arena.getBlockCount()); }
            case "world" -> { return arena.getWorldName(); }
            case "creator" -> { return arena.getCreatorName(); }
            case "mode" -> { return arena.getRegenerationMode(); }
            case "schedule" -> { return arena.getSchedule() != null ? arena.getSchedule() : "None"; }
            case "next_regen" -> {
                long until = plugin.getScheduleManager().getTimeUntilNext(arena.getId());
                return until >= 0 ? Utils.formatTime(until) : "N/A";
            }
            case "next_regen_seconds" -> {
                long until = plugin.getScheduleManager().getTimeUntilNext(arena.getId());
                return until >= 0 ? String.valueOf(until) : "-1";
            }
            case "last_regen" -> {
                if (arena.getLastRegenerated() <= 0) return "Never";
                long ago = (System.currentTimeMillis() - arena.getLastRegenerated()) / 1000;
                return Utils.formatTime(ago) + " ago";
            }
            case "sub_arenas" -> { return String.valueOf(arena.getSubArenas().size()); }
            case "has_sub_arenas" -> { return String.valueOf(arena.hasSubArenas()); }
            case "is_sub_arena" -> { return String.valueOf(arena.getParent() != null); }
            case "parent" -> { return arena.getParent() != null ? arena.getParent().getName() : "None"; }
            case "id" -> { return arena.getId().toString(); }

            // Player-specific placeholders
            case "player_inside" -> {
                if (player == null) return "false";
                return String.valueOf(arena.getPlayersInside().contains(player.getUniqueId()));
            }

            default -> { return null; }
        }
    }
}
