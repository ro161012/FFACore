package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Renders the Kokushibo Sword ability visuals.
 *
 * <p>Catastrophe shoots out expanding rings of full-purple moon-energy
 * particles, Moonbow fires a white crescent gleam where the caster aims, and
 * the passive fires a single drifting crescent. Every display entity is
 * removed when its animation ends — nothing lingers.
 */
public final class KokushiboEffects {

    private static final double TAU = Math.PI * 2.0;

    /** Solid bright-purple colour used by the moon-energy rings. */
    private static final Color MOON = Color.fromRGB(177, 74, 255);

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — a
     * sequence of expanding rings of full-purple moon energy. Each ring
     * sweeps outward from the caster and strikes every living target it
     * passes exactly once, so more rings land more hits.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster
     * @param maxRadius the radius each ring expands out to, in blocks
     * @param rings     how many moon-energy rings shoot out per cast
     * @param onStrike  called once per target per ring
     * @param ticks     how long each ring takes to expand
     */
    public static void playCatastrophe(final JavaPlugin plugin, final Player player,
                                       final double maxRadius, final int rings,
                                       final Consumer<LivingEntity> onStrike,
                                       final int ticks) {
        final Location center = player.getEyeLocation().add(0.0, -0.4, 0.0);
        // Stagger each ring a little so several waves read as one vortex.
        final long staggerTicks = 4L;
        final int ringCount = Math.max(1, rings);
        for (int wave = 0; wave < ringCount; wave++) {
            final long delay = wave * staggerTicks;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> sweepRing(plugin, player, center.clone(), maxRadius,
                            onStrike, ticks),
                    delay);
        }
    }

    /**
     * Expands a single ring of solid purple moon-energy particles outward,
     * striking each living target once as the ring passes over it.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster (excluded from strikes)
     * @param center    the ring centre
     * @param maxRadius final reach of the ring in blocks
     * @param onStrike  called once per target as the ring passes it
     * @param ticks     how long the ring takes to expand
     */
    private static void sweepRing(final JavaPlugin plugin, final Player player,
                                  final Location center, final double maxRadius,
                                  final Consumer<LivingEntity> onStrike,
                                  final int ticks) {
        final World world = center.getWorld();
        final Set<UUID> struck = new HashSet<>();
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= ticks) {
                    cancel();
                    return;
                }
                final double progress = tick / (double) Math.max(1, ticks - 1);
                final double radius = 1.0 + (maxRadius - 1.0) * progress;
                final int points = Math.max(48, (int) Math.round(TAU * radius / 0.6));
                for (int i = 0; i < points; i++) {
                    final double angle = i * (TAU / points);
                    final double y = 0.6 * Math.sin(progress * Math.PI);
                    final Location point = center.clone().add(
                            Math.sin(angle) * radius, y, Math.cos(angle) * radius);
                    world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0,
                            new Particle.DustOptions(MOON, 1.7f));
                }
                for (final Entity entity : world.getNearbyEntities(
                        center, radius, 4.0, radius)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    final double dx = living.getLocation().getX() - center.getX();
                    final double dz = living.getLocation().getZ() - center.getZ();
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    if (struck.add(living.getUniqueId())) {
                        onStrike.accept(living);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Fires one white crescent gleam in the direction the caster is looking,
     * dealing true damage (through the {@code onHit} callback) to the first
     * living target it strikes. Used by the Sixteenth Form, Moonbow, Half
     * Moon.
     *
     * @param plugin      owning plugin (for the scheduler)
     * @param player      the caster (excluded from hits)
     * @param travelTicks how many ticks the crescent flies before expiring
     * @param onHit       called once per target the crescent touches
     */
    public static void fireMoonbowCrescent(final JavaPlugin plugin, final Player player,
                                           final int travelTicks,
                                           final Consumer<LivingEntity> onHit) {
        final Location origin = player.getEyeLocation();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final ItemDisplay display = player.getWorld().spawn(origin, ItemDisplay.class);
        display.setItemStack(KokushiboSword.whiteCrescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        setScale(display, 1.6f, 1.6f, 1.6f);

        final int duration = Math.max(6, travelTicks);
        final double speed = 0.9;
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
                pos.add(direction.clone().multiply(speed));
                display.teleport(pos);
                final float scale = Math.max(0.6f, 1.6f - tick * 0.05f);
                setScale(display, scale, scale, scale);
                // White gleam trail behind the flying crescent.
                pos.getWorld().spawnParticle(Particle.END_ROD, pos, 4,
                        0.12, 0.12, 0.12, 0.0);

                if (pos.getBlock().getType().isSolid()) {
                    pos.getWorld().spawnParticle(Particle.END_ROD, pos, 8,
                            0.2, 0.2, 0.2, 0.02);
                    display.remove();
                    cancel();
                    return;
                }

                for (final Entity entity : pos.getWorld().getNearbyEntities(
                        pos, 1.2, 1.2, 1.2)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    if (!hit.add(living.getUniqueId())) {
                        continue;
                    }
                    onHit.accept(living);
                    pos.getWorld().spawnParticle(Particle.END_ROD, pos, 10,
                            0.3, 0.3, 0.3, 0.02);
                    display.remove();
                    cancel();
                    return;
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

}
