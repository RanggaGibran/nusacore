package id.nusacore.listeners;

import id.nusacore.NusaCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class TPAListener implements Listener {

    private final NusaCore plugin;
    
    public TPAListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Bersihkan permintaan teleport saat pemain keluar
        plugin.getTPAManager().clearRequests(player.getUniqueId());
    }
}