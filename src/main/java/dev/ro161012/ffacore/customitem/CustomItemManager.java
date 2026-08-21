package dev.ro161012.ffacore.customitem;

import dev.ro161012.ffacore.FFACore;
import dev.ro161012.ffacore.util.ItemUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the screenshot-based custom items and their combat abilities.
 *
 * <p>Items are identified by a persistent key rather than their display name,
 * while their model-data values also work for items created before the plugin
 * tag was added.</p>
 */
public final class CustomItemManager implements Listener {

    private static final NamespacedKey ITEM_KEY =
            new NamespacedKey("ffacore", "custom_item");
    private static final double DEFAULT_BEAM_DAMAGE = 24.0;
    private static final double DEFAULT_SLAM_DAMAGE = 12.0;

    private final FFACore plugin;
    private final Map<UUID, EnumMap<CustomItemType, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> activeMotion = new HashSet<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Map<UUID, Set<BlockDisplay>> prisons = new HashMap<>();
    private final Map<UUID, PotionEffect> previousSlowness = new HashMap<>();
    private final Map<UUID, Set<Hoglin>> summonedHoglins = new HashMap<>();
    private final Set<BlockDisplay> temporaryDisplays = new HashSet<>();
    private boolean enabled;

    /**
     * Creates the manager and reads the custom-item toggle.
     *
     * @param plugin owning plugin
     */
    public CustomItemManager(final FFACore plugin) {
        this.plugin = plugin;
        applyConfig();
    }

    /** Applies the current custom-item configuration immediately. */
    public void applyConfig() {
        final boolean wasEnabled = enabled;
        enabled = plugin.getConfig().getBoolean("custom-items.enabled", true);
        if (wasEnabled && !enabled) {
            shutdown();
        }
        refreshOnlineItems();
    }

    private void refreshOnlineItems() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            for (final ItemStack stack : player.getInventory().getContents()) {
                refreshItem(stack);
            }
            refreshItem(player.getInventory().getItemInOffHand());
        }
    }

    private void refreshItem(final ItemStack stack) {
        final CustomItemType type = typeOf(stack);
        if (stack == null || type == null || !stack.hasItemMeta()) {
            return;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(title(type));
        meta.lore(lore(type));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
        ItemUtils.applyTooltipStyle(stack, tooltipPalette(type).style());
    }

    /**
     * Builds a fresh configured item.
     *
     * @param type   item type
     * @param amount requested amount
     * @return configured stack
     */
    public ItemStack createItem(final CustomItemType type, final int amount) {
        final ItemStack stack = new ItemStack(type.material());
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.displayName(title(type));
        meta.lore(lore(type));
        meta.setCustomModelData(type.modelData());
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        addEnchantments(meta, type);
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, type.key());
        stack.setItemMeta(meta);
        ItemUtils.applyTooltipStyle(stack, tooltipPalette(type).style());
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    /**
     * Resolves a custom item from its persistent tag or model-data pair.
     *
     * @param stack item to inspect
     * @return matching type, or null
     */
    public CustomItemType typeOf(final ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        final String key = meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
        if (key != null) {
            final CustomItemType tagged = CustomItemType.fromKey(key);
            if (tagged != null && tagged.material() == stack.getType()) {
                return tagged;
            }
        }
        for (final CustomItemType type : CustomItemType.values()) {
            if (stack.getType() == type.material()
                    && hasModelData(stack, type.modelData())) {
                return type;
            }
        }
        return null;
    }

    /**
     * Resolves an item from its base material and model data.
     *
     * @param material base material
     * @param modelData model-data value
     * @return matching custom type, or null
     */
    public CustomItemType typeForModelData(final Material material, final Integer modelData) {
        if (material == null || modelData == null) {
            return null;
        }
        for (final CustomItemType type : CustomItemType.values()) {
            if (type.material() == material && type.modelData() == modelData) {
                return type;
            }
        }
        return null;
    }

    /**
     * Gives a custom item to a player, dropping overflow at their feet.
     *
     * @param player recipient
     * @param type item type
     * @param amount amount to give
     */
    public void give(final Player player, final CustomItemType type, final int amount) {
        final Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(createItem(type, amount));
        for (final ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Returns all item types in declaration order.
     *
     * @return custom item types
     */
    public List<CustomItemType> types() {
        return List.of(CustomItemType.values());
    }

    /** Activates active-item abilities from the client's swap-hands key. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        if (!enabled) {
            return;
        }
        final Player player = event.getPlayer();
        final CustomItemType main = typeOf(player.getInventory().getItemInMainHand());
        final CustomItemType offhand = typeOf(player.getInventory().getItemInOffHand());
        if (!isActive(main) && !isActive(offhand)) {
            return;
        }
        event.setCancelled(true);
        if (activeMotion.contains(player.getUniqueId())
                || activateFromSwap(player, main)
                || activateFromSwap(player, offhand)) {
            return;
        }
    }

    private boolean activateFromSwap(final Player player, final CustomItemType type) {
        if (!isActive(type) || !startCooldown(player, type)) {
            return false;
        }
        activate(player, type);
        return true;
    }

    /** Handles bow abilities when the custom bow is released. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(final EntityShootBowEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player player)) {
            return;
        }
        final CustomItemType type = typeOf(event.getBow());
        if (type == CustomItemType.GRAPPLE_BOW) {
            Vector direction = event.getProjectile().getVelocity();
            if (direction.lengthSquared() < 0.001) {
                direction = player.getEyeLocation().getDirection();
            }
            player.setVelocity(direction.normalize().multiply(
                    value(type, "dash-velocity", 1.35)));
            player.setFallDistance(0);
            trail(player.getWorld(), player.getLocation().add(0, 0.8, 0),
                    Color.fromRGB(190, 120, 255));
            sound(player.getWorld(), player.getLocation(),
                    "minecraft:entity.ender_pearl.throw", 0.8f, 1.4f);
        } else if (type == CustomItemType.VOLLEY_BOW && startCooldown(player, type)) {
            spawnVolley(player, event, type);
        }
    }

    /** Handles sword and mace passive proc effects plus Pigxaliur's hoglins. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(final EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)
                || target == attacker) {
            return;
        }
        final CustomItemType type = combatType(attacker);
        if (type == null) {
            return;
        }
        switch (type) {
            case FROST_SWORD -> {
                if (chance(type, "proc-chance", 0.20)) {
                    freeze(target);
                }
            }
            case STRIKE_SWORD -> {
                if (chance(type, "proc-chance", 0.15)) {
                    target.getWorld().strikeLightning(target.getLocation());
                    sound(target.getWorld(), target.getLocation(),
                            "minecraft:entity.lightning_bolt.thunder", 0.7f, 1.4f);
                }
            }
            case LIFESTEALER_SWORD -> {
                if (chance(type, "proc-chance", 0.20)) {
                    final double amount = value(type, "heal-amount", 4.0);
                    attacker.setHealth(Math.min(attacker.getMaxHealth(),
                            attacker.getHealth() + amount));
                    dust(attacker.getWorld(), attacker.getLocation().add(0, 1, 0),
                            Color.fromRGB(255, 55, 85), 18, 1.25f);
                    sound(attacker.getWorld(), attacker.getLocation(),
                            "minecraft:entity.player.levelup", 0.45f, 1.8f);
                }
            }
            case PIGXALIUR -> {
                if (chance(type, "proc-chance", 0.20)
                        && startCooldown(attacker, type)) {
                    spawnHoglins(attacker, target);
                }
            }
            case SEISMIC_AXE -> {
                if (!activeMotion.contains(attacker.getUniqueId())
                        && startCooldown(attacker, type)) {
                    quakeImpact(attacker, type);
                }
            }
            case WITHER_MACE -> {
                if (chance(type, "proc-chance", 0.20)
                        && startCooldown(attacker, type)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                            ticks(type, "wither-seconds", 10.0), 1, false, true, true));
                    dust(target.getWorld(), target.getLocation().add(0, 1, 0),
                            Color.fromRGB(80, 20, 90), 24, 1.25f);
                    sound(target.getWorld(), target.getLocation(),
                            "minecraft:entity.wither.hurt", 0.7f, 1.2f);
                }
            }
            default -> { }
        }
    }

    /** Handles Adrenaline Blade's lethal-hit second chance. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(final EntityDamageEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player player)
                || event.getFinalDamage() < player.getHealth()
                || !hasHeldType(player, CustomItemType.ADRENALINE_BLADE)
                || !startCooldown(player, CustomItemType.ADRENALINE_BLADE)) {
            return;
        }
        event.setCancelled(true);
        player.setHealth(player.getMaxHealth());
        final int duration = ticks(CustomItemType.ADRENALINE_BLADE,
                "second-chance-seconds", 4.0);
        final int hearts = (int) Math.round(value(CustomItemType.ADRENALINE_BLADE,
                "absorption-hearts", 14.0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration,
                Math.max(0, hearts / 2 - 1), false, false, true));
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 20));
        dust(player.getWorld(), player.getLocation().add(0, 1, 0),
                Color.fromRGB(70, 220, 255), 45, 2.0f);
        sound(player.getWorld(), player.getLocation(),
                "minecraft:item.totem.use", 1.0f, 1.1f);
    }

    /** Removes a player's active ability state as soon as they leave. */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        finish(event.getPlayer());
        cooldowns.remove(uuid);
        removeSummonedHoglins(uuid);
    }

    /**
     * Cancels active visuals and clears manager state during plugin shutdown.
     */
    public void shutdown() {
        for (final BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        activeMotion.clear();
        for (final Set<BlockDisplay> displays : prisons.values()) {
            for (final BlockDisplay display : displays) {
                if (display.isValid()) {
                    display.remove();
                }
            }
        }
        for (final UUID uuid : new HashSet<>(previousSlowness.keySet())) {
            final Player player = Bukkit.getPlayer(uuid);
            restoreSlowness(player, uuid);
        }
        previousSlowness.clear();
        prisons.clear();
        for (final BlockDisplay display : temporaryDisplays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        temporaryDisplays.clear();
        for (final Set<Hoglin> hoglins : summonedHoglins.values()) {
            for (final Hoglin hoglin : hoglins) {
                if (hoglin.isValid()) {
                    hoglin.remove();
                }
            }
        }
        summonedHoglins.clear();
        cooldowns.clear();
    }

    private void activate(final Player player, final CustomItemType type) {
        switch (type) {
            case DASH_SWORD -> dash(player, type, 8.0,
                    Color.fromRGB(185, 125, 255));
            case FLUX_SWORD -> beam(player, type);
            case ROCKET_SPEAR -> rocket(player, type);
            case VENOM_SPEAR -> venomDash(player, type);
            case DASH_SPEAR -> dash(player, type, 9.0,
                    Color.fromRGB(255, 72, 190));
            case VAULT_SPEAR -> vault(player, type);
            case PAXE -> prison(player, type);
            case SEISMIC_AXE, EARTHQUAKE_MACE -> earthquake(player, type);
            case COB_AXE, COB_MACE -> shredCobwebs(player, type);
            case MAGMA_AXE -> magma(player, type);
            case WHIRL_AXE -> whirl(player, type);
            case DASH_MACE -> lookDash(player, type);
            default -> { }
        }
    }

    private void dash(final Player player, final CustomItemType type,
                      final double defaultDistance, final Color color) {
        if (!activeMotion.add(player.getUniqueId())) {
            return;
        }
        final int duration = Math.max(2, (int) value(type, "duration-ticks", 6.0));
        final double distance = value(type, "distance", defaultDistance);
        final Vector direction = horizontalDirection(player);
        final BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || tick++ >= duration) {
                    finish(player);
                    return;
                }
                final Location next = player.getLocation().clone().add(
                        direction.clone().multiply(distance / duration));
                if (!next.getBlock().isPassable()
                        || !next.clone().add(0, 1, 0).getBlock().isPassable()) {
                    finish(player);
                    return;
                }
                player.teleport(next);
                trail(player.getWorld(), player.getLocation().add(0, 0.8, 0), color);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.put(player.getUniqueId(), task);
    }

    private void spawnVolley(final Player player, final EntityShootBowEvent event,
                             final CustomItemType type) {
        Vector direction = event.getProjectile().getVelocity();
        if (direction.lengthSquared() < 0.001) {
            direction = player.getEyeLocation().getDirection();
        }
        direction.normalize();
        final Location origin = player.getEyeLocation().add(direction.clone().multiply(0.5));
        final float speed = (float) value(type, "arrow-speed", 3.0);
        final double spread = Math.toRadians(value(type, "spread-degrees", 7.0));
        for (final double angle : new double[] {-spread, spread}) {
            final Arrow arrow = player.getWorld().spawnArrow(origin,
                    direction.clone().rotateAroundY(angle), speed, 0.0f);
            arrow.setShooter(player);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        trail(player.getWorld(), origin, Color.fromRGB(255, 215, 80));
        sound(player.getWorld(), origin, "minecraft:entity.arrow.shoot", 0.7f, 1.2f);
    }

    private void shredCobwebs(final Player player, final CustomItemType type) {
        final World world = player.getWorld();
        final Location center = player.getLocation();
        final int radius = (int) Math.round(bounded(value(type, "radius", 7.0), 1.0, 20.0));
        int removed = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) {
                        continue;
                    }
                    final Block block = world.getBlockAt(center.getBlockX() + x,
                            center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() == Material.COBWEB) {
                        final Location effect = block.getLocation().add(0.5, 0.5, 0.5);
                        block.setType(Material.AIR, false);
                        world.spawnParticle(Particle.CLOUD, effect, 5,
                                0.15, 0.15, 0.15, 0.03);
                        removed++;
                    }
                }
            }
        }
        ring(world, center.clone().add(0, 1, 0), Color.fromRGB(235, 235, 235), radius);
        sound(world, center, "minecraft:block.wool.break", 1.0f,
                removed == 0 ? 1.4f : 0.8f);
    }

    private void magma(final Player player, final CustomItemType type) {
        final World world = player.getWorld();
        final Location center = player.getLocation();
        final double radius = bounded(value(type, "radius", 6.0), 0.0, 20.0);
        final double damage = nonNegative(value(type, "damage", 14.0));
        final int segments = Math.max(16, (int) Math.round(radius * 5.0));
        final Set<BlockDisplay> displays = new HashSet<>();
        final BlockData magma = Bukkit.createBlockData(Material.MAGMA_BLOCK);
        for (int i = 0; i < segments; i++) {
            final double angle = Math.PI * 2 * i / segments;
            final Location point = center.clone().add(
                    Math.cos(angle) * radius - 0.3, -0.15,
                    Math.sin(angle) * radius - 0.3);
            final BlockDisplay display = world.spawn(point, BlockDisplay.class, spawned -> {
                spawned.setBlock(magma);
                spawned.setGlowing(true);
                spawned.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(),
                        new Vector3f(0.6f, 0.85f, 0.6f), new Quaternionf()));
            });
            displays.add(display);
        }
        temporaryDisplays.addAll(displays);
        damageNearby(player, center, radius, damage);
        igniteNearby(player, center, radius, ticks(type, "fire-seconds", 4.0));
        ring(world, center.clone().add(0, 0.4, 0), Color.fromRGB(255, 100, 35), radius);
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.5, 0),
                55, radius / 2.0, 0.4, radius / 2.0, 0.08);
        world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.3, 0),
                18, radius / 2.0, 0.2, radius / 2.0, 0.0);
        sound(world, center, "minecraft:block.lava.pop", 1.2f, 0.7f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (final BlockDisplay display : displays) {
                temporaryDisplays.remove(display);
                if (display.isValid()) {
                    display.remove();
                }
            }
        }, ticks(type, "visual-seconds", 2.0));
    }

    private void whirl(final Player player, final CustomItemType type) {
        final Location center = player.getLocation();
        final double radius = bounded(value(type, "radius", 6.0), 0.0, 20.0);
        launchNearby(player, center, radius,
                value(type, "knockback", 1.4), value(type, "vertical-knockback", 0.65));
        damageNearby(player, center, radius, value(type, "damage", 8.0));
        ring(player.getWorld(), center.clone().add(0, 1, 0),
                Color.fromRGB(185, 245, 235), radius);
        player.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0),
                70, radius / 2.0, 1.0, radius / 2.0, 0.12);
        sound(player.getWorld(), center, "minecraft:entity.generic.wind_burst", 1.2f, 0.8f);
    }

    private void earthquake(final Player player, final CustomItemType type) {
        if (!activeMotion.add(player.getUniqueId())) {
            return;
        }
        final int launchTicks = Math.max(6, ticks(type, "launch-seconds", 0.75));
        final int maximum = launchTicks + Math.max(20, ticks(type, "maximum-air-seconds", 3.0));
        player.setVelocity(new Vector(0, value(type, "launch-velocity", 1.15), 0));
        player.setFallDistance(0);
        sound(player.getWorld(), player.getLocation(),
                "minecraft:entity.player.attack.strong", 1.0f, 0.65f);
        final BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;
            private boolean descending;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    finish(player);
                    return;
                }
                if (tick++ > maximum) {
                    quakeImpact(player, type);
                    finish(player);
                    return;
                }
                player.setFallDistance(0);
                if (tick < launchTicks) {
                    trail(player.getWorld(), player.getLocation(), Color.fromRGB(225, 165, 40));
                } else if (!descending) {
                    descending = true;
                    player.setVelocity(new Vector(0,
                            -value(type, "slam-velocity", 1.8), 0));
                    sound(player.getWorld(), player.getLocation(),
                            "minecraft:entity.generic.explode", 0.6f, 0.6f);
                }
                if (descending) {
                    trail(player.getWorld(), player.getLocation(), Color.fromRGB(205, 125, 30));
                    if (player.isOnGround() && tick > launchTicks + 2) {
                        quakeImpact(player, type);
                        finish(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.put(player.getUniqueId(), task);
    }

    private void quakeImpact(final Player player, final CustomItemType type) {
        final UUID uuid = player.getUniqueId();
        final boolean guarded = activeMotion.add(uuid);
        try {
            final Location location = player.getLocation();
            final World world = player.getWorld();
            final double radius = bounded(value(type, "impact-radius", 8.0), 0.0, 24.0);
            damageNearby(player, location, radius,
                    value(type, "impact-damage", DEFAULT_SLAM_DAMAGE));
            launchNearby(player, location, radius,
                    value(type, "knockback", 1.35), value(type, "vertical-knockback", 0.85));
            ring(world, location, Color.fromRGB(225, 155, 35), radius);
            world.spawnParticle(Particle.BLOCK, location.clone().add(0, 0.2, 0),
                    90, radius / 2.0, 0.2, radius / 2.0, 0.1,
                    Bukkit.createBlockData(Material.DIRT));
            world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.8, 0),
                    60, radius / 2.0, 0.6, radius / 2.0, 0.08);
            world.spawnParticle(Particle.EXPLOSION, location.clone().add(0, 0.4, 0),
                    3, 0.3, 0.3, 0.3, 0.0);
            sound(world, location, "minecraft:entity.generic.explode", 1.3f, 0.75f);
        } finally {
            if (guarded) {
                activeMotion.remove(uuid);
            }
        }
    }

    private void lookDash(final Player player, final CustomItemType type) {
        final Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(value(type, "dash-velocity", 1.35)));
        player.setFallDistance(0);
        trail(player.getWorld(), player.getLocation().add(0, 0.8, 0),
                Color.fromRGB(100, 235, 100));
        sound(player.getWorld(), player.getLocation(),
                "minecraft:entity.player.attack.knockback", 1.0f, 1.25f);
    }

    private void igniteNearby(final Player source, final Location center,
                              final double radius, final int fireTicks) {
        final double safeRadius = nonNegative(radius);
        for (final Entity entity : center.getWorld().getNearbyEntities(
                center, safeRadius, safeRadius, safeRadius)) {
            if (entity instanceof LivingEntity target && target != source
                    && target.getLocation().distanceSquared(center) <= safeRadius * safeRadius) {
                target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
            }
        }
    }

    private void launchNearby(final Player source, final Location center,
                              final double radius, final double horizontal,
                              final double vertical) {
        final double safeRadius = nonNegative(radius);
        for (final Entity entity : center.getWorld().getNearbyEntities(
                center, safeRadius, safeRadius, safeRadius)) {
            if (!(entity instanceof LivingEntity target) || target == source
                    || target.getLocation().distanceSquared(center) > safeRadius * safeRadius) {
                continue;
            }
            final Vector away = target.getLocation().toVector().subtract(center.toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.001) {
                away.setX(1);
            }
            target.setVelocity(away.normalize().multiply(horizontal).setY(vertical));
        }
    }

    private void venomDash(final Player player, final CustomItemType type) {
        if (chance(type, "proc-chance", 0.20)) {
            final double radius = bounded(value(type, "effect-radius", 20.0), 0.0, 50.0);
            for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity target && target != player
                        && target.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                            ticks(type, "poison-seconds", 5.0), 1, false, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                            ticks(type, "blind-seconds", 3.0), 0, false, true, true));
                }
            }
            ring(player.getWorld(), player.getLocation().add(0, 1, 0),
                    Color.fromRGB(100, 255, 80), radius / 2.0);
        }
        dash(player, type, 8.0, Color.fromRGB(90, 255, 90));
    }

    private void beam(final Player player, final CustomItemType type) {
        final World world = player.getWorld();
        final Location eye = player.getEyeLocation();
        final Vector direction = eye.getDirection().normalize();
        final double range = bounded(value(type, "range", 24.0), 0.0, 64.0);
        final double radius = bounded(value(type, "radius", 1.35), 0.0, 5.0);
        final double damage = nonNegative(value(type, "damage", DEFAULT_BEAM_DAMAGE));
        final Set<UUID> hit = new HashSet<>();
        sound(world, eye, "minecraft:entity.warden.sonic_boom", 1.0f, 1.15f);
        for (double distance = 0.5; distance <= range; distance += 0.5) {
            final org.bukkit.Location point = eye.clone().add(direction.clone().multiply(distance));
            dust(world, point, Color.fromRGB(70, 190, 255), 5, 1.1f);
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 2, 0.08, 0.08, 0.08, 0.02);
            for (final Entity entity : world.getNearbyEntities(point, radius, radius, radius)) {
                if (entity instanceof LivingEntity target && target != player
                        && hit.add(target.getUniqueId())) {
                    target.damage(damage, player);
                }
            }
        }
        ring(world, eye.clone().add(direction.clone().multiply(range)),
                Color.fromRGB(125, 225, 255), 1.8);
    }

    private void rocket(final Player player, final CustomItemType type) {
        if (!activeMotion.add(player.getUniqueId())) {
            return;
        }
        final int launchTicks = Math.max(8, ticks(type, "launch-seconds", 1.0));
        final int maximum = launchTicks + Math.max(20, ticks(type, "maximum-air-seconds", 4.0));
        final double launchVelocity = value(type, "launch-velocity", 1.45);
        final double slamVelocity = value(type, "slam-velocity", 2.2);
        player.setVelocity(new Vector(0, launchVelocity, 0));
        sound(player.getWorld(), player.getLocation(),
                "minecraft:entity.firework_rocket.launch", 1.0f, 0.8f);
        final BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;
            private boolean descending;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    finish(player);
                    return;
                }
                if (tick++ > maximum) {
                    impact(player, type);
                    finish(player);
                    return;
                }
                if (tick < launchTicks) {
                    player.setFallDistance(0);
                    trail(player.getWorld(), player.getLocation(), Color.fromRGB(255, 125, 70));
                } else if (!descending) {
                    descending = true;
                    player.setVelocity(new Vector(0, -slamVelocity, 0));
                    sound(player.getWorld(), player.getLocation(),
                            "minecraft:entity.wither.shoot", 0.8f, 0.7f);
                }
                if (descending) {
                    player.setFallDistance(0);
                    trail(player.getWorld(), player.getLocation(), Color.fromRGB(255, 75, 45));
                    if (player.isOnGround() && tick > launchTicks + 2) {
                        impact(player, type);
                        finish(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.put(player.getUniqueId(), task);
    }

    private void impact(final Player player, final CustomItemType type) {
        final Location location = player.getLocation();
        final double radius = bounded(value(type, "impact-radius", 5.0), 0.0, 20.0);
        damageNearby(player, location, radius, value(type, "impact-damage", DEFAULT_SLAM_DAMAGE));
        ring(player.getWorld(), location, Color.fromRGB(255, 120, 55), radius);
        player.getWorld().spawnParticle(Particle.EXPLOSION, location.add(0, 0.5, 0),
                2, 0.2, 0.2, 0.2, 0.0);
        sound(player.getWorld(), location, "minecraft:entity.generic.explode", 1.0f, 0.9f);
    }

    private void vault(final Player player, final CustomItemType type) {
        final Vector direction = horizontalDirection(player).multiply(value(type, "forward-velocity", 0.55));
        direction.setY(value(type, "vertical-velocity", 1.35));
        player.setVelocity(direction);
        trail(player.getWorld(), player.getLocation(), Color.fromRGB(100, 190, 255));
        sound(player.getWorld(), player.getLocation(),
                "minecraft:entity.player.attack.knockback", 1.0f, 0.8f);
    }

    private void prison(final Player player, final CustomItemType type) {
        if (!activeMotion.add(player.getUniqueId())) {
            return;
        }
        final Location anchor = player.getLocation().clone();
        final Set<BlockDisplay> displays = new HashSet<>();
        final BlockData glass = Bukkit.createBlockData(Material.PINK_STAINED_GLASS);
        for (int latitude = -2; latitude <= 2; latitude++) {
            final double y = latitude * 0.85;
            final double radius = Math.sqrt(Math.max(0.0, 1.0 - (latitude * latitude / 4.0))) * 2.35;
            final int segments = radius < 0.1 ? 1 : 16;
            for (int segment = 0; segment < segments; segment++) {
                final double angle = Math.PI * 2 * segment / segments;
                final Location location = anchor.clone().add(
                        Math.cos(angle) * radius - 0.175, y + 1.05 - 0.175,
                        Math.sin(angle) * radius - 0.175);
                final BlockDisplay display = player.getWorld().spawn(location, BlockDisplay.class,
                        spawned -> {
                            spawned.setBlock(glass);
                            spawned.setGlowing(true);
                            spawned.setTransformation(new Transformation(
                                    new Vector3f(), new Quaternionf(),
                                    new Vector3f(0.35f, 0.35f, 0.35f), new Quaternionf()));
                            spawned.setInterpolationDuration(2);
                        });
                displays.add(display);
            }
        }
        prisons.put(player.getUniqueId(), displays);
        previousSlowness.put(player.getUniqueId(),
                player.getPotionEffect(PotionEffectType.SLOWNESS));
        final int duration = ticks(type, "duration-seconds", 20.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration + 5,
                10, false, false, true));
        sound(player.getWorld(), anchor, "minecraft:block.amethyst_block.chime", 1.0f, 0.8f);
        final BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || tick++ >= duration) {
                    finish(player);
                    return;
                }
                player.teleport(anchor);
                player.setVelocity(new Vector());
                ring(player.getWorld(), anchor.clone().add(0, 1, 0),
                        Color.fromRGB(255, 90, 190), 2.35);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.put(player.getUniqueId(), task);
    }

    private void spawnHoglins(final Player owner, final LivingEntity target) {
        final World world = owner.getWorld();
        final UUID ownerId = owner.getUniqueId();
        final int amount = (int) Math.round(bounded(
                value(CustomItemType.PIGXALIUR, "hoglin-count", 3.0), 1.0, 10.0));
        final int lifetime = ticks(CustomItemType.PIGXALIUR, "hoglin-lifetime-seconds", 20.0);
        for (int i = 0; i < amount; i++) {
            final double angle = Math.PI * 2 * i / amount;
            final Location location = target.getLocation().clone().add(
                    Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            final Hoglin hoglin = world.spawn(location, Hoglin.class);
            hoglin.setTarget(target);
            summonedHoglins.computeIfAbsent(ownerId, ignored -> new HashSet<>()).add(hoglin);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                final Set<Hoglin> owned = summonedHoglins.get(ownerId);
                if (owned != null) {
                    owned.remove(hoglin);
                    if (owned.isEmpty()) {
                        summonedHoglins.remove(ownerId);
                    }
                }
                if (hoglin.isValid()) {
                    hoglin.remove();
                }
            }, lifetime);
        }
        ring(world, target.getLocation().add(0, 1, 0), Color.fromRGB(100, 255, 100), 2.0);
        sound(world, target.getLocation(), "minecraft:entity.hoglin.ambient", 1.0f, 1.2f);
    }

    private void freeze(final LivingEntity target) {
        final int freezeTicks = ticks(CustomItemType.FROST_SWORD, "freeze-seconds", 3.0);
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks,
                4, false, true, true));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 28, 0.45, 0.8, 0.45, 0.02);
        sound(target.getWorld(), target.getLocation(),
                "minecraft:block.powder_snow.place", 0.8f, 1.3f);
    }

    private void damageNearby(final Player source, final Location center,
                              final double radius, final double damage) {
        final double safeRadius = nonNegative(radius);
        final double safeDamage = nonNegative(damage);
        final Collection<Entity> entities = center.getWorld().getNearbyEntities(
                center, safeRadius, safeRadius, safeRadius);
        for (final Entity entity : entities) {
            if (entity instanceof LivingEntity target && target != source
                    && target.getLocation().distanceSquared(center) <= safeRadius * safeRadius) {
                target.damage(safeDamage, source);
            }
        }
    }

    private void finish(final Player player) {
        final UUID uuid = player.getUniqueId();
        final BukkitTask task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        activeMotion.remove(uuid);
        final Set<BlockDisplay> displays = prisons.remove(uuid);
        if (displays != null) {
            for (final BlockDisplay display : displays) {
                if (display.isValid()) {
                    display.remove();
                }
            }
        }
        restoreSlowness(player, uuid);
    }

    private void restoreSlowness(final Player player, final UUID uuid) {
        if (!previousSlowness.containsKey(uuid)) {
            return;
        }
        final PotionEffect previous = previousSlowness.remove(uuid);
        if (player == null || !player.isOnline() || previous == null) {
            if (player != null) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            return;
        }
        player.addPotionEffect(previous, true);
    }

    private Vector horizontalDirection(final Player player) {
        final Vector direction = player.getLocation().getDirection().clone();
        direction.setY(0);
        return direction.lengthSquared() < 0.001 ? new Vector(0, 0, 1) : direction.normalize();
    }

    private void trail(final World world, final Location location, final Color color) {
        dust(world, location, color, 16, 1.35f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location, 5,
                0.25, 0.35, 0.25, 0.03);
    }

    private void ring(final World world, final Location center, final Color color,
                      final double radius) {
        final double safeRadius = nonNegative(radius);
        final int points = Math.max(16, (int) Math.round(safeRadius * 18));
        for (int i = 0; i < points; i++) {
            final double angle = Math.PI * 2 * i / points;
            final Location point = center.clone().add(
                    Math.cos(angle) * safeRadius, 0, Math.sin(angle) * safeRadius);
            dust(world, point, color, 2, 1.15f);
        }
    }

    private void dust(final World world, final Location location, final Color color,
                      final int count, final float size) {
        world.spawnParticle(Particle.DUST, location, count,
                0.08, 0.08, 0.08, 0.0, new Particle.DustOptions(color, size));
    }

    private boolean isActive(final CustomItemType type) {
        return type == CustomItemType.DASH_SWORD || type == CustomItemType.FLUX_SWORD
                || type == CustomItemType.ROCKET_SPEAR || type == CustomItemType.VENOM_SPEAR
                || type == CustomItemType.DASH_SPEAR || type == CustomItemType.VAULT_SPEAR
                || type == CustomItemType.PAXE || type == CustomItemType.SEISMIC_AXE
                || type == CustomItemType.COB_AXE || type == CustomItemType.MAGMA_AXE
                || type == CustomItemType.WHIRL_AXE || type == CustomItemType.EARTHQUAKE_MACE
                || type == CustomItemType.COB_MACE || type == CustomItemType.DASH_MACE;
    }

    private CustomItemType combatType(final Player player) {
        final CustomItemType main = typeOf(player.getInventory().getItemInMainHand());
        if (hasCombatProc(main)) {
            return main;
        }
        final CustomItemType offhand = typeOf(player.getInventory().getItemInOffHand());
        return hasCombatProc(offhand) ? offhand : null;
    }

    private boolean hasCombatProc(final CustomItemType type) {
        return type == CustomItemType.FROST_SWORD || type == CustomItemType.STRIKE_SWORD
                || type == CustomItemType.LIFESTEALER_SWORD || type == CustomItemType.PIGXALIUR
                || type == CustomItemType.SEISMIC_AXE || type == CustomItemType.WITHER_MACE;
    }

    private boolean hasHeldType(final Player player, final CustomItemType expected) {
        return typeOf(player.getInventory().getItemInMainHand()) == expected
                || typeOf(player.getInventory().getItemInOffHand()) == expected;
    }

    private void removeSummonedHoglins(final UUID owner) {
        final Set<Hoglin> hoglins = summonedHoglins.remove(owner);
        if (hoglins == null) {
            return;
        }
        for (final Hoglin hoglin : hoglins) {
            if (hoglin.isValid()) {
                hoglin.remove();
            }
        }
    }

    private boolean startCooldown(final Player player, final CustomItemType type) {
        final long now = System.currentTimeMillis();
        final EnumMap<CustomItemType, Long> playerCooldowns = cooldowns.computeIfAbsent(
                player.getUniqueId(), ignored -> new EnumMap<>(CustomItemType.class));
        final long until = playerCooldowns.getOrDefault(type, 0L);
        if (until > now) {
            return false;
        }
        final long duration = Math.max(0L, Math.round(cooldown(type) * 1000.0));
        final long expiry = duration > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + duration;
        playerCooldowns.put(type, expiry);
        return true;
    }

    private boolean chance(final CustomItemType type, final String key, final double fallback) {
        return ThreadLocalRandom.current().nextDouble() < Math.max(0.0,
                Math.min(1.0, value(type, key, fallback)));
    }

    private double nonNegative(final double value) {
        return bounded(value, 0.0, Double.MAX_VALUE);
    }

    private double bounded(final double value, final double min, final double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private double cooldown(final CustomItemType type) {
        return value(type, "cooldown-seconds", defaultCooldown(type));
    }

    private double defaultCooldown(final CustomItemType type) {
        return switch (type) {
            case DASH_SWORD -> 20.0;
            case FLUX_SWORD -> 20.0;
            case PIGXALIUR -> 30.0;
            case ADRENALINE_BLADE -> 70.0;
            case ROCKET_SPEAR -> 30.0;
            case VENOM_SPEAR -> 12.0;
            case DASH_SPEAR -> 20.0;
            case VAULT_SPEAR -> 20.0;
            case PAXE -> 20.0;
            case SEISMIC_AXE -> 25.0;
            case COB_AXE -> 30.0;
            case MAGMA_AXE -> 20.0;
            case WHIRL_AXE -> 20.0;
            case VOLLEY_BOW -> 8.0;
            case EARTHQUAKE_MACE -> 30.0;
            case COB_MACE -> 30.0;
            case DASH_MACE -> 20.0;
            default -> 0.0;
        };
    }

    private double value(final CustomItemType type, final String key, final double fallback) {
        return plugin.getConfig().getDouble("custom-items." + type.key() + "." + key, fallback);
    }

    private int ticks(final CustomItemType type, final String key, final double fallbackSeconds) {
        final long value = Math.round(value(type, key, fallbackSeconds) * 20.0);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, value));
    }

    private List<Component> lore(final CustomItemType type) {
        final List<Component> lines = new ArrayList<>();
        switch (type) {
            case DASH_SWORD -> lines.add(gray(bind() + " to dash "
                    + whole(value(type, "distance", 8.0)) + " blocks with a "
                    + seconds(type) + " cooldown"));
            case FROST_SWORD -> lines.add(gray("Has a " + percent(type, "proc-chance", 0.20)
                    + " chance to freeze enemies."));
            case STRIKE_SWORD -> lines.add(gray("Has a " + percent(type, "proc-chance", 0.15)
                    + " chance to strike lightning."));
            case LIFESTEALER_SWORD -> lines.add(gray("Has a "
                    + percent(type, "proc-chance", 0.20) + " chance to heal up by "
                    + whole(value(type, "heal-amount", 4.0)) + "."));
            case ADRENALINE_BLADE -> {
                lines.add(gray("A sword that gives you a second chance."));
                lines.add(Component.empty());
                lines.add(colored("POWERS:", 0x3B8CFF).decorate(TextDecoration.BOLD));
                lines.add(colored("★ SECOND CHANCE", 0x6EA8FF));
                lines.add(Component.empty());
                lines.add(colored("SECOND CHANCE", 0x3B8CFF).decorate(TextDecoration.BOLD));
                lines.add(colored("Allows its user to gain a second chance", 0x8BB8FF));
                lines.add(colored("granting full health and "
                        + whole(value(type, "absorption-hearts", 14.0))
                        + " hearts for " + whole(value(type, "second-chance-seconds", 4.0))
                        + "s.", 0x8BB8FF));
                lines.add(Component.empty());
                lines.add(colored("Cooldown: " + seconds(type), 0x4F9BFF));
            }
            case FLUX_SWORD -> {
                lines.add(gray(bind() + " to cast a powerful beam."));
                lines.add(gray("Deals massive damage to hit players."));
                lines.add(colored("Cooldown: " + seconds(type), 0x4F9BFF));
            }
            case PIGXALIUR -> {
                lines.add(gray("Has a " + percent(type, "proc-chance", 0.20)
                        + " chance on hit to summon "
                        + whole(value(type, "hoglin-count", 3.0)) + " hoglins"));
                lines.add(gray("that hunt your target. " + seconds(type) + " cooldown"));
            }
            case ROCKET_SPEAR -> lines.add(gray(bind()
                    + " to blast into the sky and slam back down, "
                    + seconds(type) + " cooldown"));
            case VENOM_SPEAR -> {
                lines.add(gray(bind() + " to dash, " + seconds(type) + " cooldown"));
                lines.add(gray(percent(type, "proc-chance", 0.20)
                        + " chance on dash to poison and blind everyone within "
                        + whole(value(type, "effect-radius", 20.0)) + " blocks"));
            }
            case DASH_SPEAR -> lines.add(gray(bind()
                    + " to dash forward in a pink trail with a "
                    + seconds(type) + " cooldown"));
            case VAULT_SPEAR -> lines.add(gray(bind()
                    + " to vault high into the air with a "
                    + seconds(type) + " cooldown"));
            case PAXE -> lines.add(gray(bind()
                    + " to trap yourself in an unbreakable pink glass ball for "
                    + whole(value(type, "duration-seconds", 20.0)) + "s"));
            case SEISMIC_AXE -> {
                lines.add(gray(bind() + " to leap and slam the ground,"));
                lines.add(gray("erupting an earthquake that deals "
                        + whole(value(type, "impact-damage", 24.0)) + " damage."));
            }
            case COB_AXE -> lines.add(gray(bind()
                    + " to shred every cobweb in a "
                    + whole(value(type, "radius", 7.0)) + " block radius with a "
                    + seconds(type) + " cooldown"));
            case MAGMA_AXE -> lines.add(gray(bind()
                    + " to erupt a ring of magma igniting everyone within "
                    + whole(value(type, "radius", 6.0)) + " blocks"));
            case WHIRL_AXE -> lines.add(gray(bind()
                    + " to unleash a whirlwind launching everyone within "
                    + whole(value(type, "radius", 6.0)) + " blocks away"));
            case GRAPPLE_BOW -> lines.add(gray("Allows its shooter to dash in any direction of shooting,"));
            case VOLLEY_BOW -> lines.add(gray("Fires 2 extra arrows in a spread with a "
                    + seconds(type) + " cooldown"));
            case EARTHQUAKE_MACE -> {
                lines.add(gray(bind()
                        + " to leap up and slam the ground, erupting an earthquake"));
                lines.add(gray("that launches everyone within "
                        + whole(value(type, "impact-radius", 8.0)) + " blocks"));
            }
            case COB_MACE -> lines.add(gray(bind()
                    + " to shred every cobweb in a "
                    + whole(value(type, "radius", 7.0)) + " block radius with a "
                    + seconds(type) + " cooldown"));
            case WITHER_MACE -> lines.add(gray("Has a " + percent(type, "proc-chance", 0.20)
                    + " chance on hit to inflict Wither II for "
                    + whole(value(type, "wither-seconds", 10.0)) + "s, "
                    + seconds(type) + " cooldown"));
            case DASH_MACE -> lines.add(gray(bind()
                    + " to dash forward in direction of looking with a "
                    + seconds(type) + " cooldown"));
            default -> { }
        }
        return lines;
    }

    private void addEnchantments(final ItemMeta meta, final CustomItemType type) {
        if (type == CustomItemType.PAXE) {
            enchant(meta, "sharpness", 5);
            enchant(meta, "efficiency", 5);
            enchant(meta, "unbreaking", 3);
            enchant(meta, "mending", 1);
            return;
        }
        if (type.material() == Material.NETHERITE_SPEAR) {
            enchant(meta, "lunge", 3);
            enchant(meta, "sharpness", 5);
            enchant(meta, "unbreaking", 3);
            enchant(meta, "mending", 1);
            return;
        }
        if (type.material() == Material.BOW) {
            enchant(meta, "power", 5);
            enchant(meta, "punch", 2);
            enchant(meta, "unbreaking", 3);
            enchant(meta, "mending", 1);
            return;
        }
        if (type.material() == Material.MACE) {
            final int windBurst = switch (type) {
                case EARTHQUAKE_MACE -> 3;
                case COB_MACE -> 1;
                case WITHER_MACE, DASH_MACE -> 2;
                default -> 1;
            };
            final int density = switch (type) {
                case EARTHQUAKE_MACE -> 3;
                case COB_MACE, WITHER_MACE -> 4;
                case DASH_MACE -> 5;
                default -> 1;
            };
            enchant(meta, "wind_burst", windBurst);
            enchant(meta, "density", density);
            enchant(meta, "unbreaking", 3);
            enchant(meta, "mending", 1);
            return;
        }
        if (type.material() == Material.NETHERITE_AXE) {
            enchant(meta, "sharpness", 5);
            enchant(meta, "efficiency", 5);
            enchant(meta, "unbreaking", 3);
            enchant(meta, "mending", 1);
            return;
        }
        enchant(meta, "sharpness", 5);
        enchant(meta, "fire_aspect", 2);
        enchant(meta, "unbreaking", 3);
        enchant(meta, "mending", 1);
    }

    private void enchant(final ItemMeta meta, final String key, final int level) {
        final org.bukkit.enchantments.Enchantment enchantment =
                Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private boolean hasModelData(final ItemStack stack, final int expected) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) {
            return false;
        }
        try {
            return meta.getCustomModelData() == expected;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Component title(final CustomItemType type) {
        final TooltipPalette palette = tooltipPalette(type);
        return ItemUtils.gradient(type.displayName(), palette.from(), palette.to())
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    private TooltipPalette tooltipPalette(final CustomItemType type) {
        String configured = plugin.getConfig().getString(
                "custom-items." + type.key() + ".tooltip-theme", "GLOBAL");
        configured = configured == null ? "GLOBAL" : configured.toUpperCase(Locale.ROOT);
        final String theme = "GLOBAL".equals(configured) ? globalTooltipTheme() : configured;
        if ("ITEM".equals(theme)) {
            final int base = type.nameColor();
            return new TooltipPalette(scaleColor(base, 0.65), scaleColor(base, 1.25),
                    Key.key("minecraft", "default"));
        }
        return switch (theme) {
            case "EMBER" -> new TooltipPalette(0xFFE0A3, 0xE04646,
                    Key.key("ffacore", "ember"));
            case "FROST" -> new TooltipPalette(0xE6FFFF, 0x429BFF,
                    Key.key("ffacore", "frost"));
            case "EARTH" -> new TooltipPalette(0xFFE2A4, 0x8B4F2A,
                    Key.key("ffacore", "earth"));
            case "GOLD" -> new TooltipPalette(0xFFF3A1, 0xE58A00,
                    Key.key("ffacore", "gold"));
            case "VOID" -> new TooltipPalette(0xF9C1FF, 0x6E2CAD,
                    Key.key("ffacore", "void"));
            default -> new TooltipPalette(0xF0D1FF, 0x8B5CF6,
                    Key.key("ffacore", "purple"));
        };
    }

    private String globalTooltipTheme() {
        final String configured = plugin.getConfig().getString(
                "custom-items.tooltip-theme", "UNIFIED_PURPLE");
        if (configured == null) {
            return "UNIFIED_PURPLE";
        }
        return switch (configured.toUpperCase(Locale.ROOT)) {
            case "ITEM", "UNIFIED_PURPLE", "EMBER", "FROST", "EARTH", "GOLD", "VOID" ->
                    configured.toUpperCase(Locale.ROOT);
            default -> "UNIFIED_PURPLE";
        };
    }

    private int scaleColor(final int color, final double scale) {
        final int red = Math.min(255, (int) Math.round(((color >> 16) & 0xFF) * scale));
        final int green = Math.min(255, (int) Math.round(((color >> 8) & 0xFF) * scale));
        final int blue = Math.min(255, (int) Math.round((color & 0xFF) * scale));
        return (red << 16) | (green << 8) | blue;
    }

    private String bind() {
        return "Swap-hands key";
    }

    private record TooltipPalette(int from, int to, Key style) {
    }

    private Component gray(final String text) {
        return Component.text(text, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component colored(final String text, final int rgb) {
        return Component.text(text).color(TextColor.color(rgb))
                .decoration(TextDecoration.ITALIC, false);
    }

    private String seconds(final CustomItemType type) {
        final double seconds = cooldown(type);
        return String.format(Locale.ROOT, "%.0fs", seconds);
    }

    private String percent(final CustomItemType type, final String key, final double fallback) {
        return String.format(Locale.ROOT, "%.0f%%", value(type, key, fallback) * 100.0);
    }

    private String whole(final double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private void sound(final World world, final org.bukkit.Location location,
                       final String key, final float volume, final float pitch) {
        world.playSound(location, key, SoundCategory.PLAYERS, volume, pitch);
    }

}
