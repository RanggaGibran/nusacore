package id.nusacore.listeners;

import id.nusacore.NusaCore;
import id.nusacore.commands.BackCommand;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.TeleportUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerEventListener implements Listener {
    
    private final NusaCore plugin;
    private final BackCommand backCommand;
    private final Map<UUID, Long> lastTeleportMap = new HashMap<>();
    private final Map<UUID, Long> lastMoveUpdate = new HashMap<>();
    private static final long TELEPORT_COOLDOWN = 2000; // 2 detik cooldown untuk menghindari spam
    
    public PlayerEventListener(NusaCore plugin, BackCommand backCommand) {
        this.plugin = plugin;
        this.backCommand = backCommand;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Jika player baru pertama kali join
        if (!player.hasPlayedBefore()) {
            // Kirim pesan selamat datang untuk pemain baru
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "<gradient:#FFD700:#FFA500>Selamat datang di server NusaTown!</gradient>"));
            player.sendMessage(ColorUtils.colorize("&f• Gunakan &e/nusatown help &funtuk melihat bantuan"));
            player.sendMessage(ColorUtils.colorize("&f• Jelajahi dunia dan bersenang-senanglah!"));
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            
            // Efek suara untuk pemain baru
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Teleport ke spawn jika konfigurasi diaktifkan
            if (plugin.getConfig().getBoolean("spawn.teleport-new-players", true)) {
                teleportToSpawn(player);
            }
            
            // Atur pesan join custom untuk pemain baru
            String newPlayerJoinMessage = ColorUtils.colorize(plugin.getConfig().getString(
                "messages.first-join", 
                "<gradient:#00A3FF:#00FFD1>{player}</gradient> <green>baru saja bergabung pertama kali!"
            ).replace("{player}", player.getName()));
            
            event.setJoinMessage(newPlayerJoinMessage);
        } else {
            // Pesan untuk pemain yang sudah pernah join
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Selamat datang kembali, &e" + player.getName() + "&f!"));
            
            // Atur pesan join custom untuk player yang kembali
            String returnPlayerJoinMessage = ColorUtils.colorize(plugin.getConfig().getString(
                "messages.join", 
                "<gradient:#00A3FF:#00FFD1>{player}</gradient> <white>telah bergabung."
            ).replace("{player}", player.getName()));
            
            event.setJoinMessage(returnPlayerJoinMessage);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Atur pesan quit custom
        String quitMessage = ColorUtils.colorize(plugin.getConfig().getString(
            "messages.quit", 
            "<gradient:#FF6B6B:#FF4500>{player}</gradient> <white>telah keluar."
        ).replace("{player}", player.getName()));
        
        event.setQuitMessage(quitMessage);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Simpan lokasi kematian untuk /back
        backCommand.setPreviousLocation(player, player.getLocation());
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // Jika konfigurasi menggunakan spawn kustom
        if (plugin.getConfig().getBoolean("spawn.use-custom-spawn", false) && 
            !event.isBedSpawn() && 
            !event.isAnchorSpawn()) {
            
            // Cek apakah lokasi spawn sudah diset
            if (plugin.getConfig().isConfigurationSection("spawn.location")) {
                String worldName = plugin.getConfig().getString("spawn.location.world");
                double x = plugin.getConfig().getDouble("spawn.location.x");
                double y = plugin.getConfig().getDouble("spawn.location.y");
                double z = plugin.getConfig().getDouble("spawn.location.z");
                float yaw = (float) plugin.getConfig().getDouble("spawn.location.yaw");
                float pitch = (float) plugin.getConfig().getDouble("spawn.location.pitch");
                
                if (worldName != null && plugin.getServer().getWorld(worldName) != null) {
                    Location spawnLoc = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
                    
                    // Set respawn location langsung
                    event.setRespawnLocation(spawnLoc);
                    
                    // Schedule effect setelah respawn (1 tick delay)
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        // Efek suara dan title
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                        player.sendTitle(
                            ColorUtils.colorize("&bTeleport ke &3Spawn"),
                            "", 
                            5, 10, 5
                        );
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda telah diteleport ke spawn."));
                    }, 1L);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Hanya proses jika pemain benar-benar berpindah posisi (bukan hanya menoleh)
        // Ini akan mengurangi overhead signifikan
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() 
                && event.getFrom().getBlockY() == event.getTo().getBlockY() 
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Gunakan throttling untuk membatasi frekuensi update
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // Cek waktu terakhir update untuk pemain ini
        long now = System.currentTimeMillis();
        if (lastMoveUpdate.containsKey(playerUUID)) {
            if (now - lastMoveUpdate.get(playerUUID) < 500) { // Update maksimal 2x per detik
                return;
            }
        }
        lastMoveUpdate.put(playerUUID, now);
        
        // Sisanya dari kode onPlayerMove() tetap sama...
        
        // Jika proteksi tidak diaktifkan, jangan lakukan pengecekan
        if (!plugin.getConfig().getBoolean("spawn.protection.enabled", true)) {
            return;
        }
        
        // Jika player memiliki izin bypass, jangan lakukan pengecekan
        if (player.hasPermission("nusatown.protection.bypass")) {
            return;
        }
        
        // Dapatkan daftar nama world yang dianggap spawn world
        List<String> spawnWorlds = plugin.getConfig().getStringList("spawn.protection.spawn-world-names");
        
        // Jika player tidak berada di spawn world, jangan lakukan pengecekan
        if (!spawnWorlds.contains(player.getWorld().getName())) {
            return;
        }
        
        // Cek cooldown untuk menghindari spam teleportasi
        long currentTime = System.currentTimeMillis();
        if (lastTeleportMap.containsKey(player.getUniqueId()) && 
            currentTime - lastTeleportMap.get(player.getUniqueId()) < TELEPORT_COOLDOWN) {
            return;
        }
        
        // Cek jika pemain berada di air
        if (plugin.getConfig().getBoolean("spawn.protection.prevent-water-damage", true)) {
            Block blockAt = event.getTo().getBlock();
            Block blockBelow = event.getTo().clone().subtract(0, 0.1, 0).getBlock();
            
            if (blockAt.getType() == Material.WATER || blockBelow.getType() == Material.WATER) {
                teleportPlayerToSpawn(player, "Anda telah diteleportasi kembali ke spawn karena terkena air.");
                return;
            }
        }
        
        // Cek jika pemain jatuh ke void
        if (plugin.getConfig().getBoolean("spawn.protection.prevent-void-damage", true)) {
            int voidLevel = plugin.getConfig().getInt("spawn.protection.void-level", -64);
            
            if (player.getLocation().getY() < voidLevel) {
                teleportPlayerToSpawn(player, "Anda telah diteleportasi kembali ke spawn karena jatuh ke void.");
                return;
            }
        }
    }
    
    /**
     * Teleport pemain ke spawn dengan pesan dan efek
     */
    private void teleportPlayerToSpawn(Player player, String reason) {
        // Update timestamp teleport terakhir
        lastTeleportMap.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Simpan lokasi sebelum teleport untuk /back command
        backCommand.setPreviousLocation(player, player.getLocation());
        
        // Teleport ke spawn
        if (plugin.getConfig().isConfigurationSection("spawn.location")) {
            String worldName = plugin.getConfig().getString("spawn.location.world");
            double x = plugin.getConfig().getDouble("spawn.location.x");
            double y = plugin.getConfig().getDouble("spawn.location.y");
            double z = plugin.getConfig().getDouble("spawn.location.z");
            float yaw = (float) plugin.getConfig().getDouble("spawn.location.yaw");
            float pitch = (float) plugin.getConfig().getDouble("spawn.location.pitch");
            
            if (worldName != null && plugin.getServer().getWorld(worldName) != null) {
                Location spawnLoc = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
                
                // Kirim pesan
                String teleportMessage = plugin.getConfig().getString("spawn.protection.teleport-message", "&cAnda telah diteleportasi kembali ke spawn.");
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + teleportMessage));
                
                // Teleport dengan countdown
                TeleportUtils.teleportWithCountdown(
                    plugin, player, spawnLoc,
                    "&bTeleport ke &3Spawn",
                    "&7Perlindungan aktif"
                );
            }
        }
    }
    
    /**
     * Teleport pemain ke lokasi spawn langsung tanpa countdown
     */
    private void teleportToSpawnInstantly(Player player) {
        if (plugin.getConfig().isConfigurationSection("spawn.location")) {
            String worldName = plugin.getConfig().getString("spawn.location.world");
            double x = plugin.getConfig().getDouble("spawn.location.x");
            double y = plugin.getConfig().getDouble("spawn.location.y");
            double z = plugin.getConfig().getDouble("spawn.location.z");
            float yaw = (float) plugin.getConfig().getDouble("spawn.location.yaw");
            float pitch = (float) plugin.getConfig().getDouble("spawn.location.pitch");
            
            if (worldName != null && plugin.getServer().getWorld(worldName) != null) {
                Location spawnLoc = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
                
                // Teleport langsung tanpa countdown
                player.teleport(spawnLoc);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                player.sendTitle(
                    ColorUtils.colorize("&bTeleport ke &3Spawn"),
                    "", 
                    5, 10, 5
                );
            }
        }
    }
    
    /**
     * Teleport pemain ke lokasi spawn yang dikonfigurasi
     */
    private void teleportToSpawn(Player player) {
        if (plugin.getConfig().isConfigurationSection("spawn.location")) {
            String worldName = plugin.getConfig().getString("spawn.location.world");
            double x = plugin.getConfig().getDouble("spawn.location.x");
            double y = plugin.getConfig().getDouble("spawn.location.y");
            double z = plugin.getConfig().getDouble("spawn.location.z");
            float yaw = (float) plugin.getConfig().getDouble("spawn.location.yaw");
            float pitch = (float) plugin.getConfig().getDouble("spawn.location.pitch");
            
            if (worldName != null && plugin.getServer().getWorld(worldName) != null) {
                Location spawnLoc = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
                
                // Teleport dengan countdown untuk pemain baru
                TeleportUtils.teleportWithCountdown(
                    plugin, player, spawnLoc,
                    "&bSelamat Datang di &3Spawn",
                    "&7Server NusaTown"
                );
            }
        }
    }
}