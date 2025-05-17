package id.nusacore.listeners;

import id.nusacore.NusaCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatGamesListener implements Listener {

    private final NusaCore plugin;
    
    public ChatGamesListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getChatGamesManager().isEnabled() || !plugin.getChatGamesManager().isGameActive()) {
            return;
        }
        
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Pindahkan ke sync thread untuk mengecek jawaban
        plugin.getServer().getScheduler().runTask(plugin, () -> 
            plugin.getChatGamesManager().checkAnswer(player, message));
    }
}