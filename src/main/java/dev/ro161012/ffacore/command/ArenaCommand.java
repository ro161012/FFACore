package dev.ro161012.ffacore.command;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.arena.Arena;
import dev.ro161012.ffacore.regeneration.RegenerationManager;
import dev.ro161012.ffacore.regeneration.RegenerationMode;
import dev.ro161012.ffacore.selection.Selection;
import dev.ro161012.ffacore.util.Utils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final FFACore plugin;

    public ArenaCommand(FFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help" -> sendHelp(sender);
            case "wand" -> cmdWand(sender);
            case "create" -> cmdCreate(sender, args);
            case "list" -> cmdList(sender);
            case "info" -> cmdInfo(sender, args);
            case "setspawn" -> cmdSetSpawn(sender, args);
            case "teleport", "tp" -> cmdTeleport(sender, args);
            case "delspawn" -> cmdDelSpawn(sender, args);
            case "delete", "remove" -> cmdDelete(sender, args);
            case "resize" -> cmdResize(sender, args);
            case "regenerate", "regen" -> cmdRegenerate(sender, args);
            case "schedule" -> cmdSchedule(sender, args);
            case "preview" -> cmdPreview(sender, args);
            case "reload" -> cmdReload(sender);
            case "menu" -> cmdMenu(sender, args);
            case "settings" -> cmdSettings(sender, args);
            case "subarena", "sub" -> cmdSubArena(sender, args);
            case "perf" -> cmdPerf(sender);
            case "debug" -> cmdDebug(sender, args);
            case "cancel" -> cmdCancel(sender, args);
            case "rename" -> cmdRename(sender, args);
            case "migrate" -> cmdMigrate(sender, args);
            default -> plugin.getMessages().send(sender, "unknown-command",
                    "&cUnknown command. Type &e/" + label + " help &cfor commands.");
        }

        return true;
    }

    // ==================== HELP ====================

    private void sendHelp(CommandSender sender) {
        if (!checkPerm(sender, "ffacore.arena.use")) return;
        plugin.getMessages().sendRaw(sender, "&b&lFFACore &7Arena Command Help");
        plugin.getMessages().sendRaw(sender, "&8&m-------------------------------");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena help &7- Show this help");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena wand &7- Get selection wand");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena create <name> &7- Create arena from selection");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena list &7- List all arenas");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena info <name> &7- Arena info");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena rename <old> <new> &7- Rename an arena");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena setspawn <name> &7- Set arena spawn");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena teleport <name> &7- Teleport to arena");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena delspawn <name> &7- Delete arena spawn");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena delete <name> &7- Delete arena");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena resize <name> &7- Resize arena to new selection");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena regenerate <name> [mode] &7- Regenerate arena");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena cancel <name> &7- Cancel in-progress regen");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena schedule <name> <time> &7- Schedule auto-regen");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena preview <name> &7- Preview arena borders");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena reload &7- Reload config");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena menu [name] &7- Open arena menu");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena settings <name> &7- View/edit settings");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena subarena ... &7- Manage sub arenas");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena perf &7- Performance stats");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena debug <name> &7- Debug info");
        plugin.getMessages().sendRaw(sender, "&e/ffa arena migrate <arena|all> &7- Migrate storage");
    }

    // ==================== WAND ====================

    private void cmdWand(CommandSender sender) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.wand")) return;
        Player player = (Player) sender;
        plugin.getSelectionManager().giveWand(player);
    }

    // ==================== CREATE ====================

    private void cmdCreate(CommandSender sender, String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.create")) return;
        Player player = (Player) sender;

        if (args.length < 2) {
            plugin.getMessages().send(player, "create.usage", "&cUsage: /ffa arena create <name>");
            return;
        }

        String name = args[1];
        if (plugin.getArenaManager().arenaExists(name)) {
            plugin.getMessages().send(player, "create.exists", "&cAn arena with that name already exists!");
            return;
        }

        Selection sel = plugin.getSelectionManager().getSelection(player);
        if (sel == null || !sel.isComplete()) {
            plugin.getMessages().send(player, "create.no-selection",
                    "&cYou must make a selection first! Use &e/ffa arena wand");
            return;
        }

        long maxSize = plugin.getConfig().getLong("selection.max-selection-size", 1000000);
        if (sel.getBlockCount() > maxSize) {
            plugin.getMessages().send(player, "create.too-large",
                    "&cSelection is too large! Max: &e" + Utils.formatBytes(maxSize) + " blocks");
            return;
        }

        Arena arena = plugin.getArenaManager().createArena(
                name, sel.getPos1(), sel.getPos2(), player.getUniqueId(), player.getName());

        // Save snapshot immediately
        plugin.getRegenerationManager().saveSnapshot(arena);

        plugin.getMessages().send(player, "create.success",
                "&aArena '&e" + name + "&a' created! &7(" + Utils.formatBytes(arena.getBlockCount()) + " blocks)");
        plugin.getSelectionManager().clearSelection(player);
    }

    // ==================== LIST ====================

    private void cmdList(CommandSender sender) {
        if (!checkPerm(sender, "ffacore.arena.use")) return;

        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessages().send(sender, "list.empty", "&7No arenas found. Create one with &e/ffa arena create <name>");
            return;
        }

        plugin.getMessages().sendRaw(sender, "&b&lArenas &7(" + plugin.getArenaManager().getArenaCount() + ")");
        for (Arena arena : arenas) {
            if (arena.getParent() != null) continue; // Skip sub-arenas in top-level list
            String status = arena.isLocked() ? "&c[LOCKED]" : "&a[READY]";
            String schedule = arena.getSchedule() != null ? " &7(scheduled)" : "";
            plugin.getMessages().sendRaw(sender,
                    " &8- &e" + arena.getName() + " " + status +
                    " &7(" + Utils.formatBytes(arena.getBlockCount()) + " blocks, " +
                    arena.getRegenerationMode() + ")" + schedule);
        }
    }

    // ==================== INFO ====================

    private void cmdInfo(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.use")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "info.usage", "&cUsage: /ffa arena info <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "info.not-found", "&cArena not found: " + args[1]);
            return;
        }

        plugin.getMessages().sendRaw(sender, "&b&lArena: &e" + arena.getName());
        plugin.getMessages().sendRaw(sender, "&7ID: &f" + arena.getId().toString().substring(0, 8) + "...");
        plugin.getMessages().sendRaw(sender, "&7World: &f" + arena.getWorldName());
        plugin.getMessages().sendRaw(sender, "&7Size: &f" + Utils.formatBytes(arena.getBlockCount()) + " blocks");
        plugin.getMessages().sendRaw(sender, "&7Pos1: &f" + Utils.locationToString(arena.getPos1()));
        plugin.getMessages().sendRaw(sender, "&7Pos2: &f" + Utils.locationToString(arena.getPos2()));
        plugin.getMessages().sendRaw(sender, "&7Spawn: &f" + Utils.locationToString(arena.getSpawn()));
        plugin.getMessages().sendRaw(sender, "&7Creator: &f" + arena.getCreatorName());
        plugin.getMessages().sendRaw(sender, "&7Mode: &f" + arena.getRegenerationMode());
        plugin.getMessages().sendRaw(sender, "&7Locked: &f" + arena.isLocked());
        plugin.getMessages().sendRaw(sender, "&7Schedule: &f" + (arena.getSchedule() != null ? arena.getSchedule() : "None"));
        plugin.getMessages().sendRaw(sender, "&7Players Inside: &f" + arena.getPlayerCount());
        plugin.getMessages().sendRaw(sender, "&7Sub Arenas: &f" + arena.getSubArenas().size());

        long timeUntilNext = plugin.getScheduleManager().getTimeUntilNext(arena.getId());
        if (timeUntilNext >= 0) {
            plugin.getMessages().sendRaw(sender, "&7Next Regen: &f" + Utils.formatTime(timeUntilNext));
        }

        if (arena.getLastRegenerated() > 0) {
            long ago = (System.currentTimeMillis() - arena.getLastRegenerated()) / 1000;
            plugin.getMessages().sendRaw(sender, "&7Last Regen: &f" + Utils.formatTime(ago) + " ago");
        }

        // Show regeneration progress if active
        RegenerationManager.RegenerationProgress progress =
                plugin.getRegenerationManager().getProgress(arena.getId());
        if (progress != null) {
            plugin.getMessages().sendRaw(sender, "&7Regen Progress: &f" + progress.getPercent() + "%");
            long etaS = progress.getEtaMs() / 1000;
            if (etaS > 0) {
                plugin.getMessages().sendRaw(sender, "&7ETA: &f" + Utils.formatTime(etaS));
            }
        }
    }

    // ==================== SETSPAWN ====================

    private void cmdSetSpawn(CommandSender sender, String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.create")) return;
        Player player = (Player) sender;

        if (args.length < 2) {
            plugin.getMessages().send(player, "setspawn.usage", "&cUsage: /ffa arena setspawn <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(player, "setspawn.not-found", "&cArena not found: " + args[1]);
            return;
        }

        arena.setSpawn(player.getLocation());
        plugin.getMessages().send(player, "setspawn.success",
                "&aSpawn set for arena '&e" + arena.getName() + "&a'!");
    }

    // ==================== TELEPORT ====================

    private void cmdTeleport(CommandSender sender, String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.teleport")) return;
        Player player = (Player) sender;

        if (args.length < 2) {
            plugin.getMessages().send(player, "teleport.usage", "&cUsage: /ffa arena teleport <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(player, "teleport.not-found", "&cArena not found: " + args[1]);
            return;
        }

        Location spawn = arena.getSpawn();
        if (spawn == null) {
            // Teleport to center of arena
            Location p1 = arena.getPos1();
            Location p2 = arena.getPos2();
            if (p1 == null || p2 == null) {
                plugin.getMessages().send(player, "teleport.no-spawn",
                        "&cNo spawn set and no region defined for this arena!");
                return;
            }
            spawn = new Location(p1.getWorld(),
                    (p1.getBlockX() + p2.getBlockX()) / 2.0 + 0.5,
                    Math.max(p1.getBlockY(), p2.getBlockY()) + 2,
                    (p1.getBlockZ() + p2.getBlockZ()) / 2.0 + 0.5);
        }

        player.teleport(spawn);
        plugin.getMessages().send(player, "teleport.success",
                "&aTeleported to arena '&e" + arena.getName() + "&a'!");
    }

    // ==================== DELSPAWN ====================

    private void cmdDelSpawn(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.create")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "delspawn.usage", "&cUsage: /ffa arena delspawn <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "delspawn.not-found", "&cArena not found: " + args[1]);
            return;
        }

        arena.setSpawn(null);
        plugin.getMessages().send(sender, "delspawn.success",
                "&aSpawn removed for arena '&e" + arena.getName() + "&a'!");
    }

    // ==================== DELETE ====================

    private void cmdDelete(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.delete")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "delete.usage", "&cUsage: /ffa arena delete <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "delete.not-found", "&cArena not found: " + args[1]);
            return;
        }

        if (plugin.getRegenerationManager().isRegenerating(arena.getId())) {
            plugin.getMessages().send(sender, "delete.regen-active",
                    "&cCannot delete arena while it's being regenerated!");
            return;
        }

        String name = arena.getName();
        plugin.getScheduleManager().cancelSchedule(arena);
        plugin.getArenaStorage().deleteSnapshot(arena.getId());
        plugin.getArenaManager().deleteArena(name);

        plugin.getMessages().send(sender, "delete.success",
                "&aArena '&e" + name + "&a' deleted!");
    }

    // ==================== RESIZE ====================

    private void cmdResize(CommandSender sender, String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.create")) return;
        Player player = (Player) sender;

        if (args.length < 2) {
            plugin.getMessages().send(player, "resize.usage", "&cUsage: /ffa arena resize <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(player, "resize.not-found", "&cArena not found: " + args[1]);
            return;
        }

        Selection sel = plugin.getSelectionManager().getSelection(player);
        if (sel == null || !sel.isComplete()) {
            plugin.getMessages().send(player, "resize.no-selection",
                    "&cYou must make a new selection first! Use &e/ffa arena wand");
            return;
        }

        arena.setPos1(sel.getPos1());
        arena.setPos2(sel.getPos2());

        // Resave snapshot
        plugin.getRegenerationManager().saveSnapshot(arena);

        plugin.getMessages().send(player, "resize.success",
                "&aArena '&e" + arena.getName() + "&a' resized! &7(" +
                Utils.formatBytes(arena.getBlockCount()) + " blocks)");
        plugin.getSelectionManager().clearSelection(player);
    }

    // ==================== REGENERATE ====================

    private void cmdRegenerate(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.regenerate")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "regen.usage", "&cUsage: /ffa arena regenerate <name> [mode]");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "regen.not-found", "&cArena not found: " + args[1]);
            return;
        }

        RegenerationMode mode = arena.getRegenerationMode() != null
                ? RegenerationMode.fromString(arena.getRegenerationMode())
                : RegenerationMode.STANDARD;

        if (args.length >= 3) {
            try {
                mode = RegenerationMode.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getMessages().send(sender, "regen.invalid-mode",
                        "&cInvalid mode! Valid: STANDARD, PHASED, SELECTIVE, WAVE, WORLD_EDIT");
                return;
            }
        }

        plugin.getMessages().send(sender, "regen.starting",
                "&aRegenerating arena '&e" + arena.getName() + "&a' in &e" +
                mode.getDisplayName() + " &amode...");

        plugin.getRegenerationManager().regenerate(arena, mode).thenRun(() -> {
            plugin.getMessages().send(sender, "regen.complete",
                    "&aArena '&e" + arena.getName() + "&a' regenerated successfully!");
        });
    }

    // ==================== SCHEDULE ====================

    private void cmdSchedule(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.schedule")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "schedule.usage",
                    "&cUsage: /ffa arena schedule <name> <time|off>");
            plugin.getMessages().sendRaw(sender, "&7Time format: 30s, 5m, 2h, 1d, 1w");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "schedule.not-found", "&cArena not found: " + args[1]);
            return;
        }

        if (args.length < 3 || args[2].equalsIgnoreCase("off")) {
            plugin.getScheduleManager().cancelSchedule(arena);
            plugin.getMessages().send(sender, "schedule.cancelled",
                    "&aSchedule cancelled for '&e" + arena.getName() + "&a'!");
            return;
        }

        String timeStr = args[2];
        long seconds = dev.ro161012.ffacore.schedule.ScheduleManager.parseTime(timeStr);
        if (seconds <= 0) {
            plugin.getMessages().send(sender, "schedule.invalid",
                    "&cInvalid time format! Use: 30s, 5m, 2h, 1d, 1w");
            return;
        }

        if (plugin.getScheduleManager().schedule(arena, timeStr)) {
            plugin.getMessages().send(sender, "schedule.success",
                    "&aArena '&e" + arena.getName() + "&a' will regenerate every &e" +
                    Utils.formatTime(seconds));
        } else {
            plugin.getMessages().send(sender, "schedule.failed",
                    "&cFailed to schedule regeneration!");
        }
    }

    // ==================== PREVIEW ====================

    private void cmdPreview(CommandSender sender, String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.preview")) return;
        Player player = (Player) sender;

        if (args.length < 2) {
            plugin.getMessages().send(player, "preview.usage", "&cUsage: /ffa arena preview <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(player, "preview.not-found", "&cArena not found: " + args[1]);
            return;
        }

        plugin.getSelectionManager().startPreview(player, arena.getName());
        plugin.getMessages().send(player, "preview.success",
                "&aShowing borders for '&e" + arena.getName() + "&a' (30 seconds)");
    }

    // ==================== RELOAD ====================

    private void cmdReload(CommandSender sender) {
        if (!checkPerm(sender, "ffacore.arena.reload")) return;
        plugin.reloadConfig();
        plugin.getMessages().send(sender, "reload.success", "&aFFACore arena configuration reloaded!");
    }

    // ==================== MENU ====================

    private void cmdMenu(CommandSender sender, String[] args) {
        if (!checkPlayer(sender) || !checkPerm(sender, "ffacore.arena.menu")) return;
        Player player = (Player) sender;

        if (args.length >= 2) {
            Arena arena = plugin.getArenaManager().getArena(args[1]);
            if (arena == null) {
                plugin.getMessages().send(player, "menu.not-found", "&cArena not found: " + args[1]);
                return;
            }
            plugin.getArenaMenu().openArenaDetails(player, arena);
        } else {
            plugin.getArenaMenu().openMainMenu(player);
        }
    }

    // ==================== SETTINGS ====================

    private void cmdSettings(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.settings")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "settings.usage", "&cUsage: /ffa arena settings <name> [key] [value]");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "settings.not-found", "&cArena not found: " + args[1]);
            return;
        }

        if (args.length == 2) {
            // List settings
            plugin.getMessages().sendRaw(sender, "&bSettings for &e" + arena.getName());
            plugin.getMessages().sendRaw(sender, "&7Mode: &f" + arena.getRegenerationMode());
            plugin.getMessages().sendRaw(sender, "&7Schedule: &f" + (arena.getSchedule() != null ? arena.getSchedule() : "None"));
            plugin.getMessages().sendRaw(sender, "&7Locked: &f" + arena.isLocked());

            Map<String, Object> settings = arena.getSettings();
            if (!settings.isEmpty()) {
                for (Map.Entry<String, Object> entry : settings.entrySet()) {
                    plugin.getMessages().sendRaw(sender, "&7" + entry.getKey() + ": &f" + entry.getValue());
                }
            } else {
                plugin.getMessages().sendRaw(sender, "&7No custom settings.");
            }

            plugin.getMessages().sendRaw(sender, "&7&oUse /ffa arena settings " + arena.getName() +
                    " <mode|schedule> <value> to change");
        } else if (args.length >= 3) {
            // Edit setting
            String key = args[2].toLowerCase();
            String value = args.length >= 4 ? args[3] : "";

            switch (key) {
                case "mode" -> {
                    try {
                        RegenerationMode mode = RegenerationMode.valueOf(value.toUpperCase());
                        arena.setRegenerationMode(mode.name());
                        plugin.getMessages().send(sender, "settings.updated",
                                "&aMode set to &e" + mode.getDisplayName());
                    } catch (IllegalArgumentException e) {
                        plugin.getMessages().send(sender, "settings.invalid-mode",
                                "&cInvalid mode! Use STANDARD, PHASED, SELECTIVE, WAVE, or WORLD_EDIT");
                    }
                }
                case "schedule" -> {
                    if (value.isEmpty() || value.equalsIgnoreCase("off")) {
                        plugin.getScheduleManager().cancelSchedule(arena);
                        plugin.getMessages().send(sender, "settings.updated", "&aSchedule removed.");
                    } else {
                        if (plugin.getScheduleManager().schedule(arena, value)) {
                            plugin.getMessages().send(sender, "settings.updated",
                                    "&aSchedule set to &e" + value);
                        } else {
                            plugin.getMessages().send(sender, "settings.invalid-schedule",
                                    "&cInvalid schedule format!");
                        }
                    }
                }
                case "lock" -> {
                    arena.setLocked(Boolean.parseBoolean(value) ||
                            value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
                    plugin.getMessages().send(sender, "settings.updated",
                            "&aLocked: &e" + arena.isLocked());
                }
                default -> {
                    arena.setSetting(key, value);
                    plugin.getMessages().send(sender, "settings.updated",
                            "&aSetting '&e" + key + "&a' set to '&e" + value + "&a'");
                }
            }
        }
    }

    // ==================== SUB ARENA ====================

    private void cmdSubArena(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.subarena")) return;

        if (args.length < 3) {
            plugin.getMessages().send(sender, "subarena.usage",
                    "&cUsage: /ffa arena subarena <parent> <create|delete|list> [name]");
            return;
        }

        String parentName = args[1];
        Arena parent = plugin.getArenaManager().getArena(parentName);
        if (parent == null) {
            plugin.getMessages().send(sender, "subarena.not-found", "&cParent arena not found: " + parentName);
            return;
        }

        String action = args[2].toLowerCase();

        switch (action) {
            case "create" -> {
                if (!(sender instanceof Player)) {
                    plugin.getMessages().send(sender, "subarena.player-only", "&cOnly players can create sub-arenas!");
                    return;
                }
                Player player = (Player) sender;
                if (args.length < 4) {
                    plugin.getMessages().send(player, "subarena.usage",
                            "&cUsage: /ffa arena subarena <parent> create <name>");
                    return;
                }

                Selection sel = plugin.getSelectionManager().getSelection(player);
                if (sel == null || !sel.isComplete()) {
                    plugin.getMessages().send(player, "subarena.no-selection",
                            "&cMake a selection first with &e/ffa arena wand");
                    return;
                }

                String subName = args[3];
                if (!sel.getPos1().getWorld().getName().equals(parent.getWorldName())) {
                    plugin.getMessages().send(player, "subarena.wrong-world",
                            "&cSub-arena must be in the same world as the parent!");
                    return;
                }

                Arena sub = new Arena(subName, sel.getPos1(), sel.getPos2(),
                        player.getUniqueId(), player.getName());
                parent.addSubArena(sub);
                plugin.getArenaManager().addArena(sub);
                plugin.getRegenerationManager().saveSnapshot(sub);

                plugin.getMessages().send(player, "subarena.created",
                        "&aSub-arena '&e" + subName + "&a' added to '&e" + parent.getName() + "&a'!");
                plugin.getSelectionManager().clearSelection(player);
            }
            case "delete" -> {
                if (args.length < 4) {
                    plugin.getMessages().send(sender, "subarena.usage",
                            "&cUsage: /ffa arena subarena <parent> delete <name>");
                    return;
                }
                Arena sub = plugin.getArenaManager().getArena(args[3]);
                if (sub == null || sub.getParent() != parent) {
                    plugin.getMessages().send(sender, "subarena.not-sub",
                            "&cSub-arena not found: " + args[3]);
                    return;
                }
                parent.removeSubArena(sub);
                plugin.getArenaManager().deleteArena(sub.getName());
                plugin.getMessages().send(sender, "subarena.deleted",
                        "&aSub-arena '&e" + args[3] + "&a' removed.");
            }
            case "list" -> {
                var subs = parent.getSubArenas();
                if (subs.isEmpty()) {
                    plugin.getMessages().send(sender, "subarena.empty",
                            "&7No sub-arenas for '&e" + parent.getName() + "&7'.");
                } else {
                    plugin.getMessages().sendRaw(sender, "&bSub Arenas for &e" + parent.getName());
                    for (Arena sub : subs) {
                        plugin.getMessages().sendRaw(sender, " &8- &e" + sub.getName() +
                                " &7(" + Utils.formatBytes(sub.getBlockCount()) + " blocks)");
                    }
                }
            }
            default -> plugin.getMessages().send(sender, "subarena.unknown-action",
                    "&cUnknown action: " + action + ". Use create, delete, or list.");
        }
    }

    // ==================== PERF ====================

    private void cmdPerf(CommandSender sender) {
        if (!checkPerm(sender, "ffacore.arena.perf")) return;

        var perf = plugin.getPerformanceTracker();
        for (String line : perf.getPerformanceSummary()) {
            plugin.getMessages().sendRaw(sender, line);
        }
    }

    // ==================== DEBUG ====================

    private void cmdDebug(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.debug")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "debug.usage", "&cUsage: /ffa arena debug <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "debug.not-found", "&cArena not found: " + args[1]);
            return;
        }

        plugin.getMessages().sendRaw(sender, "&b=== Arena Debug: &e" + arena.getName() + " &b===");
        plugin.getMessages().sendRaw(sender, "&7UUID: &f" + arena.getId());
        plugin.getMessages().sendRaw(sender, "&7World: &f" + arena.getWorldName() +
                " &7(loaded: &f" + (arena.getWorld() != null) + "&7)");
        plugin.getMessages().sendRaw(sender, "&7Region: &f" + Utils.locationToString(arena.getPos1()) +
                " -> " + Utils.locationToString(arena.getPos2()));
        plugin.getMessages().sendRaw(sender, "&7Blocks: &f" + arena.getBlockCount());
        plugin.getMessages().sendRaw(sender, "&7Mode: &f" + arena.getRegenerationMode());
        plugin.getMessages().sendRaw(sender, "&7Schedule: &f" +
                (arena.getSchedule() != null ? arena.getSchedule() : "None"));
        plugin.getMessages().sendRaw(sender, "&7Is Parent: &f" + arena.hasSubArenas());
        plugin.getMessages().sendRaw(sender, "&7Is Sub: &f" + (arena.getParent() != null));
        plugin.getMessages().sendRaw(sender, "&7Locked: &f" + arena.isLocked());
        plugin.getMessages().sendRaw(sender, "&7Regenerating: &f" +
                plugin.getRegenerationManager().isRegenerating(arena.getId()));
        plugin.getMessages().sendRaw(sender, "&7Players Inside: &f" + arena.getPlayerCount());

        if (arena.getParent() != null) {
            plugin.getMessages().sendRaw(sender, "&7Parent Arena: &f" + arena.getParent().getName());
        }
        if (arena.hasSubArenas()) {
            plugin.getMessages().sendRaw(sender, "&7Sub Arenas: &f" +
                    arena.getSubArenas().stream().map(Arena::getName).collect(Collectors.joining(", ")));
        }

        // Perf stats for this arena
        var stats = plugin.getPerformanceTracker().getArenaStats(arena.getName());
        if (stats != null) {
            plugin.getMessages().sendRaw(sender, "&7--- Performance Stats ---");
            plugin.getMessages().sendRaw(sender, "&7Regenerations: &f" + stats.regenerations);
            plugin.getMessages().sendRaw(sender, "&7Blocks Restored: &f" + Utils.formatBytes(stats.totalBlocks));
            plugin.getMessages().sendRaw(sender, "&7Total Time: &f" + Utils.formatTime(stats.totalTimeMs / 1000));
            if (stats.regenerations > 0) {
                plugin.getMessages().sendRaw(sender, "&7Avg Time: &f" +
                        String.format("%.1f ms", (double) stats.totalTimeMs / stats.regenerations));
            }
            long ago = (System.currentTimeMillis() - stats.lastRegen) / 1000;
            plugin.getMessages().sendRaw(sender, "&7Last Regen: &f" + Utils.formatTime(ago) + " ago");
        }
    }

    // ==================== CANCEL ====================

    private void cmdCancel(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.regenerate")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "cancel.usage", "&cUsage: /ffa arena cancel <name>");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            plugin.getMessages().send(sender, "cancel.not-found", "&cArena not found: " + args[1]);
            return;
        }

        if (!plugin.getRegenerationManager().isRegenerating(arena.getId())) {
            plugin.getMessages().send(sender, "cancel.not-active",
                    "&cArena '&e" + arena.getName() + "&c' is not regenerating.");
            return;
        }

        plugin.getRegenerationManager().cancel(arena.getId());
        plugin.getMessages().send(sender, "cancel.success",
                "&aRegeneration cancelled for '&e" + arena.getName() + "&a'.");
    }

    // ==================== RENAME ====================

    private void cmdRename(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.create")) return;

        if (args.length < 3) {
            plugin.getMessages().send(sender, "rename.usage",
                    "&cUsage: /ffa arena rename <oldName> <newName>");
            return;
        }

        String oldName = args[1];
        String newName = args[2];

        if (!plugin.getArenaManager().arenaExists(oldName)) {
            plugin.getMessages().send(sender, "rename.not-found",
                    "&cArena not found: " + oldName);
            return;
        }

        if (plugin.getArenaManager().arenaExists(newName)) {
            plugin.getMessages().send(sender, "rename.exists",
                    "&cAn arena named '&e" + newName + "&c' already exists.");
            return;
        }

        if (plugin.getRegenerationManager().isRegenerating(
                plugin.getArenaManager().getArena(oldName).getId())) {
            plugin.getMessages().send(sender, "rename.regen-active",
                    "&cCannot rename while arena is regenerating.");
            return;
        }

        if (plugin.getArenaManager().rename(oldName, newName)) {
            plugin.getMessages().send(sender, "rename.success",
                    "&aArena '&e" + oldName + "&a' renamed to '&e" + newName + "&a'.");
        } else {
            plugin.getMessages().send(sender, "rename.failed",
                    "&cFailed to rename arena.");
        }
    }

    // ==================== MIGRATE ====================

    private void cmdMigrate(CommandSender sender, String[] args) {
        if (!checkPerm(sender, "ffacore.arena.migrate")) return;

        if (args.length < 2) {
            plugin.getMessages().send(sender, "migrate.usage", "&cUsage: /ffa arena migrate <arena|all>");
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            plugin.getMessages().send(sender, "migrate.starting",
                    "&aStarting migration of all arena snapshots...");
            plugin.getArenaManager().getArenas().forEach(arena -> {
                plugin.getRegenerationManager().saveSnapshot(arena);
            });
            plugin.getMessages().send(sender, "migrate.complete",
                    "&aMigration complete! All snapshots resaved.");
        } else {
            Arena arena = plugin.getArenaManager().getArena(args[1]);
            if (arena == null) {
                plugin.getMessages().send(sender, "migrate.not-found", "&cArena not found: " + args[1]);
                return;
            }
            plugin.getRegenerationManager().saveSnapshot(arena);
            plugin.getMessages().send(sender, "migrate.complete",
                    "&aSnapshot for '&e" + arena.getName() + "&a' migrated!");
        }
    }

    // ==================== TAB COMPLETER ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of(
                    "help", "wand", "create", "list", "info", "rename", "setspawn",
                    "teleport", "tp", "delspawn", "delete", "remove", "resize",
                    "regenerate", "regen", "cancel", "schedule", "preview",
                    "reload", "menu", "settings", "subarena", "sub",
                    "perf", "debug", "migrate"
            ), args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "info", "setspawn", "teleport", "tp", "delspawn", "delete", "remove",
                     "resize", "preview", "menu", "settings", "debug", "regenerate", "regen",
                     "cancel", "schedule" ->
                    filter(plugin.getArenaManager().getArenaNames(), args[1]);
                case "rename" ->
                    filter(plugin.getArenaManager().getArenaNames(), args[1]);
                case "subarena", "sub" ->
                    filter(plugin.getArenaManager().getArenaNames(), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("regenerate") || args[0].equalsIgnoreCase("regen")) {
                return filter(List.of("STANDARD", "PHASED", "SELECTIVE", "WAVE", "WORLD_EDIT"), args[2]);
            }
            if (args[0].equalsIgnoreCase("subarena") || args[0].equalsIgnoreCase("sub")) {
                return filter(List.of("create", "delete", "list"), args[2]);
            }
            if (args[0].equalsIgnoreCase("settings")) {
                return filter(List.of("mode", "schedule", "lock"), args[2]);
            }
            if (args[0].equalsIgnoreCase("schedule")) {
                return filter(List.of("30s", "5m", "1h", "1d", "off"), args[2]);
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("settings")) {
                if (args[2].equalsIgnoreCase("mode")) {
                    return filter(List.of("STANDARD", "PHASED", "SELECTIVE", "WAVE", "WORLD_EDIT"), args[3]);
                }
                if (args[2].equalsIgnoreCase("schedule")) {
                    return filter(List.of("30s", "5m", "1h", "1d", "off"), args[3]);
                }
                if (args[2].equalsIgnoreCase("lock")) {
                    return filter(List.of("true", "false"), args[3]);
                }
            }
        }

        return List.of();
    }

    // ==================== HELPERS ====================

    private boolean checkPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "player-only", "&cThis command can only be used by players!");
            return false;
        }
        return true;
    }

    private boolean checkPerm(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm) && !sender.hasPermission("ffacore.arena.admin")) {
            plugin.getMessages().send(sender, "no-permission", "&cYou don't have permission for that!");
            return false;
        }
        return true;
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
