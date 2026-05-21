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

    // Aktualnie wyświetlane Scoreboardy
    private final Map<UUID, CustomScoreboard> playerBoards = new HashMap<>();

    // Dane graczy z API (Pogoda i Strefa Czasowa IP)
    private final Map<UUID, WeatherFetcher.FetchResult> playerInfos = new HashMap<>();

    // Zbiór graczy, którzy wpisali komendę wyłączającą scoreboard
    private final Set<UUID> hiddenScoreboards = new HashSet<>();

    private String cachedNews = "Oczekuję na newsy... *** ";
    private int scrollIndex = 0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // KROK 1: Pobieranie asynchroniczne danych z internetu
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            cachedNews = OnetRssParser.getLatestNews() + " *** ";

            // Odświeżamy dane dla każdego gracza aktualnie będącego na serwerze
            for (Player player : Bukkit.getOnlinePlayers()) {
                String ip = player.getAddress().getAddress().getHostAddress();
                WeatherFetcher.FetchResult info = WeatherFetcher.getInfoForIp(ip);
                playerInfos.put(player.getUniqueId(), info);
            }
        }, 0L, 12000L);

        // KROK 2: Odświeżanie Scoreboardu i animacji tekstu
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            String displayNews;
            int showChars = 28;

            // Animacja wiadomości
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

                // Jeśli gracz ukrył scoreboard komendą, pomijamy go w tej pętli
                if (hiddenScoreboards.contains(uuid)) {
                    continue;
                }

                CustomScoreboard board = playerBoards.get(uuid);
                WeatherFetcher.FetchResult info = playerInfos.get(uuid);

                if (board != null) {
                    // Ustalamy strefę czasową (jeśli jej brak, bierzemy z serwera)
                    ZoneId playerZone = (info != null && info.getTimezone() != null) ? info.getTimezone()
                            : ZoneId.systemDefault();
                    LocalDateTime playerTime = LocalDateTime.now(playerZone);

                    // Formatowanie z użyciem spersonalizowanego czasu gracza
                    String currentDate = playerTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    String currentTime = playerTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String weather = (info != null && info.getWeather() != null) ? info.getWeather() : "Ładowanie...";

                    board.updateDate(currentDate);
                    board.updateTime(currentTime);
                    board.updateWeather(weather);
                    board.updateNews(displayNews);
                }
            }
        }, 0L, 5L);
    }

    // OBSŁUGA KOMENDY WŁĄCZANIA / WYŁĄCZANIA
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tablica")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Komendę można wykonać tylko w grze!");
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (hiddenScoreboards.contains(uuid)) {
                hiddenScoreboards.remove(uuid);
                playerBoards.put(uuid, new CustomScoreboard(player));
                player.sendMessage("§aTablica informacyjna została włączona.");
            } else {
                hiddenScoreboards.add(uuid);
                playerBoards.remove(uuid);
                // Czyszczenie ekranu - przywracamy domyślny pusty panel silnika gry
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.sendMessage("§cTablica informacyjna została wyłączona.");
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Wyświetlamy panel, chyba że gracz ma go ukrytego
        if (!hiddenScoreboards.contains(uuid)) {
            playerBoards.put(uuid, new CustomScoreboard(player));
        }

        // Puste dane początkowe
        playerInfos.put(uuid, new WeatherFetcher.FetchResult("Sprawdzam chmury...", ZoneId.systemDefault()));

        // Asynchroniczne pobieranie po dołączeniu
        String ip = player.getAddress().getAddress().getHostAddress();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            WeatherFetcher.FetchResult info = WeatherFetcher.getInfoForIp(ip);
            playerInfos.put(uuid, info);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerBoards.remove(uuid);
        playerInfos.remove(uuid);
        // Zostawiamy gracza w liście 'hiddenScoreboards',
    }
}