package id.nusacore.crypto.gui;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoManager;
import id.nusacore.crypto.CryptoRisk;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryView;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class CryptoGUI implements Listener {
    private final NusaCore plugin;
    private final CryptoManager cryptoManager;
    private final ChartGUI chartGUI;
    
    // Track which GUI type a player has open
    private final Map<UUID, String> openGUITypes = new HashMap<>();
    
    // Track which crypto a player is viewing/buying/selling
    private final Map<UUID, String> selectedCrypto = new HashMap<>();
    
    // Track buy/sell amounts
    private final Map<UUID, Integer> transactionAmounts = new HashMap<>();
    
    // Constants for GUI types
    private static final String MAIN_MENU = "main_menu";
    private static final String MARKET = "market";
    private static final String PORTFOLIO = "portfolio";
    private static final String BUY = "buy";
    private static final String SELL = "sell";
    private static final String INFO = "info";
    
    // Materials for different cryptocurrencies (for visual distinction)
    private static final Map<String, Material> CRYPTO_MATERIALS = new HashMap<>();
    
    static {
        // Initialize materials for major cryptocurrencies
        CRYPTO_MATERIALS.put("btc", Material.GOLD_BLOCK);
        CRYPTO_MATERIALS.put("eth", Material.DIAMOND);
        CRYPTO_MATERIALS.put("bnb", Material.YELLOW_GLAZED_TERRACOTTA);
        CRYPTO_MATERIALS.put("doge", Material.BONE);
        CRYPTO_MATERIALS.put("usdt", Material.EMERALD_BLOCK);
        CRYPTO_MATERIALS.put("sol", Material.AMETHYST_SHARD);
    }
    
    public CryptoGUI(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
        this.chartGUI = new ChartGUI(plugin);
        
        // Register this as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Open the main crypto menu for a player
     */
    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 36, "§b§lCrypto Exchange");
        
        // Fill with background glass
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Market button
        inventory.setItem(10, createItem(
            Material.GOLD_INGOT,
            "§e§lMarket Crypto",
            Arrays.asList(
                "§7Lihat harga semua cryptocurrency",
                "§7yang tersedia untuk diperdagangkan.",
                "",
                "§7Status: " + getMarketStatus(),
                "",
                "§eKlik untuk melihat market"
            ),
            false
        ));
        
        // Portfolio button
        inventory.setItem(12, createItem(
            Material.CHEST,
            "§a§lPortfolio",
            Arrays.asList(
                "§7Kelola investasi cryptocurrency Anda.",
                "§7Lihat aset dan nilai portofolio Anda.",
                "",
                "§7Total Nilai: §f" + String.format("%.1f", cryptoManager.getPortfolioValue(player)) + " Tokens",
                "",
                "§eKlik untuk melihat portfolio"
            ),
            false
        ));
        
        // Buy crypto button
        inventory.setItem(14, createItem(
            Material.EMERALD,
            "§a§lBeli Crypto",
            Arrays.asList(
                "§7Investasikan token Anda untuk",
                "§7membeli cryptocurrency.",
                "",
                "§7Token Anda: §f" + plugin.getTokenManager().getTokens(player),
                "",
                "§eKlik untuk membeli crypto"
            ),
            false
        ));
        
        // Sell crypto button
        inventory.setItem(16, createItem(
            Material.REDSTONE,
            "§c§lJual Crypto",
            Arrays.asList(
                "§7Jual cryptocurrency Anda untuk",
                "§7mendapatkan token.",
                "",
                "§7Portfolio: §f" + cryptoManager.getPlayerInvestments(player).size() + " jenis crypto",
                "",
                "§eKlik untuk menjual crypto"
            ),
            false
        ));
        
        // Info button
        inventory.setItem(31, createItem(
            Material.BOOK,
            "§b§lInformasi",
            Arrays.asList(
                "§7Pelajari lebih lanjut tentang",
                "§7sistem cryptocurrency di server."
            ),
            false
        ));
        
        // Close button
        inventory.setItem(32, createItem(
            Material.BARRIER,
            "§c§lTutup",
            Collections.singletonList("§7Klik untuk menutup menu"),
            false
        ));
        
        // Open the inventory and track the player
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
        openGUITypes.put(player.getUniqueId(), MAIN_MENU);
    }
    
    /**
     * Get current market status string
     */
    private String getMarketStatus() {
        // Simple logic to determine market status based on last few price changes
        // This could be enhanced with more sophisticated analysis
        int bullCount = 0;
        int bearCount = 0;
        
        for (CryptoCurrency crypto : cryptoManager.getAllCryptocurrencies()) {
            List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
            if (history.size() >= 2) {
                double lastPrice = history.get(history.size() - 1);
                double prevPrice = history.get(history.size() - 2);
                
                if (lastPrice > prevPrice) {
                    bullCount++;
                } else if (lastPrice < prevPrice) {
                    bearCount++;
                }
            }
        }
        
        if (bullCount > bearCount) {
            return "§a§lBULLISH";
        } else if (bearCount > bullCount) {
            return "§c§lBEARISH";
        } else {
            return "§6§lSTABIL";
        }
    }
    
    /**
     * Open the market overview GUI
     */
    public void openMarket(Player player) {
        List<CryptoCurrency> cryptos = cryptoManager.getAllCryptocurrencies();
        int size = ((cryptos.size() / 9) + (cryptos.size() % 9 > 0 ? 2 : 1)) * 9;
        size = Math.min(54, Math.max(27, size)); // Ensure between 27 and 54 slots
        
        Inventory inventory = Bukkit.createInventory(null, size, "§e§lMarket Crypto");
        
        // Fill border
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Add cryptocurrencies
        int slot = 10;
        for (CryptoCurrency crypto : cryptos) {
            Material material = CRYPTO_MATERIALS.getOrDefault(crypto.getId(), Material.GOLD_NUGGET);
            
            // Get price change data
            List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
            double currentPrice = crypto.getCurrentPrice();
            double percentChange = 0;
            String changeStr = "§7Stable";
            
            if (history.size() >= 2) {
                double prevPrice = history.get(history.size() - 2);
                percentChange = ((currentPrice - prevPrice) / prevPrice) * 100;
                String changeColor = percentChange >= 0 ? "§a" : "§c";
                String changeSymbol = percentChange >= 0 ? "▲" : "▼";
                changeStr = changeColor + changeSymbol + " " + String.format("%.2f", Math.abs(percentChange)) + "%";
            }
            
            // Create item representing the cryptocurrency
            inventory.setItem(slot, createItem(
                material,
                "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
                Arrays.asList(
                    "§7Harga: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                    "§7Perubahan: " + changeStr,
                    "§7Risiko: " + crypto.getRisk().getColor() + crypto.getRisk().getDisplayName(),
                    "",
                    "§eKlik untuk informasi lebih lanjut",
                    "§bShift+Klik untuk melihat grafik"
                ),
                false
            ));
            
            // Move to next slot, skipping border columns
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        
        // Add back button
        inventory.setItem(size - 5, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        openGUITypes.put(player.getUniqueId(), MARKET);
    }
    
    /**
     * Open portfolio GUI showing player's investments
     */
    public void openPortfolio(Player player) {
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        int size = 36; // 4 rows
        
        Inventory inventory = Bukkit.createInventory(null, size, "§a§lPortfolio Crypto");
        
        // Fill border
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        if (investments.isEmpty()) {
            // Show empty portfolio message
            inventory.setItem(13, createItem(
                Material.BARRIER,
                "§c§lPortfolio Kosong",
                Arrays.asList(
                    "§7Anda belum memiliki investasi crypto.",
                    "§7Beli crypto untuk mulai berinvestasi."
                ),
                false
            ));
        } else {
            // Calculate total portfolio value
            double totalValue = cryptoManager.getPortfolioValue(player);
            
            // Show total value
            inventory.setItem(4, createItem(
                Material.GOLD_BLOCK,
                "§e§lTotal Nilai Portfolio",
                Arrays.asList(
                    "§7Nilai total investasi crypto Anda:",
                    "§f" + String.format("%.1f", totalValue) + " Tokens"
                ),
                false
            ));
            
            // Add investments
            int slot = 10;
            for (Map.Entry<String, Double> entry : investments.entrySet()) {
                String currencyId = entry.getKey();
                double amount = entry.getValue();
                CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
                
                if (crypto != null) {
                    double value = amount * crypto.getCurrentPrice();
                    double percentOfPortfolio = (value / totalValue) * 100;
                    
                    Material material = CRYPTO_MATERIALS.getOrDefault(currencyId, Material.GOLD_NUGGET);
                    
                    inventory.setItem(slot, createItem(
                        material,
                        "§f" + crypto.getSymbol() + " §7- §f" + String.format("%.6f", amount),
                        Arrays.asList(
                            "§7Nilai: §f" + String.format("%.1f", value) + " Tokens",
                            "§7Persentase Portfolio: §f" + String.format("%.1f", percentOfPortfolio) + "%",
                            "§7Harga Per Unit: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                            "",
                            "§eKlik kiri untuk menjual",
                            "§bKlik kanan untuk lihat info",
                            "§aShift+Klik untuk lihat grafik"
                        ),
                        false
                    ));
                    
                    // Move to next slot, skipping border columns
                    slot++;
                    if (slot % 9 == 8) {
                        slot += 2;
                    }
                }
            }
        }
        
        // Add back button
        inventory.setItem(31, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        openGUITypes.put(player.getUniqueId(), PORTFOLIO);
    }
    
    /**
     * Open buy crypto GUI with all available cryptocurrencies
     */
    public void openBuyCryptoMenu(Player player) {
        List<CryptoCurrency> cryptos = cryptoManager.getAllCryptocurrencies();
        int size = ((cryptos.size() / 9) + (cryptos.size() % 9 > 0 ? 2 : 1)) * 9;
        size = Math.min(54, Math.max(27, size)); // Ensure between 27 and 54 slots
        
        Inventory inventory = Bukkit.createInventory(null, size, "§a§lBeli Cryptocurrency");
        
        // Fill border
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Show player token balance
        inventory.setItem(4, createItem(
            Material.EMERALD_BLOCK,
            "§a§lToken Balance",
            Arrays.asList(
                "§7Anda memiliki:",
                "§f" + plugin.getTokenManager().getTokens(player) + " Tokens",
                "",
                "§7Tokens digunakan untuk membeli crypto."
            ),
            false
        ));
        
        // Add cryptocurrencies
        int slot = 10;
        for (CryptoCurrency crypto : cryptos) {
            Material material = CRYPTO_MATERIALS.getOrDefault(crypto.getId(), Material.GOLD_NUGGET);
            
            // Calculate how much of this crypto you can buy with 100 tokens
            double buyAmount = 100 / crypto.getCurrentPrice();
            
            inventory.setItem(slot, createItem(
                material,
                "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
                Arrays.asList(
                    "§7Harga: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                    "§7Volatilitas: §f" + String.format("%.1f", crypto.getVolatility() * 100) + "%",
                    "§7Risiko: " + crypto.getRisk().getColor() + crypto.getRisk().getDisplayName(),
                    "",
                    "§7Dengan 100 Tokens dapat dibeli:",
                    "§f" + String.format("%.6f", buyAmount) + " " + crypto.getSymbol(),
                    "",
                    "§eKlik untuk membeli"
                ),
                false
            ));
            
            // Move to next slot, skipping border columns
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        
        // Add back button
        inventory.setItem(size - 5, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        openGUITypes.put(player.getUniqueId(), BUY);
    }
    
    /**
     * Open sell crypto GUI showing player's investments
     */
    public void openSellCryptoMenu(Player player) {
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        int size = 36; // 4 rows
        
        Inventory inventory = Bukkit.createInventory(null, size, "§c§lJual Cryptocurrency");
        
        // Fill border
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        if (investments.isEmpty()) {
            // Show empty portfolio message
            inventory.setItem(13, createItem(
                Material.BARRIER,
                "§c§lTidak Ada Crypto",
                Arrays.asList(
                    "§7Anda belum memiliki cryptocurrency",
                    "§7untuk dijual."
                ),
                false
            ));
        } else {
            // Show current token balance
            inventory.setItem(4, createItem(
                Material.EMERALD_BLOCK,
                "§a§lToken Balance",
                Arrays.asList(
                    "§7Anda memiliki:",
                    "§f" + plugin.getTokenManager().getTokens(player) + " Tokens"
                ),
                false
            ));
            
            // Add investments
            int slot = 10;
            for (Map.Entry<String, Double> entry : investments.entrySet()) {
                String currencyId = entry.getKey();
                double amount = entry.getValue();
                CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
                
                if (crypto != null) {
                    double value = amount * crypto.getCurrentPrice();
                    Material material = CRYPTO_MATERIALS.getOrDefault(currencyId, Material.GOLD_NUGGET);
                    
                    inventory.setItem(slot, createItem(
                        material,
                        "§f" + crypto.getSymbol() + " §7- §f" + String.format("%.6f", amount),
                        Arrays.asList(
                            "§7Nilai: §f" + String.format("%.1f", value) + " Tokens",
                            "§7Harga Per Unit: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                            "",
                            "§7Klik kiri: §fJual semua",
                            "§7Klik kanan: §fJual sebagian"
                        ),
                        false
                    ));
                    
                    // Move to next slot, skipping border columns
                    slot++;
                    if (slot % 9 == 8) {
                        slot += 2;
                    }
                }
            }
        }
        
        // Add back button
        inventory.setItem(31, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        openGUITypes.put(player.getUniqueId(), SELL);
    }
    
    /**
     * Open detailed info for a specific cryptocurrency
     */
    public void openCryptoInfo(Player player, String currencyId) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        Inventory inventory = Bukkit.createInventory(null, 36, "§b§lInfo: " + crypto.getSymbol());
        
        // Fill with background glass
        GUIUtils.fillBorder(inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        
        // Get material for this crypto
        Material material = CRYPTO_MATERIALS.getOrDefault(currencyId, Material.GOLD_NUGGET);
        
        // Main crypto info
        inventory.setItem(4, createItem(
            material,
            "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
            Arrays.asList(
                "§7Harga Saat Ini: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                "§7Volatilitas: §f" + String.format("%.1f", crypto.getVolatility() * 100) + "%",
                "§7Risiko: " + crypto.getRisk().getColor() + crypto.getRisk().getDisplayName(),
                "§7Range Harga: §f" + formatPrice(crypto.getMinPrice()) + " - " + formatPrice(crypto.getMaxPrice())
            ),
            false
        ));
        
        // Price history data
        List<Double> history = cryptoManager.getPriceHistory(currencyId);
        if (history.size() >= 2) {
            double current = crypto.getCurrentPrice();
            double previousDay = history.size() >= 3 ? history.get(history.size() - 3) : history.get(0);
            double percentChangePrevDay = ((current - previousDay) / previousDay) * 100;
            
            List<String> historyLore = new ArrayList<>();
            historyLore.add("§7Perubahan 24h: " + (percentChangePrevDay >= 0 ? "§a+" : "§c") + 
                String.format("%.2f", percentChangePrevDay) + "%");
            
            // Add more history insights if available
            if (history.size() >= 5) {
                double min = Collections.min(history.subList(history.size() - 5, history.size()));
                double max = Collections.max(history.subList(history.size() - 5, history.size()));
                
                historyLore.add("§7Low (5 period): §f" + formatPrice(min));
                historyLore.add("§7High (5 period): §f" + formatPrice(max));
                historyLore.add("§7Range: §f" + formatPrice(max - min));
            }
            
            inventory.setItem(11, createItem(
                Material.CLOCK,
                "§e§lPerforma Harga",
                historyLore,
                false
            ));
        }
        
        // Player's investment in this crypto
        double playerAmount = cryptoManager.getPlayerInvestment(player, currencyId);
        if (playerAmount > 0) {
            double value = playerAmount * crypto.getCurrentPrice();
            
            inventory.setItem(13, createItem(
                Material.CHEST,
                "§a§lInvestasi Anda",
                Arrays.asList(
                    "§7Jumlah: §f" + String.format("%.6f", playerAmount) + " " + crypto.getSymbol(),
                    "§7Nilai: §f" + String.format("%.1f", value) + " Tokens",
                    "§7Profit/Loss: §f" + "Calculating...",
                    "",
                    "§eKlik untuk menjual"
                ),
                false
            ));
        }
        
        // Market trend analysis (simplified)
        inventory.setItem(15, createItem(
            Material.COMPASS,
            "§b§lAnalisis Market",
            getTrendAnalysis(crypto),
            false
        ));
        
        // Actions
        inventory.setItem(20, createItem(
            Material.EMERALD,
            "§a§lBeli " + crypto.getSymbol(),
            Arrays.asList(
                "§7Harga Saat Ini: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                "§7Token Anda: §f" + plugin.getTokenManager().getTokens(player),
                "",
                "§eKlik untuk membeli"
            ),
            false
        ));
        
        // View chart button
        inventory.setItem(22, createItem(
            Material.MAP,
            "§b§lLihat Grafik",
            Collections.singletonList("§7Klik untuk melihat grafik harga"),
            false
        ));
        
        // Sell button (if player has this crypto)
        if (playerAmount > 0) {
            inventory.setItem(24, createItem(
                Material.REDSTONE,
                "§c§lJual " + crypto.getSymbol(),
                Arrays.asList(
                    "§7Jumlah: §f" + String.format("%.6f", playerAmount),
                    "§7Nilai: §f" + String.format("%.1f", playerAmount * crypto.getCurrentPrice()) + " Tokens",
                    "",
                    "§eKlik untuk menjual"
                ),
                false
            ));
        }
        
        // Back button
        inventory.setItem(31, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        openGUITypes.put(player.getUniqueId(), INFO);
        selectedCrypto.put(player.getUniqueId(), currencyId);
    }
    
    /**
     * Get trend analysis for a cryptocurrency (simplified analysis)
     */
    private List<String> getTrendAnalysis(CryptoCurrency crypto) {
        List<String> analysis = new ArrayList<>();
        List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
        
        if (history.size() < 3) {
            analysis.add("§7Tidak cukup data untuk analisis");
            return analysis;
        }
        
        // Simple trend detection
        double current = crypto.getCurrentPrice();
        double prev1 = history.get(history.size() - 2);
        double prev2 = history.get(history.size() - 3);
        
        boolean uptrend = current > prev1 && prev1 > prev2;
        boolean downtrend = current < prev1 && prev1 < prev2;
        boolean isVolatile = Math.abs((current - prev1) / prev1) > 0.05; // 5% change
        
        if (uptrend) {
            analysis.add("§a▲ Uptrend §7terdeteksi");
            analysis.add("§7Harga terus meningkat dalam");
            analysis.add("§7beberapa periode terakhir.");
        } else if (downtrend) {
            analysis.add("§c▼ Downtrend §7terdeteksi");
            analysis.add("§7Harga terus menurun dalam");
            analysis.add("§7beberapa periode terakhir.");
        } else {
            analysis.add("§6◆ Sideways §7terdeteksi");
            analysis.add("§7Harga bergerak secara lateral.");
        }
        
        if (isVolatile) {
            analysis.add("");
            analysis.add("§c⚠ Volatilitas tinggi");
            analysis.add("§7Pergerakan harga signifikan.");
        }
        
        // Risk assessment based on volatility and risk level
        analysis.add("");
        switch (crypto.getRisk()) {
            case LOW:
                analysis.add("§a✓ Risiko rendah");
                analysis.add("§7Cocok untuk investasi jangka panjang");
                break;
            case MEDIUM:
                analysis.add("§e⚠ Risiko sedang");
                analysis.add("§7Seimbangkan portfolio Anda");
                break;
            case HIGH:
                analysis.add("§6⚠ Risiko tinggi");
                analysis.add("§7Hati-hati dengan jumlah investasi");
                break;
            case EXTREME:
                analysis.add("§c⚠ Risiko ekstrem");
                analysis.add("§7Sangat spekulatif, berhati-hatilah");
                break;
        }
        
        return analysis;
    }
    
    /**
     * Open buy amount selection GUI
     */
    public void openBuyAmountGUI(Player player, String currencyId) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        int playerTokens = plugin.getTokenManager().getTokens(player);
        
        Inventory inventory = Bukkit.createInventory(null, 36, "§a§lBeli " + crypto.getSymbol());
        
        // Fill with background glass
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Material for this crypto
        Material material = CRYPTO_MATERIALS.getOrDefault(currencyId, Material.GOLD_NUGGET);
        
        // Crypto info
        inventory.setItem(4, createItem(
            material,
            "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
            Arrays.asList(
                "§7Harga: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                "§7Token Anda: §f" + playerTokens + " Tokens"
            ),
            false
        ));
        
        // Amount options - small
        int smallAmount = Math.min(playerTokens, 10);
        inventory.setItem(11, createBuyButton(crypto, smallAmount, player));
        
        // Amount options - medium
        int mediumAmount = Math.min(playerTokens, 50);
        inventory.setItem(13, createBuyButton(crypto, mediumAmount, player));
        
        // Amount options - large
        int largeAmount = Math.min(playerTokens, 100);
        inventory.setItem(15, createBuyButton(crypto, largeAmount, player));
        
        // Custom amount option
        inventory.setItem(22, createItem(
            Material.NAME_TAG,
            "§e§lJumlah Kustom",
            Arrays.asList(
                "§7Klik untuk memasukkan",
                "§7jumlah token yang ingin",
                "§7digunakan untuk membeli."
            ),
            false
        ));
        
        // All-in option (if player has tokens)
        if (playerTokens > 0) {
            inventory.setItem(31, createBuyButton(crypto, playerTokens, player));
        }
        
        // Back button
        inventory.setItem(27, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        selectedCrypto.put(player.getUniqueId(), currencyId);
    }
    
    /**
     * Create a buy button for the given amount
     */
    private ItemStack createBuyButton(CryptoCurrency crypto, int tokenAmount, Player player) {
        double cryptoAmount = tokenAmount / crypto.getCurrentPrice();
        
        return createItem(
            Material.EMERALD,
            "§a§lBeli dengan " + tokenAmount + " Tokens",
            Arrays.asList(
                "§7Akan mendapatkan:",
                "§f" + String.format("%.6f", cryptoAmount) + " " + crypto.getSymbol(),
                "",
                "§7Tokens saat ini: §f" + plugin.getTokenManager().getTokens(player),
                "",
                "§eKlik untuk konfirmasi pembelian"
            ),
            false
        );
    }
    
    /**
     * Open sell amount selection GUI
     */
    public void openSellAmountGUI(Player player, String currencyId) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        double playerAmount = cryptoManager.getPlayerInvestment(player, currencyId);
        if (playerAmount <= 0) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki " + crypto.getSymbol() + " untuk dijual!"));
            return;
        }
        
        Inventory inventory = Bukkit.createInventory(null, 36, "§c§lJual " + crypto.getSymbol());
        
        // Fill with background glass
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Material for this crypto
        Material material = CRYPTO_MATERIALS.getOrDefault(currencyId, Material.GOLD_NUGGET);
        
        // Crypto info
        double totalValue = playerAmount * crypto.getCurrentPrice();
        inventory.setItem(4, createItem(
            material,
            "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
            Arrays.asList(
                "§7Harga: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens",
                "§7Anda memiliki: §f" + String.format("%.6f", playerAmount) + " " + crypto.getSymbol(),
                "§7Nilai total: §f" + String.format("%.1f", totalValue) + " Tokens"
            ),
            false
        ));
        
        // Amount options - 25%
        inventory.setItem(11, createSellButton(crypto, playerAmount * 0.25, player));
        
        // Amount options - 50%
        inventory.setItem(13, createSellButton(crypto, playerAmount * 0.5, player));
        
        // Amount options - 75%
        inventory.setItem(15, createSellButton(crypto, playerAmount * 0.75, player));
        
        // Custom amount option
        inventory.setItem(22, createItem(
            Material.NAME_TAG,
            "§e§lJumlah Kustom",
            Arrays.asList(
                "§7Klik untuk memasukkan",
                "§7jumlah " + crypto.getSymbol() + " yang",
                "§7ingin dijual."
            ),
            false
        ));
        
        // All-in option
        inventory.setItem(31, createSellButton(crypto, playerAmount, player));
        
        // Back button
        inventory.setItem(27, GUIUtils.createBackButton());
        
        // Open the inventory
        player.openInventory(inventory);
        selectedCrypto.put(player.getUniqueId(), currencyId);
    }
    
    /**
     * Create a sell button for the given amount
     */
    private ItemStack createSellButton(CryptoCurrency crypto, double cryptoAmount, Player player) {
        int tokenReturn = (int)(cryptoAmount * crypto.getCurrentPrice());
        tokenReturn = Math.max(1, tokenReturn); // Ensure minimum 1 token
        
        String amountPercent = "";
        double totalAmount = cryptoManager.getPlayerInvestment(player, crypto.getId());
        if (totalAmount > 0) {
            double percent = (cryptoAmount / totalAmount) * 100;
            if (Math.abs(percent - 25) < 1) amountPercent = " (25%)";
            else if (Math.abs(percent - 50) < 1) amountPercent = " (50%)";
            else if (Math.abs(percent - 75) < 1) amountPercent = " (75%)";
            else if (Math.abs(percent - 100) < 1) amountPercent = " (100%)";
        }
        
        return createItem(
            Material.REDSTONE,
            "§c§lJual " + String.format("%.6f", cryptoAmount) + amountPercent,
            Arrays.asList(
                "§7Akan mendapatkan:",
                "§f" + tokenReturn + " Tokens",
                "",
                "§7Harga Per Unit: §f" + formatPrice(crypto.getCurrentPrice()),
                "",
                "§eKlik untuk konfirmasi penjualan"
            ),
            false
        );
    }
    
    /**
     * Open a confirmation GUI for important transactions
     */
    private void openConfirmationGUI(Player player, String action, String currencyId, double amount) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        Inventory inventory = Bukkit.createInventory(null, 27, "§e§lKonfirmasi Transaksi");
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Display transaction info
        List<String> infoLore = new ArrayList<>();
        
        if (action.equals("buy")) {
            int tokenAmount = transactionAmounts.getOrDefault(player.getUniqueId(), 0);
            double cryptoAmount = tokenAmount / crypto.getCurrentPrice();
            
            infoLore.add("§7Anda akan membeli:");
            infoLore.add("§f" + String.format("%.6f", cryptoAmount) + " " + crypto.getSymbol());
            infoLore.add("");
            infoLore.add("§7Dengan harga:");
            infoLore.add("§f" + tokenAmount + " Tokens");
            infoLore.add("");
            infoLore.add("§7Harga per unit: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens");
        } else if (action.equals("sell")) {
            double tokenAmount = amount * crypto.getCurrentPrice();
            
            infoLore.add("§7Anda akan menjual:");
            infoLore.add("§f" + String.format("%.6f", amount) + " " + crypto.getSymbol());
            infoLore.add("");
            infoLore.add("§7Akan mendapatkan:");
            infoLore.add("§f" + String.format("%.1f", tokenAmount) + " Tokens");
            infoLore.add("");
            infoLore.add("§7Harga per unit: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens");
        } else if (action.equals("sell_all")) {
            double currentAmount = cryptoManager.getPlayerInvestment(player, currencyId);
            double tokenAmount = currentAmount * crypto.getCurrentPrice();
            
            infoLore.add("§7Anda akan menjual seluruh:");
            infoLore.add("§f" + String.format("%.6f", currentAmount) + " " + crypto.getSymbol());
            infoLore.add("");
            infoLore.add("§7Akan mendapatkan:");
            infoLore.add("§f" + String.format("%.1f", tokenAmount) + " Tokens");
            infoLore.add("");
            infoLore.add("§7Harga per unit: §f" + formatPrice(crypto.getCurrentPrice()) + " Tokens");
        }
        
        inventory.setItem(13, createItem(
            CRYPTO_MATERIALS.getOrDefault(currencyId, Material.GOLD_NUGGET),
            "§f" + crypto.getSymbol() + " §7- §f" + crypto.getName(),
            infoLore,
            false
        ));
        
        // Confirm button
        inventory.setItem(11, createItem(
            Material.LIME_WOOL,
            "§a§lKonfirmasi",
            Arrays.asList(
                "§7Klik untuk mengkonfirmasi transaksi"
            ),
            false
        ));
        
        // Cancel button
        inventory.setItem(15, createItem(
            Material.RED_WOOL,
            "§c§lBatalkan",
            Arrays.asList(
                "§7Klik untuk membatalkan transaksi"
            ),
            false
        ));
        
        player.openInventory(inventory);
        
        // Store transaction details
        selectedCrypto.put(player.getUniqueId(), currencyId);
        if (action.equals("buy")) {
            openGUITypes.put(player.getUniqueId(), "CONFIRM_BUY");
        } else if (action.equals("sell")) {
            openGUITypes.put(player.getUniqueId(), "CONFIRM_SELL");
            transactionAmounts.put(player.getUniqueId(), (int) amount);
        } else if (action.equals("sell_all")) {
            openGUITypes.put(player.getUniqueId(), "CONFIRM_SELL_ALL");
        }
    }
    
    /**
     * Handle inventory click events
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();
        
        // Skip if clicked nothing
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        // Always cancel clicks in crypto GUI
        if (title.contains("§a§lBeli ") || 
            title.contains("§c§lJual ") || 
            title.equals("§b§lCryptocurrency Market") ||
            title.equals("§6§lPortfolio Crypto") ||
            title.equals("§2§lCryptocurrency") ||
            title.contains("§b§lGrafik ") ||
            title.contains("§b§lInfo ")) {
            
            event.setCancelled(true);
            
            // Debug log
            plugin.getLogger().info("Crypto GUI click: " + player.getName() + " clicked " + 
                clickedItem.getType() + " in " + title);
            
            // Process click based on GUI type
            String guiType = openGUITypes.get(playerId);
            if (guiType == null) {
                // Try to determine from title
                if (title.equals("§2§lCryptocurrency")) {
                    guiType = MAIN_MENU;
                } else if (title.equals("§b§lCryptocurrency Market")) {
                    guiType = MARKET;
                } else if (title.equals("§6§lPortfolio Crypto")) {
                    guiType = PORTFOLIO;
                } else if (title.contains("§a§lBeli ")) {
                    guiType = BUY;
                } else if (title.contains("§c§lJual ")) {
                    guiType = SELL;
                } else if (title.contains("§b§lInfo ")) {
                    guiType = INFO;
                }
            }
            
            // Handle click based on GUI type
            switch (guiType) {
                case MAIN_MENU:
                    handleMainMenuClick(player, event.getSlot(), clickedItem);
                    break;
                case MARKET:
                    handleMarketClick(player, clickedItem, event.isShiftClick());
                    break;
                case PORTFOLIO:
                    handlePortfolioClick(player, clickedItem, event.isRightClick(), event.isShiftClick());
                    break;
                case BUY:
                    handleBuyMenuClick(player, clickedItem);
                    break;
                case SELL:
                    handleSellMenuClick(player, clickedItem, event.isRightClick());
                    break;
                case INFO:
                    handleInfoMenuClick(player, event.getSlot(), clickedItem);
                    break;
                case "CONFIRM_BUY":
                case "CONFIRM_SELL":
                case "CONFIRM_SELL_ALL":
                    handleConfirmationClick(player, clickedItem);
                    break;
            }
        }
    }
    
    /**
     * Handle inventory close events
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            
            // Clean up tracking maps when player closes inventory
            openGUITypes.remove(player.getUniqueId());
            selectedCrypto.remove(player.getUniqueId());
            transactionAmounts.remove(player.getUniqueId());
        }
    }
    
    /**
     * Handle inventory drag events with improved checks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        String title = event.getView().getTitle();
        
        // Batalkan semua event drag jika ini adalah GUI crypto kita
        if (title.contains("§a§lBeli ") || 
            title.contains("§c§lJual ") || 
            title.equals("§b§lCryptocurrency Market") ||
            title.equals("§6§lPortfolio Crypto") ||
            title.equals("§2§lCryptocurrency") ||
            title.contains("§b§lGrafik ") ||
            title.contains("§b§lInfo ")) {
            
            event.setCancelled(true);
            return;
        }
        
        // Tambahan check berdasarkan UUID player yang memiliki GUI terbuka
        UUID playerId = player.getUniqueId();
        if (openGUITypes.containsKey(playerId)) {
            event.setCancelled(true);
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
    private ItemStack createItem(Material material, String name, List<String> lore, boolean enchanted) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (enchanted) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Refresh GUI setelah transaksi selesai
     */
    public void refreshGUI(Player player) {
        UUID playerId = player.getUniqueId();
        String guiType = openGUITypes.get(playerId);
        
        if (guiType == null) return;
        
        switch (guiType) {
            case MARKET:
                openMarket(player);
                break;
            case PORTFOLIO:
                openPortfolio(player);
                break;
            case BUY:
                openBuyCryptoMenu(player);
                break;
            case SELL:
                openSellCryptoMenu(player);
                break;
            case INFO:
                String currencyId = selectedCrypto.get(playerId);
                if (currencyId != null) {
                    openCryptoInfo(player, currencyId);
                } else {
                    openMainMenu(player);
                }
                break;
            default:
                openMainMenu(player);
                break;
        }
    }
    
    /**
     * Handle buy menu clicks dengan validasi dan konfirmasi
     */
    private void handleBuyMenuClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }
        
        String title = clickedItem.getItemMeta().getDisplayName();
        
        // Handle back button
        if (title.contains("Kembali")) {
            openMainMenu(player);
            return;
        }
        
        // Find which crypto was clicked
        for (CryptoCurrency crypto : cryptoManager.getAllCryptocurrencies()) {
            if (title.contains(crypto.getSymbol())) {
                openBuyAmountGUI(player, crypto.getId());
                return;
            }
        }
        
        // Handle buy button clicks (already in buy amount GUI)
        if (title.contains("Beli dengan")) {
            String currencyId = selectedCrypto.get(player.getUniqueId());
            if (currencyId != null) {
                try {
                    // Extract token amount from button title
                    String amountStr = title.replaceAll("[^0-9]", "");
                    int tokenAmount = Integer.parseInt(amountStr);
                    
                    if (tokenAmount <= 0) {
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah token harus lebih besar dari 0."));
                        return;
                    }
                    
                    // Validasi apakah player memiliki cukup token
                    int playerTokens = plugin.getTokenManager().getTokens(player);
                    if (playerTokens < tokenAmount) {
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                            "&cKamu tidak memiliki cukup token! Dibutuhkan: &f" + tokenAmount + 
                            " &cToken, Kamu memiliki: &f" + playerTokens + " &cToken."));
                        return;
                    }
                    
                    // Store token amount and open confirmation
                    transactionAmounts.put(player.getUniqueId(), tokenAmount);
                    openConfirmationGUI(player, "buy", currencyId, 0);
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGagal mendapatkan jumlah token dari button."));
                }
            }
        }
        
        // Handle custom amount button
        if (title.contains("Jumlah Kustom")) {
            String currencyId = selectedCrypto.get(player.getUniqueId());
            if (currencyId != null) {
                player.closeInventory();
                
                // Simpan state untuk menunggu input chat
                plugin.getPlayerDataManager().setTempData(player.getUniqueId(), "crypto_buy_pending", currencyId);
                
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aKetik jumlah token yang ingin Anda gunakan untuk membeli di chat:"));
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&7(Ketik 'batal' untuk membatalkan)"));
            }
        }
    }
    
    /**
     * Handle sell menu clicks with confirmation
     */
    private void handleSellMenuClick(Player player, ItemStack clickedItem, boolean isRightClick) {
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }
        
        String title = clickedItem.getItemMeta().getDisplayName();
        
        // Handle back button
        if (title.contains("Kembali")) {
            openMainMenu(player);
            return;
        }
        
        // Find which crypto was clicked
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        for (Map.Entry<String, Double> entry : investments.entrySet()) {
            String currencyId = entry.getKey();
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
            
            // Check if this is the crypto being sold
            if (crypto != null && title.contains(crypto.getSymbol())) {
                if (isRightClick) {
                    // Partial sell - show amount selection GUI
                    openSellAmountGUI(player, currencyId);
                    return;
                } else {
                    // Sell all - confirmation required
                    openConfirmationGUI(player, "sell_all", currencyId, 0);
                    return;
                }
            }
        }
        
        // Check if in the sell amount selection GUI
        if (title.contains("Jual ") && title.contains("(") && title.contains(")")) {
            String currencyId = selectedCrypto.get(player.getUniqueId());
            if (currencyId != null) {
                try {
                    // Extract amount from title
                    String amountStr = title.substring(title.indexOf("Jual ") + 5);
                    if (amountStr.contains("(")) {
                        amountStr = amountStr.substring(0, amountStr.indexOf("(")).trim();
                    }
                    double amount = Double.parseDouble(amountStr);
                    
                    // Open confirmation GUI
                    openConfirmationGUI(player, "sell", currencyId, amount);
                    return;
                } catch (Exception e) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGagal mendapatkan jumlah crypto dari button."));
                }
            }
        }
        
        // Handle custom amount button
        if (title.contains("Jumlah Kustom")) {
            String currencyId = selectedCrypto.get(player.getUniqueId());
            if (currencyId != null) {
                player.closeInventory();
                plugin.getPlayerDataManager().setTempData(player.getUniqueId(), "crypto_sell_pending", currencyId);
                
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aKetik jumlah yang ingin Anda jual di chat:"));
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&7(Ketik 'batal' untuk membatalkan)"));
            }
        }
    }
    
    /**
     * Handle confirmation GUI clicks
     */
    private void handleConfirmationClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }
        
        String title = clickedItem.getItemMeta().hasDisplayName() ? clickedItem.getItemMeta().getDisplayName() : "";
        UUID playerId = player.getUniqueId();
        String guiType = openGUITypes.get(playerId);
        String currencyId = selectedCrypto.get(playerId);
        
        if (currencyId == null) {
            player.closeInventory();
            return;
        }
        
        // Handle confirmation button
        if (clickedItem.getType() == Material.LIME_WOOL && title.contains("Konfirmasi")) {
            // Process transaction based on type
            if (guiType.equals("CONFIRM_BUY")) {
                int tokenAmount = transactionAmounts.getOrDefault(playerId, 0);
                if (tokenAmount > 0) {
                    boolean success = cryptoManager.buyCrypto(player, currencyId, tokenAmount);
                    if (success) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
            } else if (guiType.equals("CONFIRM_SELL")) {
                double amount = transactionAmounts.getOrDefault(playerId, 0);
                boolean success = cryptoManager.sellCrypto(player, currencyId, amount);
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            } else if (guiType.equals("CONFIRM_SELL_ALL")) {
                boolean success = cryptoManager.sellCrypto(player, currencyId, 0); // 0 means sell all
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
            
            player.closeInventory();
        } 
        // Handle cancel button
        else if (clickedItem.getType() == Material.RED_WOOL && title.contains("Batalkan")) {
            if (guiType.equals("CONFIRM_BUY")) {
                openBuyAmountGUI(player, currencyId);
            } else if (guiType.equals("CONFIRM_SELL") || guiType.equals("CONFIRM_SELL_ALL")) {
                openSellCryptoMenu(player);
            } else {
                player.closeInventory();
            }
        }
    }
    
    /**
     * Handle clicks in the main menu
     */
    private void handleMainMenuClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }
        
        String title = clickedItem.getItemMeta().getDisplayName();
        
        switch (slot) {
            case 10: // Market button
                openMarket(player);
                break;
            case 12: // Portfolio button
                openPortfolio(player);
                break;
            case 14: // Buy crypto button
                openBuyCryptoMenu(player);
                break;
            case 16: // Sell crypto button
                openSellCryptoMenu(player);
                break;
            case 31: // Info button
                // TODO: Open general info about crypto
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aFitur informasi masih dalam pengembangan."));
                break;
            case 32: // Close button
                player.closeInventory();
                break;
        }
    }
    
    /**
     * Handle clicks in the market view
     */
    private void handleMarketClick(Player player, ItemStack clickedItem, boolean isShiftClick) {
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().getDisplayName()) {
            return;
        }
        
        String title = clickedItem.getItemMeta().getDisplayName();
        
        // Back button
        if (title.contains("Kembali")) {
            openMainMenu(player);
            return;
        }
        
        // Find which crypto was clicked
        for (CryptoCurrency crypto : cryptoManager.getAllCryptocurrencies()) {
            if (title.contains(crypto.getSymbol())) {
                if (isShiftClick) {
                    // Open chart view
                    chartGUI.openChartGUI(player, crypto.getId(), "day");
                } else {
                    // Open detailed info
                    openCryptoInfo(player, crypto.getId());
                }
                return;
            }
        }
    }
    
    /**
     * Handle clicks in the portfolio view
     */
    private void handlePortfolioClick(Player player, ItemStack clickedItem, boolean isRightClick, boolean isShiftClick) {
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().getDisplayName()) {
            return;
        }
        
        String title = clickedItem.getItemMeta().getDisplayName();
        
        // Back button
        if (title.contains("Kembali")) {
            openMainMenu(player);
            return;
        }
        
        // Find which crypto was clicked
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        for (Map.Entry<String, Double> entry : investments.entrySet()) {
            String currencyId = entry.getKey();
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
            
            if (crypto != null && title.contains(crypto.getSymbol())) {
                if (isShiftClick) {
                    // Open chart view
                    chartGUI.openChartGUI(player, crypto.getId(), "day");
                } else if (isRightClick) {
                    // Open detailed info
                    openCryptoInfo(player, crypto.getId());
                } else {
                    // Left-click - Sell
                    openSellAmountGUI(player, crypto.getId());
                }
                return;
            }
        }
    }
    
    /**
     * Handle clicks in the cryptocurrency info view
     */
    private void handleInfoMenuClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().getDisplayName()) {
            return;
        }
        
        String currencyId = selectedCrypto.get(player.getUniqueId());
        if (currencyId == null) {
            return;
        }
        
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        if (crypto == null) {
            return;
        }
        
        switch (slot) {
            case 20: // Buy button
                openBuyAmountGUI(player, currencyId);
                break;
            case 22: // Chart button
                chartGUI.openChartGUI(player, currencyId, "day");
                break;
            case 24: // Sell button
                openSellAmountGUI(player, currencyId);
                break;
            case 31: // Back button
                openMarket(player);
                break;
        }
    }
}