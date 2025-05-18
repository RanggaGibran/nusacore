package id.nusacore.crypto.gui;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoManager;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ChartGUI {
    private static final int CHART_WIDTH = 9;
    private static final int CHART_HEIGHT = 5;
    private static final int INVENTORY_SIZE = 54; // 6 rows of 9
    
    private final NusaCore plugin;
    private final CryptoManager cryptoManager;
    
    public ChartGUI(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
    }
    
    /**
     * Show price chart for a cryptocurrency
     * @param player The player viewing the chart
     * @param currencyId The currency ID to show chart for
     * @param timeframe The timeframe to display (1h, 6h, 24h, 7d, 30d)
     */
    public void showChart(Player player, String currencyId, String timeframe) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        // Get price history
        List<Double> priceHistory = cryptoManager.getPriceHistory(currencyId);
        
        if (priceHistory.isEmpty()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTidak ada data historis untuk " + crypto.getSymbol()));
            return;
        }
        
        // Create inventory
        String title = "§bGrafik " + crypto.getSymbol() + " - " + getTimeframeLabel(timeframe);
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, title);
        
        // Fill with background
        ItemStack background = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, background);
        }
        
        // Draw chart axes
        drawAxes(inventory);
        
        // Draw price chart based on timeframe
        drawChart(inventory, priceHistory, timeframe);
        
        // Add current price and percent change
        addPriceInfo(inventory, crypto);
        
        // Add timeframe buttons
        addTimeframeButtons(inventory, currencyId, timeframe);
        
        // Add action buttons (back, info)
        addActionButtons(inventory, crypto);
        
        // Show the inventory
        player.openInventory(inventory);
    }
    
    /**
     * Draw chart axes
     */
    private void drawAxes(Inventory inventory) {
        // Y-axis (left side)
        for (int row = 0; row < CHART_HEIGHT; row++) {
            inventory.setItem(row * 9, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList()));
        }
        
        // X-axis (bottom)
        for (int col = 0; col < CHART_WIDTH; col++) {
            inventory.setItem(CHART_HEIGHT * 9 - 9 + col, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList()));
        }
    }
    
    /**
     * Draw price chart
     */
    private void drawChart(Inventory inventory, List<Double> priceHistory, String timeframe) {
        // Get relevant slice of history based on timeframe
        List<Double> chartData = getTimeframeData(priceHistory, timeframe);
        
        if (chartData.size() < 2) {
            return; // Not enough data points
        }
        
        // Find min and max values
        double minValue = Collections.min(chartData);
        double maxValue = Collections.max(chartData);
        double range = maxValue - minValue;
        
        if (range == 0) {
            range = 1; // Prevent division by zero
        }
        
        // Calculate positions for each data point
        int numPoints = Math.min(chartData.size(), CHART_WIDTH - 1);
        
        for (int i = 0; i < numPoints; i++) {
            // Calculate normalized value (0-1)
            double normalizedValue = (chartData.get(chartData.size() - numPoints + i) - minValue) / range;
            
            // Map to chart position (inverted because inventory goes top to bottom)
            int yPos = CHART_HEIGHT - 1 - (int) Math.floor(normalizedValue * (CHART_HEIGHT - 1));
            int xPos = i + 1; // +1 to leave space for Y axis
            
            // Select color based on trend
            Material material;
            if (i > 0) {
                double prevValue = chartData.get(chartData.size() - numPoints + i - 1);
                double currValue = chartData.get(chartData.size() - numPoints + i);
                material = currValue >= prevValue ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            } else {
                material = Material.YELLOW_STAINED_GLASS_PANE;
            }
            
            // Place item
            inventory.setItem(yPos * 9 + xPos, createItem(material, " ", Collections.emptyList()));
            
            // Draw line to next point if available
            if (i < numPoints - 1) {
                double nextNormalizedValue = (chartData.get(chartData.size() - numPoints + i + 1) - minValue) / range;
                int nextYPos = CHART_HEIGHT - 1 - (int) Math.floor(nextNormalizedValue * (CHART_HEIGHT - 1));
                
                // Connect points with line
                if (nextYPos != yPos) {
                    int startY = Math.min(yPos, nextYPos);
                    int endY = Math.max(yPos, nextYPos);
                    
                    for (int y = startY + 1; y < endY; y++) {
                        inventory.setItem(y * 9 + xPos, createItem(material, " ", Collections.emptyList()));
                    }
                }
            }
        }
    }
    
    /**
     * Add price information to the chart
     */
    private void addPriceInfo(Inventory inventory, CryptoCurrency crypto) {
        // Current price display
        List<String> priceInfo = new ArrayList<>();
        priceInfo.add("§fHarga Saat Ini: §b" + String.format("%.6f", crypto.getCurrentPrice()) + " Tokens");
        
        // Calculate percent change if we have history
        List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
        if (history.size() >= 2) {
            double current = crypto.getCurrentPrice();
            double previous = history.get(history.size() - 2); // Previous price point
            double percentChange = ((current - previous) / previous) * 100;
            
            String changeColor = percentChange >= 0 ? "§a" : "§c";
            String changeSymbol = percentChange >= 0 ? "▲" : "▼";
            priceInfo.add(changeColor + changeSymbol + " " + String.format("%.2f", Math.abs(percentChange)) + "%");
        }
        
        inventory.setItem(45, createItem(Material.GOLD_INGOT, "§e" + crypto.getSymbol(), priceInfo));
    }
    
    /**
     * Add timeframe selector buttons
     */
    private void addTimeframeButtons(Inventory inventory, String currencyId, String currentTimeframe) {
        String[][] timeframes = {
            {"1h", "§b1 Jam", "Material.CLOCK"},
            {"6h", "§b6 Jam", "Material.CLOCK"},
            {"24h", "§b24 Jam", "Material.CLOCK"},
            {"7d", "§b7 Hari", "Material.COMPASS"},
            {"30d", "§b30 Hari", "Material.COMPASS"}
        };
        
        for (int i = 0; i < timeframes.length; i++) {
            boolean isSelected = timeframes[i][0].equals(currentTimeframe);
            Material material = isSelected ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
            String prefix = isSelected ? "§a" : "§7";
            
            List<String> lore = new ArrayList<>();
            lore.add(prefix + "Klik untuk melihat data " + timeframes[i][1].substring(2));
            
            inventory.setItem(46 + i, createItem(material, timeframes[i][1], lore));
        }
    }
    
    /**
     * Add action buttons
     */
    private void addActionButtons(Inventory inventory, CryptoCurrency crypto) {
        // Info button
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Klik untuk melihat informasi detail");
        infoLore.add("§7tentang " + crypto.getSymbol());
        inventory.setItem(53, createItem(Material.BOOK, "§bInfo " + crypto.getSymbol(), infoLore));
        
        // Back button
        List<String> backLore = new ArrayList<>();
        backLore.add("§7Kembali ke menu crypto");
        inventory.setItem(52, createItem(Material.ARROW, "§cKembali", backLore));
    }
    
    /**
     * Get data slice for the requested timeframe
     */
    private List<Double> getTimeframeData(List<Double> fullHistory, String timeframe) {
        if (fullHistory.isEmpty()) {
            return fullHistory;
        }
        
        int dataPoints;
        switch (timeframe) {
            case "1h":
                dataPoints = 12; // Assuming 5-minute intervals
                break;
            case "6h":
                dataPoints = 36; // Assuming 10-minute intervals
                break;
            case "24h":
                dataPoints = 24; // Hourly data points
                break;
            case "7d":
                dataPoints = 28; // 6-hour intervals for a week
                break;
            case "30d":
                dataPoints = 30; // Daily data points for a month
                break;
            default:
                dataPoints = 24; // Default to 24h view
                break;
        }
        
        // Return last N data points or all if less available
        int startIndex = Math.max(0, fullHistory.size() - dataPoints);
        return fullHistory.subList(startIndex, fullHistory.size());
    }
    
    /**
     * Get human-readable timeframe label
     */
    private String getTimeframeLabel(String timeframe) {
        switch (timeframe) {
            case "1h": return "1 Jam";
            case "6h": return "6 Jam";
            case "24h": return "24 Jam";
            case "7d": return "7 Hari";
            case "30d": return "30 Hari";
            default: return "24 Jam";
        }
    }
    
    /**
     * Helper method to create inventory items
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
}