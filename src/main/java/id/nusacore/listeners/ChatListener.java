package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.utils.ChatFormatter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

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
}