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
 * <p>Every effect is layered — a sweeping fan, a shockwave ring, a light
 * pillar, and a particle burst for Clear Blue Sky; a rising flame vortex, two
 * counter-rotating rings, a scorch disc and a fire pillar for Enbu. All glass
 * is full-bright and short-lived, so the companion core shader
 * ({@code rendertype_entity_alpha.fsh}) applies the display tint and makes the
 * glass glow. Everything is removed automatically when the animation ends.
 */
public final class NichirinEffects {

    /** Ticks the Clear Blue Sky effect is visible. */
    private static final int FAN_TICKS = 12;

    /** Ticks the Enbu effect is visible. */
    private static final int RING_TICKS = 22;

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a two-layer horizontal fan of glowing blue glass
     * that sweeps outward from the player, an expanding cyan shockwave ring
     * and a rising light pillar, all wrapped in a soul-fire burst.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final double yaw = Math.toRadians(eye.getYaw());
        final int segments = 28;
        final double startRadius = 0.8;

        final List<BlockDisplay> displays = new ArrayList<>();

        // Outer fan: tall light-blue blades.
        for (int i = 0; i < segments; i++) {
            displays.add(spawnGlass(player, eye, Material.LIGHT_BLUE_STAINED_GLASS,
                    0.35f, 2.2f, 0.9f, arcAngle(yaw, segments, i), startRadius));
        }
        // Inner fan: thinner white blades for a bright core.
        for (int i = 0; i < segments; i++) {
            displays.add(spawnGlass(player, eye, Material.WHITE_STAINED_GLASS,
                    0.18f, 1.6f, 0.5f, arcAngle(yaw, segments, i), startRadius));
        }

        // Expanding cyan shockwave ring at chest height.
        final BlockDisplay ring = spawnBlock(player, eye.clone().add(0, -0.6, 0),
                Material.CYAN_STAINED_GLASS);
        displays.add(ring);

        // Rising light pillar through the caster.
        final BlockDisplay pillar = spawnBlock(player, eye.clone().add(0, -0.3, 0),
                Material.LIGHT_BLUE_STAINED_GLASS);
        displays.add(pillar);

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eye, 120,
                1.2, 0.9, 1.2, 0.04);
        player.getWorld().spawnParticle(Particle.END_ROD, eye, 50,
                1.0, 0.6, 1.0, 0.03);

        animate(plugin, displays, FAN_TICKS, tick -> {
            final double radius = startRadius + tick * 0.55;
            for (int i = 0; i < segments * 2; i++) {
                final double angle = arcAngle(yaw, segments, i % segments);
                final BlockDisplay blade = displays.get(i);
                blade.teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.6, Math.cos(angle) * radius));
                setTrans(blade, 0.35f, 2.2f - tick * 0.08f, 0.9f,
                        (float) -angle, tick * 10f);
            }

            final float ringScale = 0.5f + tick * 0.9f;
            ring.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(ringScale, 0.06f, ringScale),
                    new AxisAngle4f(tick * 0.5f, 0f, 1f, 0f)));

            final float pillarHeight = tick < 6
                    ? 1.0f + tick * 0.55f
                    : 4.3f - (tick - 6) * 0.5f;
            pillar.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(0.4f, pillarHeight, 0.4f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
        });
    }

    /**
     * Plays Enbu: a rising flame vortex of orange glass with a yellow
     * counter-rotating inner ring, an expanding red scorch disc on the floor
     * and a central fire pillar.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final int segments = 28;
        final int innerSegments = 14;
        final double radius = 2.0;

        final List<BlockDisplay> displays = new ArrayList<>();

        // Outer flame vortex.
        for (int i = 0; i < segments; i++) {
            displays.add(spawnGlass(player, eye, Material.ORANGE_STAINED_GLASS,
                    0.45f, 1.9f, 1.1f, Math.toRadians(i * (360.0 / segments)), radius));
        }
        // Inner counter-rotating ring.
        for (int i = 0; i < innerSegments; i++) {
            displays.add(spawnGlass(player, eye, Material.YELLOW_STAINED_GLASS,
                    0.3f, 1.3f, 0.7f, Math.toRadians(i * (360.0 / innerSegments)),
                    radius * 0.7));
        }

        // Red scorch disc on the ground.
        final BlockDisplay disc = spawnBlock(player, eye.clone().add(0, -1.0, 0),
                Material.RED_STAINED_GLASS);
        displays.add(disc);

        // Central fire pillar.
        final BlockDisplay pillar = spawnBlock(player, eye.clone().add(0, 0.4, 0),
                Material.ORANGE_STAINED_GLASS);
        displays.add(pillar);

        player.getWorld().spawnParticle(Particle.FLAME, eye, 130,
                1.4, 0.9, 1.4, 0.05);
        player.getWorld().spawnParticle(Particle.LAVA, eye, 45,
                1.0, 0.6, 1.0, 0.03);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eye, 30,
                0.9, 0.5, 0.9, 0.03);

        animate(plugin, displays, RING_TICKS, tick -> {
            final double spin = Math.toRadians(tick * 26);
            final double rise = tick * 0.11;
            for (int i = 0; i < segments; i++) {
                final double angle = Math.toRadians(i * (360.0 / segments)) + spin;
                final BlockDisplay blade = displays.get(i);
                blade.teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.7 + rise, Math.cos(angle) * radius));
                setTrans(blade, 0.45f, 1.9f - tick * 0.05f, 1.1f,
                        (float) -angle, tick * 0.7f);
            }

            final double innerSpin = -Math.toRadians(tick * 34);
            for (int i = 0; i < innerSegments; i++) {
                final double angle = Math.toRadians(i * (360.0 / innerSegments)) + innerSpin;
                final BlockDisplay blade = displays.get(segments + i);
                blade.teleport(eye.clone().add(
                        Math.sin(angle) * radius * 0.7, -0.5 + rise * 1.4,
                        Math.cos(angle) * radius * 0.7));
                setTrans(blade, 0.3f, 1.3f, 0.7f, (float) -angle, -tick * 0.9f);
            }

            final float discScale = 0.6f + tick * 0.16f;
            disc.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(discScale, 0.05f, discScale),
                    new AxisAngle4f(tick * 0.4f, 0f, 1f, 0f)));

            pillar.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(0.5f, 1.0f + tick * 0.22f, 0.5f),
                    new AxisAngle4f(tick * 0.6f, 0f, 1f, 0f)));
        });
    }

    /**
     * Spawns one full-bright glass blade at the given angle and radius around
     * the player's eye location.
     */
    private static BlockDisplay spawnGlass(final Player player, final Location origin,
                                           final Material material, final float width,
                                           final float height, final float depth,
                                           final double angle, final double radius) {
        final BlockDisplay display = player.getWorld().spawn(origin, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        display.teleport(origin.clone().add(
                Math.sin(angle) * radius, -0.6, Math.cos(angle) * radius));
        setTrans(display, width, height, depth, (float) -angle, 0f);
        return display;
    }

    /**
     * Spawns a single full-bright glass block display.
     */
    private static BlockDisplay spawnBlock(final Player player, final Location location,
                                           final Material material) {
        final BlockDisplay display = player.getWorld().spawn(location, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        return display;
    }

    /**
     * Applies a scale, yaw and roll transform (no translation) to a display.
     */
    private static void setTrans(final BlockDisplay display, final float width,
                                 final float height, final float depth,
                                 final float yaw, final float roll) {
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(yaw, 0f, 1f, 0f),
                new Vector3f(width, height, depth),
                new AxisAngle4f(roll, 0f, 0f, 1f)));
    }

    /**
     * Returns the angle of a fan segment within the 160 degree arc ahead of
     * the player's yaw.
     */
    private static double arcAngle(final double yaw, final int segments, final int index) {
        return yaw + Math.toRadians(-80 + index * (160.0 / (segments - 1)));
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
