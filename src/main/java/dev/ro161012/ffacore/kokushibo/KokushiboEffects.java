package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Renders the Kokoshibos Sword ability visuals as flowing particle effects.
 *
 * <p>Catastrophe sweeps an expanding, whirling ring of purple witch-sparks,
 * Moonbow detonates a spark burst at each strike point, and the Upper Moon
 * One passive fires a drifting streak of purple sparks. Everything is pure
 * particles — no display entities — so nothing flickers or glitches while it
 * moves, and the effects fade out on their own.
 */
public final class KokushiboEffects {

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — an
     * omni-directional vortex of purple sparks that whirls around the player
     * while expanding outward.
     *
     * <p>The {@code onStrike} callback receives each living target exactly once
     * as the expanding front reaches it.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster
     * @param maxRadius the radius the vortex expands out to, in blocks
     * @param crescents number of crescent blades (controls spark density)
     * @param onStrike  called once per target as the vortex reaches it
     */
    public static void playCatastrophe(final JavaPlugin plugin, final Player player,
                                       final double maxRadius, final int crescents,
                                       final Consumer<LivingEntity> onStrike) {
        final Location eye = player.getEyeLocation();
        final int totalTicks = 30;
        final double startRadius = 1.5;

        final Set<UUID> struck = new HashSet<>();

        eye.getWorld().spawnParticle(Particle.WITCH, eye, 80, 1.5, 0.8, 1.5, 0.03);
        eye.getWorld().spawnParticle(Particle.END_ROD, eye, 30, 1.0, 0.6, 1.0, 0.02);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    cancel();
                    return;
                }
                final double progress = tick / (double) totalTicks;
                final double radius = startRadius + (maxRadius - startRadius) * progress;
                final double spin = Math.toRadians(tick * 40);

                // Dense expanding ring — the vortex front.
                final int samples = Math.max(24, crescents * 3);
                for (int i = 0; i < samples; i++) {
                    final double angle = Math.toRadians(i * (360.0 / samples)) + spin;
                    final Location point = eye.clone().add(
                            Math.sin(angle) * radius, -0.3 + tick * 0.04, Math.cos(angle) * radius);
                    eye.getWorld().spawnParticle(Particle.WITCH, point, 2,
                            0.12, 0.12, 0.12, 0.03);
                    if (i % 4 == 0) {
                        eye.getWorld().spawnParticle(Particle.END_ROD, point, 1,
                                0.05, 0.05, 0.05, 0.02);
                    }
                }

                // Inner counter-swirl.
                final double inner = radius * 0.6;
                for (int i = 0; i < 12; i++) {
                    final double angle = -Math.toRadians(i * (360.0 / 12)) + spin * 1.4;
                    final Location point = eye.clone().add(
                            Math.sin(angle) * inner, -0.4, Math.cos(angle) * inner);
                    eye.getWorld().spawnParticle(Particle.DRAGON_BREATH, point, 1,
                            0.15, 0.15, 0.15, 0.01);
                }

                // Strike every living target the expanding front has reached.
                for (final Entity entity : eye.getWorld().getNearbyEntities(
                        eye, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    final double dx = living.getLocation().getX() - eye.getX();
                    final double dz = living.getLocation().getZ() - eye.getZ();
                    final double dy = living.getLocation().getY() - eye.getY();
                    if (Math.abs(dy) > 4) {
                        continue;
                    }
                    if (dx * dx + dz * dz <= radius * radius
                            && struck.add(living.getUniqueId())) {
                        onStrike.accept(living);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Detonates a spark burst at the given location after a tick delay.
     *
     * @param plugin      owning plugin (for the scheduler)
     * @param location    the strike point
     * @param delayTicks  ticks to wait before the strike appears
     */
    public static void strikeCrescent(final JavaPlugin plugin, final Location location,
                                      final long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (location.getWorld() == null) {
                    return;
                }
                location.getWorld().spawnParticle(Particle.WITCH, location, 40,
                        0.5, 0.5, 0.5, 0.03);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 20,
                        0.4, 0.4, 0.4, 0.02);
                location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 8,
                        0.3, 0.3, 0.3, 0.01);
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * Fires a drifting streak of purple sparks along the given direction,
     * dealing true damage (through the {@code onHit} callback) to the first
     * living target it touches. Used by the Upper Moon One passive.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster (excluded from hits)
     * @param origin    spawn location of the crescent
     * @param direction travel direction (normalised)
     * @param onHit     called once with the target when the blade connects
     */
    public static void fireCrescent(final JavaPlugin plugin, final Player player,
                                    final Location origin, final Vector direction,
                                    final Consumer<LivingEntity> onHit) {
        final Vector velocity = direction.clone().normalize().multiply(0.55);
        final Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            private int tick;
            private final Location pos = origin.clone();

            @Override
            public void run() {
                if (tick++ >= 26) {
                    cancel();
                    return;
                }
                // Chaotic drift: a slight random wobble every tick.
                velocity.add(new Vector(
                        (Math.random() - 0.5) * 0.06,
                        (Math.random() - 0.5) * 0.05,
                        (Math.random() - 0.5) * 0.06));
                velocity.normalize().multiply(0.55);
                pos.add(velocity);

                if (pos.getBlock().getType().isSolid()) {
                    pos.getWorld().spawnParticle(Particle.WITCH, pos, 10,
                            0.2, 0.2, 0.2, 0.02);
                    cancel();
                    return;
                }

                pos.getWorld().spawnParticle(Particle.WITCH, pos, 3,
                        0.15, 0.15, 0.15, 0.03);
                pos.getWorld().spawnParticle(Particle.END_ROD, pos, 1,
                        0.1, 0.1, 0.1, 0.02);

                for (final Entity entity : pos.getWorld().getNearbyEntities(
                        pos, 1.1, 1.1, 1.1)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    if (!hit.add(living.getUniqueId())) {
                        continue;
                    }
                    onHit.accept(living);
                    pos.getWorld().spawnParticle(Particle.WITCH, pos, 12,
                            0.3, 0.3, 0.3, 0.02);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
