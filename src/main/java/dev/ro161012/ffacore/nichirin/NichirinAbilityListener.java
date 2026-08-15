package dev.ro161012.ffacore.nichirin;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.weapon.AbilityBossBars;
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
import org.bukkit.util.Vector;

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
 *   <li><b>Clear Blue Sky</b> (offhand): a fan of true damage in an arc
 *       ahead of the caster.</li>
 *   <li><b>Enbu</b> (offhand + crouch): true damage in a radius and an
 *       absorption lock on every target hit.</li>
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
    private double clearSkyArcDegrees;
    private double enbuDamageHearts;
    private double enbuRadius;
    private long absorptionLockMillis;

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
        clearSkyRadius = config.getDouble("nichirin.clear-blue-sky.radius", 3.5);
        clearSkyArcDegrees = config.getDouble("nichirin.clear-blue-sky.arc-degrees", 160.0);
        enbuDamageHearts = config.getDouble("nichirin.enbu.damage-hearts", 2.0);
        enbuRadius = config.getDouble("nichirin.enbu.radius", 3.0);
        absorptionLockMillis = Math.max(0, config.getInt(
                "nichirin.enbu.absorption-lock-seconds", 15)) * 1000L;

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
     * blade never moves between hands; crouching casts Enbu, otherwise
     * Clear Blue Sky.
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
                "§bHinokami Kagura §8» §b§lClear Blue Sky",
                BarColor.BLUE, clearSkyCooldown.getCooldownMillis());
        NichirinEffects.playClearBlueSky(plugin, player);

        final Vector facing = horizontalDirection(player);
        for (final org.bukkit.entity.Entity entity : player.getNearbyEntities(
                clearSkyRadius, clearSkyRadius, clearSkyRadius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            if (!inArc(player, living, facing, clearSkyRadius)) {
                continue;
            }
            applyTrueDamage(living, player, clearSkyDamageHearts);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.6f);
    }

    private void castEnbu(final Player player) {
        final UUID id = player.getUniqueId();
        if (enbuCooldown.isOnCooldown(id)) {
            return;
        }
        enbuCooldown.apply(id);
        bossBars.start(player, "enbu",
                "§6Hinokami Kagura §8» §6§lEnbu",
                BarColor.RED, enbuCooldown.getCooldownMillis());
        NichirinEffects.playEnbu(plugin, player);

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
        }
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);
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
     * Returns whether the target lies within the fan arc ahead of the player.
     */
    private boolean inArc(final Player player, final LivingEntity target,
                          final Vector facing, final double radius) {
        if (target.getLocation().distanceSquared(player.getLocation()) > radius * radius) {
            return false;
        }
        final Vector toTarget = target.getLocation().toVector()
                .subtract(player.getLocation().toVector()).setY(0);
        if (toTarget.lengthSquared() == 0 || facing.lengthSquared() == 0) {
            return true;
        }
        final double degrees = Math.toDegrees(facing.angle(toTarget));
        return degrees <= clearSkyArcDegrees / 2.0;
    }

    private static Vector horizontalDirection(final Player player) {
        final Vector direction = player.getLocation().getDirection().setY(0);
        return direction.lengthSquared() == 0 ? new Vector(0, 0, 1) : direction.normalize();
    }
}
