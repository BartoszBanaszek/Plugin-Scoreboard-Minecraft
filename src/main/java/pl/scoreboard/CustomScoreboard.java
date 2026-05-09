package pl.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class CustomScoreboard {
    private final Scoreboard scoreboard;
    private final Objective objective;

    public CustomScoreboard(Player player) {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("realworld", "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + "Świat Realny");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        createLine("date", ChatColor.YELLOW + "Data: ", ChatColor.WHITE + "--.--.----", 5);
        createLine("time", ChatColor.YELLOW + "Godzina: ", ChatColor.WHITE + "--:--", 4);
        createLine("weather", ChatColor.AQUA + "Pogoda: ", ChatColor.WHITE + "Ładowanie...", 3);
        createLine("news", ChatColor.RED + "Gorący temat: ", ChatColor.WHITE + "...", 2);

        player.setScoreboard(this.scoreboard);
    }

    private void createLine(String teamName, String title, String initialValue, int scorePosition) {
        Team team = scoreboard.registerNewTeam(teamName);
        team.addEntry(title);
        team.setSuffix(initialValue);
        objective.getScore(title).setScore(scorePosition);
    }

    public void updateDate(String date) {
        scoreboard.getTeam("date").setSuffix(ChatColor.WHITE + date);
    }

    public void updateTime(String time) {
        scoreboard.getTeam("time").setSuffix(ChatColor.WHITE + time);
    }

    public void updateWeather(String weather) {
        scoreboard.getTeam("weather").setSuffix(ChatColor.WHITE + weather);
    }

    public void updateNews(String news) {
        scoreboard.getTeam("news").setSuffix(ChatColor.WHITE + news);
    }
}