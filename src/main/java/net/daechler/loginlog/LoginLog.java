package net.daechler.loginlog;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class LoginLog extends Plugin {

    private Connection connection;
    private String host, database, username, password;
    private int port;

    @Override
    public void onEnable() {
        // Load configuration from file
        loadConfig();

        // Enable plugin message
        getLogger().info("\u00A7aLoginLog has been activated!");

        // Connect to the database
        connectToDatabase();

        // Create the necessary table if not exist
        createTable();

        // Register event listener
        getProxy().getPluginManager().registerListener(this, new LoginLogListener());
    }

    @Override
    public void onDisable() {
        // Disable plugin message
        getLogger().info("\u00A7cLoginLog has been disabled!");

        // Disconnect from the database
        disconnectFromDatabase();
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists())
                getDataFolder().mkdir();

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                // Copy config.yml from resources
                try (InputStream inputStream = getResourceAsStream("config.yml");
                     OutputStream outputStream = new FileOutputStream(configFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                }
            }

            Configuration configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);

            host = configuration.getString("database.host");
            port = configuration.getInt("database.port");
            database = configuration.getString("database.database");
            username = configuration.getString("database.username");
            password = configuration.getString("database.password");
        } catch (IOException e) {
            getLogger().severe("Failed to load the configuration file: " + e.getMessage());
        }
    }

    private String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    private void connectToDatabase() {
        while (true) {
            try {
                connection = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
                break; // Connected successfully, exit the loop
            } catch (SQLException e) {
                getLogger().severe("Failed to connect to the database: " + e.getMessage());
            }

            try {
                // Wait for 10 seconds before retrying
                Thread.sleep(Duration.ofSeconds(10).toMillis());
            } catch (InterruptedException e) {
                getLogger().severe("Interrupted while trying to reconnect to the database: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private void disconnectFromDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().severe("Failed to disconnect from the database: " + e.getMessage());
        }
    }

    private void createTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS player_log (id INT AUTO_INCREMENT PRIMARY KEY, datetime VARCHAR(30), username VARCHAR(30), uuid VARCHAR(36), ip VARCHAR(50))")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().severe("Failed to create table: " + e.getMessage());
        }
    }

    private void logPlayerLogin(String dateTime, String playerName, String playerUUID, String playerIP) {
        String query = "INSERT INTO player_log (datetime, username, uuid, ip) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, dateTime);
            statement.setString(2, playerName);
            statement.setString(3, playerUUID);
            statement.setString(4, playerIP);
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().severe("Failed to log player login: " + e.getMessage());
        }
    }

    public class LoginLogListener implements Listener {

        @EventHandler
        public void onPlayerLogin(PostLoginEvent event) {
            String playerName = event.getPlayer().getName();
            UUID playerUUID = event.getPlayer().getUniqueId();
            String playerIP = event.getPlayer().getSocketAddress().toString();
            String dateTime = getCurrentDateTime();

            // Log player details into the database
            logPlayerLogin(dateTime, playerName, playerUUID.toString(), playerIP);
        }
    }
}
