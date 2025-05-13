package id.nusacore.listeners;

import id.nusacore.NusaCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class EconomyListener implements Listener {
    
    private final NusaCore plugin;
    
    public EconomyListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isEconomyEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Set starting balance for first time players
        if (!player.hasPlayedBefore()) {
            double startingBalance = plugin.getConfig().getDouble("economy.starting-balance", 1000.0);
            
            // Only set balance if we're using our implementation
            if (plugin.getEconomy() == null) {
                plugin.getEconomyManager().setBalance(player, startingBalance);
            }
        }
    }
}