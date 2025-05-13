package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class RankGUIListener implements Listener {
    
    private final NusaCore plugin;
    
    public RankGUIListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("NusaTown Rank System")) {
            event.setCancelled(true);
            
            // Hanya proses jika yang klik adalah player
            if (!(event.getWhoClicked() instanceof Player)) return;
            
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            
            // Handle tombol close
            if (clickedItem.getType() == Material.BARRIER && 
                clickedItem.getItemMeta().getDisplayName().contains("Tutup")) {
                player.closeInventory();
                return;
            }
            
            // Handle tombol naik rank
            if (clickedItem.getType() == Material.EMERALD && 
                clickedItem.getItemMeta().getDisplayName().contains("NAIK RANK")) {
                player.closeInventory();
                
                // Execute rankup command
                plugin.getRankupManager().rankUp(player);
            }
        }
    }
}