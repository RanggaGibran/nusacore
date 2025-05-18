package id.nusacore.crypto;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class StakingManager {
    private final NusaCore plugin;
    private final CryptoManager cryptoManager;
    private File stakingFile;
    private FileConfiguration stakingConfig;
    
    // Map [playerUuid -> [currencyId -> StakeInfo]]
    private final Map<UUID, Map<String, StakeInfo>> playerStakes = new ConcurrentHashMap<>();
    
    // Scheduled task for stake rewards
    private BukkitTask rewardTask;
    
    // APY rates for each currency (annual percentage yield)
    private final Map<String, Double> stakingRates = new HashMap<>();
    
    // Staking lock periods in days
    private final Map<String, Integer> stakingPeriods = new HashMap<>();
    
    // Minimum stake amounts
    private final Map<String, Double> minimumStakes = new HashMap<>();
    
    public StakingManager(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
        loadConfig();
        scheduleRewards();
    }
    
    /**
     * Load staking configuration
     */
    public void loadConfig() {
        stakingFile = new File(plugin.getDataFolder(), "staking.yml");
        
        if (!stakingFile.exists()) {
            plugin.saveResource("staking.yml", false);
        }
        
        stakingConfig = YamlConfiguration.loadConfiguration(stakingFile);
        
        // Load staking rates
        ConfigurationSection ratesSection = stakingConfig.getConfigurationSection("rates");
        if (ratesSection != null) {
            for (String currencyId : ratesSection.getKeys(false)) {
                double rate = ratesSection.getDouble(currencyId, 5.0); // Default 5% APY
                stakingRates.put(currencyId, rate);
            }
        }
        
        // Load lock periods
        ConfigurationSection periodsSection = stakingConfig.getConfigurationSection("lock-periods");
        if (periodsSection != null) {
            for (String currencyId : periodsSection.getKeys(false)) {
                int period = periodsSection.getInt(currencyId, 7); // Default 7 days
                stakingPeriods.put(currencyId, period);
            }
        }
        
        // Load minimum stakes
        ConfigurationSection minimumsSection = stakingConfig.getConfigurationSection("minimums");
        if (minimumsSection != null) {
            for (String currencyId : minimumsSection.getKeys(false)) {
                double minimum = minimumsSection.getDouble(currencyId, 0.001);
                minimumStakes.put(currencyId, minimum);
            }
        }
        
        // Load player stakes
        loadPlayerStakes();
    }
    
    /**
     * Load player stakes from config
     */
    private void loadPlayerStakes() {
        ConfigurationSection stakesSection = stakingConfig.getConfigurationSection("stakes");
        if (stakesSection == null) return;
        
        for (String playerUuidStr : stakesSection.getKeys(false)) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                ConfigurationSection playerSection = stakesSection.getConfigurationSection(playerUuidStr);
                
                if (playerSection != null) {
                    Map<String, StakeInfo> playerStakeMap = new HashMap<>();
                    
                    for (String currencyId : playerSection.getKeys(false)) {
                        ConfigurationSection currencySection = playerSection.getConfigurationSection(currencyId);
                        
                        if (currencySection != null) {
                            double amount = currencySection.getDouble("amount");
                            String startDateStr = currencySection.getString("start-date");
                            String endDateStr = currencySection.getString("end-date");
                            
                            if (amount > 0 && startDateStr != null && endDateStr != null) {
                                LocalDateTime startDate = LocalDateTime.parse(startDateStr, 
                                    DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                LocalDateTime endDate = LocalDateTime.parse(endDateStr, 
                                    DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                
                                StakeInfo stakeInfo = new StakeInfo(amount, startDate, endDate);
                                playerStakeMap.put(currencyId, stakeInfo);
                            }
                        }
                    }
                    
                    if (!playerStakeMap.isEmpty()) {
                        playerStakes.put(playerUuid, playerStakeMap);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading stake for player " + playerUuidStr + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Save all staking data to file
     */
    public void saveData() {
        // Clear existing stakes section
        stakingConfig.set("stakes", null);
        
        // Create new section
        ConfigurationSection stakesSection = stakingConfig.createSection("stakes");
        
        // Save each player's stakes
        for (Map.Entry<UUID, Map<String, StakeInfo>> playerEntry : playerStakes.entrySet()) {
            String playerUuid = playerEntry.getKey().toString();
            ConfigurationSection playerSection = stakesSection.createSection(playerUuid);
            
            for (Map.Entry<String, StakeInfo> stakeEntry : playerEntry.getValue().entrySet()) {
                String currencyId = stakeEntry.getKey();
                StakeInfo stakeInfo = stakeEntry.getValue();
                
                ConfigurationSection currencySection = playerSection.createSection(currencyId);
                currencySection.set("amount", stakeInfo.amount);
                currencySection.set("start-date", stakeInfo.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                currencySection.set("end-date", stakeInfo.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        
        try {
            stakingConfig.save(stakingFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save staking.yml", e);
        }
    }
    
    /**
     * Schedule periodic rewards for stakes
     */
    private void scheduleRewards() {
        // Run once per hour
        long ticks = 20 * 60 * 60;
        
        rewardTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processRewards, 
            100L, ticks);
    }
    
    /**
     * Process rewards for all active stakes
     */
    private void processRewards() {
        LocalDateTime now = LocalDateTime.now();
        List<StakeReward> pendingRewards = new ArrayList<>();
        
        // First pass - identify matured stakes and collect rewards
        for (Map.Entry<UUID, Map<String, StakeInfo>> playerEntry : playerStakes.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<String, StakeInfo> playerStakes = playerEntry.getValue();
            
            Iterator<Map.Entry<String, StakeInfo>> stakeIterator = playerStakes.entrySet().iterator();
            while (stakeIterator.hasNext()) {
                Map.Entry<String, StakeInfo> stakeEntry = stakeIterator.next();
                String currencyId = stakeEntry.getKey();
                StakeInfo stakeInfo = stakeEntry.getValue();
                
                // Check if stake has matured
                if (now.isAfter(stakeInfo.endDate) || now.isEqual(stakeInfo.endDate)) {
                    // Stake has matured, calculate final rewards
                    double principal = stakeInfo.amount;
                    double apy = stakingRates.getOrDefault(currencyId, 5.0) / 100.0; // Convert percentage to decimal
                    
                    // Calculate actual time staked in days
                    double daysStaked = Duration.between(stakeInfo.startDate, now).toDays();
                    double yearFraction = daysStaked / 365.0;
                    
                    // Calculate rewards using compound interest formula: A = P(1 + r)^t
                    double totalValue = principal * Math.pow(1 + apy, yearFraction);
                    double reward = totalValue - principal;
                    
                    // Add to pending rewards
                    pendingRewards.add(new StakeReward(playerId, currencyId, principal, reward));
                    
                    // Remove the matured stake
                    stakeIterator.remove();
                }
            }
            
            // Remove player from map if they have no more stakes
            if (playerStakes.isEmpty()) {
                this.playerStakes.remove(playerId);
            }
        }
        
        // Second pass - process rewards synchronously
        if (!pendingRewards.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (StakeReward reward : pendingRewards) {
                    processStakeReward(reward);
                }
            });
        }
        
        // Save data after processing
        saveData();
    }
    
    /**
     * Process a single stake reward
     */
    private void processStakeReward(StakeReward reward) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(reward.playerId);
        
        // Return principal plus rewards to player's crypto balance
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        double currentAmount = investments.getOrDefault(reward.currencyId, 0.0);
        double newAmount = currentAmount + reward.principal + reward.reward;
        
        // Update player's investments in CryptoManager
        investments.put(reward.currencyId, newAmount);
        cryptoManager.updatePlayerInvestments(player.getUniqueId(), investments);
        
        // Notify player if online
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(reward.currencyId);
            if (crypto != null) {
                String symbol = crypto.getSymbol();
                onlinePlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&aStaking selesai! Anda menerima &f" + 
                    String.format("%.6f", reward.principal) + " " + symbol + 
                    " &aditambah &f" + String.format("%.6f", reward.reward) + " " + symbol + 
                    " &asebagai reward staking."));
            }
        }
    }
    
    /**
     * Stake crypto for a player
     * @param player Player who wants to stake
     * @param currencyId Currency to stake
     * @param amount Amount to stake
     * @return true if staking successful
     */
    public boolean stakeCrypto(Player player, String currencyId, double amount) {
        // Check if player already has a stake for this currency
        Map<String, StakeInfo> playerStakes = this.playerStakes.computeIfAbsent(
            player.getUniqueId(), k -> new HashMap<>());
        
        if (playerStakes.containsKey(currencyId)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda sudah memiliki stake untuk " + 
                currencyId + ". Tunggu hingga stake saat ini selesai."));
            return false;
        }
        
        // Get minimum stake amount
        double minAmount = minimumStakes.getOrDefault(currencyId, 0.001);
        if (amount < minAmount) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah minimum untuk staking " + 
                currencyId + " adalah &f" + minAmount));
            return false;
        }
        
        // Check if player has enough crypto
        if (cryptoManager.getPlayerInvestment(player, currencyId) < amount) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki cukup " + 
                currencyId + " untuk staking."));
            return false;
        }
        
        // Get lock period in days
        int lockPeriod = stakingPeriods.getOrDefault(currencyId, 7);
        
        // Create stake info
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = startDate.plusDays(lockPeriod);
        StakeInfo stakeInfo = new StakeInfo(amount, startDate, endDate);
        playerStakes.put(currencyId, stakeInfo);
        
        // Remove staked amount from player's available funds
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        double currentAmount = investments.getOrDefault(currencyId, 0.0);
        investments.put(currencyId, currentAmount - amount);
        cryptoManager.updatePlayerInvestments(player.getUniqueId(), investments);
        
        // Format dates for display
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        
        // Get APY
        double apy = stakingRates.getOrDefault(currencyId, 5.0);
        
        // Notify player
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        String symbol = crypto != null ? crypto.getSymbol() : currencyId.toUpperCase();
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&b&lSTAKING BERHASIL"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&fCrypto: &b" + symbol));
        player.sendMessage(ColorUtils.colorize("&fJumlah: &b" + String.format("%.6f", amount)));
        player.sendMessage(ColorUtils.colorize("&fAPY: &b" + String.format("%.2f", apy) + "%"));
        player.sendMessage(ColorUtils.colorize("&fPeriode Lock: &b" + lockPeriod + " hari"));
        player.sendMessage(ColorUtils.colorize("&fMulai: &b" + startDate.format(formatter)));
        player.sendMessage(ColorUtils.colorize("&fSelesai: &b" + endDate.format(formatter)));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Save staking data
        saveData();
        
        return true;
    }
    
    /**
     * Get all stakes for a player
     * @param playerId Player UUID
     * @return Map of currency ID to stake info
     */
    public Map<String, StakeInfo> getPlayerStakes(UUID playerId) {
        return playerStakes.getOrDefault(playerId, new HashMap<>());
    }
    
    /**
     * Get staking rate (APY) for a currency
     * @param currencyId Currency ID
     * @return APY rate (percentage)
     */
    public double getStakingRate(String currencyId) {
        return stakingRates.getOrDefault(currencyId, 5.0);
    }
    
    /**
     * Get lock period for a currency
     * @param currencyId Currency ID
     * @return Lock period in days
     */
    public int getLockPeriod(String currencyId) {
        return stakingPeriods.getOrDefault(currencyId, 7);
    }
    
    /**
     * Get minimum stake amount for a currency
     * @param currencyId Currency ID
     * @return Minimum stake amount
     */
    public double getMinimumStake(String currencyId) {
        return minimumStakes.getOrDefault(currencyId, 0.001);
    }
    
    /**
     * Cleanup on plugin disable
     */
    public void cleanup() {
        if (rewardTask != null) {
            rewardTask.cancel();
        }
        saveData();
    }
    
    /**
     * Class to hold stake information
     */
    public static class StakeInfo {
        private final double amount;
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        
        public StakeInfo(double amount, LocalDateTime startDate, LocalDateTime endDate) {
            this.amount = amount;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        public double getAmount() {
            return amount;
        }
        
        public LocalDateTime getStartDate() {
            return startDate;
        }
        
        public LocalDateTime getEndDate() {
            return endDate;
        }
        
        /**
         * Get remaining time in days
         */
        public long getRemainingDays() {
            return Math.max(0, Duration.between(LocalDateTime.now(), endDate).toDays());
        }
    }
    
    /**
     * Class to hold reward information
     */
    private static class StakeReward {
        private final UUID playerId;
        private final String currencyId;
        private final double principal;
        private final double reward;
        
        public StakeReward(UUID playerId, String currencyId, double principal, double reward) {
            this.playerId = playerId;
            this.currencyId = currencyId;
            this.principal = principal;
            this.reward = reward;
        }
    }
}