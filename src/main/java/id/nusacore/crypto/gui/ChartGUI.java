package id.nusacore.crypto.gui;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoManager;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ChartGUI implements Listener {
    private final NusaCore plugin;
    private final CryptoManager cryptoManager;
    
    // Track current period view for players
    private final Map<UUID, String> currentPeriod = new HashMap<>();
    // Track currently viewed crypto for players
    private final Map<UUID, String> viewedCrypto = new HashMap<>();
    
    // Constants for chart materials
    private static final Material UP_MATERIAL = Material.LIME_STAINED_GLASS_PANE;
    private static final Material DOWN_MATERIAL = Material.RED_STAINED_GLASS_PANE;
    private static final Material NEUTRAL_MATERIAL = Material.WHITE_STAINED_GLASS_PANE;
    private static final Material CURRENT_MATERIAL = Material.GOLD_BLOCK;
    
    public ChartGUI(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
        
        // Register this as listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Show price chart for a cryptocurrency
     * @param player Player viewing the chart
     * @param currencyId Cryptocurrency ID
     * @param period Period to view (24h, 7d, 30d)
     */
    public void showChart(Player player, String currencyId, String period) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        // Get price history
        List<Double> history = cryptoManager.getPriceHistory(currencyId);
        if (history.isEmpty()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTidak ada data historis untuk " + crypto.getSymbol() + "!"));
            return;
        }
        
        // Create inventory
        Inventory inventory = Bukkit.createInventory(null, 54, "§b§lGrafik " + crypto.getSymbol());
        
        // Fill border
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Show crypto info
        inventory.setItem(4, createInfoItem(crypto));
        
        // Fill chart
        fillChart(inventory, history, period);
        
        // Time period buttons
        inventory.setItem(45, createPeriodButton("24h", period.equals("24h")));
        inventory.setItem(46, createPeriodButton("7d", period.equals("7d")));
        inventory.setItem(47, createPeriodButton("30d", period.equals("30d")));
        
        // Add action buttons
        addActionButtons(inventory, crypto);
        
        // Open inventory
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
        
        // Track view data
        currentPeriod.put(player.getUniqueId(), period);
        viewedCrypto.put(player.getUniqueId(), currencyId);
    }
    
    /**
     * Alias untuk showChart (untuk kompatibilitas)
     */
    public void openChartGUI(Player player, String currencyId, String period) {
        showChart(player, currencyId, period);
    }
    
    /**
     * Create info item for cryptocurrency
     */
    private ItemStack createInfoItem(CryptoCurrency crypto) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Harga Saat Ini: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens");
        
        // Calculate 24h change if possible
        List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
        if (history.size() >= 2) {
            double current = crypto.getCurrentPrice();
            double prev = history.get(history.size() - 2);
            double change = ((current - prev) / prev) * 100;
            String changePrefix = change >= 0 ? "§a+" : "§c";
            lore.add("§7Perubahan 24h: " + changePrefix + String.format("%.2f", change) + "%");
        }
        
        lore.add("§7Volatilitas: §f" + String.format("%.1f", crypto.getVolatility() * 100) + "%");
        lore.add("§7Risiko: " + crypto.getRisk().getColor() + crypto.getRisk().getDisplayName());
        lore.add("");
        lore.add("§eKlik tombol periode waktu di bawah");
        lore.add("§euntuk mengubah tampilan grafik");
        
        return createItem(
            Material.GOLD_INGOT, 
            "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
            lore
        );
    }
    
    /**
     * Fill chart section of inventory based on price history
     */
    private void fillChart(Inventory inventory, List<Double> history, String period) {
        // Tambahkan validasi untuk mencegah crash
        if (history.isEmpty()) {
            // Tampilkan pesan "No Data" di tengah chart
            ItemStack noDataItem = createItem(Material.BARRIER, "§c§lNo Data", 
                Collections.singletonList("§7Tidak ada data historis yang tersedia"));
            inventory.setItem(31, noDataItem);
            return;
        }
        
        // Determine number of data points to show based on period
        int dataPoints;
        switch (period) {
            case "7d":
                dataPoints = Math.min(7, history.size());
                break;
            case "30d":
                dataPoints = Math.min(30, history.size());
                break;
            case "24h":
            default:
                dataPoints = Math.min(24, history.size());
                break;
        }
        
        // Extract relevant part of history
        List<Double> chartData;
        if (history.size() <= dataPoints) {
            chartData = new ArrayList<>(history);
        } else {
            chartData = history.subList(history.size() - dataPoints, history.size());
        }
        
        // Find min and max for scaling
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double price : chartData) {
            min = Math.min(min, price);
            max = Math.max(max, price);
        }
        
        // Add 10% padding to range to avoid flat charts
        double range = max - min;
        if (range < 0.00001) { // Very small range, add artificial padding
            min = min * 0.95;
            max = max * 1.05;
            range = max - min;
        }
        
        // Chart display slots (from left to right)
        int[] slots = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        
        // Clear previous chart
        for (int slot : slots) {
            inventory.setItem(slot, null);
        }
        
        // Show as many data points as possible based on slots available
        int numSlots = Math.min(slots.length, chartData.size());
        for (int i = 0; i < numSlots; i++) {
            int dataIndex = chartData.size() - numSlots + i;
            if (dataIndex < 0) continue;
            
            double price = chartData.get(dataIndex);
            
            // Normalize position (0-2 for rows)
            double normalized = (price - min) / range;
            int row = normalized >= 0.66 ? 0 : (normalized >= 0.33 ? 1 : 2);
            
            // Determine color based on trend
            Material material;
            if (dataIndex > 0) {
                double prevPrice = chartData.get(dataIndex - 1);
                if (price > prevPrice) {
                    material = UP_MATERIAL;
                } else if (price < prevPrice) {
                    material = DOWN_MATERIAL;
                } else {
                    material = NEUTRAL_MATERIAL;
                }
            } else {
                material = NEUTRAL_MATERIAL;
            }
            
            // If last point, make it gold block
            if (dataIndex == chartData.size() - 1) {
                material = CURRENT_MATERIAL;
            }
            
            // Calculate slot index based on position
            int slot = slots[i] - (row * 9); // Adjust row based on normalized value
            
            // Create chart point
            ItemStack point = new ItemStack(material);
            ItemMeta meta = point.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§fHarga: §e" + formatPrice(price) + " Tokens");
                
                List<String> lore = new ArrayList<>();
                // Show point number relative to time period
                switch (period) {
                    case "7d":
                        lore.add("§7" + (7 - (chartData.size() - 1 - dataIndex)) + " hari yang lalu");
                        break;
                    case "30d":
                        lore.add("§7" + (30 - (chartData.size() - 1 - dataIndex)) + " hari yang lalu");
                        break;
                    case "24h":
                    default:
                        lore.add("§7" + (24 - (chartData.size() - 1 - dataIndex)) + " jam yang lalu");
                        break;
                }
                
                // Add price change info if possible
                if (dataIndex > 0) {
                    double prevPrice = chartData.get(dataIndex - 1);
                    double change = ((price - prevPrice) / prevPrice) * 100;
                    String changePrefix = change >= 0 ? "§a+" : "§c";
                    lore.add(changePrefix + String.format("%.2f", change) + "%");
                }
                
                meta.setLore(lore);
                point.setItemMeta(meta);
            }
            
            // Place in inventory
            inventory.setItem(slot, point);
        }
        
        // Add min/max indicators
        ItemStack maxItem = createItem(Material.EMERALD, "§aHarga Tertinggi", 
            Collections.singletonList("§f" + formatPrice(max) + " Tokens"));
        ItemStack minItem = createItem(Material.REDSTONE, "§cHarga Terendah", 
            Collections.singletonList("§f" + formatPrice(min) + " Tokens"));
        
        inventory.setItem(18, maxItem);
        inventory.setItem(36, minItem);
    }
    
    /**
     * Create period button
     */
    private ItemStack createPeriodButton(String period, boolean selected) {
        Material material = selected ? Material.LIME_DYE : Material.GRAY_DYE;
        String name;
        List<String> lore = new ArrayList<>();
        
        switch (period) {
            case "7d":
                name = "§b7 Hari";
                lore.add("§7Lihat grafik pergerakan harga");
                lore.add("§7selama 7 hari terakhir");
                break;
            case "30d":
                name = "§b30 Hari";
                lore.add("§7Lihat grafik pergerakan harga");
                lore.add("§7selama 30 hari terakhir");
                break;
            case "24h":
            default:
                name = "§b24 Jam";
                lore.add("§7Lihat grafik pergerakan harga");
                lore.add("§7selama 24 jam terakhir");
                break;
        }
        
        if (selected) {
            lore.add("");
            lore.add("§aAktif");
        } else {
            lore.add("");
            lore.add("§eKlik untuk beralih ke periode ini");
        }
        
        return createItem(material, name, lore);
    }
    
    /**
     * Add action buttons
     */
    private void addActionButtons(Inventory inventory, CryptoCurrency crypto) {
        // Buy button
        List<String> buyLore = new ArrayList<>();
        buyLore.add("§7Harga: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens");
        buyLore.add("§7Klik untuk membeli " + crypto.getSymbol());
        inventory.setItem(49, createItem(Material.EMERALD, "§a§lBeli " + crypto.getSymbol(), buyLore));
        
        // Info button
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Klik untuk melihat informasi detail");
        infoLore.add("§7tentang " + crypto.getSymbol());
        inventory.setItem(50, createItem(Material.BOOK, "§b§lInfo " + crypto.getSymbol(), infoLore));
        
        // Back button
        List<String> backLore = new ArrayList<>();
        backLore.add("§7Kembali ke menu market");
        inventory.setItem(53, createItem(Material.ARROW, "§c§lKembali", backLore));
    }
    
    /**
     * Handle click events in chart GUI
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (!title.contains("§b§lGrafik ") || clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        // Always cancel click events in our GUI
        event.setCancelled(true);
        
        UUID playerId = player.getUniqueId();
        if (!viewedCrypto.containsKey(playerId)) {
            return;
        }
        
        String currencyId = viewedCrypto.get(playerId);
        int slot = event.getSlot();
        
        // Handle period buttons
        if (slot == 45) { // 24h button
            showChart(player, currencyId, "24h");
        } else if (slot == 46) { // 7d button
            showChart(player, currencyId, "7d");
        } else if (slot == 47) { // 30d button
            showChart(player, currencyId, "30d");
        }
        // Handle action buttons
        else if (slot == 49) { // Buy button
            player.closeInventory();
            plugin.getCryptoGUI().openBuyAmountGUI(player, currencyId);
        } else if (slot == 50) { // Info button
            player.closeInventory();
            plugin.getCryptoGUI().openCryptoInfo(player, currencyId);
        } else if (slot == 53) { // Back button
            player.closeInventory();
            plugin.getCryptoGUI().openMarket(player);
        }
    }
    
    /**
     * Format price based on its value
     */
    private String formatPrice(double price) {
        if (price >= 1000) {
            return String.format("%,.0f", price);
        } else if (price >= 100) {
            return String.format("%.1f", price);
        } else if (price >= 1) {
            return String.format("%.2f", price);
        } else {
            return String.format("%.6f", price);
        }
    }
    
    /**
     * Create an ItemStack for use in GUI
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Clean up resources when a player leaves
     */
    public void clearPlayerData(UUID playerId) {
        currentPeriod.remove(playerId);
        viewedCrypto.remove(playerId);
    }
}