package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.crypto.CryptoCurrency;
import id.nusacore.crypto.CryptoRisk;
import id.nusacore.crypto.CryptoManager;
import id.nusacore.utils.ColorUtils;
import id.nusacore.crypto.gui.CryptoGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CryptoCommand implements CommandExecutor, TabCompleter {
    
    private final NusaCore plugin;
    
    public CryptoCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        if (!player.hasPermission("nusatown.command.crypto")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        CryptoManager cryptoManager = plugin.getCryptoManager();
        
        // Tambahkan method untuk membuka GUI di onCommand
        // Ketika player mengetik /crypto gui
        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            plugin.getCryptoGUI().openMainMenu(player);
            return true;
        }
        
        // Main command - show portfolio
        if (args.length == 0) {
            showPortfolio(player);
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        switch (subCmd) {
            case "help":
                showHelp(player, label);
                break;
                
            case "market":
                showMarket(player);
                break;
                
            case "info":
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " info <crypto>"));
                    return true;
                }
                showCryptoInfo(player, args[1]);
                break;
                
            case "buy":
                if (args.length < 3) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " buy <crypto> <jumlah-token>"));
                    return true;
                }
                
                String cryptoId = args[1];
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka bulat!"));
                    return true;
                }
                
                cryptoManager.buyCrypto(player, cryptoId, amount);
                break;
                
            case "sell":
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " sell <crypto> [jumlah]"));
                    return true;
                }
                
                cryptoId = args[1];
                double cryptoAmount = 0; // 0 means sell all
                if (args.length >= 3) {
                    try {
                        cryptoAmount = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka!"));
                        return true;
                    }
                }
                
                cryptoManager.sellCrypto(player, cryptoId, cryptoAmount);
                break;
                
            case "portfolio":
                showPortfolio(player);
                break;
                
            case "update":
                if (player.hasPermission("nusatown.command.crypto.admin")) {
                    cryptoManager.updateMarket();
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aMarket crypto berhasil diupdate secara manual."));
                } else {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
                }
                break;
                
            default:
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cSubperintah tidak dikenal. Gunakan /" + label + " help untuk melihat bantuan."));
        }
        
        return true;
    }

    private void showPortfolio(Player player) {
        CryptoManager cryptoManager = plugin.getCryptoManager();
        Map<String, Double> investments = cryptoManager.getPlayerInvestments(player);
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&b&lPORTFOLIO CRYPTO ANDA"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        if (investments.isEmpty()) {
            player.sendMessage(ColorUtils.colorize("&eAnda belum memiliki investasi crypto. Gunakan &f/crypto buy&e untuk mulai berinvestasi!"));
        } else {
            double totalValue = 0;
            
            for (Map.Entry<String, Double> entry : investments.entrySet()) {
                String currencyId = entry.getKey();
                double amount = entry.getValue();
                CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
                
                if (crypto != null) {
                    double value = amount * crypto.getCurrentPrice();
                    totalValue += value;
                    
                    player.sendMessage(ColorUtils.colorize("&f" + crypto.getSymbol() + " &7- &f" + 
                        String.format("%.6f", amount) + " &7(&f" + String.format("%.1f", value) + " Tokens&7)"));
                }
            }
            
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            player.sendMessage(ColorUtils.colorize("&fTotal Value: &b" + String.format("%.1f", totalValue) + " Tokens"));
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&7Gunakan &f/crypto market &7untuk melihat pasar crypto."));
    }
    
    private void showMarket(Player player) {
        CryptoManager cryptoManager = plugin.getCryptoManager();
        List<CryptoCurrency> currencies = cryptoManager.getAllCryptocurrencies();
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&b&lCRYPTO MARKET"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        for (CryptoCurrency crypto : currencies) {
            CryptoRisk risk = crypto.getRisk();
            player.sendMessage(ColorUtils.colorize("&f" + crypto.getSymbol() + " &8- &f" + 
                String.format("%.2f", crypto.getCurrentPrice()) + " Tokens &8| " + 
                risk.getColor() + "Resiko: " + risk.getDisplayName()));
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&7Gunakan &f/crypto info <crypto> &7untuk informasi detil."));
    }
    
    private void showCryptoInfo(Player player, String currencyId) {
        CryptoManager cryptoManager = plugin.getCryptoManager();
        CryptoCurrency crypto = cryptoManager.getCryptocurrency(currencyId);
        
        if (crypto == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCryptocurrency tidak ditemukan!"));
            return;
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&b&lINFO CRYPTO: &f" + crypto.getName() + " (" + crypto.getSymbol() + ")"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&fHarga Saat Ini: &b" + String.format("%.6f", crypto.getCurrentPrice()) + " Tokens"));
        
        CryptoRisk risk = crypto.getRisk();
        player.sendMessage(ColorUtils.colorize("&fTingkat Risiko: " + risk.getColor() + risk.getDisplayName()));
        player.sendMessage(ColorUtils.colorize("&fVolatilitas: &e" + String.format("%.1f", crypto.getVolatility() * 100) + "%"));
        player.sendMessage(ColorUtils.colorize("&fRange Harga: &7" + 
            String.format("%.2f", crypto.getMinPrice()) + " - " + 
            String.format("%.2f", crypto.getMaxPrice()) + " Tokens"));
        
        // Show player's investment in this crypto
        double playerAmount = cryptoManager.getPlayerInvestment(player, currencyId);
        if (playerAmount > 0) {
            double value = playerAmount * crypto.getCurrentPrice();
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            player.sendMessage(ColorUtils.colorize("&fInvestasi Anda: &a" + String.format("%.6f", playerAmount) + 
                " " + crypto.getSymbol() + " &7(&f" + String.format("%.1f", value) + " Tokens&7)"));
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&7Perintah: &f/crypto buy " + currencyId + " <jumlah-token> &7atau &f/crypto sell " + currencyId));
    }
    
    private void showHelp(Player player, String label) {
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&b&lBANTUAN CRYPTO"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " &7- Tampilkan portfolio crypto Anda"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " market &7- Lihat pasar crypto saat ini"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " info <crypto> &7- Lihat info detail crypto"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " buy <crypto> <jumlah-token> &7- Beli crypto"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " sell <crypto> [jumlah] &7- Jual crypto (kosongkan jumlah untuk jual semua)"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " portfolio &7- Tampilkan portfolio Anda"));
        player.sendMessage(ColorUtils.colorize("&f/" + label + " gui &7- Buka antarmuka grafis crypto"));
        
        if (player.hasPermission("nusatown.command.crypto.admin")) {
            player.sendMessage(ColorUtils.colorize("&f/" + label + " update &7- Update pasar crypto secara manual (admin)"));
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("help", "market", "info", "buy", "sell", "portfolio", "gui").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("info") || subCmd.equals("buy") || subCmd.equals("sell")) {
                return plugin.getCryptoManager().getAllCryptocurrencies().stream()
                    .map(CryptoCurrency::getId)
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
}