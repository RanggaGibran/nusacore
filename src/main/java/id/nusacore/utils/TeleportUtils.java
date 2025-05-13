package id.nusacore.utils;

import id.nusacore.NusaCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class TeleportUtils {

    // Cache konfigurasi teleport
    private static int cachedDelay = -1;
    private static boolean cachedCountdownEnabled = true;

    /**
     * Teleport player dengan countdown dan efek titel
     * 
     * @param plugin      Instance plugin
     * @param player      Pemain yang akan diteleportasi
     * @param destination Lokasi tujuan
     * @param title       Judul yang ditampilkan saat teleport
     * @param subtitle    Subjudul (opsional, bisa null)
     */
    public static void teleportWithCountdown(NusaCore plugin, Player player, Location destination, 
                                            String title, String subtitle) {
        // Cek cache, reload jika belum diinisialisasi
        if (cachedDelay < 0) {
            reloadCache(plugin);
        }
        
        // Cek apakah pemain dalam combat
        if (plugin.getCombatTagManager().isTagged(player) && 
            plugin.getCombatTagManager().isTeleportPrevented()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat teleport saat dalam combat!"));
            return;
        }
        
        // Cek permission bypass teleport countdown
        if (player.hasPermission("nusatown.teleport.bypass")) {
            // Teleport langsung tanpa countdown
            player.teleport(destination);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            player.sendTitle(
                ColorUtils.colorize(title),
                subtitle != null ? ColorUtils.colorize(subtitle) : "", 
                5, 10, 5
            );
            return;
        }
        
        // Gunakan nilai cache untuk kondisi teleport
        if (!cachedCountdownEnabled) {
            player.teleport(destination);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            player.sendTitle(
                ColorUtils.colorize("&aTeleport!"), 
                "", 
                5, 10, 5
            );
            return;
        }
        
        // Jika delay 0 atau kurang, teleport langsung
        if (cachedDelay <= 0) {
            player.teleport(destination);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            player.sendTitle(
                ColorUtils.colorize("&aTeleport!"), 
                "", 
                5, 10, 5
            );
            return;
        }
        
        // Tambahkan efek titel awal
        player.sendTitle(
            ColorUtils.colorize(title), 
            subtitle != null ? ColorUtils.colorize(subtitle) : "", 
            5, 20, 5
        );
        
        // Mulai countdown
        new BukkitRunnable() {
            int secondsLeft = cachedDelay;
            
            @Override
            public void run() {
                // Cek lagi apakah pemain memasuki combat selama countdown
                if (plugin.getCombatTagManager().isTagged(player) && 
                    plugin.getCombatTagManager().isTeleportPrevented()) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&cTeleport dibatalkan! Anda memasuki combat."));
                    cancel();
                    return;
                }
                
                if (secondsLeft <= 0) {
                    // Teleport pemain
                    player.teleport(destination);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                    player.sendTitle(
                        ColorUtils.colorize("&aTeleport!"), 
                        "", 
                        5, 10, 5
                    );
                    cancel();
                    return;
                }
                
                // Tampilkan countdown
                player.sendTitle(
                    ColorUtils.colorize("&e" + secondsLeft), 
                    ColorUtils.colorize("&fTeleport dalam..."), 
                    0, 20, 0
                );
                
                // Efek suara countdown
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 detik
    }

    /**
     * Teleport player dengan countdown dan efek titel
     * 
     * @param plugin      Instance plugin
     * @param player      Pemain yang akan diteleportasi
     * @param destination Lokasi tujuan
     * @param title       Judul yang ditampilkan saat teleport
     * @param subtitle    Subjudul (opsional, bisa null)
     * @param delay       Delay sebelum teleport dalam detik
     */
    public static void teleportWithCountdown(NusaCore plugin, Player player, Location destination, 
                                            String title, String subtitle, int delay) {
        // Cek apakah pemain dalam combat
        if (plugin.getCombatTagManager().isTagged(player) && 
            plugin.getCombatTagManager().isTeleportPrevented()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat teleport saat dalam combat!"));
            return;
        }
        
        // Cek permission bypass teleport countdown
        if (player.hasPermission("nusatown.teleport.bypass")) {
            // Teleport langsung tanpa countdown
            player.teleport(destination);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            player.sendTitle(
                ColorUtils.colorize(title),
                subtitle != null ? ColorUtils.colorize(subtitle) : "", 
                5, 10, 5
            );
            return;
        }
        
        // Tambahkan efek titel awal
        player.sendTitle(
            ColorUtils.colorize(title), 
            subtitle != null ? ColorUtils.colorize(subtitle) : "", 
            5, 20, 5
        );
        
        // Mulai countdown
        new BukkitRunnable() {
            int secondsLeft = delay;
            
            @Override
            public void run() {
                // Cek lagi apakah pemain memasuki combat selama countdown
                if (plugin.getCombatTagManager().isTagged(player) && 
                    plugin.getCombatTagManager().isTeleportPrevented()) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&cTeleport dibatalkan! Anda memasuki combat."));
                    cancel();
                    return;
                }
                
                if (secondsLeft <= 0) {
                    // Teleport pemain
                    player.teleport(destination);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                    player.sendTitle(
                        ColorUtils.colorize("&aTeleport!"), 
                        "", 
                        5, 10, 5
                    );
                    cancel();
                    return;
                }
                
                // Tampilkan countdown
                player.sendTitle(
                    ColorUtils.colorize("&e" + secondsLeft), 
                    ColorUtils.colorize("&fTeleport dalam..."), 
                    0, 20, 0
                );
                
                // Efek suara countdown
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 detik
    }

    /**
     * Teleport player dengan countdown dan pencegahan fall damage
     */
    public static void teleportWithCountdownAndNoFallDamage(NusaCore plugin, Player player, 
                                                          Location destination, String title, 
                                                          String subtitle, int delay) {
        // Combat check & permission logic (sama seperti teleportWithCountdown)
        
        // Mulai countdown
        new BukkitRunnable() {
            int secondsLeft = delay;
            
            @Override
            public void run() {
                // Cek combat & player online
                if (plugin.getCombatTagManager().isTagged(player) && 
                    plugin.getCombatTagManager().isTeleportPrevented()) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&cTeleport dibatalkan! Anda memasuki combat."));
                    cancel();
                    return;
                }
                
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                if (secondsLeft <= 0) {
                    // Teleport dengan pencegahan fall damage
                    applyNoFallEffect(plugin, player);
                    player.teleport(destination);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                    player.sendTitle(
                        ColorUtils.colorize("&aTeleport!"), 
                        "", 
                        5, 10, 5
                    );
                    cancel();
                    return;
                }
                
                // Tampilkan countdown
                player.sendTitle(
                    ColorUtils.colorize("&e" + secondsLeft), 
                    ColorUtils.colorize("&fTeleport dalam..."), 
                    0, 20, 0
                );
                
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Apply no fall damage effect after teleport
     */
    private static void applyNoFallEffect(NusaCore plugin, Player player) {
        // Opsi 1: Berikan potion effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 1));
        
        // Opsi 2: Gunakan flag untuk mencegah fall damage
        final UUID playerId = player.getUniqueId();
        
        // Set fall distance ke 0
        player.setFallDistance(0);
        
        // Monitor player dan reset fall distance untuk beberapa detik
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(playerId);
                if (p == null || !p.isOnline() || ticks >= 100) { // 5 detik (100 ticks)
                    cancel();
                    return;
                }
                p.setFallDistance(0);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void reloadCache(NusaCore plugin) {
        cachedDelay = plugin.getConfig().getInt("teleport.delay", 3);
        cachedCountdownEnabled = plugin.getConfig().getBoolean("teleport.countdown-enabled", true);
    }
}