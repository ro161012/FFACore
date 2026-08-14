package dev.ro161012.ffacore.hooks;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.storage.BlockSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Optional integration with WorldEdit for fast arena save/restore.
 */
public class WorldEditHook {

    private final FFACore plugin;
    private final boolean enabled;
    private final boolean useSchematicFallback;
    private final File schematicFolder;

    public WorldEditHook(FFACore plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
        this.useSchematicFallback = plugin.getConfig().getBoolean("regeneration.world-edit.use-schematic-fallback", true);
        this.schematicFolder = resolveSchematicFolder();

        if (enabled && useSchematicFallback) {
            schematicFolder.mkdirs();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Resolves the schematic folder, treating relative config values as
     * relative to the plugin data folder.
     *
     * @return the schematic folder
     */
    private File resolveSchematicFolder() {
        final String configured = plugin.getConfig().getString(
                "regeneration.world-edit.schematic-folder", null);
        if (configured == null || configured.isBlank()) {
            return new File(plugin.getDataFolder(), "schematics");
        }
        final File candidate = new File(configured);
        return candidate.isAbsolute()
                ? candidate
                : new File(plugin.getDataFolder(), configured);
    }

    /**
     * Save arena as a WorldEdit schematic.
     */
    public boolean saveAsSchematic(Arena arena) {
        if (!enabled || !useSchematicFallback) return false;

        try {
            Location min = arena.getMinCorner();
            Location max = arena.getMaxCorner();
            if (min == null || max == null || min.getWorld() == null) return false;

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(min.getWorld());
            BlockVector3 weMin = BlockVector3.at(min.getBlockX(), min.getBlockY(), min.getBlockZ());
            BlockVector3 weMax = BlockVector3.at(max.getBlockX(), max.getBlockY(), max.getBlockZ());

            CuboidRegion region = new CuboidRegion(weWorld, weMin, weMax);

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .build()) {

                BlockVector3 origin = weMin;
                Clipboard clipboard = new BlockArrayClipboard(region);
                clipboard.setOrigin(origin);

                ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession, region, clipboard, origin);
                Operations.complete(copy);

                // Save to file
                File file = new File(schematicFolder, arena.getId().toString() + ".schem");
                try (ClipboardWriter writer = ClipboardFormats.findByFile(file).getWriter(new FileOutputStream(file))) {
                    writer.write(clipboard);
                }

                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("WorldEdit schematic save failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Restore arena from WorldEdit schematic (fast path).
     * Returns true if successful, false to fallback to standard mode.
     */
    public boolean restoreFast(Arena arena, List<BlockSnapshot> snapshots, Location origin) {
        if (!enabled) return false;

        // Try schematic-based restore first
        if (useSchematicFallback) {
            File file = new File(schematicFolder, arena.getId().toString() + ".schem");
            if (file.exists()) {
                try {
                    return restoreFromSchematic(arena, file);
                } catch (Exception e) {
                    plugin.getLogger().warning("Schematic restore failed, falling back: " + e.getMessage());
                }
            }
        }

        // Fallback: use WorldEdit's EditSession for fast block placement
        try {
            if (origin.getWorld() == null) return false;
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(origin.getWorld());

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .build()) {

                for (BlockSnapshot snap : snapshots) {
                    BlockVector3 pos = BlockVector3.at(
                            origin.getBlockX() + snap.getRelX(),
                            origin.getBlockY() + snap.getRelY(),
                            origin.getBlockZ() + snap.getRelZ());

                    BlockState weState;
                    try {
                        weState = BlockTypes.get(snap.getMaterial().name().toLowerCase()).getDefaultState();
                    } catch (Exception e) {
                        continue;
                    }
                    editSession.setBlock(pos, weState);
                }

                editSession.close();
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("WorldEdit fast restore failed: " + e.getMessage());
            return false;
        }
    }

    private boolean restoreFromSchematic(Arena arena, File schematicFile) {
        try {
            Location min = arena.getMinCorner();
            if (min == null || min.getWorld() == null) return false;

            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) return false;

            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                Clipboard clipboard = reader.read();

                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(min.getWorld());

                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(weWorld)
                        .build()) {

                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(min.getBlockX(), min.getBlockY(), min.getBlockZ()))
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                }

                return true;
            }
        } catch (IOException | com.sk89q.worldedit.WorldEditException e) {
            plugin.getLogger().warning("Schematic restore failed: " + e.getMessage());
            return false;
        }
    }
}
