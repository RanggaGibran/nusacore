package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.gui.RankGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class GUIListener implements Listener {

    private final NusaCore plugin;
    private final RankGUI rankGUI;
    
    // Map untuk pemetaan nama item ke ID rank
    private final Map<String, String> displayNameToRankId = new HashMap<>();
    
    public GUIListener(NusaCore plugin) {
        this.plugin = plugin;
        this.rankGUI = new RankGUI(plugin);
        
        // Inisialisasi map display name ke rank ID
        initializeRankMapping();
    }
    
    /**
     * Menginisialisasi mapping dari display name ke rank ID
     * Metode ini bisa dipanggil ulang saat konfigurasi di-reload
     */
    public void initializeRankMapping() {
        try {
            // Bersihkan mapping yang ada
            displayNameToRankId.clear();
            
            // Bangun ulang mapping dengan data terbaru
            if (plugin.getRankManager() != null) {
                int mappedCount = 0;
                for (String rankId : plugin.getRankManager().getRankIds()) {
                    Map<String, Object> rankData = plugin.getRankManager().getRankData(rankId);
                    String displayName = (String) rankData.get("display-name");
                    displayNameToRankId.put(displayName, rankId);
                    mappedCount++;
                }
                plugin.getLogger().info("Reinitialized " + mappedCount + " rank GUI mappings");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error initializing rank GUI mappings: " + e.getMessage());
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        
        // Cek apakah inventory adalah GUI Rank
        if (title.contains(plugin.getRankManager().getTitle()) || 
            title.startsWith("§8Rank: ")) {
            
            event.setCancelled(true); // Mencegah pemain mengambil item dari GUI
            
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }
            
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            
            // Tombol tutup
            if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }
            
            // Tombol kembali
            if (clickedItem.getType() == Material.ARROW) {
                // Cek posisi klik untuk menentukan aksi (kembali atau navigasi halaman)
                int slot = event.getSlot();
                
                if (slot == 36) {
                    // Kembali ke menu utama
                    rankGUI.openMainMenu(player);
                } else if (slot == 39) {
                    // Halaman sebelumnya
                    rankGUI.previousPage(player);
                } else if (slot == 41) {
                    // Halaman berikutnya
                    rankGUI.nextPage(player);
                }
                return;
            }
            
            // Tombol beli
            if (clickedItem.getType() == Material.GOLD_INGOT) {
                String purchaseLink = plugin.getRankManager().getPurchaseLink();
                player.closeInventory();
                player.sendMessage("§aUntuk membeli rank, kunjungi: §e" + purchaseLink);
                
                try {
                    URI uri = new URI(purchaseLink);
                    player.spigot().sendMessage(
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§aKlik §e§lDI SINI §auntuk membuka link pembelian.")
                    );
                } catch (URISyntaxException e) {
                    plugin.getLogger().warning("Invalid purchase URL: " + purchaseLink);
                }
                return;
            }
            
            // Klik pada rank di menu utama
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                String rankId = displayNameToRankId.get(displayName);
                
                if (rankId != null) {
                    rankGUI.openRankDetails(player, rankId);
                }
            }
        }
        
        // Handle FPS Booster GUI
        if (title.contains("FPS Booster")) {
            event.setCancelled(true);
            
            if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
                plugin.getFPSBoosterCommand().getGUI().handleClick((Player) event.getWhoClicked(), event.getRawSlot());
            }
            
            return;
        }
    }
    
    // Tambahkan event listener untuk membersihkan data pagination
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith("§8Rank: ") && event.getPlayer() instanceof Player) {
            rankGUI.clearPaginationData((Player) event.getPlayer());
        }
    }
}