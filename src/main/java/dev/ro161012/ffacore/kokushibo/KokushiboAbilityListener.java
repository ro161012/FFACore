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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the Kokushibo Sword abilities.
 *
 * <ul>
 *   <li><b>Upper Moon One</b> (passive): melee strikes unleash chaotic
 *       crescent moon blades that fly out and deal true damage.</li>
 *   <li><b>Catastrophe, Tenman Crescent Moon</b> (offhand): an omni-directional
 *       ring of moon energy expands outward around the caster, dealing true
 *       damage to everything it sweeps.</li>
 *   <li><b>Moonbow, Half Moon</b> (offhand + crouch): arms the moonbow, then
 *       each left-click launches a white crescent where the player aims,
 *       dealing true damage to what it passes.</li>
 * </ul>
 */
public final class KokushiboAbilityListener implements Listener {

    private final FFACore plugin;
    private final AbilityBossBars bossBars;

    private final Set<UUID> trueDamageTargets = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> nextCrescentProc = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> armedMoonbow = new ConcurrentHashMap<>();
    private final Map<UUID, Long> moonbowExpiry = new ConcurrentHashMap<>();

    /** How long a player has to spend their Moonbow shots after arming. */
    private static final long MOONBOW_WINDOW_MILLIS = 6000L;

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
        armedMoonbow.remove(id);
        moonbowExpiry.remove(id);
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
        armedMoonbow.remove(id);
        moonbowExpiry.remove(id);
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
        armedMoonbow.put(id, moonbowCrescents);
        moonbowExpiry.put(id, System.currentTimeMillis() + MOONBOW_WINDOW_MILLIS);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
    }

    /**
     * Left-click while the Moonbow is armed launches one white crescent where
     * the player is aiming. Arming is done with the swap key, so the
     * left-click is consumed as the shot trigger and never breaks blocks or
     * interacts.
     */
    @EventHandler
    public void onMoonbowShot(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        final Player player = event.getPlayer();
        if (!isMoonbowArmed(player)) {
            return;
        }
        event.setCancelled(true);
        spendMoonbowShot(player);
    }

    /**
     * Returns whether the player has armed, unexpired Moonbow shots while
     * still holding the sword.
     */
    private boolean isMoonbowArmed(final Player player) {
        final UUID id = player.getUniqueId();
        final Long expiry = moonbowExpiry.get(id);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            armedMoonbow.remove(id);
            moonbowExpiry.remove(id);
            return false;
        }
        if (!KokushiboSword.isKokushiboSword(player.getInventory().getItemInMainHand())
                && !KokushiboSword.isKokushiboSword(player.getInventory().getItemInOffHand())) {
            return false;
        }
        return armedMoonbow.getOrDefault(id, 0) > 0;
    }

    /**
     * Consumes one armed Moonbow shot and launches a white crescent where the
     * player is aiming.
     */
    private void spendMoonbowShot(final Player player) {
        final UUID id = player.getUniqueId();
        final int remaining = armedMoonbow.getOrDefault(id, 0);
        if (remaining <= 0) {
            armedMoonbow.remove(id);
            moonbowExpiry.remove(id);
            return;
        }
        if (remaining - 1 <= 0) {
            armedMoonbow.remove(id);
            moonbowExpiry.remove(id);
        } else {
            armedMoonbow.put(id, remaining - 1);
        }
        KokushiboEffects.fireMoonbowCrescent(plugin, player, moonbowVfxTicks,
                living -> applyTrueDamage(living, player, moonbowDamageHearts));
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.5f);
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
