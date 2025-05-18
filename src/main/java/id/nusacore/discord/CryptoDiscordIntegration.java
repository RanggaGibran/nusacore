package id.nusacore.discord;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoManager;
import id.nusacore.crypto.CryptoRisk;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CryptoDiscordIntegration {
    private final NusaCore plugin;
    private final CryptoManager cryptoManager;
    private final boolean enabled;
    private final String integrationMethod;
    private final String webhookUrl;
    private final String webhookUsername;
    private final String webhookAvatarUrl;
    private final String discordSrvChannel;
    private final FileConfiguration config;
    
    // Market update task
    private BukkitTask marketUpdateTask;
    
    // Price alert thresholds
    private final double significantPriceChangePercent;
    private final Map<String, Double> lastReportedPrices = new HashMap<>();
    
    public CryptoDiscordIntegration(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
        this.config = plugin.getConfig();
        
        // Load Discord configuration
        ConfigurationSection discordConfig = config.getConfigurationSection("crypto.discord");
        if (discordConfig == null) {
            this.enabled = false;
            this.integrationMethod = "webhook";
            this.webhookUrl = "";
            this.webhookUsername = "Crypto Market Bot";
            this.webhookAvatarUrl = "";
            this.discordSrvChannel = "crypto";
            this.significantPriceChangePercent = 5.0;
            
            // Create default configuration
            createDefaultConfig();
            return;
        }
        
        this.enabled = discordConfig.getBoolean("enabled", false);
        this.integrationMethod = discordConfig.getString("integration-method", "webhook");
        this.webhookUrl = discordConfig.getString("webhook.url", "");
        this.webhookUsername = discordConfig.getString("webhook.username", "Crypto Market Bot");
        this.webhookAvatarUrl = discordConfig.getString("webhook.avatar-url", "");
        this.discordSrvChannel = discordConfig.getString("channel", "crypto");
        this.significantPriceChangePercent = discordConfig.getDouble("price-alerts.threshold", 5.0);
        
        // Initialize last reported prices
        for (CryptoCurrency crypto : cryptoManager.getAllCryptocurrencies()) {
            lastReportedPrices.put(crypto.getId(), crypto.getCurrentPrice());
        }
        
        // Schedule market updates if enabled
        if (enabled && discordConfig.getBoolean("market-updates.enabled", true)) {
            int updateInterval = discordConfig.getInt("market-updates.interval", 60); // minutes
            scheduleMarketUpdates(updateInterval);
        }
    }
    
    /**
     * Create default Discord configuration
     */
    private void createDefaultConfig() {
        ConfigurationSection cryptoSection = config.getConfigurationSection("crypto");
        if (cryptoSection == null) {
            cryptoSection = config.createSection("crypto");
        }
        
        ConfigurationSection discordSection = cryptoSection.createSection("discord");
        discordSection.set("enabled", false);
        discordSection.set("integration-method", "webhook");
        
        ConfigurationSection webhookSection = discordSection.createSection("webhook");
        webhookSection.set("url", "https://discord.com/api/webhooks/your-webhook-id/your-webhook-token");
        webhookSection.set("username", "Crypto Market Bot");
        webhookSection.set("avatar-url", "https://i.imgur.com/example.png");
        
        discordSection.set("channel", "crypto");
        
        ConfigurationSection marketUpdatesSection = discordSection.createSection("market-updates");
        marketUpdatesSection.set("enabled", true);
        marketUpdatesSection.set("interval", 60); // minutes
        marketUpdatesSection.set("embed-color", "#3498db"); // Discord blue
        
        ConfigurationSection priceAlertsSection = discordSection.createSection("price-alerts");
        priceAlertsSection.set("enabled", true);
        priceAlertsSection.set("threshold", 5.0); // percent
        priceAlertsSection.set("embed-color", "#e74c3c"); // Discord red
        
        ConfigurationSection transactionsSection = discordSection.createSection("transactions");
        transactionsSection.set("enabled", true);
        transactionsSection.set("min-value", 100); // minimum transaction value to notify
        transactionsSection.set("embed-color", "#2ecc71"); // Discord green
        
        ConfigurationSection messagesSection = discordSection.createSection("messages");
        
        ConfigurationSection marketUpdateMsg = messagesSection.createSection("market-update");
        marketUpdateMsg.set("title", ":chart_with_upwards_trend: **Crypto Market Update**");
        marketUpdateMsg.set("format", "**{symbol}** ({name}): {price} Tokens {change}");
        
        ConfigurationSection priceAlertMsg = messagesSection.createSection("price-alert");
        priceAlertMsg.set("title", ":rotating_light: **Significant Price Movement**");
        priceAlertMsg.set("format", "**{symbol}** has {direction} by **{change_percent}%** in the last update!\nCurrent price: {price} Tokens");
        
        ConfigurationSection transactionMsg = messagesSection.createSection("transaction");
        transactionMsg.set("buy", ":inbox_tray: **{player}** purchased **{amount} {symbol}** for {value} Tokens");
        transactionMsg.set("sell", ":outbox_tray: **{player}** sold **{amount} {symbol}** for {value} Tokens");
        
        plugin.saveConfig();
    }
    
    /**
     * Schedule periodic market updates
     */
    private void scheduleMarketUpdates(int intervalMinutes) {
        // Cancel existing task if any
        if (marketUpdateTask != null) {
            marketUpdateTask.cancel();
        }
        
        // Schedule new task
        marketUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, 
            this::sendMarketUpdate, 
            1200L, // initial delay (1 minute)
            intervalMinutes * 1200L // interval in ticks (1 minute = 1200 ticks)
        );
    }
    
    /**
     * Send market update to Discord
     */
    public void sendMarketUpdate() {
        if (!isEnabled() || !getConfigBoolean("market-updates.enabled", true)) {
            return;
        }
        
        // Title and introduction
        String title = getConfigString("messages.market-update.title", 
                ":chart_with_upwards_trend: **Crypto Market Update**");
        
        StringBuilder content = new StringBuilder(title + "\n\n");
        String format = getConfigString("messages.market-update.format", 
                "**{symbol}** ({name}): {price} Tokens {change}");
        
        // Count bull/bear trends
        int bullCount = 0;
        int bearCount = 0;
        
        // Add each cryptocurrency
        List<CryptoCurrency> cryptos = cryptoManager.getAllCryptocurrencies();
        for (CryptoCurrency crypto : cryptos) {
            // Get price history
            List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
            double currentPrice = crypto.getCurrentPrice();
            
            // Calculate percentage change
            String change = ""; 
            if (history.size() >= 2) {
                double prevPrice = history.get(history.size() - 2);
                double changePercent = ((currentPrice - prevPrice) / prevPrice) * 100;
                
                if (changePercent > 0) {
                    change = ":chart_with_upwards_trend: +" + String.format("%.2f", changePercent) + "%";
                    bullCount++;
                } else if (changePercent < 0) {
                    change = ":chart_with_downwards_trend: " + String.format("%.2f", changePercent) + "%";
                    bearCount++;
                } else {
                    change = "±0.00%";
                }
            }
            
            // Format line for this crypto
            String line = format
                .replace("{symbol}", crypto.getSymbol())
                .replace("{name}", crypto.getName())
                .replace("{price}", formatPrice(currentPrice))
                .replace("{change}", change);
            
            content.append(line).append("\n");
        }
        
        // Add market sentiment
        content.append("\n**Market Sentiment**: ");
        if (bullCount > bearCount) {
            content.append(":green_circle: Bullish");
        } else if (bearCount > bullCount) {
            content.append(":red_circle: Bearish");
        } else {
            content.append(":yellow_circle: Neutral");
        }
        
        // Add timestamp
        content.append("\n\n*Updated: ")
               .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
               .append("*");
        
        // Send to Discord
        boolean useEmbed = true;
        String colorHex = getConfigString("market-updates.embed-color", "#3498db");
        
        sendDiscordMessage(content.toString(), useEmbed, colorHex);
    }
    
    /**
     * Check for significant price changes and send alerts
     */
    public void checkPriceAlerts() {
        if (!isEnabled() || !getConfigBoolean("price-alerts.enabled", true)) {
            return;
        }
        
        for (CryptoCurrency crypto : cryptoManager.getAllCryptocurrencies()) {
            String currencyId = crypto.getId();
            double currentPrice = crypto.getCurrentPrice();
            double lastReportedPrice = lastReportedPrices.getOrDefault(currencyId, currentPrice);
            
            // Skip if this is the first check (no previous price)
            if (lastReportedPrice == 0) {
                lastReportedPrices.put(currencyId, currentPrice);
                continue;
            }
            
            // Calculate percentage change
            double percentChange = ((currentPrice - lastReportedPrice) / lastReportedPrice) * 100;
            
            // If change exceeds threshold, send alert
            if (Math.abs(percentChange) >= significantPriceChangePercent) {
                String direction = percentChange > 0 ? "increased" : "decreased";
                String title = getConfigString("messages.price-alert.title", 
                        ":rotating_light: **Significant Price Movement**");
                
                String format = getConfigString("messages.price-alert.format", 
                        "**{symbol}** has {direction} by **{change_percent}%** in the last update!\nCurrent price: {price} Tokens");
                
                String message = title + "\n\n" + format
                        .replace("{symbol}", crypto.getSymbol())
                        .replace("{direction}", direction)
                        .replace("{change_percent}", String.format("%.2f", Math.abs(percentChange)))
                        .replace("{price}", formatPrice(currentPrice));
                
                // Add risk warning for high volatility
                if (Math.abs(percentChange) >= 10.0) {
                    message += "\n\n:warning: *High volatility detected! Trade with caution.*";
                }
                
                // Send alert
                String colorHex = percentChange > 0 ? "#2ecc71" : "#e74c3c"; // Green for up, red for down
                sendDiscordMessage(message, true, colorHex);
                
                // Update last reported price
                lastReportedPrices.put(currencyId, currentPrice);
            }
        }
    }
    
    /**
     * Send notification about a transaction
     */
    public void sendTransactionNotification(Player player, String currencyId, double amount, 
                                           double value, boolean isBuy) {
        if (!isEnabled() || !getConfigBoolean("transactions.enabled", true)) {
            return;
        }
        
        // Check minimum value threshold
        double minValue = getConfigDouble("transactions.min-value", 100);
        if (value < minValue) {
            return;
        }
        
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        if (crypto == null) {
            return;
        }
        
        String messageKey = isBuy ? "messages.transaction.buy" : "messages.transaction.sell";
        String format = getConfigString(messageKey, 
                isBuy ? 
                ":inbox_tray: **{player}** purchased **{amount} {symbol}** for {value} Tokens" :
                ":outbox_tray: **{player}** sold **{amount} {symbol}** for {value} Tokens");
        
        String message = format
                .replace("{player}", player.getName())
                .replace("{amount}", String.format("%.6f", amount))
                .replace("{symbol}", crypto.getSymbol())
                .replace("{value}", String.format("%.2f", value));
        
        String colorHex = getConfigString("transactions.embed-color", "#2ecc71");
        sendDiscordMessage(message, true, colorHex);
    }
    
    /**
     * Send player portfolio to Discord
     */
    public void sendPlayerPortfolio(Player player) {
        if (!isEnabled()) {
            return;
        }
        
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        if (investments.isEmpty()) {
            return;
        }
        
        double totalValue = cryptoManager.getPortfolioValue(player);
        
        // Create portfolio message
        StringBuilder content = new StringBuilder(":briefcase: **")
                .append(player.getName())
                .append("'s Crypto Portfolio**\n\n");
        
        // Add each cryptocurrency
        for (Map.Entry<String, Double> entry : investments.entrySet()) {
            String currencyId = entry.getKey();
            double amount = entry.getValue();
            
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
            if (crypto != null) {
                double value = amount * crypto.getCurrentPrice();
                double percentOfPortfolio = (value / totalValue) * 100;
                
                content.append("**")
                      .append(crypto.getSymbol())
                      .append("**: ")
                      .append(String.format("%.6f", amount))
                      .append(" (")
                      .append(String.format("%.2f", value))
                      .append(" Tokens, ")
                      .append(String.format("%.1f", percentOfPortfolio))
                      .append("%)\n");
            }
        }
        
        // Add total value
        content.append("\n**Total Value**: ")
               .append(String.format("%.2f", totalValue))
               .append(" Tokens");
        
        // Add timestamp
        content.append("\n\n*Generated: ")
               .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
               .append("*");
        
        // Send to Discord
        sendDiscordMessage(content.toString(), true, "#9b59b6"); // Purple
    }
    
    /**
     * Send market trend analysis to Discord
     */
    public void sendMarketAnalysis() {
        if (!isEnabled()) {
            return;
        }
        
        StringBuilder content = new StringBuilder(":bar_chart: **Crypto Market Analysis**\n\n");
        
        // Global market sentiment
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
        
        // Market sentiment section
        content.append("**Market Sentiment**: ");
        if (bullCount > bearCount) {
            content.append(":green_circle: Bullish");
            content.append("\nThe market is trending upwards with ")
                  .append(bullCount)
                  .append(" cryptocurrencies showing price increases.");
        } else if (bearCount > bullCount) {
            content.append(":red_circle: Bearish");
            content.append("\nThe market is trending downwards with ")
                  .append(bearCount)
                  .append(" cryptocurrencies showing price decreases.");
        } else {
            content.append(":yellow_circle: Neutral");
            content.append("\nThe market is showing mixed signals with equal bull and bear trends.");
        }
        
        // Top gainers and losers
        List<Map.Entry<CryptoCurrency, Double>> changes = new ArrayList<>();
        
        for (CryptoCurrency crypto : cryptoManager.getAllCryptocurrencies()) {
            List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
            if (history.size() >= 2) {
                double lastPrice = crypto.getCurrentPrice();
                double prevPrice = history.get(history.size() - 2);
                double percentChange = ((lastPrice - prevPrice) / prevPrice) * 100;
                
                changes.add(new AbstractMap.SimpleEntry<>(crypto, percentChange));
            }
        }
        
        // Sort by percent change
        changes.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));
        
        // Top gainers
        content.append("\n\n**Top Gainers**:\n");
        int count = 0;
        for (Map.Entry<CryptoCurrency, Double> entry : changes) {
            if (count >= 3 || entry.getValue() <= 0) break;
            
            CryptoCurrency crypto = entry.getKey();
            double change = entry.getValue();
            
            content.append("• **")
                  .append(crypto.getSymbol())
                  .append("**: +")
                  .append(String.format("%.2f", change))
                  .append("% (")
                  .append(formatPrice(crypto.getCurrentPrice()))
                  .append(" Tokens)\n");
                  
            count++;
        }
        
        if (count == 0) {
            content.append("No cryptocurrencies are showing gains currently.\n");
        }
        
        // Top losers (reverse order)
        Collections.reverse(changes);
        
        content.append("\n**Top Losers**:\n");
        count = 0;
        for (Map.Entry<CryptoCurrency, Double> entry : changes) {
            if (count >= 3 || entry.getValue() >= 0) break;
            
            CryptoCurrency crypto = entry.getKey();
            double change = entry.getValue();
            
            content.append("• **")
                  .append(crypto.getSymbol())
                  .append("**: ")
                  .append(String.format("%.2f", change))
                  .append("% (")
                  .append(formatPrice(crypto.getCurrentPrice()))
                  .append(" Tokens)\n");
                  
            count++;
        }
        
        if (count == 0) {
            content.append("No cryptocurrencies are showing losses currently.\n");
        }
        
        // Trading volume (this would require tracking actual trades)
        // For now we'll just add a placeholder
        content.append("\n**Market Recommendation**:\n");
        if (bullCount > bearCount * 2) {
            content.append("Strong bullish trend detected. Consider buying opportunities for lower-risk assets.");
        } else if (bearCount > bullCount * 2) {
            content.append("Strong bearish trend detected. Consider securing profits or waiting for better entry points.");
        } else if (bullCount > bearCount) {
            content.append("Slightly bullish market. Diversify your portfolio with a mix of stable and growth assets.");
        } else if (bearCount > bullCount) {
            content.append("Slightly bearish market. Consider reducing exposure to high-risk assets.");
        } else {
            content.append("Neutral market conditions. Focus on long-term investments and dollar-cost averaging.");
        }
        
        // Add timestamp
        content.append("\n\n*Analysis generated: ")
               .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
               .append("*");
        
        // Send to Discord
        sendDiscordMessage(content.toString(), true, "#f39c12"); // Orange
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
     * Send message to Discord using the configured method
     */
    private void sendDiscordMessage(String content, boolean useEmbed, String colorHex) {
        if (integrationMethod.equalsIgnoreCase("webhook")) {
            sendWebhookMessage(content, useEmbed, colorHex);
        } else if (integrationMethod.equalsIgnoreCase("discordsrv")) {
            sendDiscordSRVMessage(content, useEmbed, colorHex);
        }
    }
    
    /**
     * Send message using webhook
     */
    private void sendWebhookMessage(String content, boolean useEmbed, String colorHex) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().warning("Crypto Discord: Webhook URL tidak dikonfigurasi!");
            return;
        }
        
        // Run in async thread untuk mencegah lag
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject json = new JSONObject();
                
                // Set username dan avatar jika tersedia
                if (webhookUsername != null && !webhookUsername.isEmpty()) {
                    json.put("username", webhookUsername);
                }
                
                if (webhookAvatarUrl != null && !webhookAvatarUrl.isEmpty()) {
                    json.put("avatar_url", webhookAvatarUrl);
                }
                
                if (useEmbed) {
                    JSONObject embed = new JSONObject();
                    
                    // Parse color hex to integer
                    try {
                        String hexColor = colorHex.replace("#", "");
                        Color color = Color.decode("#" + hexColor);
                        int colorInt = color.getRGB() & 0xFFFFFF; // Convert to Discord color format
                        embed.put("color", colorInt);
                    } catch (Exception e) {
                        embed.put("color", 3447003); // Default Discord blue
                    }
                    
                    embed.put("description", content);
                    
                    JSONArray embeds = new JSONArray();
                    embeds.add(embed);
                    
                    json.put("embeds", embeds);
                } else {
                    json.put("content", content);
                }
                
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "NusaCore-Crypto/1.0");
                connection.setDoOutput(true);
                
                String jsonString = json.toJSONString();
                
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode != 204) { // Discord returns 204 No Content on success
                    plugin.getLogger().warning("Crypto Discord: Webhook error! Response code: " + responseCode);
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Crypto Discord: Webhook error! " + e.getMessage());
            }
        });
    }
    
    /**
     * Send message using DiscordSRV
     */
    private void sendDiscordSRVMessage(String content, boolean useEmbed, String colorHex) {
        // Check if DiscordSRV is installed
        if (plugin.getServer().getPluginManager().getPlugin("DiscordSRV") == null) {
            plugin.getLogger().warning("Crypto Discord: DiscordSRV tidak terpasang!");
            return;
        }
        
        // Use DiscordSRV API with try-catch to avoid errors if class is not available
        try {
            // This code will only run if DiscordSRV is available in classpath
            plugin.getLogger().info("Mengirim pesan ke Discord via DiscordSRV");
            
            // Example basic without direct reference to DiscordSRV class
            // Use reflection to safely access DiscordSRV API
            Class<?> discordSRVClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object discordSRV = discordSRVClass.getMethod("getPlugin").invoke(null);
            
            if (useEmbed) {
                // For embeds, just send as plain text for now
                // In a complete implementation, you would use DiscordSRV's embed API
                Object textChannel = discordSRVClass.getMethod("getDestinationTextChannelForGameChannelName", String.class)
                    .invoke(discordSRV, discordSrvChannel);
                
                if (textChannel != null) {
                    textChannel.getClass().getMethod("sendMessage", String.class).invoke(textChannel, content);
                }
            } else {
                // Send plain text message
                Object textChannel = discordSRVClass.getMethod("getDestinationTextChannelForGameChannelName", String.class)
                    .invoke(discordSRV, discordSrvChannel);
                
                if (textChannel != null) {
                    textChannel.getClass().getMethod("sendMessage", String.class).invoke(textChannel, content);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Crypto Discord: Error sending message via DiscordSRV: " + e.getMessage());
            
            // Fallback to webhook if DiscordSRV fails
            if (!webhookUrl.isEmpty()) {
                plugin.getLogger().info("Crypto Discord: Fallback to webhook method...");
                sendWebhookMessage(content, useEmbed, colorHex);
            }
        }
    }
    
    /**
     * Get boolean configuration
     */
    private boolean getConfigBoolean(String path, boolean defaultValue) {
        return config.getBoolean("crypto.discord." + path, defaultValue);
    }
    
    /**
     * Get string configuration
     */
    private String getConfigString(String path, String defaultValue) {
        return config.getString("crypto.discord." + path, defaultValue);
    }
    
    /**
     * Get double configuration
     */
    private double getConfigDouble(String path, double defaultValue) {
        return config.getDouble("crypto.discord." + path, defaultValue);
    }
    
    /**
     * Check if Discord integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Clean up on plugin disable
     */
    public void cleanup() {
        if (marketUpdateTask != null) {
            marketUpdateTask.cancel();
        }
    }
}