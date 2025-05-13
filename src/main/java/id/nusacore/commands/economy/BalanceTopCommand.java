package id.nusacore.commands.economy;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class BalanceTopCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final int ENTRIES_PER_PAGE = 10;
    
    // Cache untuk performa lebih baik
    private long lastRefresh = 0;
    private final long CACHE_DURATION = 60000; // 1 menit
    private List<Map.Entry<UUID, Double>> cachedTopBalances = null;
    
    public BalanceTopCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.isEconomyEnabled()) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFitur ekonomi tidak diaktifkan pada server ini."));
            return true;
        }
        
        int page = 1;
        
        // Parse halaman jika ada
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cNomor halaman tidak valid."));
                return true;
            }
        }
        
        // Tampilkan top balance
        showTopBalances(sender, page);
        return true;
    }
    
    private void showTopBalances(CommandSender sender, int page) {
        // Cek apakah perlu refresh cache
        if (cachedTopBalances == null || System.currentTimeMillis() - lastRefresh > CACHE_DURATION) {
            refreshCache();
        }
        
        int totalPages = (int) Math.ceil((double) cachedTopBalances.size() / ENTRIES_PER_PAGE);
        
        if (page > totalPages) {
            page = totalPages;
        }
        
        if (totalPages == 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTidak ada data saldo pemain."));
            return;
        }
        
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("<gradient:#00A3FF:#00FFD1>Top Balance</gradient> &8- &7Halaman &f" + page + "&7/&f" + totalPages));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        int startIndex = (page - 1) * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, cachedTopBalances.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, Double> entry = cachedTopBalances.get(i);
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String formattedBalance = plugin.getEconomyManager().formatAmount(entry.getValue());
            
            String playerName = player.getName() != null ? player.getName() : "Unknown";
            String position = String.valueOf(i + 1);
            String prefix = "";
            
            // Add medal emoji based on position
            if (i == 0) prefix = "&e\u2605 "; // Gold star
            else if (i == 1) prefix = "&7\u2605 "; // Silver star
            else if (i == 2) prefix = "&6\u2605 "; // Bronze star
            
            sender.sendMessage(ColorUtils.colorize(prefix + "&f#" + position + " &e" + playerName + "&8: &a" + formattedBalance));
        }
        
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        if (totalPages > 1) {
            sender.sendMessage(ColorUtils.colorize("&7Gunakan &f/baltop <halaman> &7untuk melihat halaman lainnya."));
        }
    }
    
    private void refreshCache() {
        Map<UUID, Double> playerBalances = plugin.getEconomyManager().getBalanceCache();
        
        cachedTopBalances = playerBalances.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        lastRefresh = System.currentTimeMillis();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return Collections.emptyList();
    }
}