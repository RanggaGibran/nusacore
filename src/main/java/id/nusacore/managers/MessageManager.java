package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageManager implements Listener {
    
    private final NusaCore plugin;
    private final Map<UUID, UUID> lastMessageSender = new HashMap<>();
    
    // Format pesan
    private String messageSentFormat;
    private String messageReceivedFormat;
    private boolean notifySound;
    private boolean socialSpy;
    private final Map<UUID, Boolean> socialSpyEnabled = new HashMap<>();
    
    public MessageManager(NusaCore plugin) {
        this.plugin = plugin;
        loadConfig();
        
        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public void loadConfig() {
        messageSentFormat = ColorUtils.colorize(plugin.getConfig().getString(
            "messages.msg-sent", "&8[&7me &8» &7{receiver}&8] &f{message}"));
        messageReceivedFormat = ColorUtils.colorize(plugin.getConfig().getString(
            "messages.msg-received", "&8[&7{sender} &8» &7me&8] &f{message}"));
        notifySound = plugin.getConfig().getBoolean("messages.notify-sound", true);
        socialSpy = plugin.getConfig().getBoolean("messages.social-spy", true);
    }
    
    /**
     * Kirim pesan privat dari satu pemain ke pemain lain
     * @param sender Pemain pengirim
     * @param receiver Pemain penerima
     * @param message Pesan yang dikirim
     * @return true jika berhasil dikirim
     */
    public boolean sendMessage(Player sender, Player receiver, String message) {
        if (receiver == null || !receiver.isOnline()) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut tidak ditemukan atau sedang offline."));
            return false;
        }
        
        // Simpan pengirim terakhir untuk fitur reply
        lastMessageSender.put(receiver.getUniqueId(), sender.getUniqueId());
        
        // Format dan kirim pesan
        String sentMessage = messageSentFormat
            .replace("{receiver}", receiver.getName())
            .replace("{message}", message);
            
        String receivedMessage = messageReceivedFormat
            .replace("{sender}", sender.getName())
            .replace("{message}", message);
        
        sender.sendMessage(ColorUtils.colorize(sentMessage));
        receiver.sendMessage(ColorUtils.colorize(receivedMessage));
        
        // Mainkan suara notifikasi
        if (notifySound) {
            receiver.playSound(receiver.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1f);
        }
        
        // Social Spy - kirim ke admin yang mengaktifkan social spy
        if (socialSpy) {
            broadcastToSocialSpy(sender, receiver, message);
        }
        
        return true;
    }
    
    /**
     * Dapatkan pemain terakhir yang mengirim pesan ke pemain
     * @param player Pemain yang ingin mendapatkan pengirim terakhir
     * @return Player pengirim terakhir atau null jika tidak ada
     */
    public Player getLastMessageSender(Player player) {
        UUID lastSenderUUID = lastMessageSender.get(player.getUniqueId());
        if (lastSenderUUID == null) {
            return null;
        }
        
        Player lastSender = Bukkit.getPlayer(lastSenderUUID);
        if (lastSender == null || !lastSender.isOnline()) {
            return null;
        }
        
        return lastSender;
    }
    
    /**
     * Toggle status social spy untuk admin
     * @param player Admin yang ingin toggle social spy
     * @return Status social spy setelah toggle
     */
    public boolean toggleSocialSpy(Player player) {
        boolean current = socialSpyEnabled.getOrDefault(player.getUniqueId(), false);
        socialSpyEnabled.put(player.getUniqueId(), !current);
        return !current;
    }
    
    /**
     * Cek apakah pemain memiliki social spy aktif
     */
    public boolean hasSocialSpyEnabled(Player player) {
        return socialSpyEnabled.getOrDefault(player.getUniqueId(), false);
    }
    
    /**
     * Broadcast pesan ke admin dengan social spy aktif
     */
    private void broadcastToSocialSpy(Player sender, Player receiver, String message) {
        String spyMessage = ColorUtils.colorize("&8[&cSPY&8] &7" + sender.getName() + " &8» &7" + receiver.getName() + "&8: &f" + message);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender) || player.equals(receiver)) continue;
            
            if (player.hasPermission("nusatown.socialspy") && hasSocialSpyEnabled(player)) {
                player.sendMessage(spyMessage);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Bersihkan cache pemain yang keluar
        UUID playerUUID = event.getPlayer().getUniqueId();
        lastMessageSender.remove(playerUUID);
        socialSpyEnabled.remove(playerUUID);
    }
}