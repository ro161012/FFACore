package dev.ro161012.ffacore.nichirin;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Renders the Nichirin Blade ability visuals as the two canonical Hinokami
 * Kagura forms using full-bright translucent stained-glass block displays,
 * tinted by the pack's solar-fire core shader, with actual lava blocks that
 * erupt outward and then disappear.
 *     * <p><b>Clear Blue Sky</b> is a continuous 360&deg; disc of solar fire that
     * spins around the caster's waist: an orange-red core ring inside a
     * yellow-white outer ring and a lingering afterimage, with glowing
     * lava-orange blocks shooting outward. <b>Dancing Flash</b> opens with an
     * electric launch, sweeps a vertical crescent of glass blocks forward,
     * and detonates into an expanding yellow/orange shockwave with a forward
     * volley of glowing blocks. Every display is removed when its animation
     * ends.
 */
public final class NichirinEffects {

    private static final double TAU = Math.PI * 2.0;

    /** Solid ember-orange colour used by the ability rings. */
    private static final Color EMBER = Color.fromRGB(255, 122, 0);

    /** Lighter solar-orange highlight colour. */
    private static final Color SOLAR = Color.fromRGB(255, 190, 60);

    private NichirinEffects() {
        // Utility class.
    }

    /**
     * Plays Clear Blue Sky: a spinning 360&deg; horizontal solar disc around
     * the caster's waist — an orange-red core ring inside a yellow-white rim,
     * a lagging afterimage, and lava that shoots outward before fading.
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

            // Full-orange ember dust shoots outward from the rim, then fades
            // like heat haze.
            final Location edge = waist.clone().add(0.0, 0.15, 0.0);
            dust(edge, EMBER, 40, ringRadius, 0.35, ringRadius, 1.4f);
            dust(waist, SOLAR, 18, ringRadius, 0.25, ringRadius, 1.1f);
            dust(edge, EMBER, 30, ringRadius * 0.7, 0.35, ringRadius * 0.7, 1.2f);
            dust(edge, SOLAR, 20, ringRadius, 0.45, ringRadius, 0.9f);
            dust(edge, EMBER, 10, ringRadius * 0.5, 0.3, ringRadius * 0.5, 1.6f);
            // A bright ember ring rides the disc edge so the whole circle
            // reads as one band of solar fire, not sparse dots.
            for (int p = 0; p < 12; p++) {
                final double angle = p * (TAU / 12);
                final Location point = edge.clone().add(
                        Math.sin(angle) * ringRadius, 0.0, Math.cos(angle) * ringRadius);
                dust(point, SOLAR, 5, 0.25, 0.25, 0.25, 1.5f);
                dust(point, EMBER, 3, 0.2, 0.2, 0.2, 1.3f);
            }
        });
    }

    /**
     * Plays Dancing Flash: an electric launch at the feet, then a vertical
     * crescent of glass blocks sweeping forward with a bright leading block,
     * detonating into an expanding yellow/orange shockwave.
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
            displays.add(spawnBlock(player, eye, Material.ORANGE_STAINED_GLASS, 0.7f));
        }
        for (int i = 0; i < crescentInner; i++) {
            displays.add(spawnBlock(player, eye, Material.YELLOW_STAINED_GLASS, 0.6f));
        }
        // Bright leading glass block at the tip of the slash.
        displays.add(spawnBlock(player, eye, Material.YELLOW_STAINED_GLASS, 0.95f));
        final int tipIndex = crescentOuter + crescentInner;
        for (int i = 0; i < shockwave; i++) {
            displays.add(spawnBlock(player, eye.clone().add(0.0, -1.0, 0.0),
                    i % 2 == 0 ? Material.YELLOW_STAINED_GLASS : Material.ORANGE_STAINED_GLASS,
                    0.5f));
        }
        final int shockIndex = tipIndex + 1;

        player.getWorld().playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);

        animate(plugin, displays, ticks, tick -> {
            final double progress = tick / (double) Math.max(1, ticks - 1);
            final boolean launching = progress < 0.2;

            // Electric launch: yellow lightning crackles around the feet with
            // an orange ember flash.
            if (launching) {
                final Location feet = player.getLocation();
                feet.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, feet.add(0.0, 0.2, 0.0),
                        8, 0.5, 0.1, 0.5, 0.02);
                dust(feet.add(0.0, 0.6, 0.0), SOLAR, 5, 0.4, 0.3, 0.4, 1.1f);
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
                setScale(displays.get(i), 0.7f, 0.7f, 0.7f);
            }
            for (int i = 0; i < crescentInner; i++) {
                final double t = i / (double) (crescentInner - 1);
                final double angle = Math.toRadians(-70.0 + t * 140.0);
                final Vector dir = facing.clone().multiply(Math.cos(angle))
                        .add(up.clone().multiply(Math.sin(angle)));
                displays.get(crescentOuter + i).teleport(
                        eye.clone().add(dir.multiply(slashRadius * 0.8)));
                setScale(displays.get(crescentOuter + i), 0.6f, 0.6f, 0.6f);
            }

            // Bright leading block rides the tip, swelling then fading.
            final Location tip = eye.clone().add(facing.clone().multiply(slashRadius));
            displays.get(tipIndex).teleport(tip);
            final float tipScale = (float) (0.95 + slashProgress * 1.2);
            setScale(displays.get(tipIndex), tipScale, tipScale, tipScale);

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

            // Ember dust trail along the crescent and the final burst.
            dust(tip, EMBER, 10, 0.6, 0.6, 0.6, 1.4f);
            dust(tip, SOLAR, 6, 0.5, 0.5, 0.5, 1.1f);
            if (impactProgress > 0.0) {
                final Location ground = eye.clone().add(0.0, -1.0, 0.0);
                dust(ground, EMBER, 10, ringRadius, 0.3, ringRadius, 1.3f);
                dust(ground, SOLAR, 8, ringRadius * 0.6, 0.2, ringRadius * 0.6, 1.0f);
            }
        });
    }

    /**
     * Erupts glowing lava-orange blocks outward in a ring around the caster.
     * They arc up and out, then disappear when they land or the burst ends —
     * nothing ever stays in the world.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     * @param radius how far the blocks shoot out, in blocks
     * @param count  how many lava blocks erupt
     */
    public static void lavaBurst(final JavaPlugin plugin, final Player player,
                                 final double radius, final int count) {
        final Location center = player.getLocation().add(0.0, 0.3, 0.0);
        final double speed = Math.max(0.45, 0.35 + radius * 0.015);
        final List<Location> spawns = new ArrayList<>();
        final List<Vector> velocities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final double angle = i * (TAU / count);
            spawns.add(center.clone());
            velocities.add(new Vector(
                    Math.sin(angle) * speed, 0.45, Math.cos(angle) * speed));
        }
        launchLavaBlocks(plugin, player, spawns, velocities, 30, false, 0.0, 0.0);
    }

    /**
     * Shoots glowing lava-orange blocks forward in the player's facing
     * direction, arcing up and spreading sideways before disappearing.
     *
     * @param plugin            owning plugin (for the scheduler)
     * @param player            the caster
     * @param radius            how far the blocks shoot forward, in blocks
     * @param count             how many lava blocks fire
     * @param knockbackStrength shove applied when a block lands
     * @param knockbackRadius   range of the landing shove, in blocks
     */
    public static void lavaBurstForward(final JavaPlugin plugin, final Player player,
                                        final double radius, final int count,
                                        final double knockbackStrength,
                                        final double knockbackRadius) {
        final Location origin = player.getEyeLocation();
        final Vector facing = horizontalFacing(player);
        final Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
        final double speed = 0.5 + Math.max(0.0, radius) * 0.05;
        final List<Location> spawns = new ArrayList<>();
        final List<Vector> velocities = new ArrayList<>();
        final double spreadDenom = Math.max(1, count - 1);
        for (int i = 0; i < count; i++) {
            final double spread = (i / spreadDenom) * 2.0 - 1.0;
            // Spawn just ahead of the caster so the volley is instantly in view.
            final Location spawn = origin.clone()
                    .add(facing.clone().multiply(0.5))
                    .add(side.clone().multiply(spread * 0.5));
            spawns.add(spawn);
            velocities.add(facing.clone()
                    .multiply(speed + Math.random() * 0.25)
                    .add(side.clone().multiply(spread * 0.18))
                    .add(new Vector(0.0, 0.14 + Math.random() * 0.18, 0.0)));
        }
        launchLavaBlocks(plugin, player, spawns, velocities, 26, true,
                knockbackStrength, knockbackRadius);
    }

    /**
     * Launches glowing lava-orange block displays along the given velocities.
     * Each block flies, trails ember dust, and is removed the moment it lands
     * or when the flight times out, so no lava ever lingers in the world.
     *
     * @param plugin       owning plugin (for the scheduler)
     * @param player       the caster
     * @param spawns       one spawn location per block
     * @param velocities   one initial velocity per block
     * @param maxTicks         how long the volley flies before expiring
     * @param impactOnLand     whether a landing block sears and knocks back
     * @param knockbackStrength shove applied when a block lands
     * @param knockbackRadius   range of the landing shove, in blocks
     */
    private static void launchLavaBlocks(final JavaPlugin plugin, final Player player,
                                         final List<Location> spawns,
                                         final List<Vector> velocities,
                                         final int maxTicks,
                                         final boolean impactOnLand,
                                         final double knockbackStrength,
                                         final double knockbackRadius) {
        final World world = player.getWorld();
        final List<BlockDisplay> blocks = new ArrayList<>();
        final Map<UUID, Vector> motion = new HashMap<>();
        for (int i = 0; i < spawns.size(); i++) {
            final BlockDisplay block = world.spawn(spawns.get(i), BlockDisplay.class);
            block.setBlock(Material.SHROOMLIGHT.createBlockData());
            block.setBrightness(new Display.Brightness(15, 15));
            block.setInterpolationDuration(1);
            block.setInterpolationDelay(0);
            setScale(block, 0.55f, 0.55f, 0.55f);
            blocks.add(block);
            motion.put(block.getUniqueId(), velocities.get(i).clone());
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick++ >= maxTicks || blocks.isEmpty()) {
                    blocks.forEach(block -> {
                        if (block.isValid()) {
                            block.remove();
                        }
                    });
                    cancel();
                    return;
                }
                for (final BlockDisplay block : new ArrayList<>(blocks)) {
                    if (!block.isValid()) {
                        blocks.remove(block);
                        continue;
                    }
                    final Vector velocity = motion.get(block.getUniqueId());
                    final Location next = block.getLocation().add(velocity);
                    block.teleport(next);
                    velocity.subtract(new Vector(0.0, 0.02, 0.0));
                    dust(next, EMBER, 3, 0.15, 0.15, 0.15, 1.3f);
                    if (velocity.getY() <= 0.0 && next.getBlock().getType().isSolid()) {
                        if (impactOnLand) {
                            lavaImpact(player, next, knockbackStrength, knockbackRadius);
                        }
                        block.remove();
                        blocks.remove(block);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * A lava block just struck the ground: plays a matching lava hiss, sears
     * the impact point with ember dust, and knocks nearby targets back away
     * from the splash.
     *
     * @param caster   the player who fired the volley (excluded from knockback)
     * @param impact   where the lava block landed
     * @param strength knockback strength
     * @param radius   knockback radius, in blocks
     */
    private static void lavaImpact(final Player caster, final Location impact,
                                   final double strength, final double radius) {
        final World world = impact.getWorld();
        world.playSound(impact, Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.1f);
        dust(impact, EMBER, 10, 0.5, 0.3, 0.5, 1.4f);
        dust(impact, SOLAR, 5, 0.35, 0.25, 0.35, 1.0f);

        for (final Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(caster)) {
                continue;
            }
            final double dx = living.getLocation().getX() - impact.getX();
            final double dz = living.getLocation().getZ() - impact.getZ();
            final double length = Math.hypot(dx, dz);
            final double dirX;
            final double dirZ;
            if (length < 1.0e-6) {
                final double angle = Math.random() * TAU;
                dirX = Math.cos(angle);
                dirZ = Math.sin(angle);
            } else {
                dirX = dx / length;
                dirZ = dz / length;
            }
            living.knockback(strength, dirX, dirZ);
        }
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

        final List<Location> spots = new ArrayList<>();
        final List<BlockData> datas = new ArrayList<>();
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
                spots.add(ground.getLocation());
                datas.add(ground.getBlockData());
                phases.add(dist / radius);
            }
        }

        if (spots.isEmpty()) {
            return;
        }

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.5f);
        world.spawnParticle(Particle.CLOUD, center, 30, radius, 0.3, radius, 0.02);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 12, radius, 0.4, radius, 0.0);

        // Each block pops up only when the ripple reaches it, so a copied
        // block never sits overlapping (and thus shadowed/dark inside) the
        // real ground block.
        final int duration = 22;
        final double waveWindow = 0.45;
        for (int i = 0; i < spots.size(); i++) {
            final Location spot = spots.get(i);
            final BlockData data = datas.get(i);
            final long delay = Math.round(phases.get(i) * waveWindow * duration);
            plugin.getServer().getScheduler().runTaskLater(
                    plugin, () -> bounceBlock(plugin, spot, data), delay);
        }
    }

    /**
     * Spawns a full-bright copy of a ground block at {@code spot} and bounces
     * it up once before removing it — the slamming block of the landing
     * shockwave ripple.
     */
    private static void bounceBlock(final JavaPlugin plugin, final Location spot,
                                    final BlockData data) {
        final World world = spot.getWorld();
        if (world == null) {
            return;
        }
        final BlockDisplay display = world.spawn(spot, BlockDisplay.class);
        display.setBlock(data);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);

        final int bounceTicks = 10;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= bounceTicks || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                final double local = tick / (double) (bounceTicks - 1);
                setTranslationY(display, (float) (Math.sin(local * Math.PI) * 0.65));
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
     * Spawns solid-coloured dust particles (a full colour, not fire/lava
     * textures) at a point.
     *
     * @param at    centre of the particle cloud
     * @param color the solid particle colour
     * @param count number of particles
     * @param dx    horizontal spread
     * @param dy    vertical spread
     * @param dz    horizontal spread
     * @param size  particle size (1.0 is default)
     */
    private static void dust(final Location at, final Color color, final int count,
                             final double dx, final double dy, final double dz,
                             final float size) {
        final World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, count, dx, dy, dz,
                new Particle.DustOptions(color, size));
    }

    /**
     * Clamps a value into the [0, 1] range.
     */
    private static double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
