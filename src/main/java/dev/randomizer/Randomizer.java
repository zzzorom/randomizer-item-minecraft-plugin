package dev.randomizer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Runs the countdown, shows the sidebar, and hands out items. */
public final class Randomizer implements Listener {

    private final RandomItemPlugin plugin;
    private final ItemPool pool;

    private BukkitTask task;
    private boolean running;
    private int interval = 45;
    private int remaining;
    private int rounds;

    private Scoreboard board;
    private Team timerLine;
    private Team roundLine;

    public Randomizer(RandomItemPlugin plugin) {
        this.plugin = plugin;
        this.pool = new ItemPool(plugin.getLogger());
    }

    public boolean isRunning() {
        return running;
    }

    public int getInterval() {
        return interval;
    }

    public int getRounds() {
        return rounds;
    }

    public int getPoolSize() {
        return pool.size();
    }

    /** @return false if it was already running or the pool is empty. */
    public boolean start() {
        if (running) {
            return false;
        }
        pool.build(Bukkit.getWorlds().get(0), plugin.getConfig());
        if (pool.size() == 0) {
            return false;
        }
        interval = Math.max(5, plugin.getConfig().getInt("interval-seconds", 45));
        remaining = interval;
        rounds = 0;
        createBoard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
        updateBoard();
        running = true;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(main);
        }
        board = null;
    }

    public void setInterval(int seconds) {
        interval = Math.max(5, seconds);
        if (running) {
            remaining = Math.min(remaining, interval);
            updateBoard();
        }
    }

    /** Re-read the config (and rebuild the pool if running). */
    public void reload() {
        plugin.reloadConfig();
        if (running) {
            pool.build(Bukkit.getWorlds().get(0), plugin.getConfig());
            setInterval(plugin.getConfig().getInt("interval-seconds", 45));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (running && board != null) {
            event.getPlayer().setScoreboard(board);
        }
    }

    // ------------------------------------------------------------------ loop

    private void tick() {
        remaining--;
        if (remaining <= 0) {
            distribute();
            remaining = interval;
        }
        updateBoard();
    }

    private void distribute() {
        rounds++;
        boolean sounds = plugin.getConfig().getBoolean("sounds", true);
        boolean actionBar = plugin.getConfig().getBoolean("action-bar", true);

        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (p.getGameMode() != GameMode.SURVIVAL || p.isDead()) {
                continue;
            }
            RollResult result = pool.roll(ThreadLocalRandom.current());
            if (result == null) {
                continue;
            }

            Map<Integer, ItemStack> leftover = p.getInventory().addItem(result.stack());
            for (ItemStack extra : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), extra);
            }

            if (actionBar) {
                Component msg = Component.text("You got: ", NamedTextColor.GREEN)
                        .append(Component.translatable(result.stack().getType().translationKey(), NamedTextColor.YELLOW));
                if (result.detail() != null) {
                    msg = msg.append(Component.text(" (" + result.detail() + ")", NamedTextColor.GRAY));
                }
                p.sendActionBar(msg);
            }
            if (sounds) {
                Sound sound = result.rare() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }

    // ------------------------------------------------------------------ sidebar

    private void createBoard() {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("randomizer", Criteria.DUMMY,
                Component.text("RANDOMIZER", NamedTextColor.GOLD, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // invisible spacer entries keep the lines in a fixed order
        obj.getScore("\u00a70").setScore(5);

        String timerEntry = "\u00a71";
        timerLine = board.registerNewTeam("timer");
        timerLine.addEntry(timerEntry);
        obj.getScore(timerEntry).setScore(4);

        String roundEntry = "\u00a72";
        roundLine = board.registerNewTeam("round");
        roundLine.addEntry(roundEntry);
        obj.getScore(roundEntry).setScore(3);
    }

    private void updateBoard() {
        if (board == null) {
            return;
        }
        timerLine.prefix(Component.text("Next item in: ", NamedTextColor.WHITE)
                .append(Component.text(remaining + "s", NamedTextColor.YELLOW)));
        roundLine.prefix(Component.text("Round: ", NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(rounds), NamedTextColor.GREEN)));
    }
}
