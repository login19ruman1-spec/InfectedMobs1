package dev.infectedmobs.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectedUtil {
    private InfectedUtil() {}

    public static NamespacedKey key(JavaPlugin plugin, String name) {
        return new NamespacedKey(plugin, name);
    }

    public static void mark(LivingEntity entity, JavaPlugin plugin, String type, String source, String mobId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(key(plugin, "infected_type"), PersistentDataType.STRING, type);
        pdc.set(key(plugin, "infection_source"), PersistentDataType.STRING, source);
        pdc.set(key(plugin, "infected_mob"), PersistentDataType.STRING, mobId);
    }

    /** Id of the config entry (mobs.<id>) this entity was created from; null for very old entities. */
    public static String mobId(LivingEntity entity, JavaPlugin plugin) {
        return entity.getPersistentDataContainer().get(
                key(plugin, "infected_mob"), PersistentDataType.STRING);
    }

    public static String type(LivingEntity entity, JavaPlugin plugin) {
        return entity.getPersistentDataContainer().get(
                key(plugin, "infected_type"), PersistentDataType.STRING);
    }

    public static boolean is(LivingEntity entity, JavaPlugin plugin, String type) {
        return type.equals(type(entity, plugin));
    }

    public static boolean isInfected(LivingEntity entity, JavaPlugin plugin) {
        return type(entity, plugin) != null;
    }

    public static boolean isDay(long time) {
        long t = time % 24000L;
        return t < 12000L;
    }

    public static boolean isNight(long time) {
        long t = time % 24000L;
        return t >= 13000L && t <= 23000L;
    }

    public static boolean nearMaterial(Location center, int radius, Material... materials) {
        World world = center.getWorld();
        if (world == null) return false;
        int r = radius;
        int minX = center.getBlockX() - r, maxX = center.getBlockX() + r;
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - r);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + r);
        int minZ = center.getBlockZ() - r, maxZ = center.getBlockZ() + r;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material m = world.getBlockAt(x, y, z).getType();
                    for (Material wanted : materials) if (m == wanted) return true;
                }
            }
        }
        return false;
    }

    public static Block randomNearbyBlock(Location center, int radius, Material... materials) {
        World world = center.getWorld();
        if (world == null) return null;
        java.util.List<Block> found = new java.util.ArrayList<>();
        int r = radius;
        for (int x = center.getBlockX() - r; x <= center.getBlockX() + r; x++) {
            for (int y = Math.max(world.getMinHeight(), center.getBlockY() - r);
                 y <= Math.min(world.getMaxHeight() - 1, center.getBlockY() + r); y++) {
                for (int z = center.getBlockZ() - r; z <= center.getBlockZ() + r; z++) {
                    Material m = world.getBlockAt(x, y, z).getType();
                    for (Material wanted : materials) {
                        if (m == wanted) {
                            found.add(world.getBlockAt(x, y, z));
                            break;
                        }
                    }
                }
            }
        }
        return found.isEmpty() ? null : found.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(found.size()));
    }
}
