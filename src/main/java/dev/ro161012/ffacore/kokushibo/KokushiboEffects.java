package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Location;
import org.bukkit.Particle;
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

/**
 * Spawns the display-entity visuals for the Kokoshibos Sword abilities.
 *
 * <p>The Catastrophe vortex and the moonbow strikes are drawn with
 * {@link ItemDisplay} entities carrying the kokushibo crescent model (custom
 * model data 2). Everything is full-bright and short-lived, and the shared
 * core shader applies the display tint so the crescents glow.
 */
public final class KokushiboEffects {

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — an
     * omni-directional vortex of crescent blades that whirls around the
     * player while expanding outward, growing as it spins.
     *
     * <p>Each crescent sweeps entities in its path exactly once per cast; the
     * {@code onStrike} callback receives every living target the moment the
     * vortex touches it, so the listener can apply true damage.
     *
     * @param plugin     owning plugin (for the scheduler)
     * @param player     the caster
     * @param maxRadius  the radius the vortex expands out to, in blocks
     * @param crescents  number of crescent blades in the vortex
     * @param onStrike   called once per target as the vortex reaches it
     */
    public static void playCatastrophe(final JavaPlugin plugin, final Player player,
                                       final double maxRadius, final int crescents,
                                       final Consumer<LivingEntity> onStrike) {
        final Location eye = player.getEyeLocation();
        final int totalTicks = 40;
        final double startRadius = 1.6;

        final List<ItemDisplay> displays = new ArrayList<>();
        for (int i = 0; i < crescents; i++) {
            final ItemDisplay display = player.getWorld().spawn(eye, ItemDisplay.class);
            display.setItemStack(KokushiboSword.crescentItem());
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration(1);
            display.setInterpolationDelay(0);
            displays.add(display);
        }

        final Set<UUID> struck = new HashSet<>();

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    displays.forEach(ItemDisplay::remove);
                    cancel();
                    return;
                }
                final double progress = (double) tick / totalTicks;
                final double radius = startRadius + (maxRadius - startRadius) * progress;
                final double spin = Math.toRadians(tick * 30);
                final double rise = tick * 0.04;
                for (int i = 0; i < displays.size(); i++) {
                    final double angle = Math.toRadians(i * (360.0 / displays.size())) + spin;
                    final ItemDisplay display = displays.get(i);
                    final Location loc = eye.clone().add(
                            Math.sin(angle) * radius, -0.4 + rise, Math.cos(angle) * radius);
                    display.teleport(loc);
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f((float) -angle, 0f, 1f, 0f),
                            new Vector3f(1.5f, 1.5f, 1.5f),
                            new AxisAngle4f((float) (tick * 0.6f), 0f, 0f, 1f)));
                    for (final Entity entity : loc.getWorld().getNearbyEntities(
                            loc, 1.8, 1.8, 1.8)) {
                        if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                            continue;
                        }
                        if (struck.add(living.getUniqueId())) {
                            onStrike.accept(living);
                        }
                    }
                }
                eye.getWorld().spawnParticle(Particle.WITCH, eye.clone().add(0, -0.4, 0),
                        crescents, radius, 0.6, radius, 0.02);
                eye.getWorld().spawnParticle(Particle.END_ROD, eye.clone().add(0, -0.4, 0),
                        4, radius, 0.4, radius, 0.02);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Strikes a moon crescent at the given location after a tick delay,
     * spinning and shrinking the crescent before removing it.
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
                final ItemDisplay display = location.getWorld() != null
                        ? location.getWorld().spawn(location, ItemDisplay.class)
                        : null;
                if (display == null) {
                    return;
                }
                display.setItemStack(KokushiboSword.crescentItem());
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);

                location.getWorld().spawnParticle(Particle.END_ROD, location, 24,
                        0.5, 0.5, 0.5, 0.02);
                location.getWorld().spawnParticle(Particle.WITCH, location, 20,
                        0.5, 0.5, 0.5, 0.02);

                new BukkitRunnable() {
                    private int tick;

                    @Override
                    public void run() {
                        if (tick >= 10) {
                            display.remove();
                            cancel();
                            return;
                        }
                        final float scale = 1.7f - (tick * 0.12f);
                        display.setTransformation(new Transformation(
                                new Vector3f(0f, tick * 0.1f, 0f),
                                new AxisAngle4f((float) (tick * 0.8f), 0f, 1f, 0f),
                                new Vector3f(scale, scale, scale),
                                new AxisAngle4f(0f, 0f, 0f, 1f)));
                        tick++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * Fires a single crescent moon blade that flies along the given direction,
     * drifting chaotically as it travels. It deals true damage (through the
     * {@code onHit} callback) to the first living target it touches and then
     * disappears. Used by the Upper Moon One passive.
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
        final ItemDisplay display = player.getWorld().spawn(origin, ItemDisplay.class);
        display.setItemStack(KokushiboSword.crescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);

        final Vector velocity = direction.clone().normalize().multiply(0.55);
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
                // Chaotic drift: a slight random wobble every tick.
                velocity.add(new Vector(
                        (Math.random() - 0.5) * 0.06,
                        (Math.random() - 0.5) * 0.05,
                        (Math.random() - 0.5) * 0.06));
                velocity.normalize().multiply(0.55);
                pos.add(velocity);

                if (pos.getBlock().getType().isSolid()) {
                    pos.getWorld().spawnParticle(Particle.WITCH, pos, 8,
                            0.2, 0.2, 0.2, 0.02);
                    display.remove();
                    cancel();
                    return;
                }

                display.teleport(pos);
                final float spin = tick * 0.9f;
                final float scale = 0.9f + (float) Math.sin(tick * 0.6) * 0.25f;
                display.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(spin, 0f, 1f, 0f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f((float) (tick * 0.4), 0f, 0f, 1f)));

                pos.getWorld().spawnParticle(Particle.WITCH, pos, 2,
                        0.1, 0.1, 0.1, 0.01);
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
}
