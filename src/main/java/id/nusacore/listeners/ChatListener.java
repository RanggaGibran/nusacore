package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.utils.ChatFormatter;
import id.nusacore.utils.ColorUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ChatListener implements Listener {

    private final NusaCore plugin;
    private final ChatFormatter chatFormatter;
    private boolean enabled;
    
    public ChatListener(NusaCore plugin) {
        this.plugin = plugin;
        this.chatFormatter = new ChatFormatter(plugin);
        this.enabled = plugin.getConfig().getBoolean("chat.enabled", true);
    }
    
    public void reloadConfig() {
        // Cek apakah menggunakan format chat custom
        enabled = plugin.getConfig().getBoolean("chat.enabled", true);
        // Reload format pada ChatFormatter
        chatFormatter.reloadConfig();
        
        plugin.getLogger().info("Chat listener configuration reloaded");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        if (plugin.getConfig().getBoolean("chat.discord-integration", true)) {
            // Ambil format dari config
            String format = plugin.getConfig().getString("chat.format", "%player_name% &8» &f%message%");

            // Proses PlaceholderAPI jika tersedia
            if (plugin.hasPlaceholderAPI()) {
                format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, format);
            }

            // Ganti %player_name% dan %message% dengan format Java
            format = format.replace("%player_name%", "%1$s").replace("%message%", "%2$s");

            // Proses warna (jika perlu)
            format = id.nusacore.utils.ColorUtils.colorize(format);

            event.setFormat(format);
            return;
        }

        // Format pesan
        final var formattedMessage = chatFormatter.formatMessage(player, message);

        // Batalkan event asli jika integrasi discord dimatikan
        event.setCancelled(true);
        
        // Karena chat event asinkron, jalankan di thread utama untuk mengirim komponen ke semua pemain
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Kirim ke semua pemain dan konsol
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                recipient.spigot().sendMessage(formattedMessage);
            }
            
            // Log pesan ke konsol (tanpa format warna)
            plugin.getLogger().info(ChatColor.stripColor(formattedMessage.toLegacyText()));
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCryptoTransactionInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if player has pending crypto input
        String pendingBuy = (String) plugin.getPlayerDataManager().getTempData(playerId, "crypto_buy_pending");
        String pendingSell = (String) plugin.getPlayerDataManager().getTempData(playerId, "crypto_sell_pending");
        
        if (pendingBuy != null || pendingSell != null) {
            String message = event.getMessage().trim();
            
            // Cancel if player wants to cancel the transaction
            if (message.equalsIgnoreCase("batal")) {
                plugin.getPlayerDataManager().removeTempData(playerId, "crypto_buy_pending");
                plugin.getPlayerDataManager().removeTempData(playerId, "crypto_sell_pending");
                
                // Cancel event and send message
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTransaksi dibatalkan."));
                return;
            }
            
            // Try to parse amount
            try {
                // For buying, we expect token amount
                if (pendingBuy != null) {
                    int amount;
                    try {
                        amount = Integer.parseInt(message);
                        // Tambahkan validasi maximum
                        if (amount > 1000000) {
                            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah maksimum adalah 1.000.000 token."));
                            return;
                        }
                        if (amount <= 0) {
                            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0."));
                        } else {
                            // Process buy with amount and crypto ID
                            plugin.getCryptoManager().buyCrypto(player, pendingBuy, amount);
                        }
                        
                        // Clear pending data
                        plugin.getPlayerDataManager().removeTempData(playerId, "crypto_buy_pending");
                        event.setCancelled(true);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFormat angka tidak valid. Transaksi dibatalkan."));
                        plugin.getPlayerDataManager().removeTempData(playerId, "crypto_buy_pending");
                        plugin.getPlayerDataManager().removeTempData(playerId, "crypto_sell_pending");
                        event.setCancelled(true);
                    }
                }
                // For selling, we expect crypto amount
                else if (pendingSell != null) {
                    double amount = Double.parseDouble(message);
                    if (amount <= 0) {
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0."));
                    } else {
                        // Process sell with amount and crypto ID
                        plugin.getCryptoManager().sellCrypto(player, pendingSell, amount);
                    }
                    
                    // Clear pending data
                    plugin.getPlayerDataManager().removeTempData(playerId, "crypto_sell_pending");
                    event.setCancelled(true);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFormat angka tidak valid. Transaksi dibatalkan."));
                plugin.getPlayerDataManager().removeTempData(playerId, "crypto_buy_pending");
                plugin.getPlayerDataManager().removeTempData(playerId, "crypto_sell_pending");
                event.setCancelled(true);
            }
        }
    }
}