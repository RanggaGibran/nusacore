package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class TokenManager {
    private final NusaCore plugin;
    private FileConfiguration tokenConfig;
    private File tokenFile;
    private final Map<UUID, Integer> tokenCache = new HashMap<>();
    private int defaultTokens;
    private String tokenName;
    private String tokenNamePlural;
    
    public TokenManager(NusaCore plugin) {
        this.plugin = plugin;
        
        // Load default values from config
        FileConfiguration config = plugin.getConfig();
        this.defaultTokens = config.getInt("tokens.starting-amount", 0);
        this.tokenName = config.getString("tokens.name", "Token");
        this.tokenNamePlural = config.getString("tokens.name-plural", "Tokens");
        
        // Load or create tokens file
        loadTokenData();
    }
    
    /**
     * Load token data from file
     */
    public void loadTokenData() {
        // Create tokens.yml file if it doesn't exist
        tokenFile = new File(plugin.getDataFolder(), "tokens.yml");
        if (!tokenFile.exists()) {
            try {
                tokenFile.createNewFile();
                
                // Initialize with empty data
                tokenConfig = YamlConfiguration.loadConfiguration(tokenFile);
                tokenConfig.createSection("tokens");
                tokenConfig.save(tokenFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create tokens.yml", e);
            }
        } else {
            tokenConfig = YamlConfiguration.loadConfiguration(tokenFile);
        }
        
        // Load tokens to cache
        if (tokenConfig.isConfigurationSection("tokens")) {
            for (String uuidString : tokenConfig.getConfigurationSection("tokens").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    int tokens = tokenConfig.getInt("tokens." + uuidString);
                    tokenCache.put(uuid, tokens);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in tokens.yml: " + uuidString);
                }
            }
        }
        
        plugin.getLogger().info("Token data loaded successfully. " + tokenCache.size() + " accounts found.");
    }
    
    /**
     * Save token data to file
     */
    public void saveTokenData() {
        if (tokenConfig == null || tokenFile == null) {
            return;
        }
        
        // Save cache to config
        for (Map.Entry<UUID, Integer> entry : tokenCache.entrySet()) {
            tokenConfig.set("tokens." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            tokenConfig.save(tokenFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save tokens.yml", e);
        }
    }
    
    /**
     * Reload konfigurasi token
     */
    public void reloadConfig() {
        // Simpan data dulu
        saveTokenData();
        
        // Reset properti ke defaults
        defaultTokens = plugin.getConfig().getInt("tokens.starting-amount", 0);
        tokenName = plugin.getConfig().getString("tokens.name", "Token");
        tokenNamePlural = plugin.getConfig().getString("tokens.name-plural", "Tokens");
        
        // Load ulang data
        loadTokenData();
    }
    
    /**
     * Get player's tokens
     * @param player The player
     * @return Player's token count
     */
    public int getTokens(OfflinePlayer player) {
        return tokenCache.getOrDefault(player.getUniqueId(), defaultTokens);
    }
    
    /**
     * Set player's tokens
     * @param player The player
     * @param amount New token amount
     * @return true if successful
     */
    public boolean setTokens(OfflinePlayer player, int amount) {
        if (amount < 0) {
            return false;
        }
        
        tokenCache.put(player.getUniqueId(), amount);
        return true;
    }
    
    /**
     * Reset player's tokens to default
     * @param player The player
     * @return true if successful
     */
    public boolean resetTokens(OfflinePlayer player) {
        return setTokens(player, defaultTokens);
    }
    
    /**
     * Add tokens to player's balance
     * @param player The player
     * @param amount Amount to add
     * @return true if successful
     */
    public boolean addTokens(OfflinePlayer player, int amount) {
        if (amount < 0) {
            return false;
        }
        
        int current = getTokens(player);
        tokenCache.put(player.getUniqueId(), current + amount);
        return true;
    }
    
    /**
     * Remove tokens from player's balance
     * @param player The player
     * @param amount Amount to remove
     * @return true if successful
     */
    public boolean removeTokens(OfflinePlayer player, int amount) {
        if (amount < 0) {
            return false;
        }
        
        int current = getTokens(player);
        
        if (current < amount) {
            // Not enough tokens
            return false;
        }
        
        tokenCache.put(player.getUniqueId(), current - amount);
        return true;
    }
    
    /**
     * Check if player has enough tokens
     * @param player The player
     * @param amount Amount to check
     * @return true if player has enough tokens
     */
    public boolean hasTokens(OfflinePlayer player, int amount) {
        return getTokens(player) >= amount;
    }
    
    /**
     * Format tokens with name
     * @param amount Token amount
     * @return Formatted token string
     */
    public String formatTokens(int amount) {
        if (amount == 1) {
            return amount + " " + tokenName;
        } else {
            return amount + " " + tokenNamePlural;
        }
    }
    
    /**
     * Get the default starting tokens
     * @return Default tokens
     */
    public int getDefaultTokens() {
        return defaultTokens;
    }
    
    /**
     * Get token name (singular)
     * @return Token name
     */
    public String getTokenName() {
        return tokenName;
    }
    
    /**
     * Get token name (plural)
     * @return Token name plural
     */
    public String getTokenNamePlural() {
        return tokenNamePlural;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        saveTokenData();
        tokenCache.clear();
    }
}