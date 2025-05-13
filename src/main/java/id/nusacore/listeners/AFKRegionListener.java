package id.nusacore.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import id.nusacore.NusaCore;
import id.nusacore.managers.AFKManager;
import id.nusacore.managers.AFKRegionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AFKRegionListener implements Listener {

    private final NusaCore plugin;
    private final AFKRegionManager afkRegionManager;
    private final AFKManager afkManager;
    private final Map<UUID, String> playerLastRegion = new HashMap<>();
    private final Map<UUID, Long> lastAFKCheck = new HashMap<>();
    
    private static final long CHECK_COOLDOWN = 500; // Milliseconds between region checks (optimize performance)
    
    public AFKRegionListener(NusaCore plugin) {
        this.plugin = plugin;
        this.afkRegionManager = plugin.getAFKRegionManager();
        this.afkManager = plugin.getAFKManager();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process if block position changed (optimize)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Performance optimization - don't check too frequently
        long currentTime = System.currentTimeMillis();
        if (lastAFKCheck.containsKey(playerId) && 
            currentTime - lastAFKCheck.get(playerId) < CHECK_COOLDOWN) {
            return;
        }
        lastAFKCheck.put(playerId, currentTime);
        
        // Process region change
        processRegionChange(player, event.getTo());
    }
    
    /**
     * Process player region change
     * @param player The player
     * @param location Current location
     */
    private void processRegionChange(Player player, Location location) {
        if (!afkRegionManager.isAutoAFKOnEnterEnabled()) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        String currentRegion = getAFKRegion(player);
        String lastRegion = playerLastRegion.get(playerId);
        
        // If entered new AFK region
        if (currentRegion != null && !currentRegion.equals(lastRegion)) {
            playerLastRegion.put(playerId, currentRegion);
            
            // Auto AFK the player if they're not already AFK
            if (!afkManager.isAFK(player)) {
                // Set metadata to indicate this is an auto-region AFK
                player.setMetadata("afk_region_auto", new FixedMetadataValue(plugin, currentRegion));
                afkManager.setAFK(player, "di zona AFK");
            }
        }
        // If left AFK region
        else if (currentRegion == null && lastRegion != null) {
            playerLastRegion.remove(playerId);
            
            // If player was auto-AFK'd by this region, un-AFK them
            if (player.hasMetadata("afk_region_auto") && 
                lastRegion.equals(player.getMetadata("afk_region_auto").get(0).asString())) {
                player.removeMetadata("afk_region_auto", plugin);
                afkManager.removeAFK(player);
            }
        }
    }
    
    /**
     * Get the AFK region ID at player's location
     * @param player The player
     * @return Region ID or null if not in an AFK region
     */
    private String getAFKRegion(Player player) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));
            
            if (regions == null) return null;
            
            Location loc = player.getLocation();
            com.sk89q.worldedit.util.Location worldEditLoc = BukkitAdapter.adapt(loc);
            
            Set<ProtectedRegion> applicableRegions = regions.getApplicableRegions(
                    worldEditLoc.toVector().toBlockPoint()).getRegions();
            
            // Check if player is in any configured AFK region
            for (ProtectedRegion region : applicableRegions) {
                String regionId = region.getId();
                if (afkRegionManager.hasAFKRegion(regionId)) {
                    return regionId;
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}