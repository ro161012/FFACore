package dev.ro161012.ffacore.nichirin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Renders the Nichirin Blade ability visuals as the two canonical Hinokami
 * Kagura forms. The geometry is a mix of full-bright translucent glass block
 * displays and custom flame models from the resource pack (the
 * {@code ffacore:vfx/flame_blade} slash and {@code ffacore:vfx/flame_orb}
 * glow, rendered as {@link ItemDisplay} entities), tinted by the pack's
 * solar-fire core shader.
 *
 * <p><b>Clear Blue Sky</b> is a continuous 360&deg; disc of solar fire that
 * spins around the caster's waist: an orange-red core ring inside a
 * yellow-white outer ring, a whirling set of flat flame blades, and a
 * lingering afterimage. <b>Dancing Flash</b> opens with an electric launch,
 * sweeps a massive vertical crescent of flame blades forward, and detonates
 * into an expanding yellow/orange shockwave. Every display is removed when
 * the animation ends.
 */
public final class NichirinEffects {

    private static final double TAU = Math.PI * 2.0;

    /** Custom model data for the flat flame-blade slash model. */
    private static final int FLAME_BLADE_CMD = 2001;

    /** Custom model data for the round flame-orb glow model. */
    private static final int FLAME_ORB_CMD = 2002;

    /** How far the Clear Blue Sky flame blades lean up from fully horizontal. */
    private static final float FLAME_BLADE_TILT = 0.5f;

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a spinning 360&deg; horizontal solar disc around
     * the caster's waist — orange-red core, yellow-white rim, whirling flat
     * flame blades, a lagging afterimage, and lava that shoots outward before
     * fading.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param ticks  how long the disc spins (shorter = snappier)
     * @param radius final reach of the ring in blocks
     */
    public static void playClearBlueSky(final JavaPlugin plugin, final Player player,
                                        final int ticks, final double radius) {
        final Location waist = player.getEyeLocation().add(0.0, -1.0, 0.0);
        // Ring density scales with the radius so a big disc still reads as one
        // solid band of fire rather than sparse dots.
        final int perRing = Math.max(24, (int) Math.round(Math.PI * 2.0 * radius / 1.8));
        final int core = perRing;
        final int rim = perRing;
        final int afterimage = Math.max(12, perRing / 2);
        final int bladeCount = Math.max(20, perRing / 2);

        final List<Display> displays = new ArrayList<>();
        for (int i = 0; i < core; i++) {
            displays.add(spawnBlock(player, waist, Material.ORANGE_STAINED_GLASS, 0.6f));
        }
        for (int i = 0; i < rim; i++) {
            displays.add(spawnBlock(player, waist, Material.YELLOW_STAINED_GLASS, 0.65f));
        }
        for (int i = 0; i < afterimage; i++) {
            displays.add(spawnBlock(player, waist, Material.YELLOW_STAINED_GLASS, 0.45f));
        }
        for (int i = 0; i < bladeCount; i++) {
            displays.add(spawnVfxItem(player, waist, FLAME_BLADE_CMD,
                    Display.Billboard.FIXED));
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

            // Whirling solar flame blades lying flat over the disc, spinning
            // around the vertical axis as they ride the ring outward.
            for (int i = 0; i < bladeCount; i++) {
                final double angle = -spin * 1.3 + i * (TAU / bladeCount);
                final double bladeRadius = 0.8 + progress * radius * 0.85;
                displays.get(core + rim + afterimage + i).teleport(waist.clone().add(
                        Math.sin(angle) * bladeRadius, 0.05, Math.cos(angle) * bladeRadius));
                final float scale = (float) (1.5 + progress * 1.4
                        + Math.sin(tick * 0.5 + i) * 0.2);
                setSpinScale(displays.get(core + rim + afterimage + i), scale,
                        angle, FLAME_BLADE_TILT);
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
     * Plays Dancing Flash: an electric launch at the feet, then a massive
     * vertical crescent of flame blades sweeping forward with a leading glow
     * orb, detonating into an expanding yellow/orange shockwave.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param ticks  how long the sequence plays (shorter = snappier)
     * @param radius final reach of the slash and shockwave in blocks
     */
    public static void playEnbu(final JavaPlugin plugin, final Player player,
                                final int ticks, final double radius) {
        final Location eye = player.getEyeLocation();
        final Vector facing = horizontalFacing(player);
        final Vector up = new Vector(0.0, 1.0, 0.0);

        final int crescentOuter = Math.max(16, (int) Math.round(radius * 2.0));
        final int crescentInner = Math.max(10, crescentOuter * 3 / 4);
        final int shockwave = Math.max(24, (int) Math.round(Math.PI * 2.0 * radius / 1.8));

        final List<Display> displays = new ArrayList<>();
        for (int i = 0; i < crescentOuter; i++) {
            displays.add(spawnVfxItem(player, eye, FLAME_BLADE_CMD,
                    Display.Billboard.CENTER));
        }
        for (int i = 0; i < crescentInner; i++) {
            displays.add(spawnVfxItem(player, eye, FLAME_BLADE_CMD,
                    Display.Billboard.CENTER));
        }
        // Leading glow orb at the tip of the slash.
        displays.add(spawnVfxItem(player, eye, FLAME_ORB_CMD,
                Display.Billboard.CENTER));
        final int orbIndex = crescentOuter + crescentInner;
        for (int i = 0; i < shockwave; i++) {
            displays.add(spawnBlock(player, eye.clone().add(0.0, -1.0, 0.0),
                    i % 2 == 0 ? Material.YELLOW_STAINED_GLASS : Material.ORANGE_STAINED_GLASS,
                    0.5f));
        }
        final int shockIndex = orbIndex + 1;

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

            // The crescent sweeps forward: a vertical arc of flame blades in
            // the facing plane, growing from the body outward.
            final double slashProgress = clamp((progress - 0.15) / 0.6);
            final double slashRadius = 0.4 + slashProgress * radius;
            for (int i = 0; i < crescentOuter; i++) {
                final double t = i / (double) (crescentOuter - 1);
                final double angle = Math.toRadians(-80.0 + t * 160.0);
                final Vector dir = facing.clone().multiply(Math.cos(angle))
                        .add(up.clone().multiply(Math.sin(angle)));
                displays.get(i).teleport(eye.clone().add(dir.multiply(slashRadius)));
                setScale(displays.get(i), 1.8f, 1.8f, 1.8f);
            }
            for (int i = 0; i < crescentInner; i++) {
                final double t = i / (double) (crescentInner - 1);
                final double angle = Math.toRadians(-70.0 + t * 140.0);
                final Vector dir = facing.clone().multiply(Math.cos(angle))
                        .add(up.clone().multiply(Math.sin(angle)));
                displays.get(crescentOuter + i).teleport(
                        eye.clone().add(dir.multiply(slashRadius * 0.8)));
                setScale(displays.get(crescentOuter + i), 1.4f, 1.4f, 1.4f);
            }

            // Leading glow orb rides the tip, swelling then fading.
            final Location tip = eye.clone().add(facing.clone().multiply(slashRadius));
            displays.get(orbIndex).teleport(tip);
            final float orbScale = (float) (1.2 + slashProgress * 2.2)
                    * (1.0f - (float) progress * 0.35f);
            setScale(displays.get(orbIndex), orbScale, orbScale, orbScale);

            // Impact shockwave: a horizontal ring spreads across the ground,
            // merging the yellow lightning and orange fire.
            final double impactProgress = clamp((progress - 0.75) / 0.25);
            final double ringRadius = 0.5 + impactProgress * radius;
            for (int i = 0; i < shockwave; i++) {
                final double angle = i * (TAU / shockwave);
                displays.get(shockIndex + i).teleport(
                        eye.clone().add(
                                Math.sin(angle) * ringRadius, -1.0, Math.cos(angle) * ringRadius));
                setScale(displays.get(shockIndex + i), 0.5f, 0.2f, 0.5f);
            }

            // Flame trail along the crescent and the final electric/fire burst.
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
     * Erupts actual lava blocks outward in a ring around the caster. The lava
     * arcs up and out, then disappears once the burst finishes.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param radius how far the lava shoots out, in blocks
     */
    public static void lavaBurst(final JavaPlugin plugin, final Player player,
                                 final double radius) {
        final Location center = player.getLocation().add(0.0, 0.2, 0.0);
        final int count = 28;
        final List<BlockDisplay> lava = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final BlockDisplay display = player.getWorld().spawn(center, BlockDisplay.class);
            display.setBlock(Material.LAVA.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration(1);
            display.setInterpolationDelay(0);
            setScale(display, 0.6f, 0.6f, 0.6f);
            lava.add(display);
        }

        final int duration = 20;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= duration) {
                    lava.forEach(BlockDisplay::remove);
                    cancel();
                    return;
                }
                final double progress = tick / (double) (duration - 1);
                for (int i = 0; i < lava.size(); i++) {
                    final double angle = i * (TAU / lava.size()) + progress * 0.6;
                    final double dist = 0.4 + progress * radius * 0.9;
                    final double height = Math.sin(progress * Math.PI) * 1.6;
                    lava.get(i).teleport(center.clone().add(
                            Math.sin(angle) * dist, height, Math.cos(angle) * dist));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Shoots actual lava blocks forward in the player's facing direction,
     * arcing up and spreading sideways before disappearing.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void lavaBurstForward(final JavaPlugin plugin, final Player player) {
        final Location origin = player.getEyeLocation();
        final Vector facing = horizontalFacing(player);
        final Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
        final int count = 22;
        final List<BlockDisplay> lava = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final BlockDisplay display = player.getWorld().spawn(origin, BlockDisplay.class);
            display.setBlock(Material.LAVA.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration(1);
            display.setInterpolationDelay(0);
            setScale(display, 0.5f, 0.5f, 0.5f);
            lava.add(display);
        }

        final int duration = 18;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= duration) {
                    lava.forEach(BlockDisplay::remove);
                    cancel();
                    return;
                }
                final double progress = tick / (double) (duration - 1);
                for (int i = 0; i < lava.size(); i++) {
                    final double spread = (i / (double) (lava.size() - 1)) * 2.0 - 1.0;
                    final double dist = 0.5 + progress * 7.0;
                    final double height = Math.sin(progress * Math.PI) * 1.1;
                    final double width = 0.4 + Math.abs(spread) * 1.6;
                    lava.get(i).teleport(origin.clone()
                            .add(facing.clone().multiply(dist))
                            .add(0.0, height, 0.0)
                            .add(side.clone().multiply(spread * width)));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Detonates an earthquake shockwave under the caster as they land: the
     * actual ground blocks lift up and slam back down in an outward ripple,
     * like the terrain is being shaken.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the landing player
     */
    public static void playLandingShockwave(final JavaPlugin plugin, final Player player) {
        final World world = player.getWorld();
        final Location center = player.getLocation();
        final double radius = 5.0;

        final int feetY = center.getBlockY();
        final int minX = center.getBlockX() - (int) Math.ceil(radius);
        final int maxX = center.getBlockX() + (int) Math.ceil(radius);
        final int minZ = center.getBlockZ() - (int) Math.ceil(radius);
        final int maxZ = center.getBlockZ() + (int) Math.ceil(radius);

        final List<BlockDisplay> displays = new ArrayList<>();
        final List<Double> phases = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                final double dx = x + 0.5 - center.getX();
                final double dz = z + 0.5 - center.getZ();
                final double dist = Math.hypot(dx, dz);
                if (dist > radius) {
                    continue;
                }
                Block ground = null;
                for (int y = feetY; y >= feetY - 3; y--) {
                    final Block candidate = world.getBlockAt(x, y, z);
                    if (candidate.getType().isSolid()) {
                        ground = candidate;
                        break;
                    }
                }
                if (ground == null) {
                    continue;
                }
                final BlockDisplay display = world.spawn(ground.getLocation(), BlockDisplay.class);
                display.setBlock(ground.getBlockData());
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);
                displays.add(display);
                phases.add(dist / radius);
            }
        }

        if (displays.isEmpty()) {
            return;
        }

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.5f);
        world.spawnParticle(Particle.CLOUD, center, 30, radius, 0.3, radius, 0.02);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 12, radius, 0.4, radius, 0.0);

        final int duration = 22;
        final double waveWindow = 0.45;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= duration) {
                    displays.forEach(BlockDisplay::remove);
                    cancel();
                    return;
                }
                final double progress = tick / (double) (duration - 1);
                for (int i = 0; i < displays.size(); i++) {
                    final double local = (progress - phases.get(i) * waveWindow) / waveWindow;
                    double bounce = 0.0;
                    if (local >= 0.0 && local <= 1.0) {
                        bounce = Math.sin(local * Math.PI) * 0.65;
                    }
                    setTranslationY(displays.get(i), (float) bounce);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
     * Spawns a full-bright item display carrying one of the pack's flame VFX
     * models (selected by custom model data).
     */
    private static ItemDisplay spawnVfxItem(final Player player, final Location location,
                                            final int modelData,
                                            final Display.Billboard billboard) {
        final ItemStack stack = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelData);
            stack.setItemMeta(meta);
        }
        final ItemDisplay display = player.getWorld().spawn(location, ItemDisplay.class);
        display.setItemStack(stack);
        display.setBillboard(billboard);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
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
     * Lifts a display straight up by {@code y} blocks while keeping its scale
     * and orientation — used for the earthquake ground-block bounce.
     */
    private static void setTranslationY(final Display display, final float y) {
        display.setTransformation(new Transformation(
                new Vector3f(0f, y, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
    }

    /**
     * Applies a scale plus a horizontal spin (rotation about the vertical
     * axis) to a flat flame blade, tilted up slightly so it stays visible.
     */
    private static void setSpinScale(final Display display, final float scale,
                                     final double yaw, final float tilt) {
        final Quaternionf rotation = new Quaternionf()
                .rotateY((float) yaw)
                .rotateX(tilt);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(rotation),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
    }

    /**
     * Runs a per-tick animation callback, then removes every display.
     */
    private static void animate(final JavaPlugin plugin, final List<? extends Display> displays,
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
     * Clamps a value into the [0, 1] range.
     */
    private static double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
