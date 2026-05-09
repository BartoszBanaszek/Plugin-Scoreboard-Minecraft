package pl.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RealWorldScoreboard extends JavaPlugin implements Listener {

    private final Map<UUID, CustomScoreboard> playerBoards = new HashMap<>();
    
    // Mapa przechowująca pogodę oddzielnie dla każdego gracza
    private final Map<UUID, String> playerWeathers = new HashMap<>();

    private String cachedNews = "Oczekuję na newsy... *** ";
    private int scrollIndex = 0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Pobieranie z internetu
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            cachedNews = OnetRssParser.getLatestNews() + " *** ";
            
            // Odświeżamy pogodę dla każdego gracza aktualnie będącego na serwerze
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Pobieramy IP gracza z silnika gry
                String ip = player.getAddress().getAddress().getHostAddress();
                String weather = WeatherFetcher.getWeatherForIp(ip);
                playerWeathers.put(player.getUniqueId(), weather);
            }
        }, 0L, 12000L);

        // Odświeżanie Scoreboardu 
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

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

            // Wysyłanie do graczy
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                CustomScoreboard board = playerBoards.get(uuid);
                
                if (board != null) {
                    board.updateDate(currentDate);
                    board.updateTime(currentTime);
                    // Pobieramy z mapy pogodę przypisaną tylko do tego gracza
                    board.updateWeather(playerWeathers.getOrDefault(uuid, "Ładowanie..."));
                    board.updateNews(displayNews);
                }
            }
        }, 0L, 5L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        playerBoards.put(uuid, new CustomScoreboard(player));
        playerWeathers.put(uuid, "Sprawdzam chmury...");

        // NOWOŚĆ: Gdy gracz wejdzie, natychmiast (asynchronicznie) pobieramy jego pogodę
        String ip = player.getAddress().getAddress().getHostAddress();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            String weather = WeatherFetcher.getWeatherForIp(ip);
            playerWeathers.put(uuid, weather);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerBoards.remove(uuid);
        playerWeathers.remove(uuid); // Czyścimy pogodę, by nie zapychać RAMu
    }
}