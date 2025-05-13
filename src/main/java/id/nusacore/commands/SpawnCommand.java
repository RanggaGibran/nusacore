package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.TeleportUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final NusaCore plugin;
    private final BackCommand backCommand;

    // Cache untuk teleport settings
    private boolean useCustomSpawn;
    private boolean instantRespawn;
    private Location spawnLocation;
    
    public SpawnCommand(NusaCore plugin, BackCommand backCommand) {
        this.plugin = plugin;
        this.backCommand = backCommand;
        reloadCache();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Teleport ke spawn
            teleportToSpawn(player);
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("set")) {
            // Set spawn baru
            if (!player.hasPermission("nusatown.admin.setspawn")) {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk mengatur spawn."));
                return true;
            }
            
            setSpawn(player);
            return true;
        }
        
        // Tampilkan bantuan
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Penggunaan: /spawn atau /spawn set"));
        return true;
    }
    
    private void teleportToSpawn(Player player) {
        if (spawnLocation != null) {
            // Simpan lokasi sebelum teleport
            backCommand.setPreviousLocation(player, player.getLocation());
            
            // Notifikasi dan teleport dengan countdown
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Teleport ke spawn..."));
            TeleportUtils.teleportWithCountdown(
                plugin, player, spawnLocation, 
                "&bTeleport ke &3Spawn", 
                "&7Mohon tunggu..."
            );
        } else {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Lokasi spawn tidak ditemukan. Gunakan /spawn set untuk mengatur spawn."));
        }
    }
    
    private void setSpawn(Player player) {
        Location loc = player.getLocation();
        
        // Simpan lokasi spawn di config
        plugin.getConfig().set("spawn.location.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.location.x", loc.getX());
        plugin.getConfig().set("spawn.location.y", loc.getY());
        plugin.getConfig().set("spawn.location.z", loc.getZ());
        plugin.getConfig().set("spawn.location.yaw", loc.getYaw());
        plugin.getConfig().set("spawn.location.pitch", loc.getPitch());
        plugin.saveConfig();
        
        // Cache ulang lokasi spawn
        reloadCache();
        
        // Set world spawn juga
        loc.getWorld().setSpawnLocation(loc);
        
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Lokasi spawn berhasil diatur!"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }
    
    /**
     * Reload cache untuk spawn settings
     */
    public void reloadCache() {
        useCustomSpawn = plugin.getConfig().getBoolean("spawn.use-custom-spawn", false);
        instantRespawn = plugin.getConfig().getBoolean("spawn.instant-respawn", false);
        
        // Cache spawn location jika tersedia
        if (plugin.getConfig().isConfigurationSection("spawn.location")) {
            World world = Bukkit.getWorld(plugin.getConfig().getString("spawn.location.world"));
            if (world != null) {
                spawnLocation = new Location(
                    world,
                    plugin.getConfig().getDouble("spawn.location.x"),
                    plugin.getConfig().getDouble("spawn.location.y"),
                    plugin.getConfig().getDouble("spawn.location.z"),
                    (float) plugin.getConfig().getDouble("spawn.location.yaw"),
                    (float) plugin.getConfig().getDouble("spawn.location.pitch")
                );
            }
        }
    }
}