package dev.ro161012.ffacore.arena;

import dev.ro161012.ffacore.FFACore;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {

    private final FFACore plugin;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, Arena> arenasById = new ConcurrentHashMap<>();

    public ArenaManager(FFACore plugin) {
        this.plugin = plugin;
    }

    public Arena createArena(String name, Location pos1, Location pos2, UUID creator, String creatorName) {
        Arena arena = new Arena(name, pos1, pos2, creator, creatorName);
        arenas.put(name.toLowerCase(), arena);
        arenasById.put(arena.getId(), arena);
        return arena;
    }

    public boolean deleteArena(String name) {
        Arena arena = arenas.remove(name.toLowerCase());
        if (arena != null) {
            arenasById.remove(arena.getId());
            // Remove from parent if sub-arena
            if (arena.getParent() != null) {
                arena.getParent().removeSubArena(arena);
            }
            // Remove sub-arenas
            for (Arena sub : arena.getSubArenas()) {
                arenas.remove(sub.getName().toLowerCase());
                arenasById.remove(sub.getId());
            }
            return true;
        }
        return false;
    }

    public Arena getArena(String name) {
        // First try exact name
        Arena a = arenas.get(name.toLowerCase());
        if (a != null) return a;

        // Try partial match
        for (Arena arena : arenas.values()) {
            if (arena.getName().toLowerCase().startsWith(name.toLowerCase())) {
                return arena;
            }
        }
        return null;
    }

    public Arena getArenaById(UUID id) {
        return arenasById.get(id);
    }

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public List<String> getArenaNames() {
        List<String> names = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            names.add(arena.getName());
        }
        Collections.sort(names);
        return names;
    }

    public boolean arenaExists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public Arena getArenaAt(Location location) {
        for (Arena arena : arenas.values()) {
            if (arena.contains(location)) return arena;
        }
        return null;
    }

    public void addArena(Arena arena) {
        arenas.put(arena.getName().toLowerCase(), arena);
        arenasById.put(arena.getId(), arena);
    }

    public void clear() {
        arenas.clear();
        arenasById.clear();
    }

    /**
     * Renames an arena. The old name must exist and the new name must not.
     * Returns false if the old name isn't found or the new name is taken.
     */
    public boolean rename(String oldName, String newName) {
        String oldKey = oldName.toLowerCase();
        String newKey = newName.toLowerCase();
        Arena arena = arenas.get(oldKey);
        if (arena == null) return false;
        if (!oldKey.equals(newKey) && arenas.containsKey(newKey)) return false;

        arenas.remove(oldKey);
        arena.setName(newName);
        arenas.put(newKey, arena);
        return true;
    }

    public int getArenaCount() {
        return arenas.size();
    }
}
