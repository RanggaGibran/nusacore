package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankManager {

    private final NusaCore plugin;
    private FileConfiguration rankConfig;
    private File rankFile;
    
    private String title;
    private String header;
    private String footer;
    private String purchaseLink;
    
    @SuppressWarnings("unused")
    private String displayName; // Digunakan untuk fitur mendatang
    
    private final Map<String, Map<String, Object>> ranks;
    private Map<String, List<String>> rankCommands;

    public RankManager(NusaCore plugin) {
        this.plugin = plugin;
        this.ranks = new HashMap<>();
        this.rankCommands = new HashMap<>();
        loadConfig();
    }
    
    /**
     * Muat konfigurasi rank dari file
     */
    public void loadConfig() {
        try {
            if (rankFile == null) {
                rankFile = new File(plugin.getDataFolder(), "rank.yml");
            }
            
            if (!rankFile.exists()) {
                plugin.saveResource("rank.yml", false);
                plugin.getLogger().info("Created new rank.yml file");
            }
            
            rankConfig = YamlConfiguration.loadConfiguration(rankFile);
            plugin.getLogger().info("Loaded donation ranks configuration from " + rankFile.getName());
            
            // Load settings
            title = ColorUtils.colorize(rankConfig.getString("settings.title", "&6✦ &b&lDonation Ranks &6✦"));
            header = ColorUtils.colorize(rankConfig.getString("settings.header", "&7Dukung server dan dapatkan fitur eksklusif!"));
            footer = ColorUtils.colorize(rankConfig.getString("settings.footer", "&eKunjungi website untuk membeli rank"));
            purchaseLink = rankConfig.getString("settings.purchase-link", "https://store.nusatown.com");
            
            // Load ranks
            ranks.clear();
            rankCommands.clear(); // Clear commands map
            ConfigurationSection ranksSection = rankConfig.getConfigurationSection("ranks");
            
            if (ranksSection != null) {
                int rankCount = 0;
                for (String rankId : ranksSection.getKeys(false)) {
                    ConfigurationSection rankSection = ranksSection.getConfigurationSection(rankId);
                    
                    if (rankSection != null) {
                        Map<String, Object> rankData = new HashMap<>();
                        
                        rankData.put("id", rankId);
                        rankData.put("display-name", ColorUtils.colorize(rankSection.getString("display-name", rankId)));
                        rankData.put("cost", rankSection.getString("cost", "0"));
                        rankData.put("description", ColorUtils.colorize(rankSection.getString("description", "")));
                        rankData.put("color", ColorUtils.colorize(rankSection.getString("color", "&f")));
                        
                        // Load benefits
                        List<String> benefits = new ArrayList<>();
                        for (String benefit : rankSection.getStringList("benefits")) {
                            benefits.add(ColorUtils.colorize(benefit));
                        }
                        rankData.put("benefits", benefits);
                        
                        // Load commands (baru)
                        List<String> commands = rankSection.getStringList("commands");
                        rankCommands.put(rankId, commands);
                        
                        ranks.put(rankId, rankData);
                        rankCount++;
                    }
                }
                
                plugin.getLogger().info("Loaded " + rankCount + " donation ranks");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading donation ranks configuration: " + e.getMessage());
        }
    }
    
    /**
     * Simpan konfigurasi ke file
     */
    public void saveConfig() {
        try {
            rankConfig.save(rankFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Tidak dapat menyimpan konfigurasi rank: " + e.getMessage());
        }
    }
    
    /**
     * Dapatkan semua ID rank
     * @return List ID rank
     */
    public List<String> getRankIds() {
        return new ArrayList<>(ranks.keySet());
    }
    
    /**
     * Dapatkan data rank berdasarkan ID
     * @param rankId ID rank
     * @return Data rank
     */
    public Map<String, Object> getRankData(String rankId) {
        return ranks.getOrDefault(rankId, new HashMap<>());
    }
    
    /**
     * Cek apakah rank dengan ID tertentu ada
     * @param rankId ID rank
     * @return true jika rank ada
     */
    public boolean hasRank(String rankId) {
        return ranks.containsKey(rankId);
    }
    
    /**
     * Jalankan perintah untuk rank tertentu
     * @param player Pemain yang mendapatkan rank
     * @param rankId ID rank
     */
    public void executeRankCommands(Player player, String rankId) {
        List<String> commands = rankCommands.get(rankId);
        if (commands == null || commands.isEmpty()) {
            return;
        }
        
        for (String command : commands) {
            // Ganti placeholder dengan nama pemain
            command = command.replace("{player}", player.getName());
            
            // Jalankan perintah sebagai console
            plugin.getLogger().info("Executing rank command for " + player.getName() + ": " + command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
    
    /**
     * Ambil commands untuk rank tertentu (untuk melihat saja)
     * @param rankId ID rank
     * @return List perintah
     */
    public List<String> getRankCommands(String rankId) {
        return rankCommands.getOrDefault(rankId, new ArrayList<>());
    }
    
    // Getter untuk properti setting
    public String getTitle() {
        return title;
    }
    
    public String getHeader() {
        return header;
    }
    
    public String getFooter() {
        return footer;
    }
    
    public String getPurchaseLink() {
        return purchaseLink;
    }
}