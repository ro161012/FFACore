package dev.ro161012.ffacore;

import dev.ro161012.ffacore.afk.AfkCommand;
import dev.ro161012.ffacore.afk.AfkListener;
import dev.ro161012.ffacore.afk.AfkManager;
import dev.ro161012.ffacore.arena.ArenaListener;
import dev.ro161012.ffacore.arena.ArenaManager;
import dev.ro161012.ffacore.command.ArenaCommand;
import dev.ro161012.ffacore.command.FfaCommand;
import dev.ro161012.ffacore.config.ConfigMenu;
import dev.ro161012.ffacore.gui.ArenaMenu;
import dev.ro161012.ffacore.hooks.WorldEditHook;
import dev.ro161012.ffacore.killtoken.CompressedBlockListener;
import dev.ro161012.ffacore.killtoken.KillListener;
import dev.ro161012.ffacore.killtoken.KillTokenCommand;
import dev.ro161012.ffacore.killtoken.KillTokenGuiListener;
import dev.ro161012.ffacore.killtoken.KillTokenManager;
import dev.ro161012.ffacore.perf.PerformanceTracker;
import dev.ro161012.ffacore.placeholder.AfkExpansion;
import dev.ro161012.ffacore.placeholder.ArenaExpansion;
import dev.ro161012.ffacore.placeholder.KillTokenExpansion;
import dev.ro161012.ffacore.regeneration.RegenerationManager;
import dev.ro161012.ffacore.schedule.ScheduleManager;
import dev.ro161012.ffacore.selection.SelectionManager;
import dev.ro161012.ffacore.storage.ArenaStorage;
import dev.ro161012.ffacore.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FFACore: the all-in-one core for Free-For-All servers.
 *
 * <p>Combines three subsystems behind a single plugin:
 * <ul>
 *   <li><b>Arena regeneration</b> — snapshot and restore arena builds.</li>
 *   <li><b>Kill Token currency</b> — PvP kill rewards with anti-farming.</li>
 *   <li><b>AFK zones</b> — idle players earn ocean-themed AFK Shards.</li>
 * </ul>
 */
public final class FFACore extends JavaPlugin {

    private static FFACore instance;

    // Arena subsystem.
    private ArenaManager arenaManager;
    private SelectionManager selectionManager;
    private ArenaStorage arenaStorage;
    private RegenerationManager regenerationManager;
    private ScheduleManager scheduleManager;
    private ArenaMenu arenaMenu;
    private PerformanceTracker performanceTracker;
    private WorldEditHook worldEditHook;

    // Currency and AFK subsystems.
    private KillTokenManager killTokenManager;
    private AfkManager afkManager;

    private ConfigMenu configMenu;
    private Messages messages;

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        messages = new Messages(this);

        // Only load the WorldEdit integration class when WorldEdit is actually
        // present; constructing it without WorldEdit on the classpath throws a
        // NoClassDefFoundError and logs an "Error initializing plugin" warning.
        worldEditHook = getServer().getPluginManager().isPluginEnabled("WorldEdit")
                ? new WorldEditHook(this)
                : null;
        if (worldEditHook != null && worldEditHook.isEnabled()) {
            getLogger().info("WorldEdit integration enabled.");
        }
    }

    @Override
    public void onEnable() {
        performanceTracker = new PerformanceTracker(this);
        arenaStorage = new ArenaStorage(this);

        arenaManager = new ArenaManager(this);
        selectionManager = new SelectionManager(this);
        regenerationManager = new RegenerationManager(this);
        scheduleManager = new ScheduleManager(this);
        arenaMenu = new ArenaMenu(this);

        killTokenManager = new KillTokenManager(this);
        afkManager = new AfkManager(this);
        configMenu = new ConfigMenu(this);

        final ArenaCommand arenaCommand = new ArenaCommand(this);
        final KillTokenCommand killTokenCommand = new KillTokenCommand(killTokenManager);
        final AfkCommand afkCommand = new AfkCommand(this);

        registerArena();
        registerKillToken();
        registerAfk();

        final PluginCommand ffa = getCommand("ffa");
        if (ffa != null) {
            final FfaCommand executor = new FfaCommand(this, arenaCommand,
                    killTokenCommand, afkCommand);
            ffa.setExecutor(executor);
            ffa.setTabCompleter(executor);
        }

        registerExpansions();

        if (getConfig().getBoolean("general.auto-load-on-startup", true)) {
            arenaStorage.loadAllArenas(arenaManager);
        }

        getLogger().info("FFACore v" + getPluginMeta().getVersion() + " enabled.");
    }

    private void registerArena() {
        getServer().getPluginManager().registerEvents(selectionManager, this);
        getServer().getPluginManager().registerEvents(arenaMenu, this);
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
    }

    private void registerKillToken() {
        getServer().getPluginManager().registerEvents(new KillListener(killTokenManager), this);
        getServer().getPluginManager().registerEvents(new CompressedBlockListener(), this);
        getServer().getPluginManager().registerEvents(new KillTokenGuiListener(killTokenManager), this);
    }

    private void registerAfk() {
        getServer().getPluginManager().registerEvents(new AfkListener(this), this);
    }

    /**
     * Pushes the current in-memory configuration to every subsystem so edits
     * made through the dialog config menu (or a reload) take effect without
     * a server restart.
     */
    public void applyConfig() {
        messages.reload();
        killTokenManager.applyConfig();
        afkManager.applyConfig();
        regenerationManager.applyConfig();
        scheduleManager.applyConfig();
        arenaStorage.applyConfig();
        getLogger().info("Configuration applied to all FFACore subsystems.");
    }

    private void registerExpansions() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        new ArenaExpansion(this).register();
        new KillTokenExpansion(killTokenManager).register();
        new AfkExpansion(this).register();
        getLogger().info("PlaceholderAPI integrations registered.");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null && arenaStorage != null) {
            arenaStorage.saveAllArenas(arenaManager);
        }
        if (scheduleManager != null) {
            scheduleManager.shutdown();
        }
        if (regenerationManager != null) {
            regenerationManager.shutdown();
        }
        if (performanceTracker != null) {
            performanceTracker.shutdown();
        }
        if (afkManager != null) {
            afkManager.shutdown();
        }
        getLogger().info("FFACore disabled.");
    }

    public static FFACore getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public ArenaStorage getArenaStorage() {
        return arenaStorage;
    }

    public RegenerationManager getRegenerationManager() {
        return regenerationManager;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public ArenaMenu getArenaMenu() {
        return arenaMenu;
    }

    public PerformanceTracker getPerformanceTracker() {
        return performanceTracker;
    }

    public WorldEditHook getWorldEditHook() {
        return worldEditHook;
    }

    public KillTokenManager getKillTokenManager() {
        return killTokenManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public ConfigMenu getConfigMenu() {
        return configMenu;
    }

    public Messages getMessages() {
        return messages;
    }
}
