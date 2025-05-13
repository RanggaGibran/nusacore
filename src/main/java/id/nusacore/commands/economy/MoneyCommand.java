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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final List<String> subcommands = Arrays.asList("give", "take", "set", "reset");
    
    public MoneyCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isEconomyEnabled()) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFitur ekonomi tidak diaktifkan pada server ini."));
            return true;
        }
        
        // Check if has admin permission
        if (!sender.hasPermission("nusatown.command.money.admin")) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini!"));
            return true;
        }
        
        // Show help if no args
        if (args.length < 2) {
            showHelp(sender, label);
            return true;
        }
        
        // Process subcommand
        String subCmd = args[0].toLowerCase();
        String targetName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut tidak ditemukan!"));
            return true;
        }
        
        // Process based on subcommand
        switch (subCmd) {
            case "give":
                return handleGiveCommand(sender, targetPlayer, args);
                
            case "take":
                return handleTakeCommand(sender, targetPlayer, args);
                
            case "set":
                return handleSetCommand(sender, targetPlayer, args);
                
            case "reset":
                return handleResetCommand(sender, targetPlayer);
                
            default:
                showHelp(sender, label);
                return true;
        }
    }
    
    /**
     * Handle 'give' subcommand
     */
    private boolean handleGiveCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /money give <pemain> <jumlah>"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka!"));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0!"));
            return true;
        }
        
        // Round to 2 decimal places
        amount = Math.round(amount * 100) / 100.0;
        
        // Add money to player
        boolean success = plugin.getEconomyManager().addBalance(target, amount);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil menambahkan " + 
                    plugin.getEconomyManager().formatAmount(amount) + " &ake saldo &e" + target.getName() + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aAnda menerima " + plugin.getEconomyManager().formatAmount(amount) + " &adari admin."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat menambahkan uang."));
            return true;
        }
    }
    
    /**
     * Handle 'take' subcommand
     */
    private boolean handleTakeCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /money take <pemain> <jumlah>"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka!"));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0!"));
            return true;
        }
        
        // Round to 2 decimal places
        amount = Math.round(amount * 100) / 100.0;
        
        // Check if player has enough money
        double playerBalance = plugin.getEconomyManager().getBalance(target);
        if (playerBalance < amount) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&c" + target.getName() + " tidak memiliki cukup uang! " + 
                    "Saldo saat ini: " + plugin.getEconomyManager().formatAmount(playerBalance)));
            return true;
        }
        
        // Remove money from player
        boolean success = plugin.getEconomyManager().removeBalance(target, amount);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil mengambil " + 
                    plugin.getEconomyManager().formatAmount(amount) + " &adari saldo &e" + target.getName() + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&c" + plugin.getEconomyManager().formatAmount(amount) + " &ctelah dikurangi dari saldo Anda oleh admin."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat mengurangi uang."));
            return true;
        }
    }
    
    /**
     * Handle 'set' subcommand
     */
    private boolean handleSetCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /money set <pemain> <jumlah>"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka!"));
            return true;
        }
        
        if (amount < 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah tidak boleh negatif!"));
            return true;
        }
        
        // Round to 2 decimal places
        amount = Math.round(amount * 100) / 100.0;
        
        // Set player balance
        boolean success = plugin.getEconomyManager().setBalance(target, amount);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil menetapkan saldo &e" + target.getName() + 
                    " &amenjadi " + plugin.getEconomyManager().formatAmount(amount) + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aSaldo Anda telah diubah menjadi " + plugin.getEconomyManager().formatAmount(amount) + " &aoleh admin."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat menetapkan saldo."));
            return true;
        }
    }
    
    /**
     * Handle 'reset' subcommand
     */
    private boolean handleResetCommand(CommandSender sender, OfflinePlayer target) {
        double defaultBalance = plugin.getEconomyManager().getDefaultBalance();
        
        // Reset player balance to default
        boolean success = plugin.getEconomyManager().resetBalance(target);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil mengatur ulang saldo &e" + target.getName() + 
                    " &ake nilai default " + plugin.getEconomyManager().formatAmount(defaultBalance) + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aSaldo Anda telah diatur ulang ke nilai default " + plugin.getEconomyManager().formatAmount(defaultBalance) + "&a."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat mengatur ulang saldo."));
            return true;
        }
    }
    
    /**
     * Show command help
     */
    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&b&lBantuan Perintah Money"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&6/" + label + " give <pemain> <jumlah> &7- Beri uang ke pemain"));
        sender.sendMessage(ColorUtils.colorize("&6/" + label + " take <pemain> <jumlah> &7- Ambil uang dari pemain"));
        sender.sendMessage(ColorUtils.colorize("&6/" + label + " set <pemain> <jumlah> &7- Atur saldo pemain"));
        sender.sendMessage(ColorUtils.colorize("&6/" + label + " reset <pemain> &7- Atur ulang saldo pemain ke default"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nusatown.command.money.admin")) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return subcommands.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            
            // All subcommands need player name
            if (subcommands.contains(subCmd)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            String input = args[2].toLowerCase();
            
            if (subCmd.equals("give") || subCmd.equals("take") || subCmd.equals("set")) {
                // Suggest common amounts
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
        }
        
        return Collections.emptyList();
    }
}