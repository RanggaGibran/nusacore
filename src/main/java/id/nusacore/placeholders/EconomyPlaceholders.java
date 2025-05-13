package id.nusacore.placeholders;

import id.nusacore.NusaCore;
import id.nusacore.managers.EconomyManager;
import id.nusacore.managers.TokenManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Ekspansi PlaceholderAPI untuk fitur ekonomi NusaCore
 */
public class EconomyPlaceholders extends PlaceholderExpansion {

    private final NusaCore plugin;
    private final EconomyManager economyManager;
    private final TokenManager tokenManager;

    public EconomyPlaceholders(NusaCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.tokenManager = plugin.getTokenManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nusacore";
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
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        
        // Jika ekonomi tidak diaktifkan, semua placeholder mengembalikan 0
        if (!plugin.isEconomyEnabled()) {
            if (identifier.contains("balance")) {
                return "0";
            }
            return "";
        }
        
        // Placeholder untuk saldo
        switch (identifier) {
            case "balance":
                return String.valueOf(economyManager.getBalance(player));
                
            case "balance_formatted":
                return economyManager.formatAmount(economyManager.getBalance(player));
                
            case "balance_int":
                return String.valueOf((int)economyManager.getBalance(player));
                
            case "balance_short":
                return formatShortNumber(economyManager.getBalance(player));
        }
        
        // Placeholder untuk status ekonomi server
        if (identifier.equals("server_economy_total")) {
            double total = 0;
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                total += economyManager.getBalance(offlinePlayer);
            }
            return String.valueOf(total);
        }
        
        if (identifier.equals("server_economy_total_formatted")) {
            double total = 0;
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                total += economyManager.getBalance(offlinePlayer);
            }
            return economyManager.formatAmount(total);
        }
        
        // Placeholder untuk info mata uang
        if (identifier.equals("currency_symbol")) {
            return economyManager.getCurrencySymbol();
        }
        
        if (identifier.equals("currency_name")) {
            return economyManager.getCurrencyName();
        }
        
        if (identifier.equals("currency_name_plural")) {
            return economyManager.getCurrencyNamePlural();
        }
        
        // Placeholder untuk token
        if (identifier.equals("tokens")) {
            return String.valueOf(tokenManager.getTokens(player));
        }

        if (identifier.equals("tokens_formatted")) {
            return tokenManager.formatTokens(tokenManager.getTokens(player));
        }

        if (identifier.equals("token_name")) {
            return tokenManager.getTokenName();
        }

        if (identifier.equals("token_name_plural")) {
            return tokenManager.getTokenNamePlural();
        }
        
        // Placeholder tidak ditemukan
        return null;
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
}