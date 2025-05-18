package id.nusacore.managers;

import id.nusacore.NusaCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager implements Listener {
    private final NusaCore plugin;
    private final Map<UUID, Map<String, Object>> tempPlayerData = new ConcurrentHashMap<>();
    
    public PlayerDataManager(NusaCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Set temporary data for a player
     * @param playerId Player UUID
     * @param key Data key
     * @param value Data value
     */
    public void setTempData(UUID playerId, String key, Object value) {
        Map<String, Object> playerData = tempPlayerData.computeIfAbsent(playerId, k -> new HashMap<>());
        playerData.put(key, value);
    }
    
    /**
     * Get temporary data for a player
     * @param playerId Player UUID
     * @param key Data key
     * @return Data value or null if not found
     */
    public Object getTempData(UUID playerId, String key) {
        Map<String, Object> playerData = tempPlayerData.get(playerId);
        if (playerData != null) {
            return playerData.get(key);
        }
        return null;
    }
    
    /**
     * Remove temporary data for a player
     * @param playerId Player UUID
     * @param key Data key
     */
    public void removeTempData(UUID playerId, String key) {
        Map<String, Object> playerData = tempPlayerData.get(playerId);
        if (playerData != null) {
            playerData.remove(key);
            if (playerData.isEmpty()) {
                tempPlayerData.remove(playerId);
            }
        }
    }
    
    /**
     * Clean up player data when they leave
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tempPlayerData.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Clear all temporary data
     */
    public void clearAllTempData() {
        tempPlayerData.clear();
    }
}