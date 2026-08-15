package dev.ro161012.ffacore.kokushibo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns the display-entity visuals for the Kokoshibos Sword abilities.
 *
 * <p>Crescent projectiles and moonbow strikes are drawn with {@link ItemDisplay}
 * entities carrying the kokushibo crescent model (custom model data 2), while
 * the Lunar Eclipse window draws a spinning ring of {@link BlockDisplay} purple
 * glass. Everything is full-bright and short-lived, and the shared core shader
 * applies the display tint so the glass and crescents glow.
 */
public final class KokushiboEffects {

    private KokushiboEffects() {
        // Utility class.
    }

    /**
     * Fires a slowing-star crescent projectile in the player's facing
     * direction. Returns the snowball id so the listener can track it.
     *
     * @param plugin owning plugin (for the safety cleanup)
     * @param player the caster
     * @return the launched snowball's unique id
     */
    public static java.util.UUID fireCrescent(final JavaPlugin plugin, final Player player) {
        final Vector direction = player.getLocation().getDirection();
        final Snowball snowball = player.launchProjectile(Snowball.class, direction.multiply(2.4));
        snowball.setGravity(false);

        final ItemDisplay display = player.getWorld().spawn(
                snowball.getLocation(), ItemDisplay.class);
        display.setItemStack(KokushiboSword.crescentItem());
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(1.8f, 1.8f, 1.8f),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
        snowball.addPassenger(display);

        snowball.getWorld().spawnParticle(Particle.WITCH,
                snowball.getLocation(), 30, 0.4, 0.4, 0.4, 0.03);
        snowball.getWorld().spawnParticle(Particle.END_ROD,
                snowball.getLocation(), 15, 0.3, 0.3, 0.3, 0.02);

        // Safety net: drop the crescent if the snowball despawns without a hit.
        plugin.getServer().getScheduler().runTaskLater(plugin, display::remove, 120L);
        return snowball.getUniqueId();
    }

    /**
     * Plays the Lunar Eclipse window burst: a spinning ring of purple glass
     * around the player.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param player the caster
     */
    public static void playEclipseBurst(final JavaPlugin plugin, final Player player) {
        final Location eye = player.getEyeLocation();
        final int segments = 20;
        final double radius = 2.2;

        final List<BlockDisplay> displays = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            final BlockDisplay display = player.getWorld().spawn(eye, BlockDisplay.class);
            display.setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration(1);
            display.setInterpolationDelay(0);
            displays.add(display);
        }

        player.getWorld().spawnParticle(Particle.WITCH, eye, 60,
                1.0, 0.7, 1.0, 0.04);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, eye, 25,
                0.9, 0.6, 0.9, 0.01);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= 16) {
                    displays.forEach(BlockDisplay::remove);
                    cancel();
                    return;
                }
                final double spin = Math.toRadians(tick * 45);
                final double rise = tick * 0.07;
                for (int i = 0; i < displays.size(); i++) {
                    final double angle = Math.toRadians(i * (360.0 / segments)) + spin;
                    final BlockDisplay display = displays.get(i);
                    display.teleport(eye.clone().add(
                            Math.sin(angle) * radius, -0.6 + rise, Math.cos(angle) * radius));
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f((float) -angle, 0f, 1f, 0f),
                            new Vector3f(0.3f, 1.4f, 0.9f),
                            new AxisAngle4f(0f, 0f, 0f, 1f)));
                }
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
}
