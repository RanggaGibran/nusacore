package id.nusacore.listeners;

import id.nusacore.NusaCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class AFKListener implements Listener {

    private final NusaCore plugin;
    
    public AFKListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip jika hanya menoleh (bukan perpindahan posisi)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() 
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        plugin.getAFKManager().updateActivity(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        plugin.getAFKManager().updateActivity(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        plugin.getAFKManager().updateActivity(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Don't count /afk command for activity
        if (!event.getMessage().toLowerCase().startsWith("/afk ") && 
            !event.getMessage().equalsIgnoreCase("/afk")) {
            
            Player player = event.getPlayer();
            plugin.getAFKManager().updateActivity(player);
        }
    }
}