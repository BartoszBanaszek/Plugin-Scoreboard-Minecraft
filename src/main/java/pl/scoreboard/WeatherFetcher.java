package pl.scoreboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WeatherFetcher {
    // Klucz z OpenWeatherMap
    private static final String API_KEY = "8ad0ce062546bd41ce8b8f2e755e2f39";

    // 1. CACHE IP -> Miasto i Strefa Czasowa (Ochrona API geolokalizacji)
    private static final Map<String, LocationInfo> ipLocationCache = new ConcurrentHashMap<>();

    // 2. CACHE Miasto -> Pogoda (Ochrona API OpenWeatherMap)
    private static final Map<String, String> cityWeatherCache = new ConcurrentHashMap<>();

    // Wewnętrzna klasa do zapamiętywania lokalizacji z IP
    private static class LocationInfo {
        final String city;
        final ZoneId timezone;

        LocationInfo(String city, ZoneId timezone) {
            this.city = city;
            this.timezone = timezone;
        }
    }

    public static class FetchResult {
        private final String weather;
        private final ZoneId timezone;

        public FetchResult(String weather, ZoneId timezone) {
            this.weather = weather;
            this.timezone = timezone;
        }

        public String getWeather() {
            return weather;
        }

        public ZoneId getTimezone() {
            return timezone;
        }
    }

    // Metoda wywoływana co 10 minut z głównej klasy, by odświeżyć pogodę
    public static void clearWeatherCache() {
        cityWeatherCache.clear();
    }

    public static FetchResult getInfoForIp(String ip) {
        // Zabezpieczenie dla serwerów lokalnych i sieci LAN
        if (ip.equals("127.0.0.1") || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return new FetchResult(getLatestWeatherByCityCached("Warszawa"), ZoneId.of("Europe/Warsaw"));
        }

        LocationInfo locInfo;

        // KROK 1: Sprawdzamy, czy znamy już to IP (Cache)
        if (ipLocationCache.containsKey(ip)) {
            locInfo = ipLocationCache.get(ip);
        } else {
            try {
                // Jeśli nie znamy IP, pytamy ip-api.com
                URL ipUrl = new URL("http://ip-api.com/json/" + ip);
                HttpURLConnection ipConn = (HttpURLConnection) ipUrl.openConnection();
                ipConn.setRequestMethod("GET");

                InputStreamReader ipReader = new InputStreamReader(ipConn.getInputStream());
                JsonObject ipJson = JsonParser.parseReader(ipReader).getAsJsonObject();
                ipReader.close();

                if (!ipJson.get("status").getAsString().equals("success")) {
                    return new FetchResult("Błąd lokalizacji IP", ZoneId.systemDefault());
                }

                String city = ipJson.get("city").getAsString();
                String timezoneStr = ipJson.has("timezone") ? ipJson.get("timezone").getAsString()
                        : ZoneId.systemDefault().getId();
                ZoneId zoneId = ZoneId.of(timezoneStr);

                locInfo = new LocationInfo(city, zoneId);

                // Zapisujemy nową lokalizację do pamięci RAM, żeby nie pytać o nią więcej!
                ipLocationCache.put(ip, locInfo);

            } catch (Exception e) {
                return new FetchResult("Błąd usług pogody", ZoneId.systemDefault());
            }
        }

        // KROK 2: Pobieramy pogodę dla danego miasta (wykorzystując Cache Miast)
        String weather = getLatestWeatherByCityCached(locInfo.city);

        return new FetchResult(weather, locInfo.timezone);
    }

    private static String getLatestWeatherByCityCached(String city) {
        // Sprawdzamy, czy pogoda dla tego miasta została już pobrana w ciągu ostatnich
        // 10 minut
        if (cityWeatherCache.containsKey(city)) {
            return cityWeatherCache.get(city);
        }

        try {
            // Jeśli nie ma pogody w Cache, odpytujemy OpenWeatherMap
            String safeCity = city.replace(" ", "%20");
            URL url = new URL("https://api.openweathermap.org/data/2.5/weather?q=" + safeCity
                    + "&units=metric&lang=pl&appid=" + API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();

            int temp = Math.round(json.getAsJsonObject("main").get("temp").getAsFloat());
            String desc = json.getAsJsonArray("weather").get(0).getAsJsonObject().get("description").getAsString();

            String weatherResult = temp + "°C, " + desc;

            // Zapisujemy świeżą pogodę do pamięci RAM dla innych graczy z tego miasta
            cityWeatherCache.put(city, weatherResult);

            return weatherResult;
        } catch (Exception e) {
            return "Błąd API pogody";
        }
    }
}