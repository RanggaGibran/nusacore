// filepath: d:\Plugin Nusa\nusacore\src\main\java\id\nusacore\crypto\mining\MiningManager.java
package id.nusacore.crypto.mining;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoManager;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MiningManager implements Listener {
    private final NusaCore plugin;
    private final CryptoManager cryptoManager;
    private File configFile;
    private FileConfiguration config;
    
    // Mining cooldowns per player
    private final Map<UUID, LocalDateTime> miningCooldowns = new ConcurrentHashMap<>();
    
    // Mining rewards configuration
    private final Map<Material, MiningReward> blockRewards = new HashMap<>();
    
    // Global mining rate limiter
    private int miningLimit = 50;
    private final LinkedList<LocalDateTime> recentMiningEvents = new LinkedList<>();
    
    // Daily mining limits per player
    private final Map<UUID, PlayerMiningStats> playerStats = new ConcurrentHashMap<>();
    
    // Cleanup task
    private BukkitTask cleanupTask;
    
    public MiningManager(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
        loadConfig();
        
        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Schedule cleanup task
        scheduleCleanup();
    }
    
    /**
     * Load mining configuration
     */
    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "mining.yml");
        
        if (!configFile.exists()) {
            plugin.saveResource("mining.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load mining settings
        miningLimit = config.getInt("settings.global-rate-limit", 50);
        
        // Load block rewards
        ConfigurationSection rewardsSection = config.getConfigurationSection("block-rewards");
        if (rewardsSection != null) {
            for (String materialName : rewardsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(materialName);
                    ConfigurationSection blockSection = rewardsSection.getConfigurationSection(materialName);
                    
                    if (blockSection != null) {
                        String currencyId = blockSection.getString("currency", "btc");
                        double baseAmount = blockSection.getDouble("base-amount", 0.0001);
                        double chance = blockSection.getDouble("chance", 100.0);
                        int xpRequired = blockSection.getInt("min-level", 0);
                        
                        blockRewards.put(material, new MiningReward(currencyId, baseAmount, chance, xpRequired));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in mining config: " + materialName);
                }
            }
        }
        
        // Load player stats
        loadPlayerStats();
    }
    
    /**
     * Load player mining stats from file
     */
    private void loadPlayerStats() {
        ConfigurationSection statsSection = config.getConfigurationSection("player-stats");
        if (statsSection != null) {
            for (String uuidString : statsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    ConfigurationSection playerSection = statsSection.getConfigurationSection(uuidString);
                    
                    if (playerSection != null) {
                        int totalBlocksMined = playerSection.getInt("blocks-mined", 0);
                        int miningLevel = playerSection.getInt("level", 1);
                        int currentXP = playerSection.getInt("xp", 0);
                        double dailyMined = playerSection.getDouble("daily-mined", 0.0);
                        String lastDayStr = playerSection.getString("last-day", "");
                        
                        PlayerMiningStats stats = new PlayerMiningStats(
                            totalBlocksMined, miningLevel, currentXP, dailyMined, lastDayStr);
                        playerStats.put(uuid, stats);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in mining stats: " + uuidString);
                }
            }
        }
    }
    
    /**
     * Save player mining stats to file
     */
    public void savePlayerStats() {
        // Clear existing stats section
        config.set("player-stats", null);
        
        // Create new section
        ConfigurationSection statsSection = config.createSection("player-stats");
        
        // Save each player's stats
        for (Map.Entry<UUID, PlayerMiningStats> entry : playerStats.entrySet()) {
            String uuid = entry.getKey().toString();
            PlayerMiningStats stats = entry.getValue();
            
            ConfigurationSection playerSection = statsSection.createSection(uuid);
            playerSection.set("blocks-mined", stats.getTotalBlocksMined());
            playerSection.set("level", stats.getMiningLevel());
            playerSection.set("xp", stats.getCurrentXP());
            playerSection.set("daily-mined", stats.getDailyMined());
            playerSection.set("last-day", stats.getLastDay());
        }
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mining.yml", e);
        }
    }
    
    /**
     * Schedule cleanup task
     */
    private void scheduleCleanup() {
        // Run every 10 minutes
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Clean up old mining events (older than 1 minute)
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
            synchronized (recentMiningEvents) {
                while (!recentMiningEvents.isEmpty() && recentMiningEvents.getFirst().isBefore(cutoff)) {
                    recentMiningEvents.removeFirst();
                }
            }
            
            // Save player stats periodically
            savePlayerStats();
        }, 12000L, 12000L); // 10 minutes = 12000 ticks
    }
    
    /**
     * Handle block break events for mining
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlock().getType();
        
        // Check if this block type has rewards
        MiningReward reward = blockRewards.get(material);
        if (reward == null) {
            return; // No reward for this block
        }
        
        // Check for rate limiting
        if (isRateLimited(player)) {
            return;
        }
        
        // Get player stats or create new stats
        PlayerMiningStats stats = getPlayerStats(player.getUniqueId());
        
        // Check if player meets level requirement
        if (stats.getMiningLevel() < reward.getXpRequired()) {
            return;
        }
        
        // Check daily limit
        double dailyLimit = getDailyLimit(stats.getMiningLevel());
        if (stats.getDailyMined() >= dailyLimit) {
            // Player reached daily limit, only add XP
            stats.addBlockMined();
            return;
        }
        
        // Roll for chance
        if (Math.random() * 100 > reward.getChance()) {
            // Bad luck, no reward this time
            stats.addBlockMined();
            return;
        }
        
        // Calculate reward amount with level bonus
        double levelBonus = 1.0 + (stats.getMiningLevel() - 1) * 0.05; // 5% increase per level
        double rewardAmount = reward.getBaseAmount() * levelBonus;
        
        // Add reward to player's crypto balance
        addCryptoReward(player, reward.getCurrencyId(), rewardAmount);
        
        // Update stats
        stats.addBlockMined();
        stats.addCryptoMined(rewardAmount);
        
        // Check for level up
        int newLevel = calculateMiningLevel(stats.getTotalBlocksMined());
        if (newLevel > stats.getMiningLevel()) {
            stats.setMiningLevel(newLevel);
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                "&aMining level naik! Anda sekarang level &f" + newLevel + 
                " &a(+" + (newLevel - stats.getMiningLevel()) + ")"));
        }
    }
    
    /**
     * Check if mining is rate limited
     */
    private boolean isRateLimited(Player player) {
        UUID playerId = player.getUniqueId();
        LocalDateTime now = LocalDateTime.now();
        
        // Check player cooldown
        LocalDateTime lastMining = miningCooldowns.get(playerId);
        if (lastMining != null && lastMining.plusSeconds(1).isAfter(now)) {
            return true; // Still on cooldown
        }
        miningCooldowns.put(playerId, now);
        
        // Check global rate limit
        synchronized (recentMiningEvents) {
            if (recentMiningEvents.size() >= miningLimit) {
                return true; // Global rate limit reached
            }
            recentMiningEvents.addLast(now);
        }
        
        return false;
    }
    
    /**
     * Add crypto reward to player
     */
    private void addCryptoReward(Player player, String currencyId, double amount) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        if (crypto == null) {
            plugin.getLogger().warning("Attempted to reward invalid crypto: " + currencyId);
            return;
        }
        
        // Add to player's crypto balance
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        double current = investments.getOrDefault(currencyId, 0.0);
        investments.put(currencyId, current + amount);
        cryptoManager.updatePlayerInvestments(player.getUniqueId(), investments);
        
        // Notify player
        String symbol = crypto.getSymbol();
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
            "&6+&f" + String.format("%.6f", amount) + " " + symbol + " &6dari mining!"));
    }
    
    /**
     * Get player mining stats or create new entry
     */
    private PlayerMiningStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, k -> new PlayerMiningStats(0, 1, 0, 0.0, ""));
    }
    
    /**
     * Calculate mining level based on total blocks mined
     */
    private int calculateMiningLevel(int totalBlocks) {
        // Simple level formula: level = sqrt(blocks/100) + 1
        return (int) Math.floor(Math.sqrt(totalBlocks / 100.0)) + 1;
    }
    
    /**
     * Get daily mining limit based on level
     */
    private double getDailyLimit(int level) {
        // Base limit + level bonus
        return 0.01 + (level - 1) * 0.002;
    }
    
    /**
     * Clean up on plugin disable
     */
    public void cleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        savePlayerStats();
    }
    
    /**
     * Static class to hold mining reward information
     */
    private static class MiningReward {
        private final String currencyId;
        private final double baseAmount;
        private final double chance;
        private final int xpRequired;
        
        public MiningReward(String currencyId, double baseAmount, double chance, int xpRequired) {
            this.currencyId = currencyId;
            this.baseAmount = baseAmount;
            this.chance = chance;
            this.xpRequired = xpRequired;
        }
        
        public String getCurrencyId() {
            return currencyId;
        }
        
        public double getBaseAmount() {
            return baseAmount;
        }
        
        public double getChance() {
            return chance;
        }
        
        public int getXpRequired() {
            return xpRequired;
        }
    }
    
    /**
     * Class to hold player mining statistics
     */
    public static class PlayerMiningStats {
        private int totalBlocksMined;
        private int miningLevel;
        private int currentXP;
        private double dailyMined;
        private String lastDay;
        
        public PlayerMiningStats(int totalBlocksMined, int miningLevel, int currentXP, double dailyMined, String lastDay) {
            this.totalBlocksMined = totalBlocksMined;
            this.miningLevel = miningLevel;
            this.currentXP = currentXP;
            this.dailyMined = dailyMined;
            this.lastDay = lastDay;
            
            // Reset daily stats if it's a new day
            checkDayReset();
        }
        
        public void addBlockMined() {
            totalBlocksMined++;
            currentXP++;
            checkDayReset();
        }
        
        public void addCryptoMined(double amount) {
            checkDayReset();
            dailyMined += amount;
        }
        
        private void checkDayReset() {
            String today = LocalDateTime.now().toLocalDate().toString();
            if (!today.equals(lastDay)) {
                dailyMined = 0.0;
                lastDay = today;
            }
        }
        
        public int getTotalBlocksMined() {
            return totalBlocksMined;
        }
        
        public int getMiningLevel() {
            return miningLevel;
        }
        
        public void setMiningLevel(int miningLevel) {
            this.miningLevel = miningLevel;
        }
        
        public int getCurrentXP() {
            return currentXP;
        }
        
        public double getDailyMined() {
            return dailyMined;
        }
        
        public String getLastDay() {
            return lastDay;
        }
    }
}