package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HelpManager {

    private final NusaCore plugin;
    private final Map<String, List<HelpEntry>> helpCategories = new HashMap<>();
    private final List<HelpEntry> allCommands = new ArrayList<>();
    private FileConfiguration commandsConfig;
    private Set<String> interceptedCommands;
    private boolean interceptedCommandsEnabled;
    
    private static final int COMMANDS_PER_PAGE = 8;
    
    public HelpManager(NusaCore plugin) {
        this.plugin = plugin;
        loadCommands();
    }
    
    /**
     * Load commands from commands.yml
     */
    public void loadCommands() {
        // Clear existing commands
        helpCategories.clear();
        allCommands.clear();
        
        // Create commands.yml if it doesn't exist
        File commandsFile = new File(plugin.getDataFolder(), "commands.yml");
        if (!commandsFile.exists()) {
            plugin.saveResource("commands.yml", false);
        }
        
        // Load commands.yml
        commandsConfig = YamlConfiguration.loadConfiguration(commandsFile);
        
        // Load categories
        ConfigurationSection categoriesSection = commandsConfig.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String categoryKey : categoriesSection.getKeys(false)) {
                ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryKey);
                if (categorySection != null) {
                    String displayName = ColorUtils.colorize(categorySection.getString("display-name", categoryKey));
                    
                    List<HelpEntry> commands = new ArrayList<>();
                    ConfigurationSection commandsSection = categorySection.getConfigurationSection("commands");
                    if (commandsSection != null) {
                        for (String commandKey : commandsSection.getKeys(false)) {
                            ConfigurationSection commandSection = commandsSection.getConfigurationSection(commandKey);
                            if (commandSection != null) {
                                String command = commandKey;
                                String description = ColorUtils.colorize(commandSection.getString("description", ""));
                                String usage = ColorUtils.colorize(commandSection.getString("usage", "/" + command));
                                String permission = commandSection.getString("permission", null);
                                
                                HelpEntry entry = new HelpEntry(command, description, usage, permission, categoryKey);
                                commands.add(entry);
                                allCommands.add(entry);
                            }
                        }
                    }
                    
                    helpCategories.put(categoryKey, commands);
                }
            }
        }
        
        // Load uncategorized commands
        ConfigurationSection uncategorizedSection = commandsConfig.getConfigurationSection("uncategorized");
        if (uncategorizedSection != null) {
            List<HelpEntry> commands = new ArrayList<>();
            for (String commandKey : uncategorizedSection.getKeys(false)) {
                ConfigurationSection commandSection = uncategorizedSection.getConfigurationSection(commandKey);
                if (commandSection != null) {
                    String command = commandKey;
                    String description = ColorUtils.colorize(commandSection.getString("description", ""));
                    String usage = ColorUtils.colorize(commandSection.getString("usage", "/" + command));
                    String permission = commandSection.getString("permission", null);
                    
                    HelpEntry entry = new HelpEntry(command, description, usage, permission, "uncategorized");
                    commands.add(entry);
                    allCommands.add(entry);
                }
            }
            
            helpCategories.put("uncategorized", commands);
        }
    }
    
    /**
     * Reload konfigurasi bantuan dari commands.yml
     */
    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "commands.yml");
        
        if (!configFile.exists()) {
            plugin.saveResource("commands.yml", false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Clear existing data
        helpCategories.clear();
        allCommands.clear();
        
        // Load categories
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String categoryId : categoriesSection.getKeys(false)) {
                // Implementasi loading category...
            }
        }
        
        // Load uncategorized commands
        ConfigurationSection uncategorizedSection = config.getConfigurationSection("uncategorized");
        if (uncategorizedSection != null) {
            for (String commandName : uncategorizedSection.getKeys(false)) {
                // Implementasi loading command...
            }
        }
        
        // Load intercepted commands
        interceptedCommands = new HashSet<>(plugin.getConfig().getStringList("help.intercepted-commands"));
        interceptedCommandsEnabled = plugin.getConfig().getBoolean("help.intercept-commands", true);
    }
    
    /**
     * Show help menu
     * @param sender CommandSender to show the help to
     * @param page Page number
     */
    public void showHelp(CommandSender sender, int page) {
        // Calculate max pages
        List<HelpEntry> visibleCommands = getVisibleCommands(sender);
        int maxPages = (int) Math.ceil((double) visibleCommands.size() / COMMANDS_PER_PAGE);
        
        // Validate page
        if (page < 1) {
            page = 1;
        } else if (page > maxPages) {
            page = maxPages;
        }
        
        // Calculate start and end indices
        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, visibleCommands.size());
        
        // Show header
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("<gradient:#00A3FF:#00FFD1>NusaTown</gradient> <dark_gray>-</dark_gray> <white>Bantuan Perintah</white> <gray>(Halaman " + page + "/" + maxPages + ")</gray>"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Show commands
        if (visibleCommands.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize("&7Tidak ada perintah yang dapat ditampilkan."));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                HelpEntry entry = visibleCommands.get(i);
                sender.sendMessage(ColorUtils.colorize("&f" + entry.usage + " &8- &7" + entry.description));
            }
        }
        
        // Show categories
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&7Kategori: " + String.join(", ", getCategories())));
        sender.sendMessage(ColorUtils.colorize("&7Ketik &f/help <kategori> &7untuk melihat perintah dalam kategori."));
        
        // Show navigation
        if (maxPages > 1) {
            sender.sendMessage(ColorUtils.colorize("&7Ketik &f/help " + (page + 1) + " &7untuk halaman berikutnya."));
        }
        
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    /**
     * Show help menu for a specific category
     * @param sender CommandSender to show the help to
     * @param category Category name
     * @param page Page number
     */
    public void showCategoryHelp(CommandSender sender, String category, int page) {
        // Check if category exists
        if (!helpCategories.containsKey(category)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cKategori tidak ditemukan. Gunakan &f/help &cuntuk melihat daftar kategori."));
            return;
        }
        
        // Get category commands
        List<HelpEntry> categoryCommands = helpCategories.get(category);
        
        // Filter commands based on permissions
        List<HelpEntry> visibleCommands = categoryCommands.stream()
            .filter(entry -> hasPermission(sender, entry.permission))
            .toList();
        
        // Calculate max pages
        int maxPages = (int) Math.ceil((double) visibleCommands.size() / COMMANDS_PER_PAGE);
        
        // Validate page
        if (page < 1) {
            page = 1;
        } else if (page > maxPages) {
            page = maxPages;
        }
        
        // Calculate start and end indices
        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, visibleCommands.size());
        
        // Get category display name
        String categoryDisplayName = commandsConfig.getString("categories." + category + ".display-name", category);
        
        // Show header
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("<gradient:#00A3FF:#00FFD1>NusaTown</gradient> <dark_gray>-</dark_gray> <white>Kategori: " + categoryDisplayName + "</white> <gray>(Halaman " + page + "/" + maxPages + ")</gray>"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Show commands
        if (visibleCommands.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize("&7Tidak ada perintah yang dapat ditampilkan dalam kategori ini."));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                HelpEntry entry = visibleCommands.get(i);
                sender.sendMessage(ColorUtils.colorize("&f" + entry.usage + " &8- &7" + entry.description));
            }
        }
        
        // Show navigation
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        if (maxPages > 1) {
            sender.sendMessage(ColorUtils.colorize("&7Ketik &f/help " + category + " " + (page + 1) + " &7untuk halaman berikutnya."));
        }
        sender.sendMessage(ColorUtils.colorize("&7Ketik &f/help &7untuk kembali ke menu utama."));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    /**
     * Check if sender has permission
     * @param sender CommandSender to check
     * @param permission Permission to check
     * @return true if sender has permission or permission is null
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isEmpty() || sender.isOp()) {
            return true;
        }
        return sender.hasPermission(permission);
    }
    
    /**
     * Get all categories
     * @return List of category names
     */
    public List<String> getCategories() {
        return new ArrayList<>(helpCategories.keySet());
    }
    
    /**
     * Get all visible commands for the sender
     * @param sender CommandSender to check permissions
     * @return List of visible HelpEntry objects
     */
    private List<HelpEntry> getVisibleCommands(CommandSender sender) {
        return allCommands.stream()
            .filter(entry -> hasPermission(sender, entry.permission))
            .toList();
    }
    
    /**
     * Help entry class
     */
    private static class HelpEntry {
        private final String command;
        private final String description;
        private final String usage;
        private final String permission;
        private final String category;
        
        public HelpEntry(String command, String description, String usage, String permission, String category) {
            this.command = command;
            this.description = description;
            this.usage = usage;
            this.permission = permission;
            this.category = category;
        }
    }
}