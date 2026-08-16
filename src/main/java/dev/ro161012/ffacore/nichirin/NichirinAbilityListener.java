package dev.ro161012.ffacore.nichirin;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.weapon.AbilityBossBars;
import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the Nichirin Blade combat abilities.
 *
 * <ul>
 *   <li><b>Flame Combo</b> (passive): land N hits without taking damage to
 *       gain Strength II; taking damage resets the combo.</li>
 *   <li><b>Clear Blue Sky</b> (offhand): a full-circle burst of true damage
 *       around the caster, boosted into the air, that sears every target in
 *       the ring with fire that ignores Fire Resistance.</li>
 *   <li><b>Dancing Flash</b> (offhand + crouch): true damage in a radius and
 *       an absorption lock on every target hit.</li>
 * </ul>
 *
 * <p>Both actives are triggered by pressing the swap-hands key while the
 * blade is held (in either hand), and share the {@link BlockDisplay} visuals from
 * {@link NichirinEffects}. Damage ignores armour, toughness, resistance and
 * protection enchantments (true damage) via a short-lived marker consumed by
 * the damage event handler.
 */
public final class NichirinAbilityListener implements Listener {

    private final FFACore plugin;
    private final AbilityBossBars bossBars;

    private final Set<UUID> trueDamageTargets = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> absorptionLockedUntil = new ConcurrentHashMap<>();

    private NichirinCooldown clearSkyCooldown;
    private NichirinCooldown enbuCooldown;
    private NichirinCombo combo;

    // Cached configuration (refreshed by applyConfig()).
    private int comboHits;
    private int strengthSeconds;
    private double clearSkyDamageHearts;
    private double clearSkyRadius;
    private double clearSkyBoost;
    private int clearSkyFireSeconds;
    private double clearSkySearHearts;
    private double enbuDamageHearts;
    private double enbuRadius;
    private long absorptionLockMillis;
    private int clearSkyVfxTicks;
    private int enbuVfxTicks;
    private int clearSkyLavaCount;
    private int enbuLavaCount;
    private double enbuKnockbackStrength;
    private double enbuKnockbackRadius;
    private int enbuFireSeconds;
    private double enbuSearHearts;

    /**
     * Creates the listener and loads configuration.
     *
     * @param plugin owning FFACore plugin
     */
    public NichirinAbilityListener(final FFACore plugin) {
        this.plugin = plugin;
        this.bossBars = new AbilityBossBars(plugin);
        applyConfig();
    }

    /**
     * Re-reads the nichirin configuration and pushes it to the trackers.
     */
    public void applyConfig() {
        final FileConfiguration config = plugin.getConfig();
        comboHits = Math.max(1, config.getInt("nichirin.combo-hits", 4));
        strengthSeconds = Math.max(1, config.getInt("nichirin.combo-strength-duration-seconds", 6));
        clearSkyDamageHearts = config.getDouble("nichirin.clear-blue-sky.damage-hearts", 2.0);
        clearSkyRadius = config.getDouble("nichirin.clear-blue-sky.radius", 15.0);
        clearSkyBoost = config.getDouble("nichirin.clear-blue-sky.boost-power", 1.4);
        clearSkyFireSeconds = Math.max(1, config.getInt(
                "nichirin.clear-blue-sky.fire-seconds", 3));
        clearSkySearHearts = config.getDouble("nichirin.clear-blue-sky.sear-hearts", 1.0);
        enbuDamageHearts = config.getDouble("nichirin.enbu.damage-hearts", 2.0);
        enbuRadius = config.getDouble("nichirin.enbu.radius", 4.0);
        absorptionLockMillis = Math.max(0, config.getInt(
                "nichirin.enbu.absorption-lock-seconds", 15)) * 1000L;
        clearSkyVfxTicks = Math.max(4, config.getInt(
                "nichirin.clear-blue-sky.vfx-ticks", 12));
        enbuVfxTicks = Math.max(4, config.getInt("nichirin.enbu.vfx-ticks", 20));
        clearSkyLavaCount = Math.max(1, config.getInt(
                "nichirin.clear-blue-sky.lava-count", 28));
        enbuLavaCount = Math.max(1, config.getInt("nichirin.enbu.lava-count", 20));
        enbuKnockbackStrength = Math.max(0.0, config.getDouble(
                "nichirin.enbu.lava-knockback-strength", 1.2));
        enbuKnockbackRadius = Math.max(1.0, config.getDouble(
                "nichirin.enbu.lava-knockback-radius", 2.0));
        enbuFireSeconds = Math.max(1, config.getInt("nichirin.enbu.fire-seconds", 3));
        enbuSearHearts = config.getDouble("nichirin.enbu.sear-hearts", 1.0);

        if (clearSkyCooldown == null) {
            clearSkyCooldown = new NichirinCooldown(
                    config.getInt("nichirin.clear-blue-sky.cooldown-seconds", 50));
            enbuCooldown = new NichirinCooldown(
                    config.getInt("nichirin.enbu.cooldown-seconds", 70));
            combo = new NichirinCombo(comboHits);
        } else {
            clearSkyCooldown.setCooldownSeconds(
                    config.getInt("nichirin.clear-blue-sky.cooldown-seconds", 50));
            enbuCooldown.setCooldownSeconds(
                    config.getInt("nichirin.enbu.cooldown-seconds", 70));
            combo = new NichirinCombo(comboHits);
        }
    }

    /**
     * Strips armour, toughness, resistance and protection reductions so the
     * marked ability hit lands as true damage.
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
     * Advances the Flame Combo passive on landed melee hits.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onComboHit(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        // Ability damage must not feed the melee combo.
        if (trueDamageTargets.contains(event.getEntity().getUniqueId())) {
            return;
        }
        final ItemStack main = player.getInventory().getItemInMainHand();
        if (!NichirinBlade.isNichirinBlade(main)) {
            return;
        }
        if (combo.registerHit(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    strengthSeconds * 20, 1, false, true, true));
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.2f);
        }
    }

    /**
     * Taking damage resets the Flame Combo immediately.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            combo.reset(player.getUniqueId());
        }
    }

    /**
     * Triggers an active ability when the player presses the swap-hands key
     * while holding the blade in either hand. The swap is cancelled so the
     * blade never moves between hands; crouching casts Dancing Flash,
     * otherwise Clear Blue Sky.
     */
    @EventHandler
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();
        final boolean inMainHand = NichirinBlade.isNichirinBlade(
                player.getInventory().getItemInMainHand());
        final boolean inOffHand = NichirinBlade.isNichirinBlade(
                player.getInventory().getItemInOffHand());
        if (!inMainHand && !inOffHand) {
            return;
        }
        // The blade never moves between hands via the swap key: it can only
        // be placed in a hand manually. Swapping is the ability trigger and
        // works whether the blade is in the main hand or the offhand.
        event.setCancelled(true);
        if (player.isSneaking()) {
            castEnbu(player);
        } else {
            castClearBlueSky(player);
        }
    }

    /**
     * Cleans up a leaver's combo, cooldowns and lock state.
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        combo.reset(id);
        clearSkyCooldown.clear(id);
        enbuCooldown.clear(id);
        absorptionLockedUntil.remove(id);
        bossBars.clear(id);
    }

    /**
     * Removes every active cooldown bar (called on plugin disable).
     */
    public void close() {
        bossBars.close();
    }

    /**
     * Clears every active cooldown and cooldown bar for a player.
     *
     * @param id the player id
     */
    public void resetCooldowns(final UUID id) {
        clearSkyCooldown.clear(id);
        enbuCooldown.clear(id);
        bossBars.clear(id);
    }

    /**
     * Blocks the Absorption potion effect while a target is locked.
     */
    @EventHandler
    public void onPotionEffect(final EntityPotionEffectEvent event) {
        final UUID id = event.getEntity().getUniqueId();
        final Long until = absorptionLockedUntil.get(id);
        if (until == null) {
            return;
        }
        if (until <= System.currentTimeMillis()) {
            absorptionLockedUntil.remove(id, until);
            return;
        }
        if (event.getModifiedType() == PotionEffectType.ABSORPTION) {
            event.setCancelled(true);
        }
    }

    private void castClearBlueSky(final Player player) {
        final UUID id = player.getUniqueId();
        if (clearSkyCooldown.isOnCooldown(id)) {
            return;
        }
        clearSkyCooldown.apply(id);
        bossBars.start(player, "clear-blue-sky",
                "§6Hinokami Kagura §8» §6§lClear Blue Sky",
                BarColor.RED, clearSkyCooldown.getCooldownMillis());

        // Launch the caster upward so the ring sweeps mid-air. The boost
        // always at least reaches the configured upward speed and stacks on
        // top of any existing upward motion, so it works on the ground or in
        // the air.
        final double upward = Math.max(clearSkyBoost,
                clearSkyBoost + player.getVelocity().getY());
        player.setVelocity(player.getVelocity().setY(upward));
        NichirinEffects.playClearBlueSky(plugin, player, clearSkyVfxTicks, clearSkyRadius);
        NichirinEffects.lavaBurst(plugin, player, clearSkyRadius, clearSkyLavaCount);
        watchForLanding(player);

        // Horizontal cylinder: hits everyone around and below, whatever height
        // they are at relative to the caster.
        final Location center = player.getLocation();
        final double radiusSq = clearSkyRadius * clearSkyRadius;
        for (final org.bukkit.entity.Entity entity : player.getNearbyEntities(
                clearSkyRadius, 128.0, clearSkyRadius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            if (horizontalDistanceSq(center, living.getLocation()) > radiusSq) {
                continue;
            }
            applyTrueDamage(living, player, clearSkyDamageHearts);
            ignite(living, player, clearSkyFireSeconds, clearSkySearHearts);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);
    }

    private void castEnbu(final Player player) {
        final UUID id = player.getUniqueId();
        if (enbuCooldown.isOnCooldown(id)) {
            return;
        }
        enbuCooldown.apply(id);
        bossBars.start(player, "enbu",
                "§6Hinokami Kagura §8» §6§lDancing Flash",
                BarColor.RED, enbuCooldown.getCooldownMillis());
        NichirinEffects.playEnbu(plugin, player, enbuVfxTicks, enbuRadius);
        NichirinEffects.lavaBurstForward(plugin, player, enbuRadius, enbuLavaCount,
                enbuKnockbackStrength, enbuKnockbackRadius);

        final long lockUntil = System.currentTimeMillis() + absorptionLockMillis;
        for (final org.bukkit.entity.Entity entity : player.getNearbyEntities(
                enbuRadius, enbuRadius, enbuRadius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            if (living.getLocation().distanceSquared(player.getLocation())
                    > enbuRadius * enbuRadius) {
                continue;
            }
            applyTrueDamage(living, player, enbuDamageHearts);
            absorptionLockedUntil.put(living.getUniqueId(), lockUntil);
            living.setAbsorptionAmount(0);
            // The lava sears targets with fire that ignores Fire Resistance.
            ignite(living, player, enbuFireSeconds, enbuSearHearts);
        }
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
    }

    /**
     * Deals hearts of damage that ignores armour, toughness, resistance and
     * protection enchantments.
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
     * Lights a target on fire with searing ticks that ignore Fire Resistance.
     * The visible burn fades naturally; the searing damage is applied directly
     * as true damage over the configured duration.
     *
     * @param target      who burns
     * @param source      the caster
     * @param fireSeconds seconds of visible fire
     * @param searHearts  hearts of true damage per second while burning
     */
    private void ignite(final LivingEntity target, final Player source,
                        final int fireSeconds, final double searHearts) {
        target.setFireTicks(Math.max(target.getFireTicks(), fireSeconds * 20));
        final UUID targetId = target.getUniqueId();
        new BukkitRunnable() {
            private int remaining = fireSeconds;

            @Override
            public void run() {
                if (!source.isOnline()) {
                    cancel();
                    return;
                }
                final org.bukkit.entity.Entity current = Bukkit.getEntity(targetId);
                if (!(current instanceof LivingEntity living) || living.isDead()) {
                    cancel();
                    return;
                }
                applyTrueDamage(living, source, searHearts);
                if (--remaining <= 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Watches the caster until they come back down from the Clear Blue Sky
     * boost, then detonates the earthquake shockwave on the ground beneath
     * them.
     *
     * @param player the boosted player
     */
    private void watchForLanding(final Player player) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (tick > 3 && isLanded(player)) {
                    NichirinEffects.playLandingShockwave(plugin, player);
                    cancel();
                    return;
                }
                if (tick++ > 120) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Returns true once the player has stopped rising and is standing on a
     * solid block — i.e. they have landed from the boost.
     */
    private static boolean isLanded(final Player player) {
        if (player.getVelocity().getY() > 0.05) {
            return false;
        }
        final Location below = player.getLocation().subtract(0.0, 0.05, 0.0);
        return below.getBlock().getType().isSolid();
    }

    /**
     * Returns the squared horizontal distance between two locations (Y ignored).
     */
    private static double horizontalDistanceSq(final Location a, final Location b) {
        final double dx = a.getX() - b.getX();
        final double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
