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
import java.util.function.IntConsumer;

/**
 * Renders the Nichirin Blade ability visuals as structured fire geometry.
 *
 * <p>Clear Blue Sky spins an expanding horizontal ring of glowing flame cubes
 * around the caster, and Enbu whirls a rising stack of flame rings above a
 * spreading ground ring. The cubes are full-bright translucent glass rendered
 * through the companion core shader ({@code rendertype_entity_alpha.fsh}), so
 * they glow like embers, and every display is removed when the animation ends.
 */
public final class NichirinEffects {

    /** Ticks the Clear Blue Sky ring is visible. */
    private static final int FAN_TICKS = 12;

    /** Ticks the Enbu spiral is visible. */
    private static final int RING_TICKS = 20;

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a spinning ring of flame that expands outward
     * around the caster, with a counter-rotating inner ring and a rising
     * pillar of fire.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final int ring = 36;
        final int inner = 18;
        final double startRadius = 0.6;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int i = 0; i < ring; i++) {
            displays.add(spawnBlock(player, eye, Material.ORANGE_STAINED_GLASS, 0.4f));
        }
        for (int i = 0; i < inner; i++) {
            displays.add(spawnBlock(player, eye, Material.RED_STAINED_GLASS, 0.35f));
        }
        final BlockDisplay pillar = spawnBlock(player, eye.clone().add(0, -0.3, 0),
                Material.ORANGE_STAINED_GLASS, 0.5f);
        displays.add(pillar);

        player.getWorld().spawnParticle(Particle.FLAME, eye, 80, 0.8, 0.5, 0.8, 0.06);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eye, 40, 0.6, 0.4, 0.6, 0.04);

        animate(plugin, displays, FAN_TICKS, tick -> {
            final double radius = startRadius + tick * 0.24;
            final double spin = Math.toRadians(tick * 24);
            for (int i = 0; i < ring; i++) {
                final double angle = Math.toRadians(i * (360.0 / ring)) + spin;
                final BlockDisplay block = displays.get(i);
                block.teleport(eye.clone().add(
                        Math.sin(angle) * radius, -0.5, Math.cos(angle) * radius));
                setScale(block, 0.4f, 0.4f, 0.4f);
            }
            final double innerSpin = -Math.toRadians(tick * 30);
            for (int i = 0; i < inner; i++) {
                final double angle = Math.toRadians(i * (360.0 / inner)) + innerSpin;
                final BlockDisplay block = displays.get(ring + i);
                block.teleport(eye.clone().add(
                        Math.sin(angle) * radius * 0.7, -0.35, Math.cos(angle) * radius * 0.7));
                setScale(block, 0.35f, 0.35f, 0.35f);
            }
            final float pillarHeight = tick < 6 ? 1.0f + tick * 0.5f : 4.0f - (tick - 6) * 0.45f;
            setScale(pillar, 0.5f, pillarHeight, 0.5f);
        });
    }

    /**
     * Plays Enbu: a rising whirl of flame rings around the caster with a
     * spreading ground ring and a central fire pillar.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final int rings = 3;
        final int perRing = 16;
        final int ground = 20;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int r = 0; r < rings; r++) {
            final Material material = r == 0
                    ? Material.ORANGE_STAINED_GLASS : Material.RED_STAINED_GLASS;
            for (int i = 0; i < perRing; i++) {
                displays.add(spawnBlock(player, eye, material, 0.45f));
            }
        }
        for (int i = 0; i < ground; i++) {
            displays.add(spawnBlock(player, eye, Material.RED_STAINED_GLASS, 0.4f));
        }
        final BlockDisplay pillar = spawnBlock(player, eye.clone().add(0, 0.3, 0),
                Material.ORANGE_STAINED_GLASS, 0.55f);
        displays.add(pillar);

        player.getWorld().spawnParticle(Particle.FLAME, eye, 100, 1.0, 0.6, 1.0, 0.06);
        player.getWorld().spawnParticle(Particle.LAVA, eye, 40, 0.8, 0.5, 0.8, 0.03);

        animate(plugin, displays, RING_TICKS, tick -> {
            final double spin = Math.toRadians(tick * 28);
            final double rise = tick * 0.12;
            final double radius = 1.7 + Math.sin(tick * 0.5) * 0.25;
            for (int r = 0; r < rings; r++) {
                final double ringPhase = spin + r * Math.toRadians(40);
                for (int i = 0; i < perRing; i++) {
                    final double angle = Math.toRadians(i * (360.0 / perRing)) + ringPhase;
                    final double y = -0.6 + rise + r * 0.55;
                    final BlockDisplay block = displays.get(r * perRing + i);
                    block.teleport(eye.clone().add(
                            Math.sin(angle) * radius, y, Math.cos(angle) * radius));
                    setScale(block, 0.45f, 0.45f, 0.45f);
                }
            }
            final double groundRadius = 0.6 + tick * 0.18;
            for (int i = 0; i < ground; i++) {
                final double angle = Math.toRadians(i * (360.0 / ground)) + spin * 0.5;
                final BlockDisplay block = displays.get(rings * perRing + i);
                block.teleport(eye.clone().add(
                        Math.sin(angle) * groundRadius, -0.95, Math.cos(angle) * groundRadius));
                setScale(block, 0.4f, 0.4f, 0.4f);
            }
            setScale(pillar, 0.55f, 1.0f + tick * 0.22f, 0.55f);
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
