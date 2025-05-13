package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.TeleportUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackCommand implements CommandExecutor {
    private final NusaCore plugin;
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    
    public BackCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.back")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        if (!previousLocations.containsKey(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki lokasi sebelumnya untuk kembali."));
            return true;
        }
        
        // Cek apakah pemain dalam combat
        if (plugin.getCombatTagManager().isTagged(player)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat kembali ke lokasi sebelumnya saat dalam combat!"));
            return true;
        }
        
        // Get previous location
        Location previousLoc = previousLocations.get(player.getUniqueId());
        
        // Store current location before teleporting
        Location currentLoc = player.getLocation();
        
        // Teleport player to previous location with countdown
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Teleport ke lokasi sebelumnya..."));
        TeleportUtils.teleportWithCountdown(
            plugin, player, previousLoc, 
            "&bKembali ke &3Lokasi Sebelumnya", 
            "&7Mohon tunggu..."
        );
        
        // Update previous location to current
        previousLocations.put(player.getUniqueId(), currentLoc);
        
        return true;
    }
    
    /**
     * Menyimpan lokasi pemain sebelum teleportasi
     * @param player Pemain yang akan teleportasi
     * @param location Lokasi saat ini sebelum teleportasi
     */
    public void setPreviousLocation(Player player, Location location) {
        previousLocations.put(player.getUniqueId(), location);
    }
    
    /**
     * Memeriksa apakah pemain memiliki lokasi sebelumnya
     * @param player Pemain yang diperiksa
     * @return true jika pemain memiliki lokasi sebelumnya
     */
    public boolean hasPreviousLocation(Player player) {
        return previousLocations.containsKey(player.getUniqueId());
    }
}