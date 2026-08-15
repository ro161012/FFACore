package dev.ro161012.ffacore.kokushibo;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.nichirin.NichirinCooldown;
import dev.ro161012.ffacore.weapon.AbilityBossBars;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the Kokoshibos Sword abilities.
 *
 * <ul>
 *   <li><b>Upper Moon One</b> (passive): while holding the sword, periodically
 *       empower the wielder.</li>
 *   <li><b>Catastrophe, Tenman Crescent Moon</b> (offhand): an omni-directional
 *       vortex of crescent blades expands outward around the caster, dealing
 *       true damage to everything it sweeps.</li>
 *   <li><b>Moonbow, Half Moon</b> (offhand + crouch): six crescents strike in
 *       a line ahead, dealing true damage.</li>
 * </ul>
 */
public final class KokushiboAbilityListener implements Listener {

    private final FFACore plugin;
    private final AbilityBossBars bossBars;

    private final Set<UUID> trueDamageTargets = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> nextUpperMoonGrant = new ConcurrentHashMap<>();

    private final NichirinCooldown catastropheCooldown;
    private final NichirinCooldown moonbowCooldown;

    // Cached configuration (refreshed by applyConfig()).
    private long passiveIntervalMillis;
    private int buffDurationSeconds;
    private int strengthAmplifier;
    private int speedAmplifier;
    private double catastropheDamageHearts;
    private double catastropheMaxRadius;
    private int catastropheCrescents;
    private int moonbowCrescents;
    private double moonbowSpacing;
    private double strikeRadius;
    private double moonbowDamageHearts;

    /**
     * Creates the listener and loads configuration.
     *
     * @param plugin owning FFACore plugin
     */
    public KokushiboAbilityListener(final FFACore plugin) {
        this.plugin = plugin;
        this.bossBars = new AbilityBossBars(plugin);
        this.catastropheCooldown = new NichirinCooldown(70);
        this.moonbowCooldown = new NichirinCooldown(80);
        applyConfig();
        startPassiveTask();
    }

    /**
     * Re-reads the kokushibo configuration and pushes it to the trackers.
     */
    public void applyConfig() {
        final FileConfiguration config = plugin.getConfig();
        passiveIntervalMillis = Math.max(1, config.getInt(
                "kokushibo.upper-moon-one.interval-seconds", 10)) * 1000L;
        buffDurationSeconds = Math.max(1, config.getInt(
                "kokushibo.upper-moon-one.buff-duration-seconds", 6));
        strengthAmplifier = Math.max(0, config.getInt(
                "kokushibo.upper-moon-one.strength-amplifier", 0));
        speedAmplifier = Math.max(0, config.getInt(
                "kokushibo.upper-moon-one.speed-amplifier", 0));
        catastropheDamageHearts = config.getDouble(
                "kokushibo.catastrophe.damage-hearts", 3.0);
        catastropheMaxRadius = Math.max(2.0, config.getDouble(
                "kokushibo.catastrophe.max-radius", 9.0));
        catastropheCrescents = Math.max(4, config.getInt(
                "kokushibo.catastrophe.crescents", 12));
        moonbowCrescents = Math.max(1, config.getInt("kokushibo.moonbow.crescents", 6));
        moonbowSpacing = Math.max(0.5, config.getDouble("kokushibo.moonbow.spacing", 1.6));
        strikeRadius = Math.max(0.5, config.getDouble("kokushibo.moonbow.strike-radius", 1.2));
        moonbowDamageHearts = config.getDouble("kokushibo.moonbow.damage-hearts", 3.0);

        catastropheCooldown.setCooldownSeconds(
                config.getInt("kokushibo.catastrophe.cooldown-seconds", 70));
        moonbowCooldown.setCooldownSeconds(
                config.getInt("kokushibo.moonbow.cooldown-seconds", 80));
    }

    /**
     * Zeroes armour, protection and resistance reductions so ability hits
     * land as true damage.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAbilityDamage(final EntityDamageByEntityEvent event) {
        if (!trueDamageTargets.contains(event.getEntity().getUniqueId())) {
            return;
        }
        event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
        event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
        event.setDamage(EntityDamageEvent.DamageModifier.RESISTANCE, 0);
    }

    /**
     * Triggers an ability when the player presses the swap-hands key while
     * holding the sword in either hand. The swap is cancelled so the sword
     * never moves between hands; crouching casts Moonbow, otherwise
     * Catastrophe.
     */
    @EventHandler
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();
        final boolean inMainHand = KokushiboSword.isKokushiboSword(
                player.getInventory().getItemInMainHand());
        final boolean inOffHand = KokushiboSword.isKokushiboSword(
                player.getInventory().getItemInOffHand());
        if (!inMainHand && !inOffHand) {
            return;
        }
        // The sword never moves between hands via the swap key: it can only
        // be placed in a hand manually. Swapping is the ability trigger and
        // works whether the sword is in the main hand or the offhand.
        event.setCancelled(true);
        if (player.isSneaking()) {
            castMoonbow(player);
        } else {
            castCatastrophe(player);
        }
    }

    /**
     * Cleans up a leaver's state.
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        catastropheCooldown.clear(id);
        moonbowCooldown.clear(id);
        nextUpperMoonGrant.remove(id);
        bossBars.clear(id);
    }

    /**
     * Removes every active cooldown bar (called on plugin disable).
     */
    public void close() {
        bossBars.close();
    }

    /**
     * Clears every active cooldown, the passive timer and cooldown bars for a
     * player.
     *
     * @param id the player id
     */
    public void resetCooldowns(final UUID id) {
        catastropheCooldown.clear(id);
        moonbowCooldown.clear(id);
        nextUpperMoonGrant.remove(id);
        bossBars.clear(id);
    }

    private void castCatastrophe(final Player player) {
        final UUID id = player.getUniqueId();
        if (catastropheCooldown.isOnCooldown(id)) {
            return;
        }
        catastropheCooldown.apply(id);
        bossBars.start(player, "catastrophe", "Catastrophe",
                BarColor.PURPLE, catastropheCooldown.getCooldownMillis());
        KokushiboEffects.playCatastrophe(plugin, player, catastropheMaxRadius,
                catastropheCrescents,
                living -> applyTrueDamage(living, player, catastropheDamageHearts));
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
    }

    private void castMoonbow(final Player player) {
        final UUID id = player.getUniqueId();
        if (moonbowCooldown.isOnCooldown(id)) {
            return;
        }
        moonbowCooldown.apply(id);
        bossBars.start(player, "moonbow", "Moonbow, Half Moon",
                BarColor.PURPLE, moonbowCooldown.getCooldownMillis());

        final Location eye = player.getEyeLocation();
        final Vector facing = eye.getDirection();
        for (int i = 0; i < moonbowCrescents; i++) {
            final Location strike = eye.clone().add(
                    facing.clone().multiply(1.2 + i * moonbowSpacing)).add(0, -0.5, 0);
            KokushiboEffects.strikeCrescent(plugin, strike, i * 3L);
            strikeMoonbowAt(player, strike, i * 3L);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
    }

    /**
     * Deals true damage to living entities within the strike point.
     */
    private void strikeMoonbowAt(final Player player, final Location strike,
                                 final long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (final org.bukkit.entity.Entity entity : strike.getWorld()
                    .getNearbyEntities(strike, strikeRadius, strikeRadius, strikeRadius)) {
                if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                    continue;
                }
                applyTrueDamage(living, player, moonbowDamageHearts);
            }
        }, delayTicks);
    }

    /**
     * Deals hearts of damage that ignores armour, resistance and protection.
     */
    private void applyTrueDamage(final LivingEntity target, final Player source,
                                 final double hearts) {
        trueDamageTargets.add(target.getUniqueId());
        try {
            target.damage(hearts * 2.0, source);
        } finally {
            trueDamageTargets.remove(target.getUniqueId());
        }
    }

    /**
     * Periodically empowers sword holders with the Upper Moon One buff.
     */
    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                final long now = System.currentTimeMillis();
                for (final Player player : plugin.getServer().getOnlinePlayers()) {
                    final UUID id = player.getUniqueId();
                    if (!isHoldingSword(player)) {
                        continue;
                    }
                    if (now < nextUpperMoonGrant.getOrDefault(id, 0L)) {
                        continue;
                    }
                    nextUpperMoonGrant.put(id, now + passiveIntervalMillis);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                            buffDurationSeconds * 20, strengthAmplifier, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                            buffDurationSeconds * 20, speedAmplifier, false, true, true));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private static boolean isHoldingSword(final Player player) {
        return KokushiboSword.isKokushiboSword(player.getInventory().getItemInMainHand())
                || KokushiboSword.isKokushiboSword(player.getInventory().getItemInOffHand());
    }
}
