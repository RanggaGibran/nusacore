package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class CombatTagManager {

    private final NusaCore plugin;
    private final Map<UUID, Long> combatTags = new HashMap<>(); // player UUID -> timestamp when tag expires
    private final Map<UUID, BukkitTask> taskMap = new HashMap<>(); // For tracking expiry tasks
    private final Map<UUID, BossBar> bossBars = new HashMap<>(); // For visual countdown
    private final Set<String> commandWhitelist;
    private boolean enabled;
    private int duration;
    private boolean preventCommands;
    private boolean preventTeleport;
    private boolean punishCombatLog;
    private boolean saveInventoryOnCombatLog;
    private String combatEntryMessage;
    private String combatExpireMessage;
    private String combatLogMessage;

    public CombatTagManager(NusaCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("combat.enabled", true);
        this.duration = plugin.getConfig().getInt("combat.duration", 10);
        this.preventCommands = plugin.getConfig().getBoolean("combat.prevent-commands", true);
        this.preventTeleport = plugin.getConfig().getBoolean("combat.prevent-teleport", true);
        this.punishCombatLog = plugin.getConfig().getBoolean("combat.punish-combat-log", true);
        this.saveInventoryOnCombatLog = plugin.getConfig().getBoolean("combat.save-inventory-on-combat-log", false);
        this.commandWhitelist = new HashSet<>(plugin.getConfig().getStringList("combat.command-whitelist"));
        this.combatEntryMessage = ColorUtils.colorize(plugin.getConfig().getString("combat.combat-entry-message", 
                "&c⚔ Anda dalam combat! &7Tunggu {time} detik."));
        this.combatExpireMessage = ColorUtils.colorize(plugin.getConfig().getString("combat.combat-expire-message", 
                "&a✓ Anda sudah tidak dalam combat."));
        this.combatLogMessage = ColorUtils.colorize(plugin.getConfig().getString("combat.combat-log-message", 
                "&c{player} &fkeluar saat dalam combat dan telah dibunuh!"));
    }

    /**
     * Reload combat tag settings dari config.yml
     */
    public void reloadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        this.enabled = config.getBoolean("combat.enabled", true);
        this.duration = config.getInt("combat.duration", 10);
        this.preventCommands = config.getBoolean("combat.prevent-commands", true);
        this.preventTeleport = config.getBoolean("combat.prevent-teleport", true);
        this.punishCombatLog = config.getBoolean("combat.punish-combat-log", true);
        this.saveInventoryOnCombatLog = config.getBoolean("combat.save-inventory-on-combat-log", false);
        
        // Load whitelisted commands
        this.commandWhitelist.clear();
        this.commandWhitelist.addAll(config.getStringList("combat.command-whitelist"));
        
        // Update messages
        this.combatEntryMessage = ColorUtils.colorize(config.getString("combat.combat-entry-message", 
                "&c⚔ Anda dalam combat! &7Tunggu {time} detik."));
        this.combatExpireMessage = ColorUtils.colorize(config.getString("combat.combat-expire-message", 
                "&a✓ Anda sudah tidak dalam combat."));
        this.combatLogMessage = ColorUtils.colorize(config.getString("combat.combat-log-message", 
                "&c{player} &fkeluar saat dalam combat dan telah dibunuh!"));
    }

    /**
     * Tag a player as being in combat
     * @param player The player to tag
     */
    public void tagPlayer(Player player) {
        if (!enabled || player.hasPermission("nusatown.combat.bypass")) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long expireTime = System.currentTimeMillis() + (duration * 1000L);
        boolean wasTagged = combatTags.containsKey(playerId);
        
        // Update tag expiration
        combatTags.put(playerId, expireTime);
        
        // Send message only if player wasn't already tagged
        if (!wasTagged) {
            String message = combatEntryMessage.replace("{time}", String.valueOf(duration));
            player.sendMessage(NusaCore.PREFIX + message);
        }

        // Cancel existing task if any
        if (taskMap.containsKey(playerId)) {
            taskMap.get(playerId).cancel();
            taskMap.remove(playerId);
        }
        
        // Create or update boss bar
        createOrUpdateBossBar(player, duration);
        
        // Schedule expiration task
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (combatTags.containsKey(playerId) && combatTags.get(playerId) <= System.currentTimeMillis()) {
                untagPlayer(player);
            }
        }, duration * 20L);
        
        taskMap.put(playerId, task);
    }
    
    /**
     * Remove combat tag from player
     * @param player The player to untag
     */
    public void untagPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (combatTags.containsKey(playerId)) {
            combatTags.remove(playerId);
            
            if (player.isOnline()) {
                player.sendMessage(NusaCore.PREFIX + combatExpireMessage);
                
                // Remove boss bar
                if (bossBars.containsKey(playerId)) {
                    bossBars.get(playerId).removePlayer(player);
                    bossBars.remove(playerId);
                }
            }
            
            // Cancel task if exists
            if (taskMap.containsKey(playerId)) {
                taskMap.get(playerId).cancel();
                taskMap.remove(playerId);
            }
        }
    }
    
    /**
     * Check if a player is in combat
     * @param player The player to check
     * @return true if player is in combat
     */
    public boolean isTagged(Player player) {
        if (!enabled || player.hasPermission("nusatown.combat.bypass")) {
            return false;
        }
        
        UUID playerId = player.getUniqueId();
        
        if (combatTags.containsKey(playerId)) {
            long expireTime = combatTags.get(playerId);
            
            if (System.currentTimeMillis() > expireTime) {
                untagPlayer(player);
                return false;
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a command can be used during combat
     * @param command The command to check
     * @return true if command is allowed during combat
     */
    public boolean isCommandAllowed(String command) {
        if (!preventCommands) {
            return true;
        }
        
        // Extract base command without args
        String baseCommand = command.split(" ")[0].toLowerCase().replaceAll("/", "");
        return commandWhitelist.contains(baseCommand);
    }
    
    /**
     * Check if teleportation is prevented during combat
     * @return true if teleportation is blocked during combat
     */
    public boolean isTeleportPrevented() {
        return preventTeleport;
    }
    
    /**
     * Create or update the boss bar for combat countdown
     * @param player The player to show the boss bar to
     * @param seconds The seconds remaining
     */
    private void createOrUpdateBossBar(Player player, int seconds) {
        UUID playerId = player.getUniqueId();
        BossBar bar;
        
        if (bossBars.containsKey(playerId)) {
            bar = bossBars.get(playerId);
        } else {
            bar = Bukkit.createBossBar(
                ColorUtils.colorize("&c⚔ Combat: " + seconds + "s"),
                BarColor.RED,
                BarStyle.SOLID
            );
            bar.addPlayer(player);
            bossBars.put(playerId, bar);
            
            // Use a named class that holds its task reference
            BossBarUpdater updater = new BossBarUpdater(player, playerId, bar, seconds);
            updater.task = Bukkit.getScheduler().runTaskTimer(plugin, updater, 20L, 20L); // Update every second
        }
    }

    // Inner class for boss bar updates
    private class BossBarUpdater implements Runnable {
        private final Player player;
        private final UUID playerId;
        private final BossBar bar;
        private final int totalSeconds;
        private int remainingSeconds;
        public BukkitTask task;
        
        public BossBarUpdater(Player player, UUID playerId, BossBar bar, int seconds) {
            this.player = player;
            this.playerId = playerId;
            this.bar = bar;
            this.totalSeconds = seconds;
            this.remainingSeconds = seconds;
        }
        
        @Override
        public void run() {
            remainingSeconds--;
            if (remainingSeconds <= 0 || !player.isOnline() || !isTagged(player)) {
                if (bossBars.containsKey(playerId)) {
                    bossBars.get(playerId).removePlayer(player);
                    bossBars.remove(playerId);
                }
                
                // Cancel task directly
                if (task != null) {
                    task.cancel();
                }
                return;
            }
            
            double progress = (double) remainingSeconds / totalSeconds;
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            bar.setTitle(ColorUtils.colorize("&c⚔ Combat: " + remainingSeconds + "s"));
            
            // Change color based on time remaining
            if (remainingSeconds <= 3) {
                bar.setColor(BarColor.GREEN);
            } else if (remainingSeconds <= 5) {
                bar.setColor(BarColor.YELLOW);
            }
        }
    }
    
    /**
     * Clear all combat tags (e.g., on server shutdown or reload)
     */
    public void clearAllTags() {
        for (UUID playerId : new ArrayList<>(combatTags.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                untagPlayer(player);
            }
        }
        
        combatTags.clear();
        
        // Cancel all tasks
        for (BukkitTask task : taskMap.values()) {
            task.cancel();
        }
        taskMap.clear();
        
        // Remove all boss bars
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }
}