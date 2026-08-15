package dev.ro161012.ffacore.nichirin;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Renders the Nichirin Blade ability visuals as flowing particle waves.
 *
 * <p>Clear Blue Sky sweeps an expanding arc of soul-fire across the player's
 * facing, and Enbu whirls a rising vortex of flame with a lava ground ring.
 * Everything is pure particles — nothing block-shaped is spawned — so the
 * effects read as fluid waves of fire rather than solid geometry, and they
 * vanish on their own as the particles burn out.
 */
public final class NichirinEffects {

    /** Ticks the Clear Blue Sky wave is visible. */
    private static final int FAN_TICKS = 10;

    /** Ticks the Enbu vortex is visible. */
    private static final int RING_TICKS = 20;

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a wave of blue soul-fire that sweeps outward
     * through a 160 degree arc in front of the player.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final double yaw = Math.toRadians(eye.getYaw());
        final int arcDegrees = 160;

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eye, 90,
                0.6, 0.4, 0.6, 0.06);
        player.getWorld().spawnParticle(Particle.SOUL, eye, 40,
                0.6, 0.8, 0.6, 0.05);
        player.getWorld().spawnParticle(Particle.END_ROD, eye, 30,
                0.4, 0.4, 0.4, 0.03);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= FAN_TICKS) {
                    cancel();
                    return;
                }
                final double radius = 0.6 + tick * 0.34;
                final double height = -0.5 + tick * 0.06;
                final int samples = 36;
                for (int i = 0; i < samples; i++) {
                    final double angle = yaw + Math.toRadians(
                            -arcDegrees / 2.0 + i * (arcDegrees / (double) (samples - 1)));
                    final Location point = eye.clone().add(
                            Math.sin(angle) * radius, height, Math.cos(angle) * radius);
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 2,
                            0.12, 0.12, 0.12, 0.04);
                    if (i % 3 == 0) {
                        player.getWorld().spawnParticle(Particle.END_ROD, point, 1,
                                0.05, 0.05, 0.05, 0.02);
                    }
                }
                player.getWorld().spawnParticle(Particle.SOUL, eye.clone().add(0, 0.4, 0), 6,
                        0.5, 0.6, 0.5, 0.03);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Plays Enbu: a rising whirl of flame around the player with a
     * counter-spinning soul-fire ring and an expanding lava ring on the floor.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();

        player.getWorld().spawnParticle(Particle.FLAME, eye, 90,
                0.8, 0.5, 0.8, 0.07);
        player.getWorld().spawnParticle(Particle.LAVA, eye, 40,
                0.6, 0.4, 0.6, 0.04);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= RING_TICKS) {
                    cancel();
                    return;
                }
                final double spin = Math.toRadians(tick * 30);
                final double rise = tick * 0.1;

                // Outer flame vortex.
                final int ring = 32;
                for (int i = 0; i < ring; i++) {
                    final double angle = Math.toRadians(i * (360.0 / ring)) + spin;
                    final double radius = 1.8 + Math.sin(tick * 0.5) * 0.3;
                    final Location point = eye.clone().add(
                            Math.sin(angle) * radius, -0.6 + rise, Math.cos(angle) * radius);
                    player.getWorld().spawnParticle(Particle.FLAME, point, 2,
                            0.15, 0.1, 0.15, 0.05);
                    if (i % 4 == 0) {
                        player.getWorld().spawnParticle(Particle.LAVA, point, 1,
                                0.1, 0.1, 0.1, 0.02);
                    }
                }

                // Counter-spinning inner soul-fire ring.
                for (int i = 0; i < 12; i++) {
                    final double angle = -Math.toRadians(i * (360.0 / 12)) + spin * 0.7;
                    final Location point = eye.clone().add(
                            Math.sin(angle) * 1.0, -0.4 + rise * 1.3, Math.cos(angle) * 1.0);
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1,
                            0.1, 0.1, 0.1, 0.03);
                }

                // Expanding lava ring on the floor.
                final double groundRadius = 0.5 + tick * 0.2;
                for (int i = 0; i < 20; i++) {
                    final double angle = Math.toRadians(i * (360.0 / 20));
                    final Location point = eye.clone().add(
                            Math.sin(angle) * groundRadius, -0.9, Math.cos(angle) * groundRadius);
                    player.getWorld().spawnParticle(Particle.LAVA, point, 1,
                            0.05, 0.02, 0.05, 0.01);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
