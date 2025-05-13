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

public class EconomyManager {
    private final NusaCore plugin;
    private FileConfiguration economyConfig;
    private File economyFile;
    private final Map<UUID, Double> balanceCache = new HashMap<>();
    private double defaultBalance;
    private String currencySymbol;
    private String currencyName;
    private String currencyNamePlural;
    
    public EconomyManager(NusaCore plugin) {
        this.plugin = plugin;
        
        // Load default values from config
        FileConfiguration config = plugin.getConfig();
        this.defaultBalance = config.getDouble("economy.starting-balance", 1000.0);
        this.currencySymbol = config.getString("economy.currency.symbol", "$");
        this.currencyName = config.getString("economy.currency.name", "Coin");
        this.currencyNamePlural = config.getString("economy.currency.name-plural", "Coins");
        
        // Load or create economy file
        loadEconomyData();
    }
    
    /**
     * Load economy data from file
     */
    public void loadEconomyData() {
        // Create economy.yml file if it doesn't exist
        economyFile = new File(plugin.getDataFolder(), "economy.yml");
        if (!economyFile.exists()) {
            try {
                economyFile.createNewFile();
                
                // Initialize with empty data
                economyConfig = YamlConfiguration.loadConfiguration(economyFile);
                economyConfig.createSection("balances");
                economyConfig.save(economyFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create economy.yml", e);
            }
        } else {
            economyConfig = YamlConfiguration.loadConfiguration(economyFile);
        }
        
        // Load balances to cache
        if (economyConfig.isConfigurationSection("balances")) {
            for (String uuidString : economyConfig.getConfigurationSection("balances").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    double balance = economyConfig.getDouble("balances." + uuidString);
                    balanceCache.put(uuid, balance);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in economy.yml: " + uuidString);
                }
            }
        }
        
        plugin.getLogger().info("Economy data loaded successfully. " + balanceCache.size() + " accounts found.");
    }
    
    /**
     * Save economy data to file
     */
    public void saveEconomyData() {
        if (economyConfig == null || economyFile == null) {
            return;
        }
        
        // Save cache to config
        for (Map.Entry<UUID, Double> entry : balanceCache.entrySet()) {
            economyConfig.set("balances." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            economyConfig.save(economyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save economy.yml", e);
        }
    }
    
    /**
     * Reload konfigurasi ekonomi
     */
    public void reloadConfig() {
        // Simpan data dulu untuk amannya
        saveEconomyData();
        
        // Reset properti ke defaults
        defaultBalance = plugin.getConfig().getDouble("economy.starting-balance", 1000.0);
        currencySymbol = plugin.getConfig().getString("economy.currency.symbol", "$");
        currencyName = plugin.getConfig().getString("economy.currency.name", "Coin");
        currencyNamePlural = plugin.getConfig().getString("economy.currency.name-plural", "Coins");
        
        // Load ulang data
        loadEconomyData();
    }
    
    /**
     * Get player's balance
     * @param player The player
     * @return Player's balance
     */
    public double getBalance(OfflinePlayer player) {
        // Check if economy is enabled but AVOID calling our own provider
        if (plugin.isEconomyEnabled() && plugin.getEconomy() != null 
                && !(plugin.getEconomy() instanceof id.nusacore.economy.VaultEconomyProvider)) {
            // If using an external Vault provider, get balance from there
            return plugin.getEconomy().getBalance(player);
        } else {
            // Use our own implementation
            return balanceCache.getOrDefault(player.getUniqueId(), defaultBalance);
        }
    }
    
    /**
     * Set player's balance
     * @param player The player
     * @param amount New balance
     * @return true if successful
     */
    public boolean setBalance(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return false;
        }
        
        // Check if using external economy but AVOID calling our own provider
        if (plugin.isEconomyEnabled() && plugin.getEconomy() != null 
                && !(plugin.getEconomy() instanceof id.nusacore.economy.VaultEconomyProvider)) {
            // Use external provider
            double current = plugin.getEconomy().getBalance(player);
            if (current > amount) {
                plugin.getEconomy().withdrawPlayer(player, current - amount);
            } else if (current < amount) {
                plugin.getEconomy().depositPlayer(player, amount - current);
            }
            return true;
        } else {
            // Use our own implementation
            balanceCache.put(player.getUniqueId(), amount);
            return true;
        }
    }
    
    /**
     * Reset player's balance to default
     * @param player The player
     * @return true if successful
     */
    public boolean resetBalance(OfflinePlayer player) {
        return setBalance(player, defaultBalance);
    }
    
    /**
     * Add money to player's balance
     * @param player The player
     * @param amount Amount to add
     * @return true if successful
     */
    public boolean addBalance(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return false;
        }
        
        // Check if using external economy but AVOID calling our own provider
        if (plugin.isEconomyEnabled() && plugin.getEconomy() != null 
                && !(plugin.getEconomy() instanceof id.nusacore.economy.VaultEconomyProvider)) {
            // If using external Vault provider
            return plugin.getEconomy().depositPlayer(player, amount).transactionSuccess();
        } else {
            // Use our own implementation
            double current = balanceCache.getOrDefault(player.getUniqueId(), defaultBalance);
            balanceCache.put(player.getUniqueId(), current + amount);
            return true;
        }
    }
    
    /**
     * Remove money from player's balance
     * @param player The player
     * @param amount Amount to remove
     * @return true if successful
     */
    public boolean removeBalance(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return false;
        }
        
        double current = balanceCache.getOrDefault(player.getUniqueId(), defaultBalance);
        
        if (current < amount) {
            // Not enough money
            return false;
        }
        
        // Check if using external economy but AVOID calling our own provider
        if (plugin.isEconomyEnabled() && plugin.getEconomy() != null 
                && !(plugin.getEconomy() instanceof id.nusacore.economy.VaultEconomyProvider)) {
            // If using external Vault provider
            return plugin.getEconomy().withdrawPlayer(player, amount).transactionSuccess();
        } else {
            // Use our own implementation
            balanceCache.put(player.getUniqueId(), current - amount);
            return true;
        }
    }
    
    /**
     * Transfer money from one player to another
     * @param from Source player
     * @param to Destination player
     * @param amount Amount to transfer
     * @return true if successful
     */
    public boolean transferMoney(OfflinePlayer from, OfflinePlayer to, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        // Check if source player has enough money
        if (getBalance(from) < amount) {
            return false;
        }
        
        // Remove from source player
        boolean success = removeBalance(from, amount);
        
        // Add to destination player if successful
        if (success) {
            addBalance(to, amount);
            
            // Log transaction for both online players
            Player fromPlayer = from.getPlayer();
            Player toPlayer = to.getPlayer();
            
            if (fromPlayer != null && fromPlayer.isOnline()) {
                fromPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&aAnda telah mengirim " + formatAmount(amount) + " &akepada &e" + to.getName()));
            }
            
            if (toPlayer != null && toPlayer.isOnline()) {
                toPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&aAnda telah menerima " + formatAmount(amount) + " &adari &e" + from.getName()));
            }
        }
        
        return success;
    }
    
    /**
     * Format money amount with currency symbol
     * @param amount Amount to format
     * @return Formatted amount string
     */
    public String formatAmount(double amount) {
        if (amount == 1) {
            return currencySymbol + String.format("%,.2f", amount) + " " + currencyName;
        } else {
            return currencySymbol + String.format("%,.2f", amount) + " " + currencyNamePlural;
        }
    }
    
    /**
     * Get the default starting balance
     * @return Default balance
     */
    public double getDefaultBalance() {
        return defaultBalance;
    }
    
    /**
     * Mendapatkan simbol mata uang
     * @return Simbol mata uang
     */
    public String getCurrencySymbol() {
        return currencySymbol;
    }

    /**
     * Mendapatkan nama mata uang (bentuk tunggal)
     * @return Nama mata uang
     */
    public String getCurrencyName() {
        return currencyName;
    }

    /**
     * Mendapatkan nama mata uang (bentuk jamak)
     * @return Nama mata uang jamak
     */
    public String getCurrencyNamePlural() {
        return currencyNamePlural;
    }
    
    /**
     * Get the balance cache map - used by VaultEconomyProvider to avoid recursion
     * @return The balance cache map
     */
    public Map<UUID, Double> getBalanceCache() {
        return balanceCache;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        saveEconomyData();
        balanceCache.clear();
    }
}