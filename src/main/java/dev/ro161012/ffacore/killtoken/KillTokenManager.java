package dev.ro161012.ffacore.killtoken;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Main class of the KillToken plugin.
 *
 * <p>KillToken drops a configurable currency item whenever a player kills
 * another player, and applies a pair-based cooldown to prevent token farming
 * between the same two players.
 *
 * <p>All configuration-derived values are resolved once when the config is
 * loaded or reloaded and cached in fields, so hot paths (death events,
 * commands) never re-parse YAML, enum names or color codes.
 */
public final class KillTokenManager {

    private final FFACore plugin;

    /** Configuration path of the serialized currency item. */
    public static final String CURRENCY_PATH = "currency-item";

    private static final String DEFAULT_TOKEN_NAME = "Kill Token";
    private static final String DEFAULT_TOKEN_LORE = "Awarded for killing another player.";
    private static final String LEGACY_DEFAULT_TOKEN_LORE = "Awarded for slaying another player.";
    private static final String DEFAULT_KILLSTREAK_MESSAGE = "&4%player% &7is on a &c&l%streak% &7killstreak!";
    private static final String LEGACY_KILLSTREAK_MESSAGE = "&6Killstreak&8: &f%streak%";
    private static final String PREVIOUS_NEUTRAL_KILLSTREAK_MESSAGE = "&c%player% &7is on a &6%streak% &7killstreak!";
    private static final String PREVIOUS_WARM_KILLSTREAK_MESSAGE = "&c%player% &6is on a &e%streak% &ekillstreak!";
    private static final int MAX_KILLSTREAK_TOKEN_MULTIPLIER = 5;

    private PairCooldown pairCooldown;
    private KillstreakTracker killstreakTracker;
    private CompressedBlockManager compressedBlocks;
    private ItemStack currencyItem;

    // Cached configuration values (refreshed by refreshConfigCache()).
    private int tokensPerKill;
    private long cooldownSeconds;
    private boolean notifyOnCooldown;
    private String cooldownMessage;
    private String killMessage;
    private boolean killstreakEnabled;
    private String killstreakMessage;
    private Sound killstreakSound;
    private int killstreakAnnouncementMinimum;
    private int killstreakRewardStart;
    private int killstreakRewardStep;
    private int killstreakMaxTokenMultiplier;

    /**
     * Creates the Kill Token subsystem. Every configuration value is resolved
     * once and cached, then the currency item templates are built.
     *
     * @param plugin owning FFACore plugin
     */
    public KillTokenManager(final FFACore plugin) {
        this.plugin = plugin;
        refreshConfigCache();
        this.pairCooldown = new PairCooldown(cooldownSeconds);
        this.killstreakTracker = new KillstreakTracker(this);
        this.compressedBlocks = new CompressedBlockManager(this);
        loadCurrencyItem();
        compressedBlocks.refresh();
    }

    /**
     * Reloads configuration values from disk. The currency item is re-read
     * and the cached configuration values are refreshed.
     */
    public void reload() {
        reloadConfig();
        applyConfig();
        getLogger().info("Configuration reloaded.");
    }

    /**
     * Re-applies the current in-memory configuration to runtime state: the
     * currency item, the cached configuration values, the pair cooldown
     * duration and the compressed block template are refreshed.
     */
    public void applyConfig() {
        loadCurrencyItem();
        refreshConfigCache();
        pairCooldown.setCooldownSeconds(cooldownSeconds);
        compressedBlocks.refresh();
    }

    /**
     * Loads the currency item from {@code config.yml}, seeding the default
     * Nether Star token on first startup.
     *
     * <p>The item is persisted as base64-encoded bytes so the gradient tooltip
     * components (custom font and unicode glyphs) survive a config round-trip
     * exactly.
     */
    private void loadCurrencyItem() {
        final FileConfiguration config = getConfig();
        final String encoded = config.getString(CURRENCY_PATH);
        if (encoded != null && !encoded.isEmpty()) {
            final ItemStack stored = decodeItem(encoded);
            if (stored != null) {
                if (isLegacyDefaultToken(stored)) {
                    this.currencyItem = createDefaultToken();
                    config.set(CURRENCY_PATH, encodeItem(currencyItem));
                    saveConfig();
                    getLogger().info("Updated the default Kill Token lore.");
                } else {
                    this.currencyItem = stored;
                }
                return;
            }
        }
        this.currencyItem = createDefaultToken();
        config.set(CURRENCY_PATH, encodeItem(currencyItem));
        saveConfig();
        getLogger().fine("Seeded default currency item (NETHER_STAR).");
    }

    /**
     * Encodes an item as a base64 string for storage.
     *
     * @param stack the item
     * @return base64 bytes
     */
    private static String encodeItem(final ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    /**
     * Decodes a base64 item, returning null on malformed data.
     *
     * @param encoded base64 bytes
     * @return the item, or null
     */
    private static ItemStack decodeItem(final String encoded) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reads every config value used at runtime into fields, so hot paths
     * only touch fields. Called on enable and on every reload.
     */
    private void refreshConfigCache() {
        final FileConfiguration config = getConfig();
        migrateKillstreakConfig(config);

        this.tokensPerKill = Math.max(1, config.getInt("tokens-per-kill", 1));
        this.cooldownSeconds = config.getLong("cooldown-seconds", 60L);
        this.notifyOnCooldown = config.getBoolean("notify-on-cooldown", true);
        this.cooldownMessage = color(config.getString("cooldown-message",
                "&cNo Kill Token dropped - you and this player are on cooldown."));
        this.killMessage = color(config.getString("kill-message", "&4&l+1 &cKill Token"));

        this.killstreakEnabled = config.getBoolean("killstreak.enabled", true);
        this.killstreakMessage = color(config.getString("killstreak.message",
                DEFAULT_KILLSTREAK_MESSAGE));
        final String soundName = config.getString(
                "killstreak.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            this.killstreakSound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            this.killstreakSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        this.killstreakAnnouncementMinimum = Math.max(1,
                config.getInt("killstreak.announcement-minimum", 2));
        this.killstreakRewardStart = Math.max(1, config.getInt("killstreak.reward-start", 3));
        this.killstreakRewardStep = Math.max(1, config.getInt("killstreak.reward-step", 3));
        this.killstreakMaxTokenMultiplier = Math.min(MAX_KILLSTREAK_TOKEN_MULTIPLIER,
                Math.max(1, config.getInt("killstreak.max-token-multiplier", 5)));
    }

    /**
     * Updates the old stock action-bar template and adds streak multiplier
     * settings to existing server configurations. Administrator-customized
     * messages are preserved.
     *
     * @param config current plugin configuration
     */
    private void migrateKillstreakConfig(final FileConfiguration config) {
        boolean changed = false;
        final String configuredMessage = config.getString("killstreak.message");
        if (LEGACY_KILLSTREAK_MESSAGE.equals(configuredMessage)
                || PREVIOUS_NEUTRAL_KILLSTREAK_MESSAGE.equals(configuredMessage)
                || PREVIOUS_WARM_KILLSTREAK_MESSAGE.equals(configuredMessage)) {
            config.set("killstreak.message", DEFAULT_KILLSTREAK_MESSAGE);
            changed = true;
        }
        if (!config.contains("killstreak.announcement-minimum")) {
            config.set("killstreak.announcement-minimum", 2);
            changed = true;
        }
        if (!config.contains("killstreak.reward-start")) {
            config.set("killstreak.reward-start", 3);
            changed = true;
        }
        if (!config.contains("killstreak.reward-step")) {
            config.set("killstreak.reward-step", config.getInt("killstreak.reward-every", 3));
            changed = true;
        }
        if (!config.contains("killstreak.max-token-multiplier")) {
            config.set("killstreak.max-token-multiplier", 5);
            changed = true;
        }
        if (changed) {
            saveConfig();
        }
    }

    /**
     * Creates the default token: a Nether Star with an ember gradient name,
     * the ember tooltip style, and the {@code ffacore:kill_token} tag. It
     * renders as the vanilla Nether Star item.
     *
     * @return the default currency item
     */
    private ItemStack createDefaultToken() {
        final ItemStack stack = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ItemUtils.emberTitle(DEFAULT_TOKEN_NAME));
            meta.lore(List.of(
                    Component.text(DEFAULT_TOKEN_LORE, NamedTextColor.GRAY)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        ItemUtils.tag(stack, ItemUtils.KILL_TOKEN_KEY);
        ItemUtils.applyTooltipStyle(stack, ItemUtils.EMBER_TOOLTIP);
        return stack;
    }

    /**
     * Returns whether an item is the v1.2.2-or-earlier default token.
     *
     * <p>Only this exact stock item is migrated. Administrator-created
     * currency items, including custom Nether Stars, remain untouched.
     *
     * @param stack item read from configuration
     * @return {@code true} when the item has the old default lore
     */
    private boolean isLegacyDefaultToken(ItemStack stack) {
        if (stack.getType() != Material.NETHER_STAR || !stack.hasItemMeta()) {
            return false;
        }

        final ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore() || meta.getLore() == null
                || meta.getLore().size() != 1) {
            return false;
        }

        return DEFAULT_TOKEN_NAME.equals(ChatColor.stripColor(meta.getDisplayName()))
                && LEGACY_DEFAULT_TOKEN_LORE.equals(ChatColor.stripColor(meta.getLore().get(0)));
    }

    /**
     * Creates a fresh copy of the currency item with the configured drop
     * amount, ready to be spawned into a world.
     *
     * @return the token item stack to drop
     */
    public ItemStack createToken() {
        return createToken(tokensPerKill);
    }

    /**
     * Creates a fresh copy of the currency item with the given amount.
     *
     * @param amount stack size of the returned item (clamped to at least 1)
     * @return the token item stack
     */
    public ItemStack createToken(final int amount) {
        final ItemStack stack = currencyItem.clone();
        stack.setAmount(Math.max(1, amount));
        return stack;
    }

    /**
     * Replaces the currency item and persists it to {@code config.yml}.
     *
     * @param stack the item to use as the new currency (amount is normalised to 1)
     */
    public void setCurrencyItem(final ItemStack stack) {
        final ItemStack copy = stack.clone();
        copy.setAmount(1);
        this.currencyItem = copy;
        getConfig().set(CURRENCY_PATH, encodeItem(copy));
        saveConfig();
    }

    /**
     * Returns a defensive copy of the current currency item.
     *
     * @return copy of the currency item
     */
    public ItemStack getCurrencyItem() {
        return currencyItem.clone();
    }

    /**
     * Returns the pair cooldown tracker.
     *
     * @return the cooldown tracker
     */
    public PairCooldown getPairCooldown() {
        return pairCooldown;
    }

    /**
     * Returns the killstreak tracker.
     *
     * @return the killstreak tracker
     */
    public KillstreakTracker getKillstreakTracker() {
        return killstreakTracker;
    }

    /**
     * Returns the compressed block manager.
     *
     * @return the compressed block manager
     */
    public CompressedBlockManager getCompressedBlockManager() {
        return compressedBlocks;
    }

    /**
     * Whether killstreak chat announcements, personal sounds, and token
     * multipliers are enabled.
     *
     * @return true if enabled
     */
    public boolean killstreakEnabled() {
        return killstreakEnabled;
    }

    /**
     * Returns the colourised killstreak chat message with the {@code %player%}
     * and {@code %streak%} placeholders.
     *
     * @return message template
     */
    public String getKillstreakMessage() {
        return killstreakMessage;
    }

    /**
     * Returns the configured killstreak sound, resolved once at config load.
     * The sound is always played at Minecraft's normal pitch of 1.0.
     *
     * @return the sound to play
     */
    public Sound getKillstreakSound() {
        return killstreakSound;
    }

    /**
     * Returns whether a streak should be announced in chat and with a sound.
     *
     * @param streak current streak length
     * @return true when announcements are enabled at this streak length
     */
    public boolean shouldAnnounceKillstreak(final int streak) {
        return killstreakEnabled && streak >= killstreakAnnouncementMinimum;
    }

    /**
     * Returns the Kill Token multiplier for a streak. The multiplier starts
     * at two on the configured reward-start streak and increases by one every
     * reward-step kills, capped at the configured maximum.
     *
     * @param streak current streak length
     * @return multiplier for the normal token drop, at least one
     */
    public int getKillstreakTokenMultiplier(final int streak) {
        if (!killstreakEnabled || streak < killstreakRewardStart) {
            return 1;
        }

        final int multiplier = 2 + (streak - killstreakRewardStart) / killstreakRewardStep;
        return Math.min(killstreakMaxTokenMultiplier, multiplier);
    }

    /**
     * Returns the token amount for a qualifying kill at the supplied streak.
     *
     * @param streak current streak length
     * @return normal configured drop amount multiplied by the streak multiplier
     */
    public int getKillstreakTokenAmount(final int streak) {
        return tokensPerKill * getKillstreakTokenMultiplier(streak);
    }

    /**
     * Runs a safe preview of the chat announcements and multiplier drop for
     * an administrator. It does not change a real streak or pair cooldown.
     *
     * @param player administrator running the preview
     * @return false when killstreaks are disabled
     */
    public boolean runKillstreakTest(final Player player) {
        if (!killstreakEnabled) {
            return false;
        }

        killstreakTracker.preview(player, killstreakAnnouncementMinimum);
        if (killstreakRewardStart != killstreakAnnouncementMinimum) {
            killstreakTracker.preview(player, killstreakRewardStart);
        }
        player.getWorld().dropItemNaturally(player.getLocation(),
                createToken(getKillstreakTokenAmount(killstreakRewardStart)));
        return true;
    }

    /**
     * Returns the minimum streak announced to chat.
     *
     * @return announcement minimum, always at least one
     */
    public int getKillstreakAnnouncementMinimum() {
        return killstreakAnnouncementMinimum;
    }

    /**
     * Returns the streak where token multiplication starts.
     *
     * @return reward start, always at least one
     */
    public int getKillstreakRewardStart() {
        return killstreakRewardStart;
    }

    /**
     * Returns the configured pair cooldown length in seconds.
     *
     * @return cooldown length in seconds
     */
    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    /**
     * Returns the configured number of tokens dropped per qualifying kill.
     *
     * @return tokens per kill (always at least 1)
     */
    public int getTokensPerKill() {
        return tokensPerKill;
    }

    /**
     * Whether the killer should be notified when a drop is suppressed by the
     * pair cooldown.
     *
     * @return true if the notification is enabled
     */
    public boolean notifyOnCooldown() {
        return notifyOnCooldown;
    }

    /**
     * Returns the colourised cooldown notification message.
     *
     * @return cooldown message
     */
    public String getCooldownMessage() {
        return cooldownMessage;
    }

    /**
     * Returns the colourised message sent to the killer on a successful drop.
     * May be empty if disabled in the configuration.
     *
     * @return kill message
     */
    public String getKillMessage() {
        return killMessage;
    }

    /**
     * Returns the owning FFACore plugin.
     *
     * @return owning plugin
     */
    public FFACore getPlugin() {
        return plugin;
    }

    /**
     * Returns the plugin version string, used by the PlaceholderAPI expansion.
     *
     * @return plugin version
     */
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * Delegates to the owning plugin so downstream components can reach the
     * shared configuration, logger and server without holding a JavaPlugin.
     *
     * @return the plugin configuration
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * Persists the owning plugin's configuration to disk.
     */
    public void saveConfig() {
        plugin.saveConfig();
    }

    /**
     * Reloads the owning plugin's configuration from disk.
     */
    public void reloadConfig() {
        plugin.reloadConfig();
    }

    /**
     * Returns the owning plugin's logger.
     *
     * @return plugin logger
     */
    public java.util.logging.Logger getLogger() {
        return plugin.getLogger();
    }

    /**
     * Returns the owning server instance.
     *
     * @return server
     */
    public org.bukkit.Server getServer() {
        return plugin.getServer();
    }

    /**
     * Translates {@code &}-style colour codes in the given string.
     *
     * @param value raw string, may be null
     * @return colourised string, never null
     */
    public static String color(final String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
