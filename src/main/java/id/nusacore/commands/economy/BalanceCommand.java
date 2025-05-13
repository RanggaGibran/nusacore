package id.nusacore.commands.economy;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BalanceCommand implements CommandExecutor, TabCompleter {
    
    private final NusaCore plugin;
    
    public BalanceCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isEconomyEnabled()) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFitur ekonomi tidak diaktifkan pada server ini."));
            return true;
        }
        
        if (args.length == 0) {
            // Check own balance
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cKonsol tidak memiliki saldo! Gunakan /balance <player> untuk memeriksa saldo pemain."));
                return true;
            }
            
            Player player = (Player) sender;
            double balance = plugin.getEconomyManager().getBalance(player);
            String formattedBalance = plugin.getEconomyManager().formatAmount(balance);
            
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aSaldo Anda: " + formattedBalance));
            return true;
        } else {
            // Check other player's balance
            if (!sender.hasPermission("nusatown.command.balance.others")) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk melihat saldo pemain lain!"));
                return true;
            }
            
            String targetName = args[0];
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            
            if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut tidak ditemukan!"));
                return true;
            }
            
            double balance = plugin.getEconomyManager().getBalance(targetPlayer);
            String formattedBalance = plugin.getEconomyManager().formatAmount(balance);
            
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aSaldo &e" + targetPlayer.getName() + "&a: " + formattedBalance));
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nusatown.command.balance.others")) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            // Return online player names that start with the current input
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}