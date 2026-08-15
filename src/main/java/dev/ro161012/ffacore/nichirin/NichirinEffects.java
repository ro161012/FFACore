package dev.ro161012.ffacore.nichirin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * Renders the Nichirin Blade ability visuals as the two canonical Hinokami
 * Kagura forms, built from full-bright translucent glass block displays and
 * tinted by the pack's solar-fire core shader.
 *
 * <p><b>Clear Blue Sky</b> is a continuous 360&deg; disc of solar fire that
 * spins around the caster's waist: an orange-red core ring inside a
 * yellow-white outer ring, with a lingering afterimage and a trail of     * sparkling embers. <b>Dancing Flash</b> opens with an electric launch (yellow sparks
 * crackling at the feet), sweeps a massive vertical crescent of fire forward,
 * and detonates into an expanding yellow/orange shockwave. Every display is
 * removed when the animation ends.
 */
public final class NichirinEffects {

    private static final double TAU = Math.PI * 2.0;

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a spinning 360&deg; horizontal solar disc around
     * the caster's waist — orange-red core, yellow-white rim, a lagging
     * afterimage, and lava that shoots outward before fading.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param ticks  how long the disc spins (shorter = snappier)
     * @param radius final reach of the ring in blocks
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player,
                                        final int ticks, final double radius) {
        final Location waist = player.getEyeLocation().add(0.0, -1.0, 0.0);
        final int core = 28;
        final int rim = 28;
        final int afterimage = 24;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int i = 0; i < core; i++) {
            displays.add(spawnBlock(player, waist, Material.ORANGE_STAINED_GLASS, 0.6f));
        }
        for (int i = 0; i < rim; i++) {
            displays.add(spawnBlock(player, waist, Material.YELLOW_STAINED_GLASS, 0.65f));
        }
        for (int i = 0; i < afterimage; i++) {
            displays.add(spawnBlock(player, waist, Material.YELLOW_STAINED_GLASS, 0.45f));
        }

        animate(plugin, displays, ticks, tick -> {
            final double progress = tick / (double) Math.max(1, ticks - 1);
            final double ringRadius = 0.8 + progress * radius;
            final double spin = progress * TAU;

            for (int i = 0; i < core; i++) {
                final double angle = spin + i * (TAU / core);
                displays.get(i).teleport(waist.clone().add(
                        Math.sin(angle) * ringRadius * 0.72, 0.0,
                        Math.cos(angle) * ringRadius * 0.72));
                setScale(displays.get(i), 0.6f, 0.45f, 0.6f);
            }
            for (int i = 0; i < rim; i++) {
                final double angle = -spin + i * (TAU / rim);
                displays.get(core + i).teleport(waist.clone().add(
                        Math.sin(angle) * ringRadius, 0.15 * Math.sin(progress * TAU),
                        Math.cos(angle) * ringRadius));
                setScale(displays.get(core + i), 0.65f, 0.4f, 0.65f);
            }
            // Afterimage: trails the rim, spins slower, and shrinks as it fades.
            final double ghostRadius = ringRadius * 0.82;
            final float ghostScale = (float) (0.45 * (1.0 - progress * 0.6));
            for (int i = 0; i < afterimage; i++) {
                final double angle = -spin * 0.7 + i * (TAU / afterimage);
                displays.get(core + rim + i).teleport(waist.clone().add(
                        Math.sin(angle) * ghostRadius, -0.1, Math.cos(angle) * ghostRadius));
                setScale(displays.get(core + rim + i), ghostScale, ghostScale * 0.7f, ghostScale);
            }

            // Lava shoots outward from the rim, then fades like heat haze.
            final Location edge = waist.clone().add(0.0, 0.15, 0.0);
            edge.getWorld().spawnParticle(Particle.LAVA, edge, 14, ringRadius, 0.3, ringRadius, 0.08);
            edge.getWorld().spawnParticle(Particle.FALLING_LAVA, waist, 6, ringRadius, 0.2, ringRadius, 0.02);
            edge.getWorld().spawnParticle(Particle.FLAME, edge, 8, ringRadius * 0.6, 0.3, ringRadius * 0.6, 0.03);
            edge.getWorld().spawnParticle(Particle.END_ROD, edge, 10, ringRadius, 0.4, ringRadius, 0.01);
            edge.getWorld().spawnParticle(Particle.SMOKE, edge, 4, ringRadius * 0.5, 0.3, ringRadius * 0.5, 0.0);
        });
    }

    /**
     * Plays Dancing Flash: an electric launch at the feet, then a massive vertical
     * crescent of solar fire sweeping forward, detonating into an expanding
     * yellow/orange shockwave.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param ticks  how long the sequence plays (shorter = snappier)
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player,
                                final int ticks) {
        final Location eye = player.getEyeLocation();
        final Vector facing = horizontalFacing(player);
        final Vector up = new Vector(0.0, 1.0, 0.0);

        final int crescentOuter = 16;
        final int crescentInner = 12;
        final int shockwave = 20;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int i = 0; i < crescentOuter; i++) {
            displays.add(spawnBlock(player, eye, Material.YELLOW_STAINED_GLASS, 0.55f));
        }
        for (int i = 0; i < crescentInner; i++) {
            displays.add(spawnBlock(player, eye, Material.ORANGE_STAINED_GLASS, 0.45f));
        }
        for (int i = 0; i < shockwave; i++) {
            displays.add(spawnBlock(player, eye.clone().add(0.0, -1.0, 0.0),
                    i % 2 == 0 ? Material.YELLOW_STAINED_GLASS : Material.ORANGE_STAINED_GLASS,
                    0.5f));
        }

        player.getWorld().playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);

        animate(plugin, displays, ticks, tick -> {
            final double progress = tick / (double) Math.max(1, ticks - 1);
            final boolean launching = progress < 0.2;

            // Electric launch: yellow lightning crackles around the feet.
            if (launching) {
                final Location feet = player.getLocation();
                feet.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, feet.add(0.0, 0.2, 0.0),
                        8, 0.5, 0.1, 0.5, 0.02);
                feet.getWorld().spawnParticle(Particle.FIREWORK, feet.add(0.0, 0.6, 0.0),
                        5, 0.4, 0.3, 0.4, 0.01);
            }

            // The crescent sweeps forward: a vertical arc of fire in the
            // facing plane, growing from the body outward.
            final double slashProgress = clamp((progress - 0.15) / 0.6);
            final double slashRadius = 0.4 + slashProgress * 3.8;
            for (int i = 0; i < crescentOuter; i++) {
                final double t = i / (double) (crescentOuter - 1);
                final double angle = Math.toRadians(-80.0 + t * 160.0);
                final Vector dir = facing.clone().multiply(Math.cos(angle))
                        .add(up.clone().multiply(Math.sin(angle)));
                displays.get(i).teleport(eye.clone().add(dir.multiply(slashRadius)));
                setScale(displays.get(i), 0.55f, 0.55f, 0.55f);
            }
            for (int i = 0; i < crescentInner; i++) {
                final double t = i / (double) (crescentInner - 1);
                final double angle = Math.toRadians(-70.0 + t * 140.0);
                final Vector dir = facing.clone().multiply(Math.cos(angle))
                        .add(up.clone().multiply(Math.sin(angle)));
                displays.get(crescentOuter + i).teleport(
                        eye.clone().add(dir.multiply(slashRadius * 0.8)));
                setScale(displays.get(crescentOuter + i), 0.45f, 0.45f, 0.45f);
            }

            // Impact shockwave: a horizontal ring spreads across the ground,
            // merging the yellow lightning and orange fire.
            final double impactProgress = clamp((progress - 0.75) / 0.25);
            final double ringRadius = 0.5 + impactProgress * 3.0;
            for (int i = 0; i < shockwave; i++) {
                final double angle = i * (TAU / shockwave);
                displays.get(crescentOuter + crescentInner + i).teleport(
                        eye.clone().add(
                                Math.sin(angle) * ringRadius, -1.0, Math.cos(angle) * ringRadius));
                setScale(displays.get(crescentOuter + crescentInner + i), 0.5f, 0.2f, 0.5f);
            }

            // Flame trail along the crescent and the final electric/fire burst.
            final Location tip = eye.clone().add(facing.clone().multiply(slashRadius));
            tip.getWorld().spawnParticle(Particle.FLAME, tip, 8, 0.6, 0.6, 0.6, 0.04);
            tip.getWorld().spawnParticle(Particle.END_ROD, tip, 5, 0.5, 0.5, 0.5, 0.02);
            if (impactProgress > 0.0) {
                final Location ground = eye.clone().add(0.0, -1.0, 0.0);
                ground.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, ground, 6,
                        ringRadius, 0.3, ringRadius, 0.02);
                ground.getWorld().spawnParticle(Particle.LAVA, ground, 8,
                        ringRadius * 0.6, 0.2, ringRadius * 0.6, 0.02);
            }
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

    /**
     * Clamps a value into the [0, 1] range.
     */
    private static double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
