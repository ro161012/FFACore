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
 * Renders the Kokoshibos Sword ability visuals as glowing purple geometry and
 * crescent displays.
 *
 * <p>Catastrophe expands a ring of purple glass blocks outward from the
 * caster, Moonbow fires white crescent gleams straight up one by one, and the
 * passive fires a single drifting crescent. The glass is full-bright
 * translucent rendered through the companion core shader, and every display
 * entity is removed when its animation ends — nothing lingers.
 */
public final class KokushiboEffects {

    private static final double TAU = Math.PI * 2.0;

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — an
     * omni-directional ring of purple energy expanding outward in every
     * direction, growing as it sweeps.
     *
     * <p>The {@code onStrike} callback receives each living target exactly once
     * as the expanding front reaches it.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster
     * @param maxRadius the radius the ring expands out to, in blocks
     * @param crescents number of glass blocks in the expanding ring
     * @param onStrike  called once per target as the ring reaches it
     * @param ticks     how long the ring plays (shorter = snappier)
     */
    public static void playCatastrophe(final JavaPlugin plugin, final Player player,
                                       final double maxRadius, final int crescents,
                                       final Consumer<LivingEntity> onStrike,
                                       final int ticks) {
        final Location center = player.getEyeLocation().add(0.0, -0.5, 0.0);
        final int totalTicks = Math.max(1, ticks);
        final double startRadius = 1.6;
        final int blocks = Math.max(16, crescents);
        final int outerCount = blocks * 2 / 3;
        final int innerCount = blocks - outerCount;

        final List<Display> ring = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            ring.add(spawnBlockAt(center, Material.PURPLE_STAINED_GLASS));
        }

        final Set<UUID> struck = new HashSet<>();
        center.getWorld().spawnParticle(Particle.WITCH, center, 40, 1.0, 0.6, 1.0, 0.02);

        animate(plugin, ring, totalTicks, tick -> {
            final double progress = tick / (double) totalTicks;
            final double radius = startRadius + (maxRadius - startRadius) * progress;

            // Outer ring: a clean expanding band of purple glass.
            for (int i = 0; i < outerCount; i++) {
                final double angle = i * (TAU / outerCount);
                ring.get(i).teleport(center.clone().add(
                        Math.sin(angle) * radius, 0.0, Math.cos(angle) * radius));
                final float scale = 0.4f + (float) progress * 0.5f;
                setScale(ring.get(i), scale, 0.22f, scale);
            }

            // Inner halo: a tighter, slightly offset band inside it.
            for (int i = 0; i < innerCount; i++) {
                final double angle = i * (TAU / innerCount) + TAU * 0.5;
                ring.get(outerCount + i).teleport(center.clone().add(
                        Math.sin(angle) * radius * 0.62, 0.0,
                        Math.cos(angle) * radius * 0.62));
                final float scale = 0.3f + (float) progress * 0.4f;
                setScale(ring.get(outerCount + i), scale, 0.18f, scale);
            }

            // Sparse sparkle drifting outward with the ring.
            if (tick % 3 == 0) {
                center.getWorld().spawnParticle(Particle.END_ROD, center, 2,
                        radius, 0.2, radius, 0.005);
            }

            for (final Entity entity : center.getWorld().getNearbyEntities(
                    center, radius, radius, radius)) {
                if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                    continue;
                }
                final double dx = living.getLocation().getX() - center.getX();
                final double dz = living.getLocation().getZ() - center.getZ();
                final double dy = living.getLocation().getY() - center.getY();
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
     * Fires one white crescent gleam straight up from the caster, dealing
     * true damage (through the {@code onHit} callback) to every living target
     * it passes on the way up. Used by the Sixteenth Form, Moonbow, Half
     * Moon.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster (excluded from hits)
     * @param riseTicks how many ticks the crescent takes to rise
     * @param onHit     called once per target the crescent touches
     */
    public static void fireMoonbowCrescent(final JavaPlugin plugin, final Player player,
                                           final int riseTicks,
                                           final Consumer<LivingEntity> onHit) {
        final Location origin = player.getLocation().add(0.0, 0.6, 0.0);
        final ItemDisplay display = player.getWorld().spawn(origin, ItemDisplay.class);
        display.setItemStack(KokushiboSword.whiteCrescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        setScale(display, 1.6f, 1.6f, 1.6f);

        final int duration = Math.max(4, riseTicks);
        final double rise = 10.0 / duration;
        final Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            private int tick;
            private final Location pos = origin.clone();

            @Override
            public void run() {
                if (tick++ >= duration || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                pos.add(0.0, rise, 0.0);
                display.teleport(pos);
                final float scale = Math.max(0.6f, 1.6f - tick * 0.05f);
                setScale(display, scale, scale, scale);
                // White gleam trail behind the rising crescent.
                pos.getWorld().spawnParticle(Particle.END_ROD, pos, 3,
                        0.12, 0.12, 0.12, 0.0);

                for (final Entity entity : pos.getWorld().getNearbyEntities(
                        pos, 1.1, 1.1, 1.1)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    if (!hit.add(living.getUniqueId())) {
                        continue;
                    }
                    onHit.accept(living);
                }
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
     * Spawns a full-bright glowing purple glass block display at a world
     * location.
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
}
