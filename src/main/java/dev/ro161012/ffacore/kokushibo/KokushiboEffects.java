package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Renders the Kokoshibos Sword ability visuals as crescent displays and
 * glowing purple geometry.
 *
 * <p>Catastrophe whirls a multitude of crescent blades outward in two
 * counter-rotating rings that incrementally grow in size, Moonbow brings six
 * curved slashes crashing down in front of the caster — each leaving a crater
 * ring and scattered crescent blades — and the passive fires a single drifting
 * crescent. The glass is full-bright translucent rendered through the
 * companion core shader, and every display entity is removed when its
 * animation ends — nothing lingers.
 */
public final class KokushiboEffects {

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — a
     * multitude of curved circular slashes expanding outward in every
     * direction, incrementally growing into an omni-directional vortex of
     * crescent moon blades.
     *
     * <p>The {@code onStrike} callback receives each living target exactly once
     * as the expanding front reaches it.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster
     * @param maxRadius the radius the vortex expands out to, in blocks
     * @param crescents number of crescent blades in the vortex
     * @param onStrike  called once per target as the vortex reaches it
     * @param ticks     how long the vortex plays (shorter = snappier)
     */
    public static void playCatastrophe(final JavaPlugin plugin, final Player player,
                                       final double maxRadius, final int crescents,
                                       final Consumer<LivingEntity> onStrike,
                                       final int ticks) {
        final Location eye = player.getEyeLocation();
        final int totalTicks = Math.max(1, ticks);
        final double startRadius = 1.6;
        final int bladeCount = Math.max(12, Math.max(crescents, (int) Math.round(maxRadius)));
        final int outerCount = bladeCount * 2 / 3;
        final int innerCount = bladeCount - outerCount;

        final List<Display> displays = new ArrayList<>();
        for (int i = 0; i < outerCount; i++) {
            displays.add(spawnCrescent(player, eye));
        }
        for (int i = 0; i < innerCount; i++) {
            displays.add(spawnCrescent(player, eye));
        }

        final Set<UUID> struck = new HashSet<>();
        player.getWorld().spawnParticle(Particle.WITCH, eye, 40, 1.0, 0.6, 1.0, 0.02);

        animate(plugin, displays, totalTicks, tick -> {
            final double progress = tick / (double) totalTicks;
            final double radius = startRadius + (maxRadius - startRadius) * progress;
            final double spin = Math.toRadians(tick * 26);

            // Outer ring: one clean, uniform whirl of crescents spinning flat
            // around the vertical axis as it grows outward.
            for (int i = 0; i < outerCount; i++) {
                final double angle = Math.toRadians(i * (360.0 / outerCount)) + spin;
                displays.get(i).teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.4, Math.cos(angle) * radius));
                final float scale = (float) (1.6 + progress * 1.8);
                setSpinScale(displays.get(i), scale, angle);
                trail(displays.get(i));
            }

            // Inner ring: a tighter counter-rotating halo, slightly smaller.
            for (int i = 0; i < innerCount; i++) {
                final double angle = Math.toRadians(i * (360.0 / innerCount)) - spin * 1.25;
                final double orbit = radius * 0.62;
                displays.get(outerCount + i).teleport(eye.clone().add(
                        Math.sin(angle) * orbit, -0.35, Math.cos(angle) * orbit));
                final float scale = (float) (1.15 + progress * 1.4);
                setSpinScale(displays.get(outerCount + i), scale, angle);
            }

            // Sparse sparkle drifting outward with the vortex.
            if (tick % 3 == 0) {
                eye.getWorld().spawnParticle(Particle.END_ROD,
                        eye.clone().add(0, -0.3, 0), 2, radius, 0.2, radius, 0.005);
            }

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
        });
    }

    /**
     * Brings one curved slash of the Sixteenth Form crashing down onto the
     * given location: a crescent falls from above, then detonates into a
     * crater ring and a scatter of crescent moon blades.
     *
     * @param plugin     owning plugin (for the scheduler)
     * @param location   the impact point
     * @param delayTicks ticks to wait before the slash begins to fall
     * @param ticks      how long the crescent falls before impact
     */
    public static void strikeCrescent(final JavaPlugin plugin, final Location location,
                                      final long delayTicks, final int ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (location.getWorld() == null) {
                    return;
                }
                final World world = location.getWorld();
                final double fallHeight = 10.0;
                final Location start = location.clone().add(0.0, fallHeight, 0.0);
                final ItemDisplay display = world.spawn(start, ItemDisplay.class);
                display.setItemStack(KokushiboSword.crescentItem());
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);

                new BukkitRunnable() {
                    private int tick;

                    @Override
                    public void run() {
                        if (tick >= ticks) {
                            display.remove();
                            crashImpact(plugin, location);
                            cancel();
                            return;
                        }
                        final double progress = tick / (double) Math.max(1, ticks - 1);
                        final double fall = (1.0 - progress) * fallHeight;
                        display.teleport(location.clone().add(0.0, fall, 0.0));
                        final float scale = 1.0f + (float) progress * 1.6f;
                        setScale(display, scale, scale, scale);
                        // White streak trailing the falling crescent.
                        world.spawnParticle(Particle.END_ROD, display.getLocation(),
                                3, 0.15, 0.15, 0.15, 0.01);
                        world.spawnParticle(Particle.WITCH, display.getLocation(),
                                2, 0.15, 0.15, 0.15, 0.01);
                        tick++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * Detonates a crashing slash: a debris burst, a crater rim, and a scatter
     * of smaller crescent moon blades.
     */
    private static void crashImpact(final JavaPlugin plugin, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.WITCH, location, 30, 0.5, 0.3, 0.5, 0.05);
        world.spawnParticle(Particle.END_ROD, location, 12, 0.4, 0.4, 0.4, 0.03);
        world.spawnParticle(Particle.POOF, location, 20, 0.5, 0.2, 0.5, 0.04);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location, 8, 0.4, 0.2, 0.4, 0.01);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.9f);

        for (int i = 0; i < 3; i++) {
            scatterCrescent(plugin, world, location);
        }
        craterRing(plugin, location);
    }

    /**
     * Fires one small crescent blade outward from an impact point, shrinking
     * as it travels.
     */
    private static void scatterCrescent(final JavaPlugin plugin, final World world,
                                        final Location location) {
        final ItemDisplay display = world.spawn(
                location.clone().add(0.0, 0.3, 0.0), ItemDisplay.class);
        display.setItemStack(KokushiboSword.crescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);

        final Vector velocity = new Vector(rand(-1.0, 1.0), rand(0.2, 0.9), rand(-1.0, 1.0))
                .normalize().multiply(0.35);

        new BukkitRunnable() {
            private int tick;
            private final Location pos = location.clone().add(0.0, 0.3, 0.0);

            @Override
            public void run() {
                if (tick++ >= 12 || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                pos.add(velocity);
                velocity.multiply(0.9);
                display.teleport(pos);
                final float scale = 1.0f - tick * 0.06f;
                setScale(display, scale, scale, scale);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Draws a brief glowing crater rim at an impact point: a flat purple ring
     * that expands and fades.
     */
    private static void craterRing(final JavaPlugin plugin, final Location location) {
        final List<BlockDisplay> rim = new ArrayList<>();
        final int blocks = 12;
        for (int i = 0; i < blocks; i++) {
            rim.add(spawnBlockAt(location, Material.PURPLE_STAINED_GLASS));
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= 10) {
                    rim.forEach(BlockDisplay::remove);
                    cancel();
                    return;
                }
                final double radius = 0.3 + tick * 0.16;
                for (int i = 0; i < rim.size(); i++) {
                    final double angle = i * (Math.PI * 2.0 / rim.size());
                    rim.get(i).teleport(location.clone().add(
                            Math.sin(angle) * radius, 0.03, Math.cos(angle) * radius));
                    setScale(rim.get(i), 0.35f, 0.06f, 0.35f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Fires a single drifting crescent along the given direction, dealing
     * true damage (through the {@code onHit} callback) to the first living
     * target it touches. Used by the Upper Moon One passive.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster (excluded from hits)
     * @param origin    spawn location of the crescent
     * @param direction travel direction (normalised)
     * @param onHit     called once with the target when the blade connects
     * @param speed     flight speed multiplier (1.0 = default)
     */
    public static void fireCrescent(final JavaPlugin plugin, final Player player,
                                    final Location origin, final Vector direction,
                                    final Consumer<LivingEntity> onHit,
                                    final double speed) {
        final ItemDisplay display = player.getWorld().spawn(origin, ItemDisplay.class);
        display.setItemStack(KokushiboSword.crescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);

        final double baseSpeed = 0.55 * Math.max(0.1, speed);
        final Vector velocity = direction.clone().normalize().multiply(baseSpeed);
        final Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            private int tick;
            private final Location pos = origin.clone();

            @Override
            public void run() {
                if (tick++ >= 26 || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                velocity.add(new Vector(
                        (Math.random() - 0.5) * 0.06,
                        (Math.random() - 0.5) * 0.05,
                        (Math.random() - 0.5) * 0.06));
                velocity.normalize().multiply(baseSpeed);
                pos.add(velocity);

                if (pos.getBlock().getType().isSolid()) {
                    pos.getWorld().spawnParticle(Particle.WITCH, pos, 10,
                            0.2, 0.2, 0.2, 0.02);
                    display.remove();
                    cancel();
                    return;
                }

                display.teleport(pos);
                final float scale = 1.0f + (float) Math.sin(tick * 0.6) * 0.25f;
                setScale(display, scale, scale, scale);
                pos.getWorld().spawnParticle(Particle.WITCH, pos, 2,
                        0.1, 0.1, 0.1, 0.02);
                pos.getWorld().spawnParticle(Particle.END_ROD, pos, 1,
                        0.1, 0.1, 0.1, 0.01);

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
                    display.remove();
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Leaves a single white spark trail behind a moving crescent.
     */
    private static void trail(final Display display) {
        final Location location = display.getLocation();
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.END_ROD, location, 1,
                    0.05, 0.05, 0.05, 0.0);
        }
    }

    /**
     * How far the Catastrophe crescents lean up from fully horizontal, so they
     * read as spinning blades without vanishing edge-on.
     */
    private static final float CRESCENT_TILT = 0.55f;

    /**
     * Spawns a crescent item display with a fixed (non-billboarded) orientation
     * so the Catastrophe vortex can spin it flat around the vertical axis.
     */
    private static ItemDisplay spawnCrescent(final Player player, final Location location) {
        final ItemDisplay display = player.getWorld().spawn(location, ItemDisplay.class);
        display.setItemStack(KokushiboSword.crescentItem());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        setScale(display, 1.4f, 1.4f, 1.4f);
        return display;
    }

    /**
     * Applies a scale plus a horizontal spin (rotation about the vertical
     * axis) to a crescent, tilted up slightly so the flat blade stays visible.
     */
    private static void setSpinScale(final Display display, final float scale,
                                     final double yaw) {
        final Quaternionf rotation = new Quaternionf()
                .rotateY((float) yaw)
                .rotateX(CRESCENT_TILT);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(rotation),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
    }

    /**
     * Spawns a full-bright glowing purple glass block display at a world
     * location (used for the crater rim, which has no player reference).
     */
    private static BlockDisplay spawnBlockAt(final Location location, final Material material) {
        final BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        setScale(display, 0.35f, 0.06f, 0.35f);
        return display;
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
    private static void animate(final JavaPlugin plugin, final List<Display> displays,
                                final int totalTicks, final IntConsumer ticker) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    displays.forEach(Display::remove);
                    cancel();
                    return;
                }
                ticker.accept(tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Returns a random double in the inclusive range [min, max].
     */
    private static double rand(final double min, final double max) {
        return min + Math.random() * (max - min);
    }
}
