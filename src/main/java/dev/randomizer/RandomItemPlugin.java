package dev.randomizer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RandomItemPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Randomizer randomizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        randomizer = new Randomizer(this);
        getServer().getPluginManager().registerEvents(randomizer, this);

        for (String name : List.of("start-randomizer", "stop-randomizer", "randomitem")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
    }

    @Override
    public void onDisable() {
        if (randomizer != null) {
            randomizer.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "start-randomizer" -> {
                if (randomizer.isRunning()) {
                    msg(sender, "The randomizer is already running.", NamedTextColor.RED);
                } else if (randomizer.start()) {
                    Bukkit.broadcast(Component.text("Randomizer started! A new item every "
                            + randomizer.getInterval() + " seconds. ("
                            + randomizer.getPoolSize() + " item chances)", NamedTextColor.GOLD));
                } else {
                    msg(sender, "Could not start: the item pool is empty. Check the server log.", NamedTextColor.RED);
                }
            }
            case "stop-randomizer" -> {
                if (!randomizer.isRunning()) {
                    msg(sender, "The randomizer is not running.", NamedTextColor.RED);
                } else {
                    randomizer.stop();
                    Bukkit.broadcast(Component.text("Randomizer stopped.", NamedTextColor.GOLD));
                }
            }
            case "randomitem" -> handleSettings(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, "Usage: /randomitem <interval <seconds>|reload|status>", NamedTextColor.YELLOW);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "interval" -> {
                if (args.length < 2) {
                    msg(sender, "Current interval: " + getConfig().getInt("interval-seconds", 45)
                            + "s. Usage: /randomitem interval <seconds>", NamedTextColor.YELLOW);
                    return;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    msg(sender, "That's not a number.", NamedTextColor.RED);
                    return;
                }
                if (seconds < 5) {
                    msg(sender, "Minimum interval is 5 seconds.", NamedTextColor.RED);
                    return;
                }
                getConfig().set("interval-seconds", seconds);
                saveConfig();
                randomizer.setInterval(seconds);
                msg(sender, "Interval set to " + seconds + " seconds.", NamedTextColor.GREEN);
            }
            case "reload" -> {
                randomizer.reload();
                msg(sender, "Config reloaded.", NamedTextColor.GREEN);
            }
            case "status" -> msg(sender, "Running: " + randomizer.isRunning()
                    + " | interval: " + getConfig().getInt("interval-seconds", 45) + "s"
                    + " | rounds: " + randomizer.getRounds()
                    + " | item chances: " + randomizer.getPoolSize(), NamedTextColor.YELLOW);
            default -> msg(sender, "Usage: /randomitem <interval <seconds>|reload|status>", NamedTextColor.YELLOW);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("randomitem") && args.length == 1) {
            for (String s : List.of("interval", "reload", "status")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static void msg(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }
}
