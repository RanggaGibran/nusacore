package id.nusacore.gui;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RankGUI {

    private final NusaCore plugin;
    
    // Map untuk menyimpan Material representasi untuk setiap rank
    private static final Map<String, Material> RANK_MATERIALS = new HashMap<>();
    
    // Tambahkan map untuk menyimpan data pagination
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerViewingRank = new HashMap<>();
    private static final int BENEFITS_PER_PAGE = 14; // Jumlah benefit per halaman
    
    static {
        // Tetapkan material untuk setiap rank
        RANK_MATERIALS.put("alkemis", Material.EMERALD);
        RANK_MATERIALS.put("phantom", Material.AMETHYST_SHARD);
        RANK_MATERIALS.put("oracle", Material.LAPIS_LAZULI);
        RANK_MATERIALS.put("leviathan", Material.DIAMOND);
        RANK_MATERIALS.put("celestial", Material.NETHER_STAR);
    }

    public RankGUI(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Buka menu utama daftar rank untuk pemain
     * 
     * @param player Pemain yang membuka menu
     */
    public void openMainMenu(Player player) {
        // Buat inventory dengan ukuran 3 baris (27 slot)
        Inventory inventory = Bukkit.createInventory(null, 27, 
                ColorUtils.colorize(plugin.getRankManager().getTitle()));
        
        // Isi border inventory
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Tambahkan tombol close
        inventory.setItem(22, GUIUtils.createCloseButton());
        
        // Tambahkan semua rank ke dalam inventory
        List<String> rankIds = plugin.getRankManager().getRankIds();
        int slot = 10;
        
        for (String rankId : rankIds) {
            Map<String, Object> rankData = plugin.getRankManager().getRankData(rankId);
            String displayName = (String) rankData.get("display-name");
            String cost = (String) rankData.get("cost");
            String description = (String) rankData.get("description");
            
            Material material = RANK_MATERIALS.getOrDefault(rankId.toLowerCase(), Material.PAPER);
            
            List<String> lore = new ArrayList<>();
            lore.add("&7" + description);
            lore.add("");
            lore.add("&6Harga: &f" + cost);
            lore.add("");
            lore.add("&eKlik untuk melihat detail!");
            
            inventory.setItem(slot, GUIUtils.createItem(material, displayName, lore, true));
            
            // Increment slot, skip border
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        
        // Buka inventory untuk pemain
        player.openInventory(inventory);
    }
    
    /**
     * Buka menu detail untuk rank tertentu
     * 
     * @param player Pemain yang membuka menu
     * @param rankId ID rank yang akan ditampilkan
     * @param page Halaman yang akan ditampilkan (dimulai dari 0)
     */
    public void openRankDetails(Player player, String rankId, int page) {
        // Reset atau update data pagination
        playerPages.put(player.getUniqueId(), page);
        playerViewingRank.put(player.getUniqueId(), rankId);
        
        Map<String, Object> rankData = plugin.getRankManager().getRankData(rankId);
        
        String displayName = (String) rankData.get("display-name");
        String cost = (String) rankData.get("cost");
        String description = (String) rankData.get("description");
        @SuppressWarnings("unchecked")
        List<String> allBenefits = (List<String>) rankData.get("benefits");
        
        // Hitung total halaman
        int totalPages = (int) Math.ceil((double) allBenefits.size() / BENEFITS_PER_PAGE);
        
        // Batas page
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        
        // Buat inventory dengan ukuran 5 baris (45 slot) untuk menampung tombol navigasi
        Inventory inventory = Bukkit.createInventory(null, 45, 
                ColorUtils.colorize("&8Rank: " + displayName + " &7(Hal. " + (page + 1) + "/" + totalPages + ")"));
        
        // Isi border inventory
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Tambahkan item representasi rank
        Material material = RANK_MATERIALS.getOrDefault(rankId.toLowerCase(), Material.PAPER);
        List<String> lore = new ArrayList<>();
        lore.add("&7" + description);
        lore.add("");
        lore.add("&6Harga: &f" + cost);
        
        inventory.setItem(4, GUIUtils.createItem(material, displayName, lore, true));
        
        // Hitung indeks awal dan akhir benefit untuk halaman ini
        int startIndex = page * BENEFITS_PER_PAGE;
        int endIndex = Math.min(startIndex + BENEFITS_PER_PAGE, allBenefits.size());
        
        // Filter benefits untuk halaman ini
        List<String> pageBenefits = allBenefits.subList(startIndex, endIndex);
        
        // Tambahkan benefit rank
        int slot = 11;
        for (String benefit : pageBenefits) {
            inventory.setItem(slot, GUIUtils.createItem(Material.BOOK, "&a✓ Benefit", 
                    Collections.singletonList(benefit), false));
            
            // Increment slot, skip border
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        
        // Tambahkan tombol kembali dan beli
        inventory.setItem(36, GUIUtils.createBackButton());
        
        // Tombol navigasi halaman
        if (page > 0) {
            // Tombol halaman sebelumnya
            inventory.setItem(39, GUIUtils.createItem(Material.ARROW, "&e« Halaman Sebelumnya", 
                    Collections.singletonList("&7Klik untuk melihat benefit sebelumnya"), false));
        }
        
        if (page < totalPages - 1) {
            // Tombol halaman berikutnya
            inventory.setItem(41, GUIUtils.createItem(Material.ARROW, "&eHalaman Berikutnya »", 
                    Collections.singletonList("&7Klik untuk melihat benefit selanjutnya"), false));
        }
        
        // Info halaman
        inventory.setItem(40, GUIUtils.createItem(Material.PAPER, "&fHalaman " + (page + 1) + "/" + totalPages, 
                Collections.singletonList("&7" + allBenefits.size() + " total benefits"), false));
        
        ItemStack buyButton = GUIUtils.createItem(Material.GOLD_INGOT, "&a&lBELI SEKARANG", 
                Arrays.asList(
                    "&7Klik untuk membeli rank " + displayName,
                    "",
                    "&7Harga: &f" + cost
                ), false);
        inventory.setItem(44, buyButton);
        
        // Buka inventory untuk pemain
        player.openInventory(inventory);
    }
    
    /**
     * Buka menu detail untuk rank tertentu (Halaman pertama)
     * 
     * @param player Pemain yang membuka menu
     * @param rankId ID rank yang akan ditampilkan
     */
    public void openRankDetails(Player player, String rankId) {
        openRankDetails(player, rankId, 0);
    }
    
    /**
     * Navigasi ke halaman berikutnya
     * 
     * @param player Pemain yang menavigasi
     */
    public void nextPage(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerPages.containsKey(playerId) && playerViewingRank.containsKey(playerId)) {
            int currentPage = playerPages.get(playerId);
            String rankId = playerViewingRank.get(playerId);
            openRankDetails(player, rankId, currentPage + 1);
        }
    }
    
    /**
     * Navigasi ke halaman sebelumnya
     * 
     * @param player Pemain yang menavigasi
     */
    public void previousPage(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerPages.containsKey(playerId) && playerViewingRank.containsKey(playerId)) {
            int currentPage = playerPages.get(playerId);
            String rankId = playerViewingRank.get(playerId);
            openRankDetails(player, rankId, currentPage - 1);
        }
    }
    
    /**
     * Bersihkan data pagination saat pemain keluar atau inventori ditutup
     * 
     * @param player Pemain yang datanya akan dibersihkan
     */
    public void clearPaginationData(Player player) {
        UUID playerId = player.getUniqueId();
        playerPages.remove(playerId);
        playerViewingRank.remove(playerId);
    }
}