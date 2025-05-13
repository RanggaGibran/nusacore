package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankupManager {
    
    private final NusaCore plugin;
    private final Map<UUID, String> playerRanks = new HashMap<>();
    private FileConfiguration ranksConfig;
    private File ranksFile;
    
    // Settings
    private String prefix;
    private String rankupMessage;
    private String notEnoughMoneyMessage;
    private String maxRankMessage;
    private String placeholderFormat;
    
    public RankupManager(NusaCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * Muat konfigurasi ranks dari file
     */
    public void loadConfig() {
        try {
            if (ranksFile == null) {
                ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
            }
            
            if (!ranksFile.exists()) {
                plugin.saveResource("ranks.yml", false);
                plugin.getLogger().info("Created new ranks.yml file");
            }
            
            ranksConfig = YamlConfiguration.loadConfiguration(ranksFile);
            plugin.getLogger().info("Loaded ranks configuration from " + ranksFile.getName());
            
            // Load settings
            ConfigurationSection settings = ranksConfig.getConfigurationSection("settings");
            if (settings != null) {
                prefix = ColorUtils.colorize(settings.getString("prefix", "&8[&7Rank&8] "));
                rankupMessage = ColorUtils.colorize(settings.getString("rankup-message", "&aSelamat! Anda telah naik ke rank {rank}!"));
                notEnoughMoneyMessage = ColorUtils.colorize(settings.getString("not-enough-money-message", 
                        "&cAnda membutuhkan {money} untuk naik ke rank berikutnya."));
                maxRankMessage = ColorUtils.colorize(settings.getString("max-rank-message", "&eAnda sudah mencapai rank tertinggi!"));
                placeholderFormat = ColorUtils.colorize(settings.getString("placeholder-format", "&7[{rank_name}&7]"));
                
                plugin.getLogger().info("Loaded rank settings: prefix, messages, and placeholders");
            }
            
            // Load player ranks
            loadPlayerRanks();
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading ranks configuration: " + e.getMessage());
        }
    }
    
    /**
     * Muat rank pemain dari file
     */
    private void loadPlayerRanks() {
        File playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        
        // Clear existing data
        playerRanks.clear();
        
        // Load player data for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            String rank = loadPlayerRank(player.getUniqueId());
            if (rank == null) {
                // Set default rank for new players
                rank = "I";
                savePlayerRank(player.getUniqueId(), rank);
            }
            playerRanks.put(player.getUniqueId(), rank);
        }
    }
    
    /**
     * Load rank pemain dari file
     */
    private String loadPlayerRank(UUID uuid) {
        File playerFile = new File(plugin.getDataFolder() + "/playerdata", uuid.toString() + ".yml");
        if (!playerFile.exists()) {
            return null;
        }
        
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        return playerConfig.getString("rank", "I");
    }
    
    /**
     * Simpan rank pemain ke file
     */
    private void savePlayerRank(UUID uuid, String rank) {
        File playerFile = new File(plugin.getDataFolder() + "/playerdata", uuid.toString() + ".yml");
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        
        playerConfig.set("rank", rank);
        playerConfig.set("last-updated", System.currentTimeMillis());
        
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Tidak dapat menyimpan data rank pemain: " + e.getMessage());
        }
    }
    
    /**
     * Dapatkan rank pemain
     * @param player Pemain
     * @return Rank pemain
     */
    public String getPlayerRank(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!playerRanks.containsKey(uuid)) {
            String rank = loadPlayerRank(uuid);
            if (rank == null) {
                rank = "I";  // Default rank
                savePlayerRank(uuid, rank);
            }
            playerRanks.put(uuid, rank);
        }
        
        return playerRanks.get(uuid);
    }
    
    /**
     * Set rank pemain
     * @param player Pemain
     * @param rank Rank baru
     */
    public void setPlayerRank(Player player, String rank) {
        UUID uuid = player.getUniqueId();
        playerRanks.put(uuid, rank);
        savePlayerRank(uuid, rank);
        
        // Execute commands for this rank
        executeRankCommands(player, rank);
    }
    
    /**
     * Eksekusi perintah saat naik rank
     */
    private void executeRankCommands(Player player, String rank) {
        List<String> commands = ranksConfig.getStringList("ranks." + rank + ".commands");
        if (commands == null) return;
        
        for (String command : commands) {
            command = command.replace("{player}", player.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
        }
    }
    
    /**
     * Cek apakah pemain memenuhi syarat untuk naik rank
     * @param player Pemain
     * @return true jika pemain memenuhi syarat
     */
    public boolean canRankUp(Player player) {
        String currentRank = getPlayerRank(player);
        String nextRank = ranksConfig.getString("ranks." + currentRank + ".next-rank");
        
        // Jika sudah rank tertinggi
        if (nextRank == null || nextRank.isEmpty()) {
            return false;
        }
        
        // Cek playtime requirement
        int requiredHours = ranksConfig.getInt("ranks." + nextRank + ".playtime-required", 0);
        int playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int playedHours = playedTicks / (20 * 60 * 60);  // Convert ticks to hours
        
        if (playedHours < requiredHours) {
            return false;
        }
        
        // Cek money requirement
        if (plugin.isEconomyEnabled()) {
            double requiredMoney = ranksConfig.getDouble("ranks." + nextRank + ".cost", 0);
            double playerMoney = plugin.getEconomyManager().getBalance(player);
            
            return playerMoney >= requiredMoney;
        }
        
        return true;
    }
    
    /**
     * Dapatkan biaya untuk naik ke rank berikutnya
     */
    public double getNextRankCost(Player player) {
        String currentRank = getPlayerRank(player);
        String nextRank = ranksConfig.getString("ranks." + currentRank + ".next-rank");
        
        if (nextRank == null || nextRank.isEmpty()) {
            return 0;
        }
        
        return ranksConfig.getDouble("ranks." + nextRank + ".cost", 0);
    }
    
    /**
     * Naik rank
     * @param player Pemain
     * @return true jika berhasil naik rank
     */
    public boolean rankUp(Player player) {
        String currentRank = getPlayerRank(player);
        String nextRank = ranksConfig.getString("ranks." + currentRank + ".next-rank");
        
        // Cek jika sudah rank terakhir
        if (nextRank == null || nextRank.isEmpty()) {
            player.sendMessage(prefix + maxRankMessage);
            return false;
        }
        
        // Cek playtime requirement
        int requiredHours = ranksConfig.getInt("ranks." + nextRank + ".playtime-required", 0);
        int playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int playedHours = playedTicks / (20 * 60 * 60);  // Convert ticks to hours
        
        if (playedHours < requiredHours) {
            player.sendMessage(prefix + ColorUtils.colorize("&cAnda membutuhkan " + requiredHours + 
                    " jam playtime untuk naik ke rank berikutnya. (Saat ini: " + playedHours + " jam)"));
            return false;
        }
        
        // Cek money requirement
        if (plugin.isEconomyEnabled()) {
            double requiredMoney = ranksConfig.getDouble("ranks." + nextRank + ".cost", 0);
            double playerMoney = plugin.getEconomyManager().getBalance(player);
            
            if (playerMoney < requiredMoney) {
                player.sendMessage(prefix + notEnoughMoneyMessage.replace("{money}", 
                        plugin.getEconomyManager().formatAmount(requiredMoney)));
                return false;
            }
            
            // Kurangi uang pemain
            plugin.getEconomyManager().removeBalance(player, requiredMoney);
        }
        
        // Set rank baru
        setPlayerRank(player, nextRank);
        
        // Beri tahu pemain
        String displayName = ColorUtils.colorize(ranksConfig.getString("ranks." + nextRank + ".display-name", nextRank));
        player.sendMessage(prefix + rankupMessage.replace("{rank}", displayName));
        
        // Efek visual dan suara
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        // Broadcast ke server
        Bukkit.broadcastMessage(prefix + ColorUtils.colorize("&e" + player.getName() + 
                " &7telah naik ke rank &e" + displayName + "&7!"));
        
        return true;
    }
    
    /**
     * Format placeholder untuk rank
     */
    public String formatRankPlaceholder(Player player) {
        String rank = getPlayerRank(player);
        String rankName = ranksConfig.getString("ranks." + rank + ".name", rank);
        
        return placeholderFormat.replace("{rank_name}", rankName);
    }
    
    /**
     * Dapatkan informasi perks untuk rank tertentu
     */
    public List<String> getRankPerks(String rank) {
        List<String> perks = ranksConfig.getStringList("ranks." + rank + ".perks");
        List<String> coloredPerks = new ArrayList<>();
        
        for (String perk : perks) {
            coloredPerks.add(ColorUtils.colorize("&7- " + perk));
        }
        
        return coloredPerks;
    }
    
    /**
     * Dapatkan prefix rank
     */
    public String getRankPrefix(Player player) {
        String rank = getPlayerRank(player);
        return ColorUtils.colorize(ranksConfig.getString("ranks." + rank + ".prefix", ""));
    }
    
    /**
     * Dapatkan warna rank
     */
    public String getRankColor(Player player) {
        String rank = getPlayerRank(player);
        return ColorUtils.colorize(ranksConfig.getString("ranks." + rank + ".color", "&7"));
    }
    
    /**
     * Muat rank pemain saat login
     */
    public void loadPlayerRankOnJoin(Player player) {
        String rank = loadPlayerRank(player.getUniqueId());
        if (rank == null) {
            rank = "I";  // Default rank
            savePlayerRank(player.getUniqueId(), rank);
        }
        playerRanks.put(player.getUniqueId(), rank);
    }
    
    /**
     * Hapus rank pemain dari cache saat logout
     */
    public void unloadPlayerRank(Player player) {
        playerRanks.remove(player.getUniqueId());
    }
    
    /**
     * Reload semua cache rank untuk pemain online
     */
    public void reloadOnlinePlayersRank() {
        try {
            int reloadedCount = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                String rank = loadPlayerRank(uuid);
                if (rank != null) {
                    playerRanks.put(uuid, rank);
                    reloadedCount++;
                }
            }
            plugin.getLogger().info("Reloaded ranks for " + reloadedCount + " online players");
        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading player ranks: " + e.getMessage());
        }
    }
    
    /**
     * Dapatkan konfigurasi ranks
     */
    public FileConfiguration getRanksConfig() {
        return ranksConfig;
    }

    /**
     * Dapatkan display name untuk rank tertentu
     */
    public String getDisplayName(String rankId) {
        if (ranksConfig == null) return rankId;
        return ColorUtils.colorize(ranksConfig.getString("ranks." + rankId + ".display-name", rankId));
    }

    /**
     * Dapatkan biaya untuk rank tertentu
     */
    public double getRankCost(String rankId) {
        if (ranksConfig == null) return 0;
        return ranksConfig.getDouble("ranks." + rankId + ".cost", 0);
    }

    /**
     * Dapatkan playtime requirement untuk rank tertentu
     */
    public int getPlaytimeRequired(String rankId) {
        if (ranksConfig == null) return 0;
        return ranksConfig.getInt("ranks." + rankId + ".playtime-required", 0);
    }

    /**
     * Dapatkan rank berikutnya dari rank tertentu
     */
    public String getNextRank(String rankId) {
        if (ranksConfig == null) return "";
        return ranksConfig.getString("ranks." + rankId + ".next-rank", "");
    }
}