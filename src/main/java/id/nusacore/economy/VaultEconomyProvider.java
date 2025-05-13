package id.nusacore.economy;

import id.nusacore.NusaCore;
import id.nusacore.managers.EconomyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provider ekonomi kustom NusaCore yang terintegrasi dengan Vault
 */
public class VaultEconomyProvider implements Economy {
    
    private final NusaCore plugin;
    private final EconomyManager economyManager;
    
    public VaultEconomyProvider(NusaCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public boolean isEnabled() {
        return plugin.isEconomyEnabled();
    }
    
    @Override
    public String getName() {
        // Beberapa plugin seperti Towny mungkin mencari nama tertentu
        return "NusaCoreEconomy"; // Tanpa spasi untuk kompatibilitas lebih baik
    }
    
    @Override
    public boolean hasBankSupport() {
        return false; // Tidak mendukung sistem bank
    }
    
    @Override
    public int fractionalDigits() {
        return 2; // Mendukung 2 digit desimal (00.00)
    }
    
    @Override
    public String format(double amount) {
        return economyManager.formatAmount(amount);
    }
    
    @Override
    public String currencyNamePlural() {
        return economyManager.getCurrencyNamePlural();
    }
    
    @Override
    public String currencyNameSingular() {
        return economyManager.getCurrencyName();
    }
    
    @Override
    public boolean hasAccount(String playerName) {
        return true; // Semua pemain otomatis memiliki akun
    }
    
    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true; // Semua pemain otomatis memiliki akun
    }
    
    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return getBalance(player);
    }
    
    @Override
    public double getBalance(OfflinePlayer player) {
        // Access cache directly instead of calling economyManager.getBalance()
        UUID uuid = player.getUniqueId();
        // If this player has an entry in the cache, use it, otherwise return default balance
        return economyManager.getBalanceCache().getOrDefault(uuid, economyManager.getDefaultBalance());
    }
    
    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }
    
    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }
    
    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Jumlah tidak boleh negatif");
        }
        
        if (!has(player, amount)) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Saldo tidak cukup");
        }
        
        boolean success = economyManager.removeBalance(player, amount);
        
        if (success) {
            return new EconomyResponse(amount, getBalance(player), ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Kesalahan saat mengurangi saldo");
        }
    }
    
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Jumlah tidak boleh negatif");
        }
        
        boolean success = economyManager.addBalance(player, amount);
        
        if (success) {
            return new EconomyResponse(amount, getBalance(player), ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Kesalahan saat menambah saldo");
        }
    }
    
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "NusaCore tidak mendukung system bank");
    }
    
    @Override
    public List<String> getBanks() {
        return new ArrayList<>(); // Kembalikan list kosong, karena kita tidak mendukung bank
    }
    
    @Override
    public boolean createPlayerAccount(String playerName) {
        // Semua akun otomatis dibuat saat pertama kali
        return true;
    }
    
    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        // Semua akun otomatis dibuat saat pertama kali
        return true;
    }
    
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName); // Tidak ada perbedaan antar dunia
    }
    
    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player); // Tidak ada perbedaan antar dunia
    }
}