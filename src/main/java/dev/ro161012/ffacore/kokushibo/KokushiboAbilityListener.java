package dev.ro161012.ffacore.kokushibo;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.nichirin.NichirinCooldown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
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
 *   <li><b>Crescent Throw</b> (offhand): open a Lunar Eclipse window; left
 *       clicks during the window fire a slowing crescent star.</li>
 *   <li><b>Moonbow, Half Moon</b> (offhand + crouch): six crescents strike in
 *       a line ahead, dealing true damage.</li>
 * </ul>
 */
public final class KokushiboAbilityListener implements Listener {

    private final FFACore plugin;

    private final Set<UUID> trueDamageTargets = ConcurrentHashMap.newKeySet();
    private final Set<UUID> crescentSnowballs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lunarEclipseUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextCrescentFire = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextUpperMoonGrant = new ConcurrentHashMap<>();

    private final NichirinCooldown crescentCooldown;
    private final NichirinCooldown moonbowCooldown;

    // Cached configuration (refreshed by applyConfig()).
    private long passiveIntervalMillis;
    private int buffDurationSeconds;
    private int strengthAmplifier;
    private int speedAmplifier;
    private long windowMillis;
    private long fireIntervalMillis;
    private int slowSeconds;
    private int slowAmplifier;
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
        this.crescentCooldown = new NichirinCooldown(70);
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
        windowMillis = Math.max(1, config.getInt(
                "kokushibo.crescent-throw.window-seconds", 10)) * 1000L;
        fireIntervalMillis = Math.max(100, config.getInt(
                "kokushibo.crescent-throw.fire-interval-ms", 1000));
        slowSeconds = Math.max(1, config.getInt(
                "kokushibo.crescent-throw.slow-seconds", 4));
        slowAmplifier = Math.max(0, config.getInt(
                "kokushibo.crescent-throw.slow-amplifier", 1));
        moonbowCrescents = Math.max(1, config.getInt("kokushibo.moonbow.crescents", 6));
        moonbowSpacing = Math.max(0.5, config.getDouble("kokushibo.moonbow.spacing", 1.6));
        strikeRadius = Math.max(0.5, config.getDouble("kokushibo.moonbow.strike-radius", 1.2));
        moonbowDamageHearts = config.getDouble("kokushibo.moonbow.damage-hearts", 3.0);

        crescentCooldown.setCooldownSeconds(
                config.getInt("kokushibo.crescent-throw.cooldown-seconds", 70));
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
     * Triggers an ability when the player swaps hands with the sword in the
     * offhand. Crouching casts Moonbow; otherwise Crescent Throw.
     */
    @EventHandler
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();
        if (!KokushiboSword.isKokushiboSword(player.getInventory().getItemInOffHand())) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            castMoonbow(player);
        } else {
            castCrescentThrow(player);
        }
    }

    /**
     * During the Lunar Eclipse window, each arm swing fires a slowing star
     * (rate-limited to one per second).
     */
    @EventHandler
    public void onArmSwing(final PlayerAnimationEvent event) {
        final Player player = event.getPlayer();
        final UUID id = player.getUniqueId();
        final Long until = lunarEclipseUntil.get(id);
        if (until == null) {
            return;
        }
        if (until <= System.currentTimeMillis()) {
            lunarEclipseUntil.remove(id, until);
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextCrescentFire.getOrDefault(id, 0L)) {
            return;
        }
        nextCrescentFire.put(id, now + fireIntervalMillis);
        crescentSnowballs.add(KokushiboEffects.fireCrescent(plugin, player));
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.8f);
    }

    /**
     * Slows any entity struck by a crescent star.
     */
    @EventHandler
    public void onCrescentHit(final ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !crescentSnowballs.remove(snowball.getUniqueId())) {
            return;
        }
        snowball.getPassengers().forEach(org.bukkit.entity.Entity::remove);
        if (event.getHitEntity() instanceof LivingEntity living) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    slowSeconds * 20, slowAmplifier, false, true, true));
        }
    }

    /**
     * Cleans up a leaver's state.
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        crescentCooldown.clear(id);
        moonbowCooldown.clear(id);
        lunarEclipseUntil.remove(id);
        nextCrescentFire.remove(id);
        nextUpperMoonGrant.remove(id);
    }

    private void castCrescentThrow(final Player player) {
        final UUID id = player.getUniqueId();
        if (crescentCooldown.isOnCooldown(id)) {
            sendCooldown(player, crescentCooldown.remainingMillis(id));
            return;
        }
        crescentCooldown.apply(id);
        lunarEclipseUntil.put(id, System.currentTimeMillis() + windowMillis);
        KokushiboEffects.playEclipseBurst(plugin, player);
        player.sendActionBar(Component.text("Lunar Eclipse - 10s window!",
                NamedTextColor.LIGHT_PURPLE));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.2f);
    }

    private void castMoonbow(final Player player) {
        final UUID id = player.getUniqueId();
        if (moonbowCooldown.isOnCooldown(id)) {
            sendCooldown(player, moonbowCooldown.remainingMillis(id));
            return;
        }
        moonbowCooldown.apply(id);

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

    private void sendCooldown(final Player player, final long remainingMillis) {
        final double seconds = Math.ceil(remainingMillis / 1000.0);
        player.sendActionBar(Component.text(
                "On cooldown - " + (long) seconds + "s", NamedTextColor.GRAY));
    }
}
