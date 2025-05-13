package id.nusacore.listeners;

import id.nusacore.NusaCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommandInterceptListener implements Listener {

    private final NusaCore plugin;
    private final Set<String> interceptedCommands;
    
    public CommandInterceptListener(NusaCore plugin) {
        this.plugin = plugin;
        this.interceptedCommands = new HashSet<>();
        loadInterceptedCommands();
    }
    
    /**
     * Load intercepted commands from config
     */
    public void loadInterceptedCommands() {
        interceptedCommands.clear();
        
        // Add default intercepted commands
        interceptedCommands.addAll(Arrays.asList(
            "/bukkit:help", "/minecraft:help", "/plugins", "/pl", 
            "/bukkit:plugins", "/bukkit:pl", "/minecraft:plugins", 
            "/minecraft:pl", "/?", "/ver", "/version", "/about",
            "/bukkit:ver", "/bukkit:version", "/bukkit:about",
            "/minecraft:ver", "/minecraft:version", "/minecraft:about"
        ));
        
        // Add commands from config
        interceptedCommands.addAll(plugin.getConfig().getStringList("help.intercepted-commands"));
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        String command = event.getMessage().split(" ")[0].toLowerCase();
        
        // Check if command is intercepted
        if (interceptedCommands.contains(command)) {
            // Cancel the command
            event.setCancelled(true);
            
            // Redirect to help command
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.performCommand("help");
            });
        }
        
        // Handle /help or /? commands directly
        if (command.equals("/help") || command.equals("/?")) {
            // Check if it's the vanilla help command (not our custom one)
            if (!plugin.isCustomHelpEnabled()) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.performCommand("nusatown help");
                });
            }
        }
    }
}