package id.nusacore.commands.economy;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PayCommand implements CommandExecutor, TabCompleter {
    
    private final NusaCore plugin;
    
    public PayCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isEconomyEnabled()) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFitur ekonomi tidak diaktifkan pada server ini."));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cHanya pemain yang dapat mengirim uang!"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.pay")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini!"));
            return true;
        }
        
        // Check arguments
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /" + label + " <pemain> <jumlah>"));
            return true;
        }
        
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut tidak ditemukan atau sedang offline!"));
            return true;
        }
        
        // Check if trying to pay yourself
        if (player.equals(targetPlayer)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak dapat mengirim uang kepada diri sendiri!"));
            return true;
        }
        
        // Parse amount
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka!"));
            return true;
        }
        
        // Check if amount is positive
        if (amount <= 0) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0!"));
            return true;
        }
        
        // Round to 2 decimal places
        amount = Math.round(amount * 100) / 100.0;
        
        // Check if player has enough money
        double playerBalance = plugin.getEconomyManager().getBalance(player);
        
        if (playerBalance < amount) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki cukup uang! Saldo Anda: " + 
                    plugin.getEconomyManager().formatAmount(playerBalance)));
            return true;
        }
        
        // Transfer money
        boolean success = plugin.getEconomyManager().transferMoney(player, targetPlayer, amount);
        
        if (success) {
            // Message already sent in transferMoney method
            return true;
        } else {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat mengirim uang. Mohon coba lagi nanti."));
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("nusatown.command.pay")) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            
            // Don't suggest the sender's name
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equals(sender.getName()) && name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggest common amounts
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            suggestions.add("10");
            suggestions.add("50");
            suggestions.add("100");
            suggestions.add("500");
            suggestions.add("1000");
            
            return suggestions.stream()
                    .filter(amount -> amount.startsWith(input))
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}