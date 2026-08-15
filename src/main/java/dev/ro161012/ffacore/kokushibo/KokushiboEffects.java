package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
 * <p>Catastrophe whirls a ring of crescent blades (billboard item displays)
 * outward alongside a spinning ring of purple glass cubes, Moonbow stamps a
 * growing crescent at each strike point, and the passive fires a single
 * drifting crescent. The glass is full-bright translucent rendered through
 * the companion core shader, and every display entity is removed when its
 * animation ends — nothing lingers.
 */
public final class KokushiboEffects {

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — an
     * omni-directional vortex of crescent blades that whirls outward while
     * expanding.
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
        final int bladeCount = Math.max(8, crescents);

        final List<Display> displays = new ArrayList<>();
        for (int i = 0; i < bladeCount; i++) {
            displays.add(spawnCrescent(player, eye));
        }
        final int blockRing = 24;
        for (int i = 0; i < blockRing; i++) {
            displays.add(spawnBlock(player, eye, Material.PURPLE_STAINED_GLASS, 0.4f));
        }

        final Set<UUID> struck = new HashSet<>();
        player.getWorld().spawnParticle(Particle.WITCH, eye, 60, 1.5, 0.8, 1.5, 0.02);

        animate(plugin, displays, totalTicks, tick -> {
            final double progress = tick / (double) totalTicks;
            final double radius = startRadius + (maxRadius - startRadius) * progress;
            final double spin = Math.toRadians(tick * 28);

            for (int i = 0; i < bladeCount; i++) {
                final double angle = Math.toRadians(i * (360.0 / bladeCount)) + spin;
                final Display blade = displays.get(i);
                blade.teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.4 + tick * 0.05, Math.cos(angle) * radius));
                final float scale = 1.4f + (float) Math.sin(tick * 0.5 + i) * 0.3f;
                setScale(blade, scale, scale, scale);
            }

            for (int i = 0; i < blockRing; i++) {
                final double angle = Math.toRadians(i * (360.0 / blockRing)) + spin * 1.2;
                final Display block = displays.get(bladeCount + i);
                block.teleport(eye.clone().add(
                        Math.sin(angle) * radius * 0.85, -0.3, Math.cos(angle) * radius * 0.85));
                setScale(block, 0.4f, 0.4f, 0.4f);
            }

            if (tick % 2 == 0) {
                eye.getWorld().spawnParticle(Particle.END_ROD,
                        eye.clone().add(0, -0.3, 0), 2, radius, 0.3, radius, 0.01);
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
     * Stamps a crescent at the given location after a tick delay, growing it
     * briefly before it disappears.
     *
     * @param plugin      owning plugin (for the scheduler)
     * @param location    the strike point
     * @param delayTicks  ticks to wait before the strike appears
     * @param ticks       how long the crescent grows before fading
     */
    public static void strikeCrescent(final JavaPlugin plugin, final Location location,
                                      final long delayTicks, final int ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (location.getWorld() == null) {
                    return;
                }
                final ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class);
                display.setItemStack(KokushiboSword.crescentItem());
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);

                location.getWorld().spawnParticle(Particle.WITCH, location, 20,
                        0.4, 0.4, 0.4, 0.02);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 10,
                        0.3, 0.3, 0.3, 0.02);

                new BukkitRunnable() {
                    private int tick;

                    @Override
                    public void run() {
                        if (tick >= ticks) {
                            display.remove();
                            cancel();
                            return;
                        }
                        final float scale = 0.6f + tick * 0.25f;
                        setScale(display, scale, scale, scale);
                        tick++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, delayTicks);
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
}
