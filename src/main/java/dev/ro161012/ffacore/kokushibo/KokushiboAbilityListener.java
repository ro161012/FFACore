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
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the Kokoshibos Sword abilities.
 *
 * <ul>
 *   <li><b>Upper Moon One</b> (passive): melee strikes unleash chaotic
 *       crescent moon blades that fly out and deal true damage.</li>
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
    private final Map<UUID, Long> nextCrescentProc = new ConcurrentHashMap<>();

    private final NichirinCooldown catastropheCooldown;
    private final NichirinCooldown moonbowCooldown;

    // Cached configuration (refreshed by applyConfig()).
    private double procChance;
    private long procCooldownMillis;
    private int crescentCount;
    private double crescentDamageHearts;
    private double catastropheDamageHearts;
    private double catastropheMaxRadius;
    private int catastropheCrescents;
    private int moonbowCrescents;
    private double moonbowSpacing;
    private double strikeRadius;
    private double moonbowDamageHearts;
    private int catastropheVfxTicks;
    private int moonbowVfxTicks;
    private double crescentSpeed;

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
    }

    /**
     * Re-reads the kokushibo configuration and pushes it to the trackers.
     */
    public void applyConfig() {
        final FileConfiguration config = plugin.getConfig();
        procChance = Math.min(1.0, Math.max(0.0,
                config.getDouble("kokushibo.upper-moon-one.proc-chance", 1.0)));
        procCooldownMillis = Math.max(0, config.getInt(
                "kokushibo.upper-moon-one.proc-cooldown-seconds", 2)) * 1000L;
        crescentCount = Math.max(1, config.getInt(
                "kokushibo.upper-moon-one.crescent-count", 3));
        crescentDamageHearts = config.getDouble(
                "kokushibo.upper-moon-one.damage-hearts", 1.0);
        catastropheDamageHearts = config.getDouble(
                "kokushibo.catastrophe.damage-hearts", 3.0);
        catastropheMaxRadius = Math.max(2.0, config.getDouble(
                "kokushibo.catastrophe.max-radius", 20.0));
        catastropheCrescents = Math.max(4, config.getInt(
                "kokushibo.catastrophe.crescents", 24));
        moonbowCrescents = Math.max(1, config.getInt("kokushibo.moonbow.crescents", 6));
        moonbowSpacing = Math.max(0.5, config.getDouble("kokushibo.moonbow.spacing", 1.6));
        strikeRadius = Math.max(0.5, config.getDouble("kokushibo.moonbow.strike-radius", 1.2));
        moonbowDamageHearts = config.getDouble("kokushibo.moonbow.damage-hearts", 3.0);
        catastropheVfxTicks = Math.max(4, config.getInt(
                "kokushibo.catastrophe.vfx-ticks", 24));
        moonbowVfxTicks = Math.max(4, config.getInt("kokushibo.moonbow.vfx-ticks", 16));
        crescentSpeed = Math.max(0.1, config.getDouble(
                "kokushibo.upper-moon-one.crescent-speed", 1.0));

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
     * Upper Moon One passive: a melee strike with the sword unleashes a
     * volley of chaotic crescent moon blades (Kokushibo's Blood Demon Art)
     * that fly toward the target and deal true damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrescentBlades(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        // Ability damage must not feed the passive.
        if (trueDamageTargets.contains(target.getUniqueId())) {
            return;
        }
        if (!KokushiboSword.isKokushiboSword(player.getInventory().getItemInMainHand())) {
            return;
        }
        final UUID id = player.getUniqueId();
        final long now = System.currentTimeMillis();
        if (now < nextCrescentProc.getOrDefault(id, 0L)) {
            return;
        }
        if (Math.random() > procChance) {
            return;
        }
        nextCrescentProc.put(id, now + procCooldownMillis);

        final Location eye = player.getEyeLocation();
        final Vector base = target.getEyeLocation().toVector()
                .subtract(eye.toVector()).normalize();
        for (int i = 0; i < crescentCount; i++) {
            final Vector direction = base.clone()
                    .add(new Vector(rand(-0.5, 0.5), rand(-0.25, 0.35), rand(-0.5, 0.5)))
                    .normalize();
            KokushiboEffects.fireCrescent(plugin, player, eye, direction,
                    living -> applyTrueDamage(living, player, crescentDamageHearts),
                    crescentSpeed);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
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
        nextCrescentProc.remove(id);
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
        nextCrescentProc.remove(id);
        bossBars.clear(id);
    }

    private void castCatastrophe(final Player player) {
        final UUID id = player.getUniqueId();
        if (catastropheCooldown.isOnCooldown(id)) {
            return;
        }
        catastropheCooldown.apply(id);
        bossBars.start(player, "catastrophe",
                "§dFourteenth Form §8» §d§lCatastrophe, Tenman Crescent Moon",
                BarColor.PURPLE, catastropheCooldown.getCooldownMillis());
        KokushiboEffects.playCatastrophe(plugin, player, catastropheMaxRadius,
                catastropheCrescents,
                living -> applyTrueDamage(living, player, catastropheDamageHearts),
                catastropheVfxTicks);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
    }

    private void castMoonbow(final Player player) {
        final UUID id = player.getUniqueId();
        if (moonbowCooldown.isOnCooldown(id)) {
            return;
        }
        moonbowCooldown.apply(id);
        bossBars.start(player, "moonbow",
                "§dSixteenth Form §8» §d§lMoonbow, Half Moon",
                BarColor.PURPLE, moonbowCooldown.getCooldownMillis());

        final Location eye = player.getEyeLocation();
        final Vector facing = eye.getDirection();
        for (int i = 0; i < moonbowCrescents; i++) {
            final Location strike = eye.clone().add(
                    facing.clone().multiply(1.2 + i * moonbowSpacing)).add(0, -0.5, 0);
            KokushiboEffects.strikeCrescent(plugin, strike, i * 3L, moonbowVfxTicks);
            strikeMoonbowAt(player, strike, i * 3L + moonbowVfxTicks);
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
     * Returns a random double in the inclusive range [min, max].
     */
    private static double rand(final double min, final double max) {
        return min + Math.random() * (max - min);
    }
}
