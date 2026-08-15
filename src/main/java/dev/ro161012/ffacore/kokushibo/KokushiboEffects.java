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
        final int bladeCount = Math.max(8, Math.max(crescents, (int) Math.round(maxRadius)));
        final int innerCount = bladeCount / 3;
        final int midCount = bladeCount / 3;
        final int outerCount = bladeCount - innerCount - midCount;
        final int blockRing = Math.max(24, (int) Math.round(Math.PI * 2.0 * maxRadius / 2.0));

        final List<Display> displays = new ArrayList<>();
        for (int i = 0; i < innerCount; i++) {
            displays.add(spawnCrescent(player, eye));
        }
        for (int i = 0; i < midCount; i++) {
            displays.add(spawnCrescent(player, eye));
        }
        for (int i = 0; i < outerCount; i++) {
            displays.add(spawnCrescent(player, eye));
        }
        for (int i = 0; i < blockRing; i++) {
            displays.add(spawnBlock(player, eye, Material.PURPLE_STAINED_GLASS, 0.4f));
        }

        final Set<UUID> struck = new HashSet<>();
        player.getWorld().spawnParticle(Particle.WITCH, eye, 90, 1.5, 1.0, 1.5, 0.03);
        player.getWorld().spawnParticle(Particle.END_ROD, eye, 40, 1.5, 1.0, 1.5, 0.02);

        animate(plugin, displays, totalTicks, tick -> {
            final double progress = tick / (double) totalTicks;
            final double radius = startRadius + (maxRadius - startRadius) * progress;
            final double spin = Math.toRadians(tick * 34);

            // Inner ring: tight, fast orbit with a white trail streaking behind.
            for (int i = 0; i < innerCount; i++) {
                final double angle = Math.toRadians(i * (360.0 / innerCount)) + spin;
                final double orbit = radius * 0.55;
                final double bob = Math.sin(tick * 0.45 + i) * 0.6;
                displays.get(i).teleport(eye.clone().add(
                        Math.sin(angle) * orbit, -0.2 + bob, Math.cos(angle) * orbit));
                final float scale = 1.3f + (float) (progress * 1.4)
                        + (float) Math.sin(tick * 0.5 + i) * 0.25f;
                setScale(displays.get(i), scale, scale, scale);
                trail(displays.get(i));
            }

            // Mid ring: counter-rotating, medium orbit, incrementally growing.
            for (int i = 0; i < midCount; i++) {
                final double angle = -spin + Math.toRadians(i * (360.0 / midCount));
                final double orbit = radius * 0.78;
                final double bob = Math.sin(tick * 0.35 + i) * 0.7;
                displays.get(innerCount + i).teleport(eye.clone().add(
                        Math.sin(angle) * orbit, -0.4 + bob, Math.cos(angle) * orbit));
                final float scale = 1.6f + (float) (progress * 1.6)
                        + (float) Math.sin(tick * 0.5 + i) * 0.3f;
                setScale(displays.get(innerCount + i), scale, scale, scale);
                trail(displays.get(innerCount + i));
            }

            // Outer ring: widest, slowest, biggest crescents.
            for (int i = 0; i < outerCount; i++) {
                final double angle = spin * 0.8 + Math.toRadians(i * (360.0 / outerCount));
                final double bob = Math.sin(tick * 0.3 + i) * 0.8;
                displays.get(innerCount + midCount + i).teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.6 + bob, Math.cos(angle) * radius));
                final float scale = 1.9f + (float) (progress * 1.8)
                        + (float) Math.sin(tick * 0.5 + i) * 0.35f;
                setScale(displays.get(innerCount + midCount + i), scale, scale, scale);
                trail(displays.get(innerCount + midCount + i));
            }

            // Counter-rotating purple glass ring woven through the crescents.
            for (int i = 0; i < blockRing; i++) {
                final double angle = Math.toRadians(i * (360.0 / blockRing)) + spin * 1.2;
                displays.get(bladeCount + i).teleport(eye.clone().add(
                        Math.sin(angle) * radius * 0.8, -0.3, Math.cos(angle) * radius * 0.8));
                setScale(displays.get(bladeCount + i), 0.4f, 0.4f, 0.4f);
            }

            if (tick % 2 == 0) {
                eye.getWorld().spawnParticle(Particle.END_ROD,
                        eye.clone().add(0, -0.3, 0), 4, radius, 0.3, radius, 0.01);
                eye.getWorld().spawnParticle(Particle.WITCH,
                        eye.clone().add(0, -0.3, 0), 3, radius, 0.4, radius, 0.01);
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
     * Spawns a billboarded crescent item display facing the camera. Only its
     * position and scale are animated, so it never flickers.
     */
    private static ItemDisplay spawnCrescent(final Player player, final Location location) {
        final ItemDisplay display = player.getWorld().spawn(location, ItemDisplay.class);
        display.setItemStack(KokushiboSword.crescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        setScale(display, 1.4f, 1.4f, 1.4f);
        return display;
    }

    /**
     * Spawns a full-bright glowing purple glass block display.
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
