package id.nusacore.placeholders;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Ekspansi PlaceholderAPI untuk fitur cryptocurrency NusaCore
 */
public class CryptoPlaceholders extends PlaceholderExpansion {

    private final NusaCore plugin;
    private final CryptoManager cryptoManager;

    public CryptoPlaceholders(NusaCore plugin) {
        this.plugin = plugin;
        this.cryptoManager = plugin.getCryptoManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nusacrypto";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true; // Ekspansi tetap terdaftar sampai reload/restart
    }
    
    @Override
    public boolean canRegister() {
        return true; // Ekspansi selalu bisa register
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return "";
        }
        
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return "";
        }
        
        // Portfolio placeholders
        if (identifier.equals("portfolio_value")) {
            return String.format("%.2f", cryptoManager.getPortfolioValue(player));
        }
        
        if (identifier.equals("portfolio_value_formatted")) {
            return formatNumber(cryptoManager.getPortfolioValue(player));
        }
        
        if (identifier.equals("portfolio_count")) {
            return String.valueOf(cryptoManager.getPlayerInvestments(player).size());
        }
        
        // Market placeholders
        if (identifier.equals("market_count")) {
            return String.valueOf(cryptoManager.getAllCryptocurrencies().size());
        }
        
        if (identifier.startsWith("market_trend_")) {
            String trend = identifier.substring("market_trend_".length());
            return getMarketTrend(trend);
        }
        
        // Top cryptocurrency placeholders
        if (identifier.startsWith("top_crypto_")) {
            String[] parts = identifier.substring("top_crypto_".length()).split("_");
            if (parts.length >= 2) {
                int position;
                try {
                    position = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    return "";
                }
                
                return getTopCryptoInfo(position, parts[1]);
            }
        }
        
        // Specific crypto placeholders
        if (identifier.contains("_price_")) {
            String[] parts = identifier.split("_price_");
            if (parts.length == 2) {
                CryptoCurrency crypto = cryptoManager.getCryptocurrency(parts[0]);
                if (crypto == null) {
                    return "";
                }
                
                switch (parts[1]) {
                    case "raw":
                        return String.valueOf(crypto.getCurrentPrice());
                    case "formatted":
                        return formatPrice(crypto.getCurrentPrice());
                    case "short":
                        return formatShortNumber(crypto.getCurrentPrice());
                    default:
                        return String.valueOf(crypto.getCurrentPrice());
                }
            }
        }
        
        if (identifier.contains("_change_")) {
            String[] parts = identifier.split("_change_");
            if (parts.length == 2) {
                return getCryptoChange(parts[0], parts[1]);
            }
        }
        
        if (identifier.contains("_risk")) {
            String cryptoId = identifier.substring(0, identifier.indexOf("_risk"));
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(cryptoId);
            if (crypto == null) {
                return "";
            }
            return crypto.getRisk().getDisplayName();
        }
        
        if (identifier.contains("_volatility")) {
            String cryptoId = identifier.substring(0, identifier.indexOf("_volatility"));
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(cryptoId);
            if (crypto == null) {
                return "";
            }
            return String.format("%.1f", crypto.getVolatility() * 100) + "%";
        }
        
        // Player specific crypto holdings
        if (identifier.contains("_holding")) {
            String cryptoId = identifier.substring(0, identifier.indexOf("_holding"));
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(cryptoId);
            if (crypto == null) {
                return "0";
            }
            return String.format("%.6f", cryptoManager.getPlayerInvestment(player, cryptoId));
        }
        
        if (identifier.contains("_value")) {
            String cryptoId = identifier.substring(0, identifier.indexOf("_value"));
            CryptoCurrency crypto = cryptoManager.getCryptocurrency(cryptoId);
            if (crypto == null) {
                return "0";
            }
            double amount = cryptoManager.getPlayerInvestment(player, cryptoId);
            double value = amount * crypto.getCurrentPrice();
            return String.format("%.2f", value);
        }
        
        return null;
    }
    
    /**
     * Format harga crypto berdasarkan nilainya
     * @param price Harga yang akan diformat
     * @return String yang diformat
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
     * Format angka menjadi format pendek (1K, 1M, dst)
     * @param value Nilai yang akan diformat
     * @return String yang diformat
     */
    private String formatShortNumber(double value) {
        if (value < 1000) {
            return String.format("%.1f", value);
        } else if (value < 1000000) {
            return String.format("%.1fK", value / 1000);
        } else if (value < 1000000000) {
            return String.format("%.1fM", value / 1000000);
        } else {
            return String.format("%.1fB", value / 1000000000);
        }
    }
    
    /**
     * Format angka dengan pemisah ribuan
     * @param value Nilai yang akan diformat
     * @return String yang diformat
     */
    private String formatNumber(double value) {
        if (value >= 1000000) {
            return String.format("%,.1fM", value / 1000000);
        } else if (value >= 1000) {
            return String.format("%,.1fK", value / 1000);
        } else {
            return String.format("%,.2f", value);
        }
    }
    
    /**
     * Dapatkan trend pasar berdasarkan parameter
     * @param type Tipe trend (bullish, bearish, atau neutral)
     * @return Status trend dalam bentuk string
     */
    private String getMarketTrend(String type) {
        int bullCount = 0;
        int bearCount = 0;
        
        List<CryptoCurrency> cryptos = cryptoManager.getAllCryptocurrencies();
        for (CryptoCurrency crypto : cryptos) {
            List<Double> history = cryptoManager.getPriceHistory(crypto.getId());
            if (history.size() >= 2) {
                double current = crypto.getCurrentPrice();
                double previous = history.get(history.size() - 2);
                
                if (current > previous) {
                    bullCount++;
                } else if (current < previous) {
                    bearCount++;
                }
            }
        }
        
        switch (type) {
            case "bullish":
                return String.valueOf(bullCount);
            case "bearish":
                return String.valueOf(bearCount);
            case "neutral":
                return String.valueOf(cryptos.size() - bullCount - bearCount);
            case "status":
                if (bullCount > bearCount) {
                    return "Bullish";
                } else if (bearCount > bullCount) {
                    return "Bearish";
                } else {
                    return "Neutral";
                }
            case "percent_bullish":
                return cryptos.isEmpty() ? "0%" : String.format("%.1f%%", ((double) bullCount / cryptos.size()) * 100);
            case "percent_bearish":
                return cryptos.isEmpty() ? "0%" : String.format("%.1f%%", ((double) bearCount / cryptos.size()) * 100);
            default:
                return "";
        }
    }
    
    /**
     * Dapatkan informasi cryptocurrency teratas
     * @param position Posisi dalam peringkat (1 adalah tertinggi)
     * @param infoType Tipe informasi yang diperlukan (price, name, symbol, change, dll)
     * @return Nilai informasi dalam bentuk string
     */
    private String getTopCryptoInfo(int position, String infoType) {
        List<CryptoCurrency> cryptos = cryptoManager.getAllCryptocurrencies();
        
        // Urutkan berdasarkan harga tertinggi
        cryptos.sort((c1, c2) -> Double.compare(c2.getCurrentPrice(), c1.getCurrentPrice()));
        
        if (position < 1 || position > cryptos.size()) {
            return "";
        }
        
        CryptoCurrency crypto = cryptos.get(position - 1);
        
        switch (infoType) {
            case "id":
                return crypto.getId();
            case "name":
                return crypto.getName();
            case "symbol":
                return crypto.getSymbol();
            case "price":
                return formatPrice(crypto.getCurrentPrice());
            case "risk":
                return crypto.getRisk().getDisplayName();
            case "volatility":
                return String.format("%.1f%%", crypto.getVolatility() * 100);
            default:
                return "";
        }
    }
    
    /**
     * Dapatkan perubahan harga cryptocurrency
     * @param cryptoId ID cryptocurrency
     * @param format Format perubahan (raw, formatted, percent, colored)
     * @return Perubahan harga dalam bentuk string
     */
    private String getCryptoChange(String cryptoId, String format) {
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(cryptoId);
        if (crypto == null) {
            return "";
        }
        
        List<Double> history = cryptoManager.getPriceHistory(cryptoId);
        if (history.size() < 2) {
            return "0";
        }
        
        double current = crypto.getCurrentPrice();
        double previous = history.get(history.size() - 2);
        double change = current - previous;
        double percentChange = (change / previous) * 100;
        
        switch (format) {
            case "raw":
                return String.valueOf(change);
            case "formatted":
                return (change >= 0 ? "+" : "") + String.format("%.6f", change);
            case "percent":
                return (percentChange >= 0 ? "+" : "") + String.format("%.2f%%", percentChange);
            case "colored":
                if (percentChange > 0) {
                    return "§a+" + String.format("%.2f%%", percentChange);
                } else if (percentChange < 0) {
                    return "§c" + String.format("%.2f%%", percentChange);
                } else {
                    return "§70.00%";
                }
            default:
                return String.valueOf(change);
        }
    }
}