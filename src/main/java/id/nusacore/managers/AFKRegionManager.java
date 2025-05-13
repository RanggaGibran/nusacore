package id.nusacore.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AFKRegionManager {
    
    private final NusaCore plugin;
    private FileConfiguration config;
    private boolean enabled;
    private int checkInterval;
    private boolean debug;
    private boolean autoAFKOnEnter;
    private boolean maintainMovement;
    private final Map<String, AFKRegion> afkRegions = new HashMap<>();
    
    // Track players in afk regions and their reward timers
    private final Map<UUID, PlayerAFKData> playerAFKData = new ConcurrentHashMap<>();
    private BukkitTask rewardTask;
    
    public AFKRegionManager(NusaCore plugin) {
        this.plugin = plugin;
        loadConfig();
        if (enabled) {
            startRewardTask();
        }
    }
    
    /**
     * Load configuration from afk.yml
     */
    public void loadConfig() {
        File afkFile = new File(plugin.getDataFolder(), "afk.yml");
        
        // Create default if not exists
        if (!afkFile.exists()) {
            plugin.saveResource("afk.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(afkFile);
        
        // Load settings
        enabled = config.getBoolean("settings.enabled", true);
        checkInterval = config.getInt("settings.check-interval", 60);
        debug = config.getBoolean("settings.debug", false);
        autoAFKOnEnter = config.getBoolean("settings.auto-afk-on-enter", true);
        maintainMovement = config.getBoolean("settings.maintain-movement", true);
        
        // Load regions
        afkRegions.clear();
        ConfigurationSection regionsSection = config.getConfigurationSection("afk-regions");
        
        if (regionsSection != null) {
            for (String regionId : regionsSection.getKeys(false)) {
                String displayName = ColorUtils.colorize(regionsSection.getString(regionId + ".display-name", regionId));
                String worldName = regionsSection.getString(regionId + ".world", "world");
                List<String> commands = regionsSection.getStringList(regionId + ".commands");
                String permission = regionsSection.getString(regionId + ".permission", null);
                String message = regionsSection.getString(regionId + ".message", null);
                if (message != null) {
                    message = ColorUtils.colorize(message);
                }
                int minimumTime = regionsSection.getInt(regionId + ".minimum-time", 300);
                
                AFKRegion region = new AFKRegion(regionId, displayName, worldName, commands, permission, message, minimumTime);
                afkRegions.put(regionId, region);
            }
        }
        
        if (debug) {
            plugin.getLogger().info("Loaded " + afkRegions.size() + " AFK regions");
        }
    }
    
    /**
     * Start the periodic reward task
     */
    private void startRewardTask() {
        // Cancel existing task if any
        if (rewardTask != null) {
            rewardTask.cancel();
        }
        
        // Start new task
        rewardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndRewardPlayers, 20L, checkInterval * 20L);
    }
    
    /**
     * Check all online AFK players and give rewards if in AFK regions
     */
    private void checkAndRewardPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getAFKManager().isAFK(player)) {
                continue; // Skip non-AFK players
            }
            
            // Get current AFK region if player is in one
            String regionId = getPlayerAFKRegion(player);
            
            if (regionId != null) {
                AFKRegion region = afkRegions.get(regionId);
                
                if (region != null) {
                    // Update player's AFK data
                    PlayerAFKData data = playerAFKData.computeIfAbsent(
                            player.getUniqueId(), 
                            uuid -> new PlayerAFKData(regionId)
                    );
                    
                    // Check if player changed regions
                    if (!data.getRegionId().equals(regionId)) {
                        data.setRegionId(regionId);
                        data.setTimeInRegion(0);
                        sendRegionEnteredMessage(player, region);
                    }
                    
                    // Update time in region
                    data.incrementTimeInRegion(checkInterval);
                    
                    // Check if player has been in region long enough for reward
                    if (data.getTimeInRegion() >= region.getMinimumTime()) {
                        giveRegionRewards(player, region);
                    }
                }
            } else {
                // Player left AFK region
                PlayerAFKData data = playerAFKData.get(player.getUniqueId());
                if (data != null && data.getRegionId() != null) {
                    AFKRegion leftRegion = afkRegions.get(data.getRegionId());
                    if (leftRegion != null) {
                        sendRegionLeftMessage(player, leftRegion);
                    }
                    // Reset data
                    data.setRegionId(null);
                    data.setTimeInRegion(0);
                }
            }
        }
    }
    
    /**
     * Get the ID of the AFK region a player is in, or null if not in any
     * 
     * @param player The player to check
     * @return The region ID or null
     */
    private String getPlayerAFKRegion(Player player) {
        try {
            // Get WorldGuard container
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));
            
            if (regions == null) return null;
            
            // Get the player's location
            Location loc = player.getLocation();
            com.sk89q.worldedit.util.Location worldEditLoc = BukkitAdapter.adapt(loc);
            
            // Get regions at player's location 
            Set<ProtectedRegion> applicableRegions = regions.getApplicableRegions(
                    worldEditLoc.toVector().toBlockPoint()).getRegions();
            
            // Check if player is in any configured AFK region
            for (ProtectedRegion region : applicableRegions) {
                String regionId = region.getId();
                if (afkRegions.containsKey(regionId)) {
                    AFKRegion afkRegion = afkRegions.get(regionId);
                    
                    // Make sure player is in the correct world
                    if (player.getWorld().getName().equals(afkRegion.getWorldName())) {
                        return regionId;
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            if (debug) {
                plugin.getLogger().warning("Error checking player AFK region: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }
    
    /**
     * Check if a player is in an AFK region and update data accordingly
     * Called when a player goes AFK
     * 
     * @param player The player to check
     */
    public void checkPlayerRegion(Player player) {
        String regionId = getPlayerAFKRegion(player);
        
        if (regionId != null) {
            AFKRegion region = afkRegions.get(regionId);
            if (region != null) {
                // Update player's AFK data
                PlayerAFKData data = playerAFKData.computeIfAbsent(
                        player.getUniqueId(), 
                        uuid -> new PlayerAFKData(regionId)
                );
                
                data.setRegionId(regionId);
                data.setTimeInRegion(0);
                sendRegionEnteredMessage(player, region);
            }
        }
    }
    
    /**
     * Give rewards to a player for being AFK in a region
     * 
     * @param player The player to reward
     * @param region The AFK region
     */
    private void giveRegionRewards(Player player, AFKRegion region) {
        // Check permission
        if (region.getPermission() != null && !player.hasPermission(region.getPermission())) {
            String noPermMsg = config.getString("notifications.no-permission", "&cAnda tidak memiliki izin untuk mendapatkan reward di area ini.");
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + noPermMsg));
            return;
        }
        
        // Execute reward commands
        for (String cmd : region.getCommands()) {
            String command = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        
        // Send reward message if configured
        if (region.getMessage() != null && !region.getMessage().isEmpty()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + region.getMessage()));
        }
        
        if (debug) {
            plugin.getLogger().info("Gave AFK rewards to " + player.getName() + " for region " + region.getRegionId());
        }
    }
    
    /**
     * Send notification when player enters an AFK region
     * 
     * @param player The player
     * @param region The AFK region
     */
    private void sendRegionEnteredMessage(Player player, AFKRegion region) {
        String message = config.getString("notifications.entered-afk-region", "&aAnda memasuki area AFK: &e{region}&a. Tetap AFK untuk mendapatkan reward.");
        message = message.replace("{region}", region.getDisplayName());
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + message));
    }
    
    /**
     * Send notification when player leaves an AFK region
     * 
     * @param player The player
     * @param region The AFK region
     */
    private void sendRegionLeftMessage(Player player, AFKRegion region) {
        String message = config.getString("notifications.left-afk-region", "&cAnda meninggalkan area AFK: &e{region}&c.");
        message = message.replace("{region}", region.getDisplayName());
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + message));
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (rewardTask != null) {
            rewardTask.cancel();
            rewardTask = null;
        }
        
        playerAFKData.clear();
    }
    
    /**
     * Periksa apakah auto-AFK saat memasuki region diaktifkan
     * @return true jika fitur diaktifkan
     */
    public boolean isAutoAFKOnEnterEnabled() {
        return enabled && autoAFKOnEnter;
    }

    /**
     * Periksa apakah player tetap boleh bergerak dalam region AFK
     * @return true jika pergerakan diperbolehkan
     */
    public boolean canMaintainMovement() {
        return maintainMovement;
    }

    /**
     * Periksa apakah region ID terdaftar sebagai region AFK
     * @param regionId ID region yang diperiksa
     * @return true jika region terdaftar
     */
    public boolean hasAFKRegion(String regionId) {
        return afkRegions.containsKey(regionId);
    }
    
    /**
     * Class to represent an AFK region configuration
     */
    private static class AFKRegion {
        private final String regionId;
        private final String displayName;
        private final String worldName;
        private final List<String> commands;
        private final String permission;
        private final String message;
        private final int minimumTime;
        
        public AFKRegion(String regionId, String displayName, String worldName, 
                       List<String> commands, String permission, String message, int minimumTime) {
            this.regionId = regionId;
            this.displayName = displayName;
            this.worldName = worldName;
            this.commands = commands;
            this.permission = permission;
            this.message = message;
            this.minimumTime = minimumTime;
        }
        
        public String getRegionId() {
            return regionId;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getWorldName() {
            return worldName;
        }
        
        public List<String> getCommands() {
            return commands;
        }
        
        public String getPermission() {
            return permission;
        }
        
        public String getMessage() {
            return message;
        }
        
        public int getMinimumTime() {
            return minimumTime;
        }
    }
    
    /**
     * Class to store AFK data for a player
     */
    private static class PlayerAFKData {
        private String regionId;
        private int timeInRegion;
        
        public PlayerAFKData(String regionId) {
            this.regionId = regionId;
            this.timeInRegion = 0;
        }
        
        public String getRegionId() {
            return regionId;
        }
        
        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }
        
        public int getTimeInRegion() {
            return timeInRegion;
        }
        
        public void setTimeInRegion(int timeInRegion) {
            this.timeInRegion = timeInRegion;
        }
        
        public void incrementTimeInRegion(int seconds) {
            this.timeInRegion += seconds;
        }
    }
}