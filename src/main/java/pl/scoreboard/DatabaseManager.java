package pl.scoreboard;

import org.bukkit.Bukkit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseManager {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public DatabaseManager(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            Class.forName("org.mariadb.jdbc.Driver");

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
            connection = DriverManager.getConnection(url, username, password);
            createTables();
            Bukkit.getLogger().info("[RealWorldScoreboard] Pomyslnie polaczono z baza MariaDB!");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[RealWorldScoreboard] Blad laczenia z MariaDB! Sprawdz dane w config.yml.");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().severe("[RealWorldScoreboard] Nie znaleziono drivera MariaDB!");
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Bukkit.getLogger().info("[RealWorldScoreboard] Rozlaczono z baza MariaDB.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Tabela przechowująca dane graczy
            statement.execute("CREATE TABLE IF NOT EXISTS rw_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "player_name VARCHAR(16), " +
                    "last_ip VARCHAR(45), " +
                    "last_weather VARCHAR(255), " +
                    "scoreboard_hidden BOOLEAN DEFAULT FALSE)");

            // Tabela archiwizująca pobrane newsy
            statement.execute("CREATE TABLE IF NOT EXISTS rw_news_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "news_content TEXT, " +
                    "fetch_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    // Zapisuje logowania i zaktualizowaną pogodę gracza
    public void savePlayerData(UUID uuid, String playerName, String ip, String weather) {
        if (connection == null)
            return;
        String sql = "INSERT INTO rw_players (uuid, player_name, last_ip, last_weather) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE player_name = ?, last_ip = ?, last_weather = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, ip);
            ps.setString(4, weather);
            ps.setString(5, playerName);
            ps.setString(6, ip);
            ps.setString(7, weather);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Aktualizuje czy gracz wyłączył panel komendą
    public void updatePlayerVisibility(UUID uuid, boolean hidden) {
        if (connection == null)
            return;
        String sql = "UPDATE rw_players SET scoreboard_hidden = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, hidden);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Sprawdza czy gracz miał wcześniej wyłączony panel
    public boolean isScoreboardHidden(UUID uuid) {
        if (connection == null)
            return false;
        String sql = "SELECT scoreboard_hidden FROM rw_players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("scoreboard_hidden");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Archiwizuje nagłówek wiadomości
    public void logNews(String newsContent) {
        if (connection == null)
            return;
        String sql = "INSERT INTO rw_news_log (news_content) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newsContent);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}