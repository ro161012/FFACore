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
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Renders the Nichirin Blade ability visuals as two distinct lava scenes.
 *
 * <p><b>Clear Blue Sky</b> is a directional fan: a widening arc of glowing
 * magma blocks slashes outward in the direction the player faces. <b>Enbu</b>
 * is an omni-directional eruption: a ring of magma spreads across the ground
 * while a shroomlight lava column erupts upward beneath a counter-rotating
 * glowstone ring. Both use full-bright lava-adjacent blocks (magma, shroomlight,
 * glowstone) so they read as molten rock, and every display is removed when
 * the animation ends.
 */
public final class NichirinEffects {

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a fan of lava blocks slashing outward in the
     * player's facing direction.
     *
     * @param plugin     owning plugin (for the scheduler)
     * @param player     the caster
     * @param ticks      how long the fan sweeps (shorter = snappier)
     * @param arcDegrees width of the fan arc in degrees, centred on facing
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player,
                                        final int ticks, final double arcDegrees) {
        final Location eye = player.getEyeLocation();
        final Vector facing = horizontalFacing(player);
        final double halfArc = Math.toRadians(Math.max(10.0, arcDegrees) / 2.0);
        final double startRadius = 0.5;

        final int blades = 28;
        final int inner = 14;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int i = 0; i < blades; i++) {
            displays.add(spawnBlock(player, eye, Material.MAGMA_BLOCK, 0.5f));
        }
        for (int i = 0; i < inner; i++) {
            displays.add(spawnBlock(player, eye, Material.SHROOMLIGHT, 0.4f));
        }

        animate(plugin, displays, ticks, tick -> {
            final double radius = startRadius + tick * 0.26;
            for (int i = 0; i < blades; i++) {
                final double t = i / (double) (blades - 1);
                final double angle = -halfArc + t * (2.0 * halfArc);
                final BlockDisplay block = displays.get(i);
                block.teleport(eye.clone()
                        .add(facing.clone().rotateAroundY(angle).multiply(radius))
                        .add(0, -0.5, 0));
                setScale(block, 0.5f, 0.5f, 0.5f);
            }
            for (int i = 0; i < inner; i++) {
                final double t = i / (double) (inner - 1);
                final double angle = -halfArc * 0.8 + t * (1.6 * halfArc);
                final BlockDisplay block = displays.get(blades + i);
                block.teleport(eye.clone()
                        .add(facing.clone().rotateAroundY(angle).multiply(radius * 0.7))
                        .add(0, -0.3, 0));
                setScale(block, 0.4f, 0.4f, 0.4f);
            }
            // Molten droplets along the leading edge of the slash.
            final Location edge = eye.clone()
                    .add(facing.clone().multiply(radius)).add(0, -0.4, 0);
            edge.getWorld().spawnParticle(Particle.LAVA, edge, 6, 0.5, 0.3, 0.5, 0.02);
            edge.getWorld().spawnParticle(Particle.FLAME, edge, 4, 0.6, 0.4, 0.6, 0.05);
        });
    }

    /**
     * Plays Enbu: a full-circle lava eruption around the caster — a magma
     * ring spreads across the ground, a shroomlight column erupts upward, and
     * a glowstone ring counter-rotates overhead.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param ticks  how long the eruption plays (shorter = snappier)
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player,
                                final int ticks) {
        final Location eye = player.getEyeLocation();
        final int ground = 28;
        final int upperRing = 16;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int i = 0; i < ground; i++) {
            displays.add(spawnBlock(player, eye, Material.MAGMA_BLOCK, 0.5f));
        }
        for (int i = 0; i < upperRing; i++) {
            displays.add(spawnBlock(player, eye, Material.GLOWSTONE, 0.4f));
        }
        final BlockDisplay pillar = spawnBlock(player, eye.clone().add(0, -0.4, 0),
                Material.SHROOMLIGHT, 0.6f);
        displays.add(pillar);

        player.getWorld().spawnParticle(Particle.LAVA, eye, 50, 0.9, 0.5, 0.9, 0.02);
        player.getWorld().spawnParticle(Particle.FLAME, eye, 80, 1.0, 0.6, 1.0, 0.06);

        animate(plugin, displays, ticks, tick -> {
            final double spin = Math.toRadians(tick * 22);
            final double groundRadius = 0.6 + tick * 0.22;
            for (int i = 0; i < ground; i++) {
                final double angle = Math.toRadians(i * (360.0 / ground)) + spin * 0.5;
                final BlockDisplay block = displays.get(i);
                block.teleport(eye.clone().add(
                        Math.sin(angle) * groundRadius, -0.95, Math.cos(angle) * groundRadius));
                setScale(block, 0.5f, 0.5f, 0.5f);
            }
            final double innerSpin = -Math.toRadians(tick * 30);
            for (int i = 0; i < upperRing; i++) {
                final double angle = Math.toRadians(i * (360.0 / upperRing)) + innerSpin;
                final BlockDisplay block = displays.get(ground + i);
                block.teleport(eye.clone().add(
                        Math.sin(angle) * 1.4, -0.2, Math.cos(angle) * 1.4));
                setScale(block, 0.4f, 0.4f, 0.4f);
            }
            // Lava column swells upward, then falls back as the cast ends.
            final float pillarHeight = 0.6f + (float) Math.sin(tick * Math.PI / Math.max(1, ticks - 1)) * 2.2f;
            setScale(pillar, 0.6f, pillarHeight, 0.6f);
        });
    }

    /**
     * Spawns a full-bright glowing block display with the given cube scale.
     */
    private static BlockDisplay spawnBlock(final Player player, final Location location,
                                           final Material material, final float scale) {
        final BlockDisplay display = player.getWorld().spawn(location, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        setScale(display, scale, scale, scale);
        return display;
    }

    /**
     * Returns the player's horizontal facing direction (pitch flattened), or
     * north when they are looking straight up or down.
     */
    private static Vector horizontalFacing(final Player player) {
        final Vector direction = player.getLocation().getDirection().setY(0);
        return direction.lengthSquared() == 0 ? new Vector(0, 0, 1) : direction.normalize();
    }

    /**
     * Applies a scale-only transform to a display (no rotation, so it never
     * flickers).
     */
    private static void setScale(final Display display, final float sx, final float sy,
                                 final float sz) {
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(sx, sy, sz),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
    }

    /**
     * Runs a per-tick animation callback, then removes every display.
     */
    private static void animate(final JavaPlugin plugin, final List<BlockDisplay> displays,
                                final int totalTicks, final IntConsumer ticker) {
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
