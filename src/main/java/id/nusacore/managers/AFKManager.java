package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AFKManager {

    private final NusaCore plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, String> afkReasons = new ConcurrentHashMap<>();
    private final Set<UUID> afkPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();
    private final String[] afkPatterns = {
        "&8▌&7▌&8▌&7▌&8▌&7▌&8▌&7▌&8▌",
        "&7▌&8▌&7▌&8▌&7▌&8▌&7▌&8▌&7▌"
    };
    private BukkitTask afkTask;
    private boolean autoAFKEnabled;
    private int autoAFKTime;
    private boolean afkImmunityToAttacks;
    private boolean afkNameTagPrefix;
    private boolean visualEffectsEnabled;
    private boolean glowingEffect;
    
    public AFKManager(NusaCore plugin) {
        this.plugin = plugin;
        loadConfig();
        startAfkChecker();
    }
    
    /**
     * Load AFK configuration
     */
    public void loadConfig() {
        // Default values if configuration not present
        autoAFKEnabled = plugin.getConfig().getBoolean("afk.auto-afk.enabled", true);
        autoAFKTime = plugin.getConfig().getInt("afk.auto-afk.time", 300); // 5 minutes default
        afkImmunityToAttacks = plugin.getConfig().getBoolean("afk.immunity-to-attacks", false);
        afkNameTagPrefix = plugin.getConfig().getBoolean("afk.name-tag-prefix", true);
        visualEffectsEnabled = plugin.getConfig().getBoolean("afk.visual-effects.enabled", true);
        glowingEffect = plugin.getConfig().getBoolean("afk.visual-effects.glowing", false);
    }
    
    /**
     * Start the auto-AFK checker task
     */
    private void startAfkChecker() {
        if (afkTask != null) {
            afkTask.cancel();
        }
        
        if (autoAFKEnabled) {
            afkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAutoAFK, 20L * 10, 20L * 10); // Check every 10 seconds
        }
    }
    
    /**
     * Check for players who should be auto-marked AFK
     */
    private void checkAutoAFK() {
        long currentTime = System.currentTimeMillis();
        long autoAfkMillis = autoAFKTime * 1000L;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            
            // Skip players already AFK
            if (isAFK(player)) continue;
            
            // Skip if player has permission to not be marked AFK
            if (player.hasPermission("nusatown.afk.exempt")) continue;
            
            // If player has been inactive
            if (lastActivity.containsKey(playerId) && currentTime - lastActivity.get(playerId) > autoAfkMillis) {
                // Set player to AFK automatically
                setAFK(player, null);
            }
        }
    }
    
    /**
     * Toggle a player's AFK status
     * 
     * @param player Player to toggle
     * @param reason Optional reason for going AFK
     */
    public void toggleAFK(Player player, String reason) {
        if (isAFK(player)) {
            removeAFK(player);
        } else {
            setAFK(player, reason);
        }
    }
    
    /**
     * Mark a player as AFK
     * 
     * @param player The player to mark
     * @param reason Optional reason message
     */
    public void setAFK(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        
        // If already AFK, do nothing
        if (afkPlayers.contains(playerId)) return;
        
        // Prevent going AFK in combat
        if (plugin.getCombatTagManager().isTagged(player)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat menggunakan AFK saat dalam combat!"));
            return;
        }
        
        // Add to AFK list and set metadata
        afkPlayers.add(playerId);
        player.setMetadata("afk", new FixedMetadataValue(plugin, true));
        lastLocations.put(playerId, player.getLocation().clone());
        
        // Set custom reason if provided
        if (reason != null && !reason.isEmpty()) {
            afkReasons.put(playerId, reason);
        }
        
        // Visual effects
        if (afkNameTagPrefix) {
            updateNameTag(player, true);
        }
        
        updatePlayerVisualEffects(player);
        
        // Send notifications
        String afkMessage;
        if (reason != null && !reason.isEmpty()) {
            afkMessage = ColorUtils.colorize(NusaCore.PREFIX + "&e" + player.getName() + 
                    " &7sekarang AFK: &f" + reason);
        } else {
            afkMessage = ColorUtils.colorize(NusaCore.PREFIX + "&e" + player.getName() + 
                    " &7sekarang AFK.");
        }
        
        Bukkit.broadcastMessage(afkMessage);
        
        // Create actionbar task
        startActionBarTask(player);
        
        // Check if player is in an AFK region immediately when going AFK
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAFK(player) && plugin.getAFKRegionManager() != null) {
                plugin.getAFKRegionManager().checkPlayerRegion(player);
            }
        }, 5L);
    }
    
    /**
     * Remove a player's AFK status
     * 
     * @param player The player to un-AFK
     */
    public void removeAFK(Player player) {
        UUID playerId = player.getUniqueId();
        
        // If not AFK, do nothing
        if (!afkPlayers.contains(playerId)) return;
        
        // Cancel action bar task if any
        if (actionBarTasks.containsKey(playerId)) {
            actionBarTasks.get(playerId).cancel();
            actionBarTasks.remove(playerId);
        }
        
        // Remove from AFK list and metadata
        afkPlayers.remove(playerId);
        player.removeMetadata("afk", plugin);
        lastActivity.put(playerId, System.currentTimeMillis());
        
        // Remove AFK reason
        afkReasons.remove(playerId);
        
        // Remove visual effects
        if (afkNameTagPrefix) {
            updateNameTag(player, false);
        }
        
        updatePlayerVisualEffects(player);
        
        // Send notification
        Bukkit.broadcastMessage(ColorUtils.colorize(NusaCore.PREFIX + "&e" + player.getName() + 
                " &7kembali dari AFK."));
    }
    
    /**
     * Update a player's nametag to show AFK status
     * 
     * @param player The player to update
     * @param isAfk Whether the player is AFK
     */
    private void updateNameTag(Player player, boolean isAfk) {
        if (isAfk) {
            // Use display name to show AFK status
            String prefix = plugin.getConfig().getString("afk.prefix", "&7[AFK] ");
            player.setDisplayName(ColorUtils.colorize(prefix + player.getName()));
            
            // Update tab list name too if configured
            if (plugin.getConfig().getBoolean("afk.tab-list-prefix", true)) {
                player.setPlayerListName(ColorUtils.colorize(prefix + player.getName()));
            }
        } else {
            // Reset to default
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }
    
    /**
     * Update visual effects untuk pemain AFK
     * @param player Pemain yang akan diupdate efeknya
     */
    private void updatePlayerVisualEffects(Player player) {
        // Hapus efek visual sebelumnya
        player.setGlowing(false);
        
        // Jika pemain dalam status AFK dan efek visual diaktifkan
        if (isAFK(player) && visualEffectsEnabled) {
            // Apply glowing effect jika diaktifkan
            if (glowingEffect) {
                player.setGlowing(true);
            }
        }
    }
    
    /**
     * Check if a player is AFK
     * 
     * @param player The player to check
     * @return true if the player is AFK
     */
    public boolean isAFK(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }
    
    /**
     * Update a player's last activity time
     * 
     * @param player The player who was active
     */
    public void updateActivity(Player player) {
        // Store current time as last activity
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Check if player moved and was AFK
        if (isAFK(player)) {
            // Jika player dalam region AFK dan maintain movement diizinkan, jangan hapus status AFK
            if (player.hasMetadata("afk_region_auto") && plugin.getAFKRegionManager().canMaintainMovement()) {
                // Tetap AFK meski bergerak dalam region AFK
                return;
            }
            
            // Player was AFK but is now active, remove AFK status
            removeAFK(player);
        }
    }
    
    /**
     * Start task to show AFK action bar message
     * @param player The player to show the action bar to
     */
    private void startActionBarTask(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Cancel existing task if any
        if (actionBarTasks.containsKey(playerId)) {
            actionBarTasks.get(playerId).cancel();
        }
        
        // Create new task
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            private int tick = 0;
            
            @Override
            public void run() {
                if (player.isOnline() && isAFK(player)) {
                    // Alternate patterns for animation effect
                    String pattern = afkPatterns[tick % 2];
                    
                    // Send action bar with AFK indicator
                    player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                            ColorUtils.colorize("&7[AFK] " + pattern + " &7[AFK]")
                        )
                    );
                    
                    tick++;
                } else {
                    // Player is offline or no longer AFK
                    BukkitTask task = actionBarTasks.remove(playerId);
                    if (task != null) {
                        task.cancel();
                    }
                }
            }
        }, 5L, 15L); // Run every 15 ticks (0.75 seconds)
        
        actionBarTasks.put(playerId, task);
    }
    
    /**
     * Clean up when plugin disables
     */
    public void cleanup() {
        if (afkTask != null) {
            afkTask.cancel();
            afkTask = null;
        }
        
        // Remove AFK status and effects from all players
        for (UUID playerId : new HashSet<>(afkPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeAFK(player);
            }
        }
        
        // Cancel all action bar tasks
        for (BukkitTask task : actionBarTasks.values()) {
            task.cancel();
        }
        actionBarTasks.clear();
    }
}