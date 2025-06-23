package id.nusacore.crypto;

import id.nusacore.NusaCore;
import id.nusacore.database.CryptoHistoryDatabase;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class CryptoManager {
    
    private final NusaCore plugin;
    private File cryptoFile;
    private FileConfiguration cryptoConfig;
    
    // Crypto currencies available in the market
    private final Map<String, CryptoCurrency> cryptoCurrencies = new HashMap<>();
    
    // Player investments [playerId -> [currencyId -> amount]]
    private final Map<UUID, Map<String, Double>> playerInvestments = new HashMap<>();
    
    // Last market update time
    private LocalDateTime lastMarketUpdate;
    
    // Scheduled task for market updates
    private int marketUpdateTask;
    
    private final CryptoHistoryDatabase historyDatabase;
    
    public CryptoManager(NusaCore plugin) {
        this.plugin = plugin;
        this.historyDatabase = new CryptoHistoryDatabase();
        loadConfig();
        initializeMarket();
        scheduleMarketUpdates();
        scheduleAutoSave();
    }
    
    /**
     * Load configuration from file
     */
    public void loadConfig() {
        cryptoFile = new File(plugin.getDataFolder(), "crypto.yml");
        
        if (!cryptoFile.exists()) {
            plugin.saveResource("crypto.yml", false);
        }
        
        cryptoConfig = YamlConfiguration.loadConfiguration(cryptoFile);
        
        // Load player investments
        loadPlayerInvestments();
    }
    
    /**
     * Initialize crypto market with default values
     */
    private void initializeMarket() {
        cryptoCurrencies.clear();
        ConfigurationSection currenciesSection = cryptoConfig.getConfigurationSection("currencies");
        if (currenciesSection != null) {
            for (String currencyId : currenciesSection.getKeys(false)) {
                ConfigurationSection currencySection = currenciesSection.getConfigurationSection(currencyId);
                if (currencySection != null) {
                    String name = currencySection.getString("name", currencyId);
                    String symbol = currencySection.getString("symbol", currencyId.toUpperCase());
                    double initialPrice = currencySection.getDouble("initial-price", 100.0);
                    double volatility = currencySection.getDouble("volatility", 0.05);
                    double minPrice = currencySection.getDouble("min-price", 10.0);
                    double maxPrice = currencySection.getDouble("max-price", 10000.0);
                    String riskLevel = currencySection.getString("risk", "medium");
                    CryptoCurrency crypto = new CryptoCurrency(
                        currencyId, name, symbol, initialPrice, volatility, minPrice, maxPrice, CryptoRisk.fromString(riskLevel)
                    );
                    cryptoCurrencies.put(currencyId, crypto);
                    plugin.getLogger().info("Loaded cryptocurrency: " + name + " (" + symbol + ") - Initial price: " + initialPrice);
                }
            }
        }
        loadPlayerInvestments();
        // --- Migration from YAML to SQLite for price history ---
        ConfigurationSection historySection = cryptoConfig.getConfigurationSection("price-history");
        if (historySection != null) {
            for (String currencyId : historySection.getKeys(false)) {
                List<Double> oldHistory = historySection.getDoubleList(currencyId);
                if (!oldHistory.isEmpty()) {
                    plugin.getLogger().info("Migrating price history for " + currencyId + " to SQLite...");
                    historyDatabase.migrateFromYaml(currencyId, oldHistory);
                }
            }
            // Remove price-history from YAML after migration
            cryptoConfig.set("price-history", null);
            try { cryptoConfig.save(cryptoFile); } catch (IOException e) { e.printStackTrace(); }
        }
    }
    
    /**
     * Schedule periodic market updates
     */
    private void scheduleMarketUpdates() {
        int interval = cryptoConfig.getInt("settings.update-interval", 600); // Default 10 minutes
        
        marketUpdateTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::updateMarket, 
            100L, interval * 20L);
            
        lastMarketUpdate = LocalDateTime.now();
    }
    
    /**
     * Add a method to periodically save data
     */
    private void scheduleAutoSave() {
        // Save data every 5 minutes (6000 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveMarketData, 6000L, 6000L);
        plugin.getLogger().info("Scheduled automatic crypto market data saving every 5 minutes");
    }
    
    /**
     * Update all crypto prices in the market
     */
    public void updateMarket() {
        if (cryptoCurrencies.isEmpty()) return;
        boolean broadcastChanges = cryptoConfig.getBoolean("settings.broadcast-changes", true);
        StringBuilder changes = new StringBuilder();
        changes.append(ColorUtils.colorize("&8&m----------------------------------------\n"));
        changes.append(ColorUtils.colorize("&b&lCRYPTO MARKET UPDATE\n"));
        changes.append(ColorUtils.colorize("&8&m----------------------------------------\n"));
        for (CryptoCurrency crypto : cryptoCurrencies.values()) {
            double oldPrice = crypto.getCurrentPrice();
            updateCryptoPrice(crypto);
            double newPrice = crypto.getCurrentPrice();
            // Store in SQLite price history
            historyDatabase.insertPrice(crypto.getId(), newPrice);
            double percentChange = ((newPrice - oldPrice) / oldPrice) * 100;
            String changeStr = String.format("%.2f", percentChange) + "%";
            String arrow = percentChange > 0 ? "&a▲" : (percentChange < 0 ? "&c▼" : "&7◆");
            changes.append(ColorUtils.colorize("&f" + crypto.getSymbol() + " &8- &f" + formatPrice(newPrice) + " Tokens " + arrow + " &f" + changeStr + "\n"));
        }
        if (broadcastChanges) {
            changes.append(ColorUtils.colorize("&8&m----------------------------------------\n"));
            Bukkit.broadcastMessage(ColorUtils.colorize(changes.toString()));
        }
        if (plugin.getCryptoDiscordIntegration() != null && plugin.getCryptoDiscordIntegration().isEnabled()) {
            plugin.getCryptoDiscordIntegration().checkPriceAlerts();
        }
        saveMarketData();
        lastMarketUpdate = LocalDateTime.now();
    }
    
    /**
     * Update a single cryptocurrency price using its volatility
     * @param crypto The cryptocurrency to update
     */
    private void updateCryptoPrice(CryptoCurrency crypto) {
        // Basic price update algorithm with random walk and mean reversion
        double randomFactor = ThreadLocalRandom.current().nextGaussian() * crypto.getVolatility();
        double trendFactor = getTrendFactor(crypto);
        double newPrice = crypto.getCurrentPrice() * (1 + randomFactor + trendFactor);
        
        // Ensure price stays within bounds
        newPrice = Math.max(crypto.getMinPrice(), Math.min(crypto.getMaxPrice(), newPrice));
        
        crypto.setCurrentPrice(newPrice);
    }
    
    /**
     * Get trend factor for a cryptocurrency (can be positive or negative)
     * More volatile cryptos have more extreme trends
     */
    private double getTrendFactor(CryptoCurrency crypto) {
        // Create multi-day trends
        int cycle = (int) (System.currentTimeMillis() / 1000) / (3600 * 12); // 12-hour cycles
        double baseTrend = Math.sin(cycle * 0.5) * 0.02; // Base trend between -2% and 2%
        
        // Amplify based on volatility and risk
        double amplifier = switch (crypto.getRisk()) {
            case LOW -> 1.0;
            case MEDIUM -> 1.5;
            case HIGH -> 2.5;
            case EXTREME -> 4.0;
        };
        
        return baseTrend * amplifier;
    }
    
    /**
     * Save market data to file
     */
    public void saveMarketData() {
        try {
            plugin.getLogger().info("Saving crypto market data... Current investments: " + playerInvestments.size() + " player(s)");
            ConfigurationSection currenciesSection = cryptoConfig.createSection("currencies");
            for (Map.Entry<String, CryptoCurrency> entry : cryptoCurrencies.entrySet()) {
                ConfigurationSection currencySection = currenciesSection.createSection(entry.getKey());
                CryptoCurrency crypto = entry.getValue();
                currencySection.set("name", crypto.getName());
                currencySection.set("symbol", crypto.getSymbol());
                currencySection.set("current-price", crypto.getCurrentPrice());
                currencySection.set("volatility", crypto.getVolatility());
                currencySection.set("min-price", crypto.getMinPrice());
                currencySection.set("max-price", crypto.getMaxPrice());
                currencySection.set("risk", crypto.getRisk().name());
            }
            ConfigurationSection investmentsSection = cryptoConfig.createSection("investments");
            for (Map.Entry<UUID, Map<String, Double>> entry : playerInvestments.entrySet()) {
                ConfigurationSection playerSection = investmentsSection.createSection(entry.getKey().toString());
                for (Map.Entry<String, Double> investment : entry.getValue().entrySet()) {
                    if (investment.getValue() > 0) {
                        playerSection.set(investment.getKey(), investment.getValue());
                    }
                }
            }
            // No longer save price-history to YAML
            cryptoConfig.save(cryptoFile);
            plugin.getLogger().info("Crypto market data saved successfully to " + cryptoFile.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save crypto market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load player investments from config
     */
    private void loadPlayerInvestments() {
        ConfigurationSection investmentsSection = cryptoConfig.getConfigurationSection("investments");
        if (investmentsSection == null) {
            plugin.getLogger().warning("No investments section found in crypto.yml");
            return;
        }
        
        // Log the size of the investments section from the file
        plugin.getLogger().info("Loading investments from crypto.yml... Found " + 
            investmentsSection.getKeys(false).size() + " player(s) with investments");
        
        playerInvestments.clear();
        
        int playerCount = 0;
        int investmentCount = 0;
        
        for (String playerUuid : investmentsSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(playerUuid);
                ConfigurationSection playerSection = investmentsSection.getConfigurationSection(playerUuid);
                
                if (playerSection != null) {
                    Map<String, Double> investments = new HashMap<>();
                    
                    for (String currencyId : playerSection.getKeys(false)) {
                        double amount = playerSection.getDouble(currencyId);
                        if (amount > 0 && cryptoCurrencies.containsKey(currencyId)) {
                            investments.put(currencyId, amount);
                            investmentCount++;
                        }
                    }
                    
                    if (!investments.isEmpty()) {
                        playerInvestments.put(uuid, investments);
                        playerCount++;
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in crypto investments: " + playerUuid);
            }
        }
        
        plugin.getLogger().info("Loaded " + investmentCount + " crypto investments for " + playerCount + " players");
    }
    
    /**
     * Get a player's investments
     * @param player The player
     * @return Map of currency ID to amount
     */
    public Map<String, Double> getPlayerInvestments(OfflinePlayer player) {
        return playerInvestments.getOrDefault(player.getUniqueId(), new HashMap<>());
    }
    
    /**
     * Get a player's investment in specific currency
     * @param player The player
     * @param currencyId The currency ID
     * @return The amount invested, 0 if none
     */
    public double getPlayerInvestment(OfflinePlayer player, String currencyId) {
        Map<String, Double> investments = getPlayerInvestments(player);
        return investments.getOrDefault(currencyId, 0.0);
    }
    
    /**
     * Buy cryptocurrency with tokens
     * @param player The player
     * @param currencyId The currency to buy
     * @param tokenAmount Amount of tokens to spend
     * @return True if purchase successful
     */
    public boolean buyCrypto(Player player, String currencyId, int tokenAmount) {
        if (tokenAmount <= 0) {
            player.sendMessage(ColorUtils.colorize("&cJumlah token harus lebih dari 0!"));
            return false;
        }
        
        CryptoCurrency crypto = cryptoCurrencies.get(currencyId);
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize("&cCryptocurrency tidak ditemukan!"));
            return false;
        }
        
        // Check if player has enough tokens
        if (!plugin.getTokenManager().hasTokens(player, tokenAmount)) {
            player.sendMessage(ColorUtils.colorize("&cAnda tidak memiliki cukup token!"));
            return false;
        }
        
        // Calculate amount of crypto to buy
        double cryptoAmount = tokenAmount / crypto.getCurrentPrice();
        
        // Update player's investments
        Map<String, Double> investments = playerInvestments.getOrDefault(player.getUniqueId(), new HashMap<>());
        double currentAmount = investments.getOrDefault(currencyId, 0.0);
        investments.put(currencyId, currentAmount + cryptoAmount);
        playerInvestments.put(player.getUniqueId(), investments);
        
        // Deduct tokens from player
        plugin.getTokenManager().removeTokens(player, tokenAmount);
        
        // Save data
        saveMarketData();
        
        // Notify Discord if integration is enabled
        if (plugin.getCryptoDiscordIntegration() != null && plugin.getCryptoDiscordIntegration().isEnabled()) {
            plugin.getCryptoDiscordIntegration().sendTransactionNotification(
                player, currencyId, cryptoAmount, tokenAmount, true
            );
        }
        
        // Send confirmation message
        player.sendMessage(ColorUtils.colorize("&aAnda telah membeli &f" + 
            String.format("%.6f", cryptoAmount) + " " + crypto.getSymbol() + 
            " &adengan &f" + tokenAmount + " Token&a!"));
        
        // Save the market data immediately after purchase
        saveMarketData();
        
        return true;
    }
    
    /**
     * Sell cryptocurrency for tokens
     * @param player The player
     * @param currencyId The currency to sell
     * @param cryptoAmount Amount of cryptocurrency to sell (0 for all)
     * @return True if sale successful
     */
    public boolean sellCrypto(Player player, String currencyId, double cryptoAmount) {
        CryptoCurrency crypto = cryptoCurrencies.get(currencyId);
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize("&cCryptocurrency tidak ditemukan!"));
            return false;
        }
        
        Map<String, Double> investments = playerInvestments.getOrDefault(player.getUniqueId(), new HashMap<>());
        double currentAmount = investments.getOrDefault(currencyId, 0.0);
        
        if (currentAmount <= 0) {
            player.sendMessage(ColorUtils.colorize("&cAnda tidak memiliki " + crypto.getSymbol() + "!"));
            return false;
        }
        
        // If cryptoAmount is 0 or more than what player has, sell all
        if (cryptoAmount <= 0 || cryptoAmount > currentAmount) {
            cryptoAmount = currentAmount;
        }
        
        // Calculate token return value
        int tokenReturn = (int)(cryptoAmount * crypto.getCurrentPrice());
        
        // Ensure minimum return is 1 token
        tokenReturn = Math.max(1, tokenReturn);
        
        // Update player's investments
        investments.put(currencyId, currentAmount - cryptoAmount);
        if (investments.get(currencyId) <= 0.000001) { // Remove if negligible amount
            investments.remove(currencyId);
        }
        
        if (investments.isEmpty()) {
            playerInvestments.remove(player.getUniqueId());
        } else {
            playerInvestments.put(player.getUniqueId(), investments);
        }
        
        // Add tokens to player
        plugin.getTokenManager().addTokens(player, tokenReturn);
        
        // Save data
        saveMarketData();
        
        // Notify Discord if integration is enabled
        if (plugin.getCryptoDiscordIntegration() != null && plugin.getCryptoDiscordIntegration().isEnabled()) {
            plugin.getCryptoDiscordIntegration().sendTransactionNotification(
                player, currencyId, cryptoAmount, tokenReturn, false
            );
        }
        
        // Send confirmation message
        player.sendMessage(ColorUtils.colorize("&aAnda telah menjual &f" + 
            String.format("%.6f", cryptoAmount) + " " + crypto.getSymbol() + 
            " &adan mendapatkan &f" + tokenReturn + " Token&a!"));
        
        // Save the market data immediately after sale
        saveMarketData();
        
        return true;
    }
    
    /**
     * Get the total value of a player's crypto portfolio in tokens
     * @param player The player
     * @return Total value in tokens
     */
    public double getPortfolioValue(OfflinePlayer player) {
        Map<String, Double> investments = getPlayerInvestments(player);
        double totalValue = 0.0;
        
        for (Map.Entry<String, Double> entry : investments.entrySet()) {
            CryptoCurrency crypto = cryptoCurrencies.get(entry.getKey());
            if (crypto != null) {
                totalValue += entry.getValue() * crypto.getCurrentPrice();
            }
        }
        
        return totalValue;
    }
    
    /**
     * Format a token price nicely
     */
    private String formatPrice(double price) {
        if (price >= 1000) {
            return String.format("%,.0f", price);
        } else if (price >= 100) {
            return String.format("%.1f", price);
        } else if (price >= 1) {
            return String.format("%.2f", price);
        } else {
            return String.format("%.6f", price);
        }
    }
    
    /**
     * Get list of all available cryptocurrencies
     */
    public List<CryptoCurrency> getAllCryptocurrencies() {
        return new ArrayList<>(cryptoCurrencies.values());
    }
    
    /**
     * Get a cryptocurrency by ID
     */
    public CryptoCurrency getCryptocurrency(String id) {
        return cryptoCurrencies.get(id);
    }
    
    /**
     * Get price history for a cryptocurrency
     */
    public List<Double> getPriceHistory(String currencyId) {
        // Fetch last 50 prices from SQLite
        return historyDatabase.getPriceHistory(currencyId, 50);
    }
    
    /**
     * Update player's investments directly
     * @param playerId Player UUID
     * @param investments New investments map
     */
    public void updatePlayerInvestments(UUID playerId, Map<String, Double> investments) {
        // Remove any empty investments
        investments.entrySet().removeIf(entry -> entry.getValue() <= 0);
        
        // If map is empty, remove player from investments
        if (investments.isEmpty()) {
            playerInvestments.remove(playerId);
        } else {
            // Update with new investment values
            playerInvestments.put(playerId, new HashMap<>(investments));
        }
        
        // Save to persist changes
        saveMarketData();
    }
    
    /**
     * Cleanup on disable
     */
    public void onDisable() {
        plugin.getLogger().info("Saving crypto market data before shutdown...");
        saveMarketData();
        plugin.getLogger().info("Crypto market data saved!");
    }
}