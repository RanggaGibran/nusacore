package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.TeleportUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemFlag;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TPAManager {

    private final NusaCore plugin;
    private final Map<UUID, UUID> tpaRequests = new HashMap<>(); // target -> requester
    private final Map<UUID, UUID> tpaHereRequests = new HashMap<>(); // target -> requester
    private final Map<UUID, BukkitTask> taskMap = new HashMap<>(); // untuk timeout
    private final int REQUEST_TIMEOUT_SECONDS = 60; // waktu timeout permintaan
    private final Map<UUID, Boolean> tpRequestsEnabled = new HashMap<>(); // true = bisa menerima request
    private final Map<UUID, Boolean> autoAcceptEnabled = new HashMap<>(); // true = auto accept

    public TPAManager(NusaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Mengirim permintaan teleport ke pemain lain
     * @param requester Pemain yang meminta teleport
     * @param target Pemain tujuan teleport
     * @return true jika permintaan berhasil dikirim
     */
    public boolean sendTeleportRequest(Player requester, Player target) {
        // Cek apakah pemain dalam combat
        if (plugin.getCombatTagManager().isTagged(requester)) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat meminta teleport saat dalam combat!"));
            return false;
        }
        
        // Cek apakah target dalam combat
        if (plugin.getCombatTagManager().isTagged(target)) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cPemain tersebut sedang dalam combat dan tidak dapat menerima permintaan teleport!"));
            return false;
        }
        
        // Batalkan permintaan sebelumnya dari requester jika ada
        cancelPreviousRequest(requester.getUniqueId());
        
        // PERBAIKAN: Periksa auto-accept terlebih dahulu jika penerimaan permintaan aktif
        boolean canReceive = canReceiveRequests(target);
        boolean hasAutoAccept = hasAutoAcceptEnabled(target);
        
        // Berikan feedback yang jelas tentang status auto-accept
        if (hasAutoAccept && !canReceive) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + target.getName() + " &cmemiliki auto-accept aktif, tetapi tidak menerima permintaan teleport."));
            return false;
        }
        
        // Jika tidak menerima permintaan, tolak request
        if (!canReceive) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + target.getName() + " &ctidak menerima permintaan teleport saat ini."));
            return false;
        }
        
        // Auto-accept jika diaktifkan
        if (hasAutoAccept) {
            // Simpan lokasi untuk fitur /back
            plugin.getBackCommand().setPreviousLocation(requester, requester.getLocation());
            
            // Kirim pesan
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Teleport ke &e" + target.getName() + "&f... (Auto-accept)"));
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + requester.getName() + " &fteleport ke lokasi Anda! (Auto-accept)"));
            
            // Teleport dengan countdown
            TeleportUtils.teleportWithCountdown(
                plugin, requester, target.getLocation(),
                "&bTeleport ke &3" + target.getName(),
                "&7Mohon tunggu..."
            );
            
            return true;
        }
        
        // Proses normal jika tidak ada auto-accept
        tpaRequests.put(target.getUniqueId(), requester.getUniqueId());
        sendRequestMessage(target, requester, false);
        requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                "Permintaan teleport dikirim ke &e" + target.getName() + "&f."));
        setupTimeout(requester, target, false);
        
        return true;
    }
    
    /**
     * Mengirim permintaan agar pemain lain teleport ke pemain ini
     * @param requester Pemain yang meminta
     * @param target Pemain yang diminta teleport ke requester
     * @return true jika permintaan berhasil dikirim
     */
    public boolean sendTeleportHereRequest(Player requester, Player target) {
        // Cek apakah pemain dalam combat
        if (plugin.getCombatTagManager().isTagged(requester)) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat meminta teleport saat dalam combat!"));
            return false;
        }
        
        // Cek apakah target dalam combat
        if (plugin.getCombatTagManager().isTagged(target)) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cPemain tersebut sedang dalam combat dan tidak dapat menerima permintaan teleport!"));
            return false;
        }
        
        // Batalkan permintaan sebelumnya dari requester jika ada
        cancelPreviousRequest(requester.getUniqueId());
        
        // PERBAIKAN: Periksa auto-accept terlebih dahulu jika penerimaan permintaan aktif
        boolean canReceive = canReceiveRequests(target);
        boolean hasAutoAccept = hasAutoAcceptEnabled(target);
        
        // Berikan feedback yang jelas tentang status auto-accept
        if (hasAutoAccept && !canReceive) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + target.getName() + " &cmemiliki auto-accept aktif, tetapi tidak menerima permintaan teleport."));
            return false;
        }
        
        // Jika tidak menerima permintaan, tolak request
        if (!canReceive) {
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + target.getName() + " &ctidak menerima permintaan teleport saat ini."));
            return false;
        }
        
        // Auto-accept jika diaktifkan
        if (hasAutoAccept) {
            // Simpan lokasi untuk fitur /back
            plugin.getBackCommand().setPreviousLocation(target, target.getLocation());
            
            // Kirim pesan
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Teleport ke &e" + requester.getName() + "&f... (Auto-accept)"));
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + target.getName() + " &fteleport ke lokasi Anda! (Auto-accept)"));
            
            // Teleport dengan countdown
            TeleportUtils.teleportWithCountdown(
                plugin, target, requester.getLocation(),
                "&bTeleport ke &3" + requester.getName(),
                "&7Mohon tunggu..."
            );
            
            return true;
        }
        
        // Proses normal jika tidak ada auto-accept
        tpaHereRequests.put(target.getUniqueId(), requester.getUniqueId());
        sendRequestMessage(target, requester, true);
        requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                "Permintaan teleport kemari dikirim ke &e" + target.getName() + "&f."));
        setupTimeout(requester, target, true);
        
        return true;
    }
    
    /**
     * Kirim pesan permintaan dengan hover dan klik
     * @param target Pemain tujuan pesan
     * @param requester Pemain yang meminta
     * @param isHereRequest true jika tpahere, false jika tpa biasa
     */
    private void sendRequestMessage(Player target, Player requester, boolean isHereRequest) {
        String message = isHereRequest ?
                String.format("&e%s &fingin agar Anda teleport ke lokasinya:", requester.getName()) :
                String.format("&e%s &fingin teleport ke lokasi Anda:", requester.getName());
        
        target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + message));
        
        // Komponen utama
        TextComponent mainComponent = new TextComponent(ColorUtils.colorize("&8[&a✓ Terima&8] "));
        
        // Atur hover dan klik untuk tombol terima
        mainComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ColorUtils.colorize("&aKlik untuk menerima permintaan teleport"))));
        mainComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
        
        // Komponen untuk tolak
        TextComponent denyComponent = new TextComponent(ColorUtils.colorize("&8[&c✗ Tolak&8]"));
        denyComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ColorUtils.colorize("&cKlik untuk menolak permintaan teleport"))));
        denyComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
        
        // Kirim komponen ke pemain
        target.spigot().sendMessage(mainComponent, denyComponent);
    }

    /**
     * Menerima permintaan teleport
     * @param target Pemain yang menerima permintaan
     * @return true jika permintaan berhasil diterima
     */
    public boolean acceptRequest(Player target) {
        // Cek apakah pemain dalam combat
        if (plugin.getCombatTagManager().isTagged(target)) {
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&cAnda tidak dapat menerima permintaan teleport saat dalam combat!"));
            return false;
        }
        
        UUID targetId = target.getUniqueId();
        
        // Cek permintaan tpa normal
        if (tpaRequests.containsKey(targetId)) {
            UUID requesterId = tpaRequests.get(targetId);
            Player requester = Bukkit.getPlayer(requesterId);
            
            if (requester == null || !requester.isOnline()) {
                target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut sudah offline."));
                tpaRequests.remove(targetId);
                cancelTask(targetId);
                return false;
            }
            
            // Simpan lokasi untuk fitur /back
            plugin.getBackCommand().setPreviousLocation(requester, requester.getLocation());
            
            // Kirim pesan
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Teleport ke &e" + target.getName() + "&f..."));
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + requester.getName() + " &fteleport ke lokasi Anda!"));
            
            // Teleport dengan countdown
            TeleportUtils.teleportWithCountdown(
                plugin, requester, target.getLocation(),
                "&bTeleport ke &3" + target.getName(),
                "&7Mohon tunggu..."
            );
            
            // Bersihkan data
            tpaRequests.remove(targetId);
            cancelTask(targetId);
            return true;
        } 
        // Cek permintaan tpahere
        else if (tpaHereRequests.containsKey(targetId)) {
            UUID requesterId = tpaHereRequests.get(targetId);
            Player requester = Bukkit.getPlayer(requesterId);
            
            if (requester == null || !requester.isOnline()) {
                target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut sudah offline."));
                tpaHereRequests.remove(targetId);
                cancelTask(targetId);
                return false;
            }
            
            // Simpan lokasi untuk fitur /back
            plugin.getBackCommand().setPreviousLocation(target, target.getLocation());
            
            // Kirim pesan
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Teleport ke &e" + requester.getName() + "&f..."));
            requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "&e" + target.getName() + " &fteleport ke lokasi Anda!"));
            
            // Teleport dengan countdown
            TeleportUtils.teleportWithCountdown(
                plugin, target, requester.getLocation(),
                "&bTeleport ke &3" + requester.getName(),
                "&7Mohon tunggu..."
            );
            
            // Bersihkan data
            tpaHereRequests.remove(targetId);
            cancelTask(targetId);
            return true;
        } 
        
        target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTidak ada permintaan teleport aktif."));
        return false;
    }

    /**
     * Menolak permintaan teleport
     * @param target Pemain yang menolak permintaan
     * @return true jika permintaan berhasil ditolak
     */
    public boolean denyRequest(Player target) {
        UUID targetId = target.getUniqueId();
        
        // Cek permintaan tpa normal
        if (tpaRequests.containsKey(targetId)) {
            UUID requesterId = tpaRequests.get(targetId);
            Player requester = Bukkit.getPlayer(requesterId);
            
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&e" + target.getName() + " &fmenolak permintaan teleport Anda."));
            }
            
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Anda menolak permintaan teleport."));
            
            // Bersihkan data
            tpaRequests.remove(targetId);
            cancelTask(targetId);
            return true;
        } 
        // Cek permintaan tpahere
        else if (tpaHereRequests.containsKey(targetId)) {
            UUID requesterId = tpaHereRequests.get(targetId);
            Player requester = Bukkit.getPlayer(requesterId);
            
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&e" + target.getName() + " &fmenolak permintaan teleport kemari Anda."));
            }
            
            target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Anda menolak permintaan teleport kemari."));
            
            // Bersihkan data
            tpaHereRequests.remove(targetId);
            cancelTask(targetId);
            return true;
        }
        
        target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTidak ada permintaan teleport aktif."));
        return false;
    }
    
    /**
     * Membatalkan permintaan teleport yang telah dikirim
     * @param requester Pemain yang ingin membatalkan permintaan
     * @return true jika permintaan berhasil dibatalkan
     */
    public boolean cancelRequest(Player requester) {
        UUID requesterId = requester.getUniqueId();
        
        // Cari permintaan tpa biasa
        for (Map.Entry<UUID, UUID> entry : tpaRequests.entrySet()) {
            if (entry.getValue().equals(requesterId)) {
                UUID targetId = entry.getKey();
                Player target = Bukkit.getPlayer(targetId);
                
                if (target != null && target.isOnline()) {
                    target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&e" + requester.getName() + " &fmembatalkan permintaan teleport."));
                }
                
                requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "Permintaan teleport dibatalkan."));
                
                // Bersihkan data
                tpaRequests.remove(targetId);
                cancelTask(targetId);
                return true;
            }
        }
        
        // Cari permintaan tpahere
        for (Map.Entry<UUID, UUID> entry : tpaHereRequests.entrySet()) {
            if (entry.getValue().equals(requesterId)) {
                UUID targetId = entry.getKey();
                Player target = Bukkit.getPlayer(targetId);
                
                if (target != null && target.isOnline()) {
                    target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&e" + requester.getName() + " &fmembatalkan permintaan teleport kemari."));
                }
                
                requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "Permintaan teleport kemari dibatalkan."));
                
                // Bersihkan data
                tpaHereRequests.remove(targetId);
                cancelTask(targetId);
                return true;
            }
        }
        
        requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki permintaan teleport aktif."));
        return false;
    }
    
    /**
     * Membersihkan semua permintaan dan task yang terkait dengan pemain tertentu
     * @param playerId ID pemain yang akan dibersihkan datanya
     */
    public void clearRequests(UUID playerId) {
        // Bersihkan sebagai target
        tpaRequests.remove(playerId);
        tpaHereRequests.remove(playerId);
        cancelTask(playerId);
        
        // Bersihkan sebagai requester
        tpaRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(playerId)) {
                cancelTask(entry.getKey());
                return true;
            }
            return false;
        });
        
        tpaHereRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(playerId)) {
                cancelTask(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Setup timeout untuk permintaan teleport
     */
    private void setupTimeout(Player requester, Player target, boolean isHereRequest) {
        UUID targetId = target.getUniqueId();
        
        // Batalkan task sebelumnya jika ada
        cancelTask(targetId);
        
        // Buat task baru
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if ((isHereRequest && tpaHereRequests.containsKey(targetId)) || 
                (!isHereRequest && tpaRequests.containsKey(targetId))) {
                
                // Beritahu kedua pemain
                if (requester.isOnline()) {
                    requester.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "Permintaan teleport ke &e" + target.getName() + " &ftelah kedaluwarsa."));
                }
                
                if (target.isOnline()) {
                    target.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "Permintaan teleport dari &e" + requester.getName() + " &ftelah kedaluwarsa."));
                }
                
                // Bersihkan data
                if (isHereRequest) {
                    tpaHereRequests.remove(targetId);
                } else {
                    tpaRequests.remove(targetId);
                }
                
                taskMap.remove(targetId);
            }
        }, REQUEST_TIMEOUT_SECONDS * 20L); // Convert to ticks
        
        taskMap.put(targetId, task);
    }
    
    /**
     * Batalkan task timeout yang terkait dengan target
     */
    private void cancelTask(UUID targetId) {
        if (taskMap.containsKey(targetId)) {
            taskMap.get(targetId).cancel();
            taskMap.remove(targetId);
        }
    }
    
    /**
     * Batalkan semua permintaan sebelumnya dari requester
     */
    private void cancelPreviousRequest(UUID requesterId) {
        // Cari dan batalkan permintaan tpa sebelumnya
        for (Map.Entry<UUID, UUID> entry : tpaRequests.entrySet()) {
            if (entry.getValue().equals(requesterId)) {
                UUID targetId = entry.getKey();
                tpaRequests.remove(targetId);
                cancelTask(targetId);
                break;
            }
        }
        
        // Cari dan batalkan permintaan tpahere sebelumnya
        for (Map.Entry<UUID, UUID> entry : tpaHereRequests.entrySet()) {
            if (entry.getValue().equals(requesterId)) {
                UUID targetId = entry.getKey();
                tpaHereRequests.remove(targetId);
                cancelTask(targetId);
                break;
            }
        }
    }

    /**
     * Periksa apakah pemain menerima permintaan teleport
     * 
     * @param player Pemain yang diperiksa
     * @return true jika pemain menerima permintaan teleport
     */
    public boolean canReceiveRequests(Player player) {
        return tpRequestsEnabled.getOrDefault(player.getUniqueId(), true); // Default: bisa menerima
    }

    /**
     * Periksa apakah pemain memiliki auto-accept aktif
     * 
     * @param player Pemain yang diperiksa
     * @return true jika pemain memiliki auto-accept aktif
     */
    public boolean hasAutoAcceptEnabled(Player player) {
        return autoAcceptEnabled.getOrDefault(player.getUniqueId(), false); // Default: tidak auto-accept
    }

    /**
     * Toggle status penerimaan permintaan teleport
     * 
     * @param player Pemain yang toggle statusnya
     * @return Status baru setelah toggle
     */
    public boolean toggleRequestReception(Player player) {
        UUID playerId = player.getUniqueId();
        boolean newState = !tpRequestsEnabled.getOrDefault(playerId, true);
        tpRequestsEnabled.put(playerId, newState);
        return newState;
    }

    /**
     * Toggle status auto-accept permintaan teleport
     * 
     * @param player Pemain yang toggle statusnya
     * @return Status baru setelah toggle
     */
    public boolean toggleAutoAccept(Player player) {
        UUID playerId = player.getUniqueId();
        boolean newState = !autoAcceptEnabled.getOrDefault(playerId, false);
        autoAcceptEnabled.put(playerId, newState);
        return newState;
    }

    /**
     * Buka GUI pengaturan teleport untuk pemain
     * 
     * @param player Pemain yang membuka GUI
     */
    public void openTPToggleGUI(Player player) {
        // Buat inventory dengan ukuran 3 baris (27 slot)
        Inventory inventory = Bukkit.createInventory(null, 27, 
                ColorUtils.colorize("&8Pengaturan Teleport"));
        
        // Isi border inventory dengan glass pane
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", null, false));
            }
        }
        
        // Ambil pengaturan pemain saat ini
        boolean requestsEnabled = canReceiveRequests(player);
        boolean autoAccept = hasAutoAcceptEnabled(player);
        
        // Buat item untuk toggle menerima permintaan
        ItemStack toggleRequests = createGuiItem(
            requestsEnabled ? Material.LIME_DYE : Material.RED_DYE,
            requestsEnabled ? "&aMenerima Permintaan: &2AKTIF" : "&cMenerima Permintaan: &4NONAKTIF",
            Arrays.asList(
                "&7Status: " + (requestsEnabled ? "&aAktif" : "&cNonaktif"),
                "",
                requestsEnabled 
                    ? "&aPlayer lain dapat mengirim permintaan teleport ke Anda." 
                    : "&cPlayer lain tidak dapat mengirim permintaan teleport ke Anda.",
                "",
                "&eKlik untuk " + (requestsEnabled ? "&cnonaktifkan" : "&aaktifkan")
            ),
            false
        );
        
        // Buat item untuk toggle auto-accept
        ItemStack toggleAutoAccept = createGuiItem(
            autoAccept ? Material.LIME_DYE : Material.RED_DYE,
            autoAccept ? "&aAuto-Accept: &2AKTIF" : "&cAuto-Accept: &4NONAKTIF",
            Arrays.asList(
                "&7Status: " + (autoAccept ? "&aAktif" : "&cNonaktif"),
                "",
                autoAccept 
                    ? "&aSemua permintaan teleport akan otomatis diterima." 
                    : "&cPermintaan teleport memerlukan persetujuan manual.",
                "",
                "&eKlik untuk " + (autoAccept ? "&cnonaktifkan" : "&aaktifkan")
            ),
            false
        );
        
        // Tambahkan item informasi di tengah
        ItemStack infoItem = createGuiItem(
            Material.PAPER,
            "&f&lPengaturan Teleport",
            Arrays.asList(
                "&7Gunakan menu ini untuk mengatur",
                "&7preferensi teleportasi Anda.",
                "",
                "&8» &eMenerima Permintaan: &7Apakah pemain lain",
                "&7  dapat mengirim permintaan teleport ke Anda.",
                "",
                "&8» &eAuto-Accept: &7Otomatis menerima semua",
                "&7  permintaan teleport yang masuk.",
                "",
                "&c&lCatatan: &7Auto-Accept hanya berfungsi jika",
                "&7Menerima Permintaan dalam status aktif."
            ),
            false
        );
        
        // Tambahkan item ke inventory
        inventory.setItem(11, toggleRequests);
        inventory.setItem(15, toggleAutoAccept);
        inventory.setItem(4, infoItem);
        
        // Buka inventory untuk pemain
        player.openInventory(inventory);
    }

    /**
     * Buat item untuk GUI
     */
    private ItemStack createGuiItem(Material material, String name, List<String> lore, boolean enchanted) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            
            if (lore != null) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ColorUtils.colorize(line));
                }
                meta.setLore(coloredLore);
            }
            
            if (enchanted) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    /**
     * Simpan data preferensi teleport
     */
    public void savePreferences() {
        // Buat file konfigurasi untuk preferensi
        File prefFile = new File(plugin.getDataFolder(), "teleport_preferences.yml");
        YamlConfiguration config = new YamlConfiguration();
        
        // Simpan data tpRequestsEnabled
        for (Map.Entry<UUID, Boolean> entry : tpRequestsEnabled.entrySet()) {
            config.set("request_reception." + entry.getKey().toString(), entry.getValue());
        }
        
        // Simpan data autoAcceptEnabled
        for (Map.Entry<UUID, Boolean> entry : autoAcceptEnabled.entrySet()) {
            config.set("auto_accept." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            config.save(prefFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Tidak dapat menyimpan preferensi teleport: " + e.getMessage());
        }
    }

    /**
     * Muat data preferensi teleport
     */
    public void loadPreferences() {
        File prefFile = new File(plugin.getDataFolder(), "teleport_preferences.yml");
        
        if (!prefFile.exists()) {
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(prefFile);
        
        // Muat data tpRequestsEnabled
        ConfigurationSection requestSection = config.getConfigurationSection("request_reception");
        if (requestSection != null) {
            for (String key : requestSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    boolean value = requestSection.getBoolean(key);
                    tpRequestsEnabled.put(playerId, value);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID tidak valid dalam preferensi teleport: " + key);
                }
            }
        }
        
        // Muat data autoAcceptEnabled
        ConfigurationSection acceptSection = config.getConfigurationSection("auto_accept");
        if (acceptSection != null) {
            for (String key : acceptSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    boolean value = acceptSection.getBoolean(key);
                    autoAcceptEnabled.put(playerId, value);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID tidak valid dalam preferensi teleport: " + key);
                }
            }
        }
    }
}