package pl.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RealWorldScoreboard extends JavaPlugin implements Listener {

    private final Map<UUID, CustomScoreboard> playerBoards = new HashMap<>();
    private final Map<UUID, WeatherFetcher.FetchResult> playerInfos = new HashMap<>();
    private final Set<UUID> hiddenScoreboards = new HashSet<>();

    private DatabaseManager dbManager;
    private String cachedNews = "Oczekuję na newsy... *** ";
    private int scrollIndex = 0;

    @Override
    public void onEnable() {
        // Generowanie domyślnego pliku config.yml
        saveDefaultConfig();

        // Inicjalizacja połączenia z bazą danych na podstawie config.yml
        dbManager = new DatabaseManager(
                getConfig().getString("database.host"),
                getConfig().getInt("database.port"),
                getConfig().getString("database.database"),
                getConfig().getString("database.username"),
                getConfig().getString("database.password"));
        dbManager.connect();

        getServer().getPluginManager().registerEvents(this, this);

        // Asynchroniczne pobieranie danych i zapis do DB
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            String rawNews = OnetRssParser.getLatestNews();
            cachedNews = rawNews + " *** ";

            // Zapisujemy zdobytego newsa do historii bazy danych
            if (!rawNews.startsWith("Błąd") && !rawNews.startsWith("Brak")) {
                dbManager.logNews(rawNews);
            }

            // Odświeżamy dane graczy online i aktualizujemy bazę
            for (Player player : Bukkit.getOnlinePlayers()) {
                String ip = player.getAddress().getAddress().getHostAddress();
                WeatherFetcher.FetchResult info = WeatherFetcher.getInfoForIp(ip);
                playerInfos.put(player.getUniqueId(), info);

                // Aktualizujemy dane gracza w bazie (IP i Najnowszą pogodę)
                dbManager.savePlayerData(player.getUniqueId(), player.getName(), ip, info.getWeather());
            }
        }, 0L, 12000L); // Co 10 minut

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            String displayNews;
            int showChars = 28;

            if (cachedNews.length() > showChars) {
                if (scrollIndex >= cachedNews.length()) {
                    scrollIndex = 0;
                }
                String doubledNews = cachedNews + cachedNews;
                displayNews = doubledNews.substring(scrollIndex, scrollIndex + showChars);
                scrollIndex++;
            } else {
                displayNews = cachedNews;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (hiddenScoreboards.contains(uuid))
                    continue;

                CustomScoreboard board = playerBoards.get(uuid);
                WeatherFetcher.FetchResult info = playerInfos.get(uuid);

                if (board != null) {
                    ZoneId playerZone = (info != null && info.getTimezone() != null) ? info.getTimezone()
                            : ZoneId.systemDefault();
                    LocalDateTime playerTime = LocalDateTime.now(playerZone);

                    board.updateDate(playerTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                    board.updateTime(playerTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    board.updateWeather(
                            (info != null && info.getWeather() != null) ? info.getWeather() : "Ładowanie...");
                    board.updateNews(displayNews);
                }
            }
        }, 0L, 5L);
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            dbManager.disconnect();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tablica")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Komendę można wykonać tylko w grze!");
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            boolean isHiddenNow = hiddenScoreboards.contains(uuid);

            if (isHiddenNow) {
                hiddenScoreboards.remove(uuid);
                playerBoards.put(uuid, new CustomScoreboard(player));
                player.sendMessage("§aTablica informacyjna została włączona.");
            } else {
                hiddenScoreboards.add(uuid);
                playerBoards.remove(uuid);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.sendMessage("§cTablica informacyjna została wyłączona.");
            }

            // Asynchronicznie zapisujemy wybór gracza
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                dbManager.updatePlayerVisibility(uuid, !isHiddenNow);
            });
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress().getAddress().getHostAddress();

        playerInfos.put(uuid, new WeatherFetcher.FetchResult("Sprawdzam chmury...", ZoneId.systemDefault()));

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {

            // Czy gracz wyłączył sobie tablicę na poprzedniej sesji
            boolean isHidden = dbManager.isScoreboardHidden(uuid);

            // Pobieramy pogodę po IP
            WeatherFetcher.FetchResult info = WeatherFetcher.getInfoForIp(ip);

            // Dodajemy gracza do bazy lub uaktualniamy jego dane
            dbManager.savePlayerData(uuid, player.getName(), ip, info.getWeather());

            Bukkit.getScheduler().runTask(this, () -> {
                playerInfos.put(uuid, info);

                if (isHidden) {
                    hiddenScoreboards.add(uuid);
                } else {
                    hiddenScoreboards.remove(uuid);
                    playerBoards.put(uuid, new CustomScoreboard(player));
                }
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerBoards.remove(uuid);
        playerInfos.remove(uuid);
        hiddenScoreboards.remove(uuid); // Czyścimy RAM
    }
}