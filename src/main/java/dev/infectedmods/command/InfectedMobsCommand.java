package dev.infectedmobs.command;

import dev.infectedmobs.InfectedMobsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public final class InfectedMobsCommand implements CommandExecutor, TabCompleter {
    private final InfectedMobsPlugin plugin;
    public InfectedMobsCommand(InfectedMobsPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (!player.hasPermission("infectedmobs.admin")) { player.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.sendMessage(ChatColor.AQUA + "/infectedmobs spawn <sculk_zombie|sculk_skeleton|sculk_spider|moss_zombie|moss_skeleton|moss_creeper>");
            player.sendMessage(ChatColor.AQUA + "/infectedmobs info");
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            player.sendMessage(ChatColor.GREEN + "InfectedMobs OK. FMM models attached: " + plugin.models().count());
            return true;
        }
        if (args[0].equalsIgnoreCase("spawn") && args.length >= 2) {
            spawn(player, args[1].toLowerCase());
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /infectedmobs spawn <model>");
        return true;
    }

    private void spawn(Player player, String id) {
        EntityType entityType;
        String infectedType;
        switch (id) {
            case "sculk_zombie" -> { entityType = EntityType.ZOMBIE; infectedType = "sculk"; }
            case "sculk_skeleton" -> { entityType = EntityType.SKELETON; infectedType = "sculk"; }
            case "sculk_spider" -> { entityType = EntityType.SPIDER; infectedType = "sculk"; }
            case "moss_zombie" -> { entityType = EntityType.ZOMBIE; infectedType = "moss"; }
            case "moss_skeleton" -> { entityType = EntityType.SKELETON; infectedType = "moss"; }
            case "moss_creeper" -> { entityType = EntityType.CREEPER; infectedType = "moss"; }
            default -> { player.sendMessage(ChatColor.RED + "Unknown model."); return; }
        }
        Location loc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(2));
        LivingEntity mob = (LivingEntity) player.getWorld().spawnEntity(loc, entityType);
        if (infectedType.equals("sculk")) plugin.sculk().convert(mob, "command");
        else plugin.moss().convert(mob, "command", false);
        player.sendMessage(ChatColor.GREEN + "Spawned " + id + ".");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("spawn", "info", "help");
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) return List.of(
                "sculk_zombie", "sculk_skeleton", "sculk_spider", "moss_zombie", "moss_skeleton", "moss_creeper");
        return List.of();
    }
}
