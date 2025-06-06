package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.TeleportUtils;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RTPCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private FileConfiguration config;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();
    private final Set<Material> safeMaterials = new HashSet<>();
    private final Set<Material> unsafeMaterials = new HashSet<>();
    private File cooldownFile;
    private YamlConfiguration cooldownConfig;
    
    // Tambahkan rate limiter untuk whole server
    private static final Map<String, AtomicInteger> worldActiveRTPs = new ConcurrentHashMap<>();
    private static int MAX_CONCURRENT_RTP_PER_WORLD = 3; // Nilai default

    // Gunakan object pool untuk Location (mengurangi garbage collection)
    private static final Queue<Location> locationPool = new ConcurrentLinkedQueue<>();
    private static final int MAX_POOLED_LOCATIONS = 100;

    // Tambahkan Set untuk menyimpan pemain yang sedang dalam proses RTP
    private static final Set<UUID> playersInRTPProcess = Collections.synchronizedSet(new HashSet<>());

    // Tambahkan map untuk menyimpan task timeout
    private static final Map<UUID, Integer> rtpTimeoutTasks = new HashMap<>();

    private Location getLocationFromPool(World world, double x, double y, double z) {
        Location location = locationPool.poll();
        
        if (location == null) {
            return new Location(world, x, y, z);
        } else {
            location.setWorld(world);
            location.setX(x);
            location.setY(y);
            location.setZ(z);
            return location;
        }
    }

    private void recycleLocation(Location location) {
        if (locationPool.size() < MAX_POOLED_LOCATIONS) {
            locationPool.offer(location);
        }
    }

    public RTPCommand(NusaCore plugin) {
        this.plugin = plugin;
        loadConfig();
        loadCooldowns();
    }
    
    /**
     * Load RTP configuration from rtp.yml
     */
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "rtp.yml");
        if (!configFile.exists()) {
            plugin.saveResource("rtp.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load nilai max concurrent RTP
        MAX_CONCURRENT_RTP_PER_WORLD = config.getInt("settings.max-concurrent-rtp", 3);
        
        // Load safe materials
        List<String> safeMaterialNames = config.getStringList("safety.safe-materials");
        safeMaterials.clear();
        for (String materialName : safeMaterialNames) {
            try {
                Material material = Material.valueOf(materialName);
                safeMaterials.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in safe-materials: " + materialName);
            }
        }
        
        // Load unsafe materials
        List<String> unsafeMaterialNames = config.getStringList("safety.unsafe-nearby-materials");
        unsafeMaterials.clear();
        for (String materialName : unsafeMaterialNames) {
            try {
                Material material = Material.valueOf(materialName);
                unsafeMaterials.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in unsafe-nearby-materials: " + materialName);
            }
        }
        
        // Load MAX_CONCURRENT_RTP_PER_WORLD from config
        MAX_CONCURRENT_RTP_PER_WORLD = config.getInt("settings.max-concurrent-rtp-per-world", 3);
    }
    
    /**
     * Load cooldown data from storage
     */
    private void loadCooldowns() {
        cooldownFile = new File(plugin.getDataFolder(), "rtpcooldowns.yml");
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        
        // Load cooldowns from config
        ConfigurationSection cooldownSection = cooldownConfig.getConfigurationSection("cooldowns");
        if (cooldownSection != null) {
            for (String uuidString : cooldownSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    long timestamp = cooldownSection.getLong(uuidString);
                    cooldowns.put(uuid, timestamp);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in rtpcooldowns.yml: " + uuidString);
                }
            }
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Admin command untuk cek counter
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            if (sender.hasPermission("nusatown.admin.rtp")) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aRTP Status:"));
                
                for (Map.Entry<String, AtomicInteger> entry : worldActiveRTPs.entrySet()) {
                    String worldName = entry.getKey();
                    int count = entry.getValue().get();
                    sender.sendMessage(ColorUtils.colorize("&7- &e" + worldName + "&7: &f" + count + "/" + MAX_CONCURRENT_RTP_PER_WORLD));
                }
                
                sender.sendMessage(ColorUtils.colorize("&7Active RTP processes: &f" + playersInRTPProcess.size()));
                return true;
            }
        }
        
        // Admin command untuk reset counter
        if (args.length > 0 && args[0].equalsIgnoreCase("resetcounter")) {
            if (sender.hasPermission("nusatown.admin.rtp")) {
                String worldName = args.length > 1 ? args[1] : "world";
                resetWorldRTPCounter(worldName);
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aReset RTP counter for world: " + worldName));
                return true;
            }
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "This command can only be used by players."));
            return true;
        }
        
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        
        // Cek apakah pemain sudah dalam proses RTP
        if (playersInRTPProcess.contains(playerUUID)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cProses RTP sedang berlangsung. Harap tunggu..."));
            return true;
        }
        
        if (!player.hasPermission("nusatown.command.rtp")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "You don't have permission to use this command."));
            return true;
        }
        
        // Get world to teleport to
        World world = player.getWorld();
        String worldName = world.getName();
        
        if (args.length > 0) {
            // Try to get the specified world
            World specifiedWorld = Bukkit.getWorld(args[0]);
            if (specifiedWorld != null && 
                config.getConfigurationSection("worlds." + specifiedWorld.getName()) != null && 
                config.getBoolean("worlds." + specifiedWorld.getName() + ".enabled", false)) {
                world = specifiedWorld;
                worldName = world.getName();
            } else {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cWorld &e" + args[0] + " &cnot found or not enabled for RTP."));
                return true;
            }
        }
        
        // Check if the world is enabled for RTP
        if (config.getConfigurationSection("worlds." + worldName) == null || 
            !config.getBoolean("worlds." + worldName + ".enabled", false)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cRTP is not enabled in this world."));
            return true;
        }
        
        // Check cooldown
        if (!checkCooldown(player)) {
            int remainingCooldown = getRemainingCooldownSeconds(player);
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cYou must wait &e" + formatTime(remainingCooldown) + " &cbefore using RTP again."));
            return true;
        }
        
        // Check rate limiter
        if (!canStartRTP(world)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cToo many RTP requests in this world. Please wait a moment and try again."));
            return true;
        }
        
        // Tambahkan pemain ke Set sebelum memulai proses pencarian lokasi
        playersInRTPProcess.add(playerUUID);
        
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + config.getString("settings.teleport-message", "&aSearching for a safe location...")));
        
        // Find a safe location
        findSafeLocation(player, world);
        
        return true;
    }
    
    /**
     * Find a safe location and teleport the player there
     * 
     * @param player Player to teleport
     * @param world World to teleport to
     */
    private void findSafeLocation(Player player, World world) {
        String worldName = world.getName();
        ConfigurationSection worldSection = config.getConfigurationSection("worlds." + worldName);
        
        int centerX = worldSection.getInt("center-x", 0);
        int centerZ = worldSection.getInt("center-z", 0);
        int minRadius = worldSection.getInt("min-radius", 500);
        int maxRadius = worldSection.getInt("max-radius", 5000);
        int minY = worldSection.getInt("min-y", 60);
        int maxY = worldSection.getInt("max-y", 120);
        int maxAttempts = config.getInt("settings.max-attempts", 50);
        
        // Untuk tracking player
        final UUID playerUUID = player.getUniqueId();
        final AtomicBoolean isSearching = new AtomicBoolean(true);
        
        // Simpan task ID untuk cancel jika perlu
        final int[] taskId = new int[1];
        
        // Batasi jumlah lokasi yang dicek per batch untuk mengurangi lag
        final int batchSize = 5;
        final AtomicInteger attemptsCounter = new AtomicInteger(0);
        
        // Tampilkan pesan memulai pencarian
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + config.getString("settings.teleport-message", 
                "&aSearching for a safe location...")));
        
        // Set timeout task untuk membersihkan status jika RTP berlangsung terlalu lama
        int timeoutTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Jika timeout tercapai dan pemain masih dalam proses RTP
            if (playersInRTPProcess.contains(playerUUID)) {
                plugin.getLogger().info("RTP timeout reached for " + player.getName() + ", automatically resetting status");
                cleanupRTPProcess(playerUUID, world);
                
                // Beritahu pemain jika online
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null && p.isOnline()) {
                    p.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&cProses RTP timeout, silakan coba lagi."));
                }
            }
        }, 20 * 60).getTaskId(); // 60 detik timeout
        
        rtpTimeoutTasks.put(playerUUID, timeoutTaskId);
        
        taskId[0] = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                while (isSearching.get() && attemptsCounter.get() < maxAttempts) {
                    // Mengambil batch lokasi untuk diperiksa
                    List<Location> batchLocations = new ArrayList<>();
                    
                    // Generate batch lokasi
                    for (int i = 0; i < batchSize && attemptsCounter.get() < maxAttempts; i++) {
                        attemptsCounter.incrementAndGet();
                        
                        // Generate random X/Z coordinates
                        double angle = random.nextDouble() * 2 * Math.PI;
                        double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
                        int x = centerX + (int)(Math.cos(angle) * radius);
                        int z = centerZ + (int)(Math.sin(angle) * radius);
                        
                        // Check worldborder
                        if (!isInsideWorldBorder(world, x, z)) {
                            i--; // Try again with new coordinates
                            continue;
                        }
                        
                        // Buat lokasi basis (Y akan ditentukan nanti)
                        batchLocations.add(new Location(world, x, 0, z));
                    }
                    
                    // Update progress setiap batch
                    int currentAttempt = attemptsCounter.get();
                    if (currentAttempt % 10 == 0 || currentAttempt >= maxAttempts) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player p = Bukkit.getPlayer(playerUUID);
                            if (p != null && p.isOnline()) {
                                sendProgressUpdate(p, currentAttempt, maxAttempts);
                            } else {
                                // Player disconnect, batalkan pencarian
                                isSearching.set(false);
                                Bukkit.getScheduler().cancelTask(taskId[0]);
                            }
                        });
                    }
                    
                    // Process batch lokasi
                    processBatchLocations(batchLocations, world, minY, maxY, player, isSearching, 
                            playerUUID, taskId[0], attemptsCounter, maxAttempts);
                    
                    // Small delay between batches to reduce server load
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                
                // Ketika semua percobaan gagal dan tidak ditemukan lokasi aman
                if (attemptsCounter.get() >= maxAttempts && isSearching.get()) {
                    isSearching.set(false);
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        cleanupRTPProcess(playerUUID, world);
                        
                        Player p = Bukkit.getPlayer(playerUUID);
                        if (p != null && p.isOnline()) {
                            p.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                    "&cTidak dapat menemukan lokasi aman setelah " + attemptsCounter.get() + 
                                    " percobaan. Silakan coba lagi."));
                        }
                    });
                }
            } catch (Exception e) {
                // Log error dan hapus pemain dari daftar proses RTP
                plugin.getLogger().severe("Error during RTP search: " + e.getMessage());
                e.printStackTrace();
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cleanupRTPProcess(playerUUID, world);
                });
            }
        }).getTaskId();
    }

    /**
     * Proses batch lokasi untuk RTP
     */
    private void processBatchLocations(List<Location> locations, World world, int minY, int maxY,
                                       Player player, AtomicBoolean isSearching, UUID playerUUID, 
                                       int taskId, AtomicInteger attempts, int maxAttempts) {
        // ... kode yang sudah ada ...
        
        // Jika pemain tidak online lagi atau keluar server
        if (!Bukkit.getPlayer(playerUUID).isOnline()) {
            isSearching.set(false);
            cleanupRTPProcess(playerUUID, world);
            return;
        }
        
        // ... kode yang sudah ada ...
        
        if (locations.isEmpty() || !isSearching.get()) return;
        
        // Proses lokasi satu per satu secara async
        Location baseLocation = locations.remove(0);
        
        // Cari Y yang aman untuk lokasi ini
        scanForSafeY(baseLocation, minY, maxY).thenAccept(safeLocation -> {
            if (safeLocation != null && isSearching.get()) {
                // Found safe location!
                isSearching.set(false);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(playerUUID);
                    if (p != null && p.isOnline()) {
                        // Teleport player ke lokasi aman
                        handleTeleport(p, safeLocation);
                        
                        // Hapus pemain dari Set setelah RTP selesai
                        cleanupRTPProcess(playerUUID, world);
                    }
                });
            } else if (!locations.isEmpty() && isSearching.get()) {
                // Coba lokasi berikutnya dalam batch ini
                processBatchLocations(locations, world, minY, maxY, player, isSearching, 
                        playerUUID, taskId, attempts, maxAttempts);
            } else if (attempts.get() >= maxAttempts && isSearching.get()) {
                // Semua lokasi sudah dicek dan tidak ada yang aman
                isSearching.set(false);
                final int finalAttempts = attempts.get();
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(playerUUID);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                "&cFailed to find a safe location after " + finalAttempts + 
                                " attempts. Please try again."));
                    }
                    
                    // Cleanup RTP process
                    cleanupRTPProcess(playerUUID, world);
                });
            }
        });
    }
    
    /**
     * Handle teleportation dengan pencegahan fall damage 
     */
    private void handleTeleport(Player player, Location location) {
        // Set cooldown first
        setCooldown(player);
        saveCooldownToStorage(player);
        
        // Cleanup RTP process
        cleanupRTPProcess(player.getUniqueId(), location.getWorld());
        
        // Save previous location for /back command
        plugin.getBackCommand().setPreviousLocation(player, player.getLocation());
        
        // Get teleport settings
        String title = config.getString("settings.teleport-title", "&a&lRandom Teleport");
        String subtitle = config.getString("settings.teleport-subtitle", "&7Finding a safe location...");
        int teleportDelay = config.getInt("settings.teleport-delay", 3);
        
        // Play sound
        String soundName = config.getString("settings.teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound in rtp.yml: " + soundName);
        }
        
        // Teleport with fall damage prevention
        TeleportUtils.teleportWithCountdownAndNoFallDamage(
            plugin, 
            player, 
            location,
            title,
            subtitle,
            teleportDelay
        );
        
        // Recycle location
        recycleLocation(location);
    }
    
    /**
     * Send progress update to player
     */
    private void sendProgressUpdate(Player player, int currentAttempt, int maxAttempts) {
        // Calculate progress percentage
        int percentage = (currentAttempt * 100) / maxAttempts;
        
        // Format message dengan progress bar
        String progressBar = createProgressBar(percentage);
        String message = ColorUtils.colorize(NusaCore.PREFIX + 
                "&7Searching for safe location... " + progressBar + " &e" + percentage + "%");
                
        // Kirim via actionbar (tidak mengganggu chat)
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    /**
     * Create a visual progress bar
     */
    private String createProgressBar(int percentage) {
        StringBuilder bar = new StringBuilder("&8[");
        int segments = 20; // Bar width
        int filledSegments = (percentage * segments) / 100;
        
        for (int i = 0; i < segments; i++) {
            if (i < filledSegments) {
                bar.append("&a|");
            } else {
                bar.append("&7|");
            }
        }
        
        bar.append("&8]");
        return bar.toString();
    }
    
    /**
     * Check if a player can use RTP (cooldown check)
     * 
     * @param player The player to check
     * @return true if the player can use RTP
     */
    private boolean checkCooldown(Player player) {
        // Bypass cooldown with permission
        if (config.getBoolean("settings.bypass-cooldown-permission", true) && 
            player.hasPermission("nusatown.rtp.bypass")) {
            return true;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Check if player has a cooldown and if it's expired
        if (cooldowns.containsKey(playerId)) {
            long lastUsage = cooldowns.get(playerId);
            long cooldownTime = config.getInt("settings.cooldown", 300) * 1000L; // Convert to milliseconds
            
            if (System.currentTimeMillis() - lastUsage < cooldownTime) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Set a cooldown for a player
     * 
     * @param player The player to set the cooldown for
     */
    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    /**
     * Save cooldown to persistent storage
     */
    private void saveCooldownToStorage(Player player) {
        if (cooldownConfig == null) return;
        
        UUID uuid = player.getUniqueId();
        cooldownConfig.set("cooldowns." + uuid.toString(), cooldowns.get(uuid));
        
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save RTP cooldown for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Save all cooldowns (untuk server shutdown)
     */
    public void saveAllCooldowns() {
        if (cooldownConfig == null) return;
        
        // Clear existing data
        cooldownConfig.set("cooldowns", null);
        
        // Save all current cooldowns
        for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
            cooldownConfig.set("cooldowns." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save RTP cooldowns: " + e.getMessage());
        }
    }
    
    /**
     * Get the remaining cooldown in seconds
     * 
     * @param player The player to check
     * @return Remaining cooldown in seconds
     */
    private int getRemainingCooldownSeconds(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (cooldowns.containsKey(playerId)) {
            long lastUsage = cooldowns.get(playerId);
            long cooldownTime = config.getInt("settings.cooldown", 300) * 1000L; // Convert to milliseconds
            long elapsedTime = System.currentTimeMillis() - lastUsage;
            
            return Math.max(0, (int)((cooldownTime - elapsedTime) / 1000));
        }
        
        return 0;
    }
    
    /**
     * Format time in seconds to a readable format
     * 
     * @param seconds The time in seconds
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "") + 
                   (remainingSeconds > 0 ? " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") : "");
        } else {
            int hours = seconds / 3600;
            int remainingMinutes = (seconds % 3600) / 60;
            return hours + " hour" + (hours != 1 ? "s" : "") + 
                   (remainingMinutes > 0 ? " " + remainingMinutes + " minute" + (remainingMinutes != 1 ? "s" : "") : "");
        }
    }
    
    /**
     * Cek apakah lokasi berada di dalam worldborder
     */
    private boolean isInsideWorldBorder(World world, int x, int z) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize() / 2;
        
        // Cek apakah koordinat dalam batas worldborder
        return Math.abs(x - center.getX()) < size && Math.abs(z - center.getZ()) < size;
    }
    
    /**
     * Scan secara vertikal untuk mencari lokasi yang aman
     * @return Location yang aman atau null jika tidak ditemukan
     */
    private CompletableFuture<Location> scanForSafeY(Location baseLocation, int minY, int maxY) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        World world = baseLocation.getWorld();
        int x = baseLocation.getBlockX();
        int z = baseLocation.getBlockZ();
        
        // Lakukan scanning di thread async Bukkit
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Buat queue lokasi untuk diperiksa dengan prioritas yang masuk akal
            List<Integer> yToCheck = new ArrayList<>();
            
            // Scan dari beberapa titik awal yang berbeda (bukan hanya dari maxY)
            // Overworld: periksa permukaan terlebih dahulu, lalu gua/pegunungan
            if (world.getEnvironment() == World.Environment.NORMAL) {
                // Periksa permukaan klasik (Y=64-80) dulu
                for (int y = 64; y <= 80; y += 4) {
                    if (y >= minY && y <= maxY) yToCheck.add(y);
                }
                
                // Lalu periksa mountain range (Y=110-160)
                for (int y = 120; y <= 160; y += 8) {
                    if (y >= minY && y <= maxY) yToCheck.add(y);
                }
                
                // Kemudian periksa level gua bawah tanah (Y=0-40)
                for (int y = 40; y >= 0; y -= 8) {
                    if (y >= minY && y <= maxY) yToCheck.add(y);
                }
                
                // Terakhir periksa sisanya dengan interval yang lebih besar
                for (int y = minY; y <= maxY; y += 16) {
                    if (!yToCheck.contains(y)) yToCheck.add(y);
                }
            } 
            // Nether: periksa ruang kosong di tengah nether terlebih dahulu
            else if (world.getEnvironment() == World.Environment.NETHER) {
                // Periksa level utama Nether (Y=30-100)
                for (int y = 70; y >= 30; y -= 5) {
                    if (y >= minY && y <= maxY) yToCheck.add(y);
                }
                
                // Periksa sisanya
                for (int y = minY; y <= maxY; y += 10) {
                    if (!yToCheck.contains(y)) yToCheck.add(y);
                }
            }
            // The End: prioritaskan pulau di sekitar Y=64
            else if (world.getEnvironment() == World.Environment.THE_END) {
                // The End islands berada di sekitar Y=64
                for (int y = 66; y >= 48; y -= 2) {
                    if (y >= minY && y <= maxY) yToCheck.add(y);
                }
                
                // Periksa sisanya
                for (int y = minY; y <= maxY; y += 10) {
                    if (!yToCheck.contains(y)) yToCheck.add(y);
                }
            }
            
            // Gunakan PaperLib untuk memastikan chunk dimuat
            PaperLib.getChunkAtAsync(baseLocation).thenAccept(chunk -> {
                // Periksa koordinat Y yang ditentukan
                scanYLevelsSequentially(x, yToCheck, z, world, 0, future);
            }).exceptionally(ex -> {
                future.complete(null); // Gagal memuat chunk
                return null;
            });
        });
        
        return future;
    }

    /**
     * Scan Y levels secara rekursif untuk mengurangi tekanan pada server
     */
    private void scanYLevelsSequentially(int x, List<Integer> yLevels, int z, World world, 
                                         int index, CompletableFuture<Location> future) {
        // Jika sudah memeriksa semua Y level, selesaikan dengan null (tidak ditemukan)
        if (index >= yLevels.size()) {
            future.complete(null);
            return;
        }
        
        int y = yLevels.get(index);
        Location location = new Location(world, x, y, z);
        
        // Periksa lokasi di main thread (diperlukan untuk akses block data)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Pastikan chunk dimuat
            if (!location.getChunk().isLoaded()) {
                location.getChunk().load(true);
            }
            
            // Periksa keamanan lokasi
            if (isLocationPotentiallySafe(location)) {
                // Jika aman, selesaikan future dengan lokasi ini
                future.complete(location);
                return;
            }
            
            // Jika lokasi tidak aman, periksa Y level berikutnya
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                scanYLevelsSequentially(x, yLevels, z, world, index + 1, future);
            }, 1L); // Delay minimal untuk mengurangi tekanan pada server
        });
    }
      /**
     * Periksa apakah lokasi potensial aman (lebih longgar untuk mengurangi false negatives)
     */
    private boolean isLocationPotentiallySafe(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        try {
            // Pemeriksaan dasar untuk region protection sudah dihapus (Towny removed)
            
            // Periksa apakah lokasi di atas ground yang solid
            Block blockBelow = world.getBlockAt(x, y - 1, z);
            if (!safeMaterials.contains(blockBelow.getType())) {
                // Jika tidak aman tapi ada air di bawah, cek kedalaman air
                if (blockBelow.getType() == Material.WATER) {
                    // Jika tidak ingin RTP ke air, return false
                    if (config.getBoolean("safety.avoid-water", true)) {
                        return false;
                    }
                    
                    // Cek kedalaman air (opsional)
                    int depth = 1;
                    while (depth < 5) {
                        if (world.getBlockAt(x, y - 1 - depth, z).getType() != Material.WATER) {
                            break;
                        }
                        depth++;
                    }
                    if (depth >= 3) return false; // Terlalu dalam
                } else {
                    return false; // Bukan air dan bukan blok aman
                }
            }
            
            // Cek block saat ini dan di atas (minimal 2 blok untuk pemain)
            Block currentBlock = world.getBlockAt(x, y, z);
            Block blockAbove = world.getBlockAt(x, y + 1, z);
            if (!currentBlock.getType().isAir() || !blockAbove.getType().isAir()) {
                return false;
            }
            
            // Jika sampai sini, lokasi cukup aman untuk RTP
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking safety: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check WorldGuard region protection (optional)
     */
    private boolean isRegionProtected(Location location) {
        // Implementasi hook WorldGuard di sini
        // Contoh sederhana:
        try {
            Class<?> worldGuardPlugin = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object instance = worldGuardPlugin.getMethod("inst").invoke(null);
            Object regionManager = worldGuardPlugin.getMethod("getRegionManager", World.class)
                                  .invoke(instance, location.getWorld());
                                  
            // Dapatkan ApplicableRegionSet dan cek flag
            // ...
            
            // Ini implementasi dasar, lengkapi dengan API WorldGuard yang benar
            return false; // Default: tidak protected
        } catch (Exception e) {
            return false; // Asumsikan tidak protected jika error
        }    }
      /**
     * Ini hanya akan berfungsi di Paper, tidak di Spigot
     */
    private CompletableFuture<Boolean> checkBlockSafetyAsync(Location location) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            // Paper memiliki getType() yang berfungsi di async thread
            World world = location.getWorld();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            
            // Konfigurasi safety
            boolean checkSafety = config.getBoolean("safety.enabled", true);
            int emptyBlocksNeeded = config.getInt("safety.required-empty-blocks", 3);
            
            // CATATAN: Penting! Di Paper, getType() bisa dipanggil async 
            // tapi HANYA jika chunk sudah loaded
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir()) {
                future.complete(false);
                return future;
            }
            
            // Cek ruang kosong di atas
            for (int i = 1; i < emptyBlocksNeeded; i++) {
                if (!world.getBlockAt(x, y + i, z).getType().isAir()) {
                    future.complete(false);
                    return future;
                }
            }
            
            // Cek ground untuk pijakan
            if (!safeMaterials.contains(world.getBlockAt(x, y - 1, z).getType())) {
                future.complete(false);
                return future;
            }
            
            // Lakukan minimal check pada blok berbahaya
            if (checkSafety) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                            if (offsetX == 0 && offsetY == 0 && offsetZ == 0) continue;
                            if (unsafeMaterials.contains(world.getBlockAt(x + offsetX, y + offsetY, z + offsetZ).getType())) {
                                future.complete(false);
                                return future;
                            }
                        }
                    }
                }
            }
            
            future.complete(true);
        } catch (Exception e) {
            plugin.getLogger().warning("Error in async block safety check: " + e.getMessage());
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Check if RTP can start in the specified world
     */
    private boolean canStartRTP(World world) {
        String worldName = world.getName();
        AtomicInteger count = worldActiveRTPs.computeIfAbsent(worldName, k -> new AtomicInteger(0));
        
        // Jika sudah mencapai batas, jangan izinkan RTP lagi
        if (count.get() >= MAX_CONCURRENT_RTP_PER_WORLD) {
            return false;
        }
        
        // Increment counter
        count.incrementAndGet();
        return true;
    }

    /**
     * Finish RTP in the specified world
     */
    private void finishRTP(World world) {
        String worldName = world.getName();
        AtomicInteger count = worldActiveRTPs.get(worldName);
        if (count != null) {
            count.decrementAndGet();
        }
    }
    
    /**
     * Reset jumlah RTP aktif untuk dunia tertentu
     * Hanya untuk admin untuk kasus emergency
     */
    private void resetWorldRTPCounter(String worldName) {
        AtomicInteger counter = worldActiveRTPs.get(worldName);
        if (counter != null) {
            counter.set(0);
            plugin.getLogger().info("RTP counter for world " + worldName + " has been reset");
        }
    }
    
    /**
     * Batalkan proses RTP untuk pemain tertentu
     * @param playerUUID UUID pemain
     * @return true jika berhasil dibatalkan, false jika tidak sedang dalam proses
     */
    public boolean cancelRTP(UUID playerUUID) {
        if (playersInRTPProcess.contains(playerUUID)) {
            cleanupRTPProcess(playerUUID, null);
            return true;
        }
        return false;
    }
    
    /**
     * Bersihkan semua status RTP untuk pemain
     */
    private void cleanupRTPProcess(UUID playerUUID, World world) {
        // Hapus dari daftar proses
        playersInRTPProcess.remove(playerUUID);
        
        // Cancel timeout task jika ada
        if (rtpTimeoutTasks.containsKey(playerUUID)) {
            Bukkit.getScheduler().cancelTask(rtpTimeoutTasks.get(playerUUID));
            rtpTimeoutTasks.remove(playerUUID);
        }
        
        // Reset counter dunia
        if (world != null) {
            finishRTP(world);
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("nusatown.command.rtp.world")) {
            List<String> worldNames = new ArrayList<>();
            
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                if (config.getConfigurationSection("worlds." + worldName) != null && 
                    config.getBoolean("worlds." + worldName + ".enabled", false) &&
                    worldName.toLowerCase().startsWith(args[0].toLowerCase())) {
                    worldNames.add(worldName);
                }
            }
            
            return worldNames;
        }
        
        return new ArrayList<>();
    }
}