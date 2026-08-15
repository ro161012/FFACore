package dev.ro161012.ffacore.nichirin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns the short-lived {@link BlockDisplay} entities that render the
 * Nichirin Blade ability visuals.
 *
 * <p>Both abilities draw a translucent, full-bright arrangement of stained
 * glass blocks and animate it for a few ticks before the displays are
 * removed, so the effects cost nothing once finished. The companion core
 * shader ({@code rendertype_entity_alpha.fsh}) applies the display tint so
 * the glass glows in its true colour.
 */
public final class NichirinEffects {

    /** Seconds the Clear Blue Sky fan is visible. */
    private static final int FAN_TICKS = 10;

    /** Seconds the Enbu flame ring is visible. */
    private static final int RING_TICKS = 20;

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays the Clear Blue Sky effect: a horizontal fan of blue glass that
     * sweeps outward from the player's chest.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final double yaw = Math.toRadians(eye.getYaw());

        final List<BlockDisplay> displays = new ArrayList<>();
        final int segments = 21;
        final double startRadius = 1.0;
        for (int i = 0; i < segments; i++) {
            final double angle = yaw + Math.toRadians(-80 + i * (160.0 / (segments - 1)));
            displays.add(spawnSegment(plugin, player, angle, startRadius, false));
        }

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eye, 80,
                1.0, 0.8, 1.0, 0.03);
        player.getWorld().spawnParticle(Particle.END_ROD, eye, 30,
                0.8, 0.5, 0.8, 0.02);

        animate(plugin, displays, FAN_TICKS, tick -> {
            final double radius = startRadius + (tick * 0.5);
            for (int i = 0; i < displays.size(); i++) {
                final double angle = yaw + Math.toRadians(-80 + i * (160.0 / (segments - 1)));
                place(displays.get(i), eye, angle, radius, tick * 8f);
            }
        });
    }

    /**
     * Plays the Enbu effect: a ring of flame-coloured glass that spins and
     * rises around the player.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();

        final List<BlockDisplay> displays = new ArrayList<>();
        final int segments = 24;
        final double radius = 2.0;
        for (int i = 0; i < segments; i++) {
            final double angle = Math.toRadians(i * (360.0 / segments));
            displays.add(spawnSegment(plugin, player, angle, radius, true));
        }

        player.getWorld().spawnParticle(Particle.FLAME, eye, 90,
                1.2, 0.8, 1.2, 0.04);
        player.getWorld().spawnParticle(Particle.LAVA, eye, 30,
                0.9, 0.6, 0.9, 0.02);

        animate(plugin, displays, RING_TICKS, tick -> {
            final double spin = Math.toRadians(tick * 32);
            final double rise = tick * 0.08;
            for (int i = 0; i < displays.size(); i++) {
                final double angle = Math.toRadians(i * (360.0 / segments)) + spin;
                final BlockDisplay display = displays.get(i);
                display.setTransformation(new Transformation(
                        new Vector3f(0f, (float) rise, 0f),
                        new AxisAngle4f((float) -angle, 0f, 1f, 0f),
                        new Vector3f(0.3f, 1.8f, 1.0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)));
                display.teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.6 + rise, Math.cos(angle) * radius));
            }
        });
    }

    /**
     * Spawns one glowing glass segment at the given angle and radius around
     * the player's eye location.
     */
    private static BlockDisplay spawnSegment(final JavaPlugin plugin, final Player player,
                                             final double angle, final double radius,
                                             final boolean flame) {
        final Location eye = player.getEyeLocation();
        final BlockDisplay display = player.getWorld().spawn(eye, BlockDisplay.class);
        display.setBlock((flame ? Material.ORANGE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS)
                .createBlockData());
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        place(display, eye, angle, radius, 0f);
        return display;
    }

    /**
     * Positions a segment in a horizontal ring around the given origin.
     */
    private static void place(final BlockDisplay display, final Location origin,
                              final double angle, final double radius, final float roll) {
        final double x = Math.sin(angle) * radius;
        final double z = Math.cos(angle) * radius;
        display.teleport(origin.clone().add(x, -0.6, z));
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f((float) -angle, 0f, 1f, 0f),
                new Vector3f(0.3f, 1.6f, 0.9f),
                new AxisAngle4f(roll, 0f, 0f, 1f)));
    }

    /**
     * Runs a per-tick animation callback, then removes every display.
     */
    private static void animate(final JavaPlugin plugin, final List<BlockDisplay> displays,
                                final int totalTicks,
                                final java.util.function.IntConsumer ticker) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    displays.forEach(BlockDisplay::remove);
                    cancel();
                    return;
                }
                ticker.accept(tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
