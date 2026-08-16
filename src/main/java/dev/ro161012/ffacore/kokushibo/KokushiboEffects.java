package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
 * Renders the Kokushibo Sword ability visuals.
 *
 * <p>Catastrophe unleashes a vortex of orbiting crescent moon blades around
 * the caster — counter-rotating outer and inner rings that climb as they
 * expand, a pulsing moon core, a ground boundary ring, and a purple impact
 * flash on every target struck. Moonbow fires a white crescent gleam where
 * the caster aims, and the passive fires a single drifting crescent. Every
 * display entity is removed when its animation ends — nothing lingers.
 */
public final class KokushiboEffects {

    private static final double TAU = Math.PI * 2.0;

    /** Solid bright-purple colour used by the moon-energy rings. */
    private static final Color MOON = Color.fromRGB(177, 74, 255);

    /** Scale of a vortex crescent blade. */
    private static final float CRESCENT_SCALE = 1.4f;

    /**
     * Power/scale data for the dragon-breath particle. On 1.21.11 this
     * particle requires an explicit {@link Float} data argument, so it must
     * be boxed (a primitive would widen to the {@code double extra} overload
     * and pass {@code null} data).
     */
    private static final Float DRAGON_BREATH_POWER = Float.valueOf(1.0f);

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Plays the Fourteenth Form: Catastrophe, Tenman Crescent Moon — a
     * vortex of crescent moon blades that whirls outward around the caster.
     * Each ring sweeps outward and strikes every living target it passes
     * exactly once, so more rings land more hits.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster
     * @param maxRadius the radius each ring expands out to, in blocks
     * @param rings     how many crescent rings shoot out per cast
     * @param onStrike  called once per target per ring
     * @param ticks     how long each ring takes to expand
     */
    public static void playCatastrophe(final JavaPlugin plugin, final Player player,
                                       final double maxRadius, final int rings,
                                       final Consumer<LivingEntity> onStrike,
                                       final int ticks) {
        final Location center = player.getEyeLocation().add(0.0, -0.4, 0.0);
        final int ringCount = Math.max(1, rings);
        final long staggerTicks = 4L;
        final int totalTicks = ticks + (ringCount - 1) * (int) staggerTicks;

        // Pulsing moon core at the caster's chest across the whole cast.
        pulseCore(plugin, center.clone(), totalTicks);

        // A purple ring marks the vortex reach on the ground beneath the caster.
        boundaryRing(plugin, center.clone().add(0.0, -1.0, 0.0), maxRadius, totalTicks);

        // Stagger each ring so the waves read as one continuous vortex.
        for (int wave = 0; wave < ringCount; wave++) {
            final long delay = wave * staggerTicks;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> vortexWave(plugin, player, center.clone(), maxRadius,
                            onStrike, ticks),
                    delay);
        }
    }

    /**
     * Expands one ring of crescent blades outward: an outer whirl and a
     * counter-rotating inner halo that climb as they grow, each blade leaving
     * a purple slash trail and striking each target once as it passes.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param player    the caster (excluded from strikes)
     * @param center    the vortex centre
     * @param maxRadius final reach of the ring in blocks
     * @param onStrike  called once per target as the ring passes it
     * @param ticks     how long the ring takes to expand
     */
    private static void vortexWave(final JavaPlugin plugin, final Player player,
                                   final Location center, final double maxRadius,
                                   final Consumer<LivingEntity> onStrike,
                                   final int ticks) {
        final World world = center.getWorld();
        final ItemStack blade = KokushiboSword.crescentItem();

        final int outerCount = 16;
        final int innerCount = 8;
        final List<ItemDisplay> outer = new ArrayList<>();
        final List<ItemDisplay> inner = new ArrayList<>();
        final List<Double> outerPhase = new ArrayList<>();
        final List<Double> innerPhase = new ArrayList<>();
        for (int i = 0; i < outerCount; i++) {
            outer.add(spawnCrescent(world, center, blade));
            outerPhase.add(i * (TAU / outerCount));
        }
        for (int i = 0; i < innerCount; i++) {
            inner.add(spawnCrescent(world, center, blade));
            innerPhase.add(i * (TAU / innerCount));
        }

        final Set<UUID> struck = new HashSet<>();
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= ticks) {
                    outer.forEach(ItemDisplay::remove);
                    inner.forEach(ItemDisplay::remove);
                    cancel();
                    return;
                }
                final double progress = tick / (double) Math.max(1, ticks - 1);
                final double radius = 1.0 + (maxRadius - 1.0) * progress;
                final double spin = tick * 0.14;
                final double climb = progress * 0.8;

                // Outer whirl spins one way, inner halo the other.
                for (int i = 0; i < outerCount; i++) {
                    placeCrescent(outer.get(i), center, outerPhase.get(i) + spin,
                            radius, 0.35 + climb);
                }
                for (int i = 0; i < innerCount; i++) {
                    placeCrescent(inner.get(i), center, innerPhase.get(i) - spin * 1.3,
                            radius * 0.55, -0.1 + climb * 0.5);
                }

                // Each target is struck once per ring as the blades sweep past.
                for (final Entity entity : world.getNearbyEntities(
                        center, radius, 5.0, radius)) {
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
                        impactFlash(living.getLocation().add(0.0, 1.0, 0.0));
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawns one full-bright crescent blade display at the vortex centre,
     * ready to be swept outward by {@link #placeCrescent}.
     *
     * @param world  the world to spawn in
     * @param at     the spawn location
     * @param blade  the crescent item
     * @return the spawned display
     */
    private static ItemDisplay spawnCrescent(final World world, final Location at,
                                             final ItemStack blade) {
        final ItemDisplay display = world.spawn(at, ItemDisplay.class);
        display.setItemStack(blade);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        return display;
    }

    /**
     * Moves a crescent blade to its position on the vortex. The blades are
     * billboarded so they always face the camera, which means no rotation is
     * ever applied — only a gentle scale pulse — keeping the display
     * transform free of anything that can glitch or crash the client.
     *
     * @param display the blade to place
     * @param center  the vortex centre
     * @param angle   position around the vertical axis
     * @param radius  distance from the centre
     * @param y       height of the blade
     */
    private static void placeCrescent(final ItemDisplay display, final Location center,
                                      final double angle, final double radius,
                                      final double y) {
        display.teleport(center.clone().add(
                Math.sin(angle) * radius, y, Math.cos(angle) * radius));
        final float scale = CRESCENT_SCALE
                * (0.9f + 0.1f * (float) Math.sin(angle * 3.0));
        setScale(display, scale, scale, scale);
        // Purple slash trail behind the flying blade.
        final Location pos = display.getLocation();
        pos.getWorld().spawnParticle(Particle.DUST, pos, 2, 0.1, 0.1, 0.1,
                new Particle.DustOptions(MOON, 1.4f));
        pos.getWorld().spawnParticle(Particle.DRAGON_BREATH, pos, 1,
                0.05, 0.05, 0.05, DRAGON_BREATH_POWER);
    }

    /**
     * Fires a ring of purple particles off a struck target — the impact flash
     * of the Fourteenth Form.
     *
     * @param center the centre of the burst
     */
    private static void impactFlash(final Location center) {
        final World world = center.getWorld();
        for (int i = 0; i < 14; i++) {
            final double angle = i * (TAU / 14);
            final Location point = center.clone().add(
                    Math.sin(angle) * 0.8, 0.2, Math.cos(angle) * 0.8);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0,
                    new Particle.DustOptions(MOON, 1.5f));
            world.spawnParticle(Particle.END_ROD, point, 1, 0.05, 0.05, 0.05, 0.01);
        }
        world.spawnParticle(Particle.DRAGON_BREATH, center, 6, 0.4, 0.3, 0.4,
                DRAGON_BREATH_POWER);
    }

    /**
     * Draws a growing purple ring on the ground marking the vortex reach.
     *
     * @param plugin    owning plugin (for the scheduler)
     * @param ground    the ground centre beneath the caster
     * @param maxRadius final radius of the boundary ring
     * @param ticks     how long the ring expands for
     */
    private static void boundaryRing(final JavaPlugin plugin, final Location ground,
                                     final double maxRadius, final int ticks) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= ticks) {
                    cancel();
                    return;
                }
                final double progress = tick / (double) Math.max(1, ticks - 1);
                final double radius = 0.5 + (maxRadius - 0.5) * progress;
                final int points = Math.max(40, (int) Math.round(TAU * radius / 0.7));
                for (int i = 0; i < points; i++) {
                    final double angle = i * (TAU / points);
                    final Location point = ground.clone().add(
                            Math.sin(angle) * radius, 0.05, Math.cos(angle) * radius);
                    ground.getWorld().spawnParticle(Particle.DUST, point, 1,
                            0.0, 0.0, 0.0, new Particle.DustOptions(MOON, 1.3f));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Pulses a glowing purple moon core at the caster's chest, radiating
     * moon-energy dust for the duration of the cast.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param center the core location
     * @param ticks  how long the core pulses for
     */
    private static void pulseCore(final JavaPlugin plugin, final Location center,
                                  final int ticks) {
        final BlockDisplay core = center.getWorld().spawn(center, BlockDisplay.class);
        core.setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
        core.setBrightness(new Display.Brightness(15, 15));
        core.setInterpolationDuration(1);
        core.setInterpolationDelay(0);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= ticks || !core.isValid()) {
                    core.remove();
                    cancel();
                    return;
                }
                final float scale = 0.7f + 0.3f * (float) Math.sin(tick * 0.5);
                setScale(core, scale, scale, scale);
                center.getWorld().spawnParticle(Particle.DUST, center, 3,
                        0.35, 0.35, 0.35, new Particle.DustOptions(MOON, 1.5f));
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
