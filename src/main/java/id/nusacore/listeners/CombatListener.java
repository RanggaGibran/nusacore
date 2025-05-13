package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatListener implements Listener {

    private final NusaCore plugin;
    
    public CombatListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Check if both entities are players
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            // Tag both players
            plugin.getCombatTagManager().tagPlayer(victim);
            plugin.getCombatTagManager().tagPlayer(attacker);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getCombatTagManager().isTagged(player) && 
            !plugin.getCombatTagManager().isCommandAllowed(event.getMessage())) {
            
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat menggunakan perintah saat dalam combat!"));
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Cek apakah pemain dalam combat saat keluar
        if (plugin.getCombatTagManager().isTagged(player)) {
            // Bunuh pemain sebelum mereka keluar
            player.setHealth(0);
            
            // Broadcast pesan ke server
            Bukkit.broadcastMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&c" + player.getName() + " &fkeluar saat dalam combat dan telah dibunuh!"));
        }
        
        // Remove the combat tag when player leaves
        plugin.getCombatTagManager().untagPlayer(player);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Remove combat tag on death
        plugin.getCombatTagManager().untagPlayer(player);
    }
}