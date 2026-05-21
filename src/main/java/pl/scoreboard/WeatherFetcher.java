package pl.scoreboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZoneId;

public class WeatherFetcher {
    // klucz z OpenWeatherMap
    private static final String API_KEY = "8ad0ce062546bd41ce8b8f2e755e2f39";

    // Pomocnicza klasa do przechowywania zarówno pogody, jak i strefy czasowej
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

    public static FetchResult getInfoForIp(String ip) {
        // Zabezpieczenie dla serwerów lokalnych i sieci LAN
        if (ip.equals("127.0.0.1") || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return new FetchResult(getLatestWeatherByCity("Warszawa"), ZoneId.of("Europe/Warsaw"));
        }

        try {
            // Pobieramy miasto i strefę czasową na podstawie adresu IP
            URL ipUrl = new URL("http://ip-api.com/json/" + ip);
            HttpURLConnection ipConn = (HttpURLConnection) ipUrl.openConnection();
            ipConn.setRequestMethod("GET");

            InputStreamReader ipReader = new InputStreamReader(ipConn.getInputStream());
            JsonObject ipJson = JsonParser.parseReader(ipReader).getAsJsonObject();
            ipReader.close();

            // Jeśli zapytanie się nie powiodło
            if (!ipJson.get("status").getAsString().equals("success")) {
                return new FetchResult("Błąd lokalizacji IP", ZoneId.systemDefault());
            }

            String city = ipJson.get("city").getAsString();

            // Pobieramy strefę czasową (jeśli API ją zwróci, inaczej bierzemy systemową)
            String timezoneStr = ipJson.has("timezone") ? ipJson.get("timezone").getAsString()
                    : ZoneId.systemDefault().getId();
            ZoneId zoneId = ZoneId.of(timezoneStr);

            // Pobieramy pogodę dla odnalezionego miasta
            String weather = getLatestWeatherByCity(city);

            return new FetchResult(weather, zoneId);

        } catch (Exception e) {
            return new FetchResult("Błąd usług pogody", ZoneId.systemDefault());
        }
    }

    private static String getLatestWeatherByCity(String city) {
        try {
            // Zamieniamy spacje w nazwie miasta na format URL
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

            return temp + "°C, " + desc;
        } catch (Exception e) {
            return "Błąd API pogody";
        }
    }
}