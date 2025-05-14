package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
        
        // Handle jika ini adalah event pembelian rank dari admin
        if (event.getView().getTitle().contains("Konfirmasi Pemberian Rank")) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
                return;
            }
            
            Player player = (Player) event.getWhoClicked();
            
            // Handle berdasarkan item yang diklik
            if (event.getSlot() == 11) { // Tombol konfirmasi (hijau)
                // Ambil data rank dari NBT atau metadata item
                ItemStack item = event.getCurrentItem();
                ItemMeta meta = item.getItemMeta();
                
                if (meta != null && meta.getPersistentDataContainer().has(
                        new NamespacedKey(plugin, "rankId"), PersistentDataType.STRING)) {
                    
                    String rankId = meta.getPersistentDataContainer().get(
                            new NamespacedKey(plugin, "rankId"), PersistentDataType.STRING);
                    
                    String targetName = meta.getPersistentDataContainer().get(
                            new NamespacedKey(plugin, "targetPlayer"), PersistentDataType.STRING);
                    
                    Player targetPlayer = Bukkit.getPlayer(targetName);
                    
                    if (targetPlayer != null) {
                        // Execute commands untuk rank
                        plugin.getRankManager().executeRankCommands(targetPlayer, rankId);
                        
                        // Notifikasi
                        String displayName = (String) plugin.getRankManager().getRankData(rankId).get("display-name");
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aRank " + displayName + " &atelah diterapkan ke " + targetPlayer.getName()));
                        targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aAnda telah menerima rank " + displayName + "&a!"));
                        
                        // Efek
                        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
                
                player.closeInventory();
            } else if (event.getSlot() == 15) { // Tombol batal (merah)
                player.closeInventory();
            }
        }
    }
}