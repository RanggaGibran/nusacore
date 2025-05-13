package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.Sound;

public class TPToggleListener implements Listener {

    private final NusaCore plugin;
    
    public TPToggleListener(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        
        if (!title.equals(ColorUtils.colorize("&8Pengaturan Teleport"))) {
            return;
        }
        
        // Mencegah player mengambil item dari GUI
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (event.getCurrentItem() == null) {
            return;
        }
        
        int slot = event.getSlot();
        
        if (slot == 11) {
            // Tombol toggle menerima request
            boolean newState = plugin.getTPAManager().toggleRequestReception(player);
            
            player.playSound(player.getLocation(), 
                    newState ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 
                    1.0f, newState ? 1.0f : 0.5f);
                    
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Penerimaan permintaan teleport: " + 
                    (newState ? "&aAKTIF" : "&cNONAKTIF")));
            
            // Buka ulang GUI dengan status terbaru
            plugin.getTPAManager().openTPToggleGUI(player);
            
        } else if (slot == 15) {
            // Tombol toggle auto-accept
            boolean newState = plugin.getTPAManager().toggleAutoAccept(player);
            boolean canReceive = plugin.getTPAManager().canReceiveRequests(player);
            
            player.playSound(player.getLocation(), 
                    newState ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 
                    1.0f, newState ? 1.0f : 0.5f);
                    
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Auto-accept permintaan teleport: " + 
                    (newState ? "&aAKTIF" : "&cNONAKTIF")));
            
            // Tambahkan pesan peringatan jika auto-accept aktif tetapi penerimaan request nonaktif
            if (newState && !canReceive) {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&e⚠ Auto-accept tidak akan berfungsi karena Anda tidak menerima permintaan teleport."));
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&eAktifkan 'Menerima Permintaan' terlebih dahulu."));
            }
            
            // Buka ulang GUI dengan status terbaru
            plugin.getTPAManager().openTPToggleGUI(player);
        }
    }
}