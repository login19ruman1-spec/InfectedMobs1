package dev.infectedmobs.command;

import dev.infectedmobs.InfectedMobsPlugin;
import dev.infectedmobs.mob.MobDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class InfectedMobsCommand implements CommandExecutor, TabCompleter {
    private final InfectedMobsPlugin plugin;

    public InfectedMobsCommand(InfectedMobsPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("infectedmobs.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded: " + plugin.registry().all().size()
                        + " mob type(s). Models are being re-attached.");
            }
            case "list" -> {
                sender.sendMessage(ChatColor.AQUA + "Infected mob types:");
                for (MobDefinition d : plugin.registry().all()) {
                    sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + d.id() + ChatColor.GRAY
                            + "  [" + d.infection() + " " + d.entity() + ", model: " + d.model() + "]");
                }
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only players can spawn mobs."); return true; }
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /infectedmobs spawn <id>  (see /infectedmobs list)"); return true; }
                spawn(player, args[1]);
            }
            case "info" -> info(sender);
            case "anim" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this."); return true; }
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /infectedmobs anim <animation name> [loop]"); return true; }
                anim(player, args[1], args.length >= 3 && args[2].equalsIgnoreCase("loop"));
            }
            default -> {
                sender.sendMessage(ChatColor.AQUA + "/infectedmobs list" + ChatColor.GRAY + " - all configured infected mobs");
                sender.sendMessage(ChatColor.AQUA + "/infectedmobs spawn <id>" + ChatColor.GRAY + " - spawn one in front of you");
                sender.sendMessage(ChatColor.AQUA + "/infectedmobs info" + ChatColor.GRAY + " - diagnostics + nearest mob's animations");
                sender.sendMessage(ChatColor.AQUA + "/infectedmobs anim <name> [loop]" + ChatColor.GRAY + " - play any model animation on the nearest infected mob");
                sender.sendMessage(ChatColor.AQUA + "/infectedmobs reload" + ChatColor.GRAY + " - reload config.yml (new mobs!) and re-attach models");
            }
        }
        return true;
    }

    private void spawn(Player player, String id) {
        MobDefinition def = plugin.registry().get(id);
        if (def == null) {
            player.sendMessage(ChatColor.RED + "Unknown mob '" + id + "'. Use /infectedmobs list.");
            return;
        }
        Location loc = player.getLocation().clone();
        Vector dir = loc.getDirection().setY(0);
        if (dir.lengthSquared() > 0.0001) loc.add(dir.normalize().multiply(2.0));

        Entity raw = player.getWorld().spawnEntity(loc, def.entity());
        if (!(raw instanceof LivingEntity mob)) {
            raw.remove();
            player.sendMessage(ChatColor.RED + "" + def.entity() + " is not a living entity.");
            return;
        }
        if (def.infection().equals("sculk")) plugin.sculk().convert(mob, def, "command");
        else plugin.moss().convert(mob, def, "command", false);
        player.sendMessage(ChatColor.GREEN + "Spawned " + def.id() + ".");
    }

    private void info(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "InfectedMobs OK. Mob types: " + plugin.registry().all().size()
                + ", tracked mobs: " + plugin.tracker().size() + ", FMM models attached: " + plugin.models().count());
        if (!(sender instanceof Player player)) return;
        LivingEntity near = nearestInfected(player);
        if (near == null) {
            sender.sendMessage(ChatColor.GRAY + "No infected mob within 12 blocks (stand next to one to see its animation mapping).");
            return;
        }
        MobDefinition def = plugin.registry().of(near);
        if (def == null) { sender.sendMessage(ChatColor.RED + "Nearest infected mob has no definition in config.yml."); return; }
        sender.sendMessage(ChatColor.AQUA + "Nearest: " + def.id() + " (model '" + def.model() + "', attached: "
                + plugin.models().isAttached(near) + ")");
        for (String line : plugin.animations().describe(near, def)) sender.sendMessage(ChatColor.GRAY + "  " + line);
    }

    private void anim(Player player, String name, boolean loop) {
        LivingEntity near = nearestInfected(player);
        if (near == null) { player.sendMessage(ChatColor.RED + "No infected mob within 12 blocks."); return; }
        if (plugin.animations().debugPlay(near, name, loop)) {
            player.sendMessage(ChatColor.GREEN + "Playing '" + name + "'" + (loop ? " (loop, ~10 s)" : "") + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Animation '" + name + "' not found in the model (names are case-sensitive), or the model is not attached.");
        }
    }

    private LivingEntity nearestInfected(Player player) {
        LivingEntity best = null;
        double bestDist = 12.0 * 12.0;
        for (LivingEntity e : plugin.tracker().snapshot()) {
            if (!e.isValid() || e.getWorld() != player.getWorld()) continue;
            double d = e.getLocation().distanceSquared(player.getLocation());
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("list", "spawn", "info", "anim", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return filter(plugin.registry().all().stream().map(MobDefinition::id).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("anim")) {
            return filter(List.of("spawn", "idle", "walk", "attack", "attack_ranged", "hurt", "death"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("anim")) return filter(List.of("loop"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        return out;
    }
}
