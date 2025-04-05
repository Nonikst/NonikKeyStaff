package org.exampleomc.nonikKeyStaff;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class NonikKeyStaff extends JavaPlugin implements CommandExecutor, Listener {

    private FileConfiguration config;
    private Connection connection;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = getConfig();

        // Initialize the database
        initializeDatabase();

        // Print ASCII art and message
        printAsciiArt();

        // Register command and event listener
        this.getCommand("key").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Close database connection on plugin disable
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/players.db");
            PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS players (name TEXT PRIMARY KEY, role TEXT)");
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("key")) {
            if (args.length != 1) {
                return true; // Don't show message, just return
            }

            String key = args[0];
            if (sender instanceof Player) {
                Player player = (Player) sender;
                String playerName = player.getName();

                // Check keys in config
                if (config.contains("keys")) {
                    boolean keyFound = false; // Flag to track if key was found
                    for (String role : config.getConfigurationSection("keys").getKeys(false)) {
                        String configKey = config.getString("keys." + role + ".key");
                        String permission = config.getString("keys." + role + ".permission");

                        if (configKey.equals(key)) {
                            keyFound = true; // Set flag if key is found

                            // Permission check
                            if (!player.hasPermission(permission)) {
                                player.sendMessage("You don't have permission.");
                                return true;
                            }

                            // Execute give commands
                            List<String> commandsGive = config.getStringList("keys." + role + ".commands_give");
                            for (String command : commandsGive) {
                                command = command.replace("{nick}", playerName);
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                            }

                            // Save player role to database
                            savePlayerRole(playerName, role);
                            player.sendMessage("Permissions granted.");
                            return true;
                        }
                    }

                    // If key was not found, send message
                    if (!keyFound) {
                        player.sendMessage("Key not found.");
                    }
                } else {
                    player.sendMessage("Key not found.");
                }
            } else {
                sender.sendMessage("This command can only be executed by a player.");
            }
        }
        return false;
    }

    // Save player role to the database
    private void savePlayerRole(String playerName, String role) {
        try {
            PreparedStatement statement = connection.prepareStatement("REPLACE INTO players (name, role) VALUES (?, ?)");
            statement.setString(1, playerName);
            statement.setString(2, role);
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Event listener for player quitting
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Remove permissions for the player based on database
        String role = getPlayerRole(playerName);
        if (role != null) {
            List<String> commandsLeave = config.getStringList("keys." + role + ".commands_leave");
            for (String command : commandsLeave) {
                command = command.replace("{nick}", playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
            // Remove player from the database
            removePlayerRole(playerName);
        }
    }

    // Get player's role from the database
    private String getPlayerRole(String playerName) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT role FROM players WHERE name = ?");
            statement.setString(1, playerName);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("role");
            }
            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Remove player's role from the database
    private void removePlayerRole(String playerName) {
        try {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM players WHERE name = ?");
            statement.setString(1, playerName);
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Method to print ASCII art and keys
    private void printAsciiArt() {
        // Read ASCII art from file
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResource("ascii.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Bukkit.getLogger().info(line);
            }

            // Print loaded keys message
            Bukkit.getLogger().info("Loaded keys:");

            // Print keys from configuration
            if (config.contains("keys")) {
                for (String role : config.getConfigurationSection("keys").getKeys(false)) {
                    String key = config.getString("keys." + role + ".key");
                    Bukkit.getLogger().info(role + ": " + key);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
