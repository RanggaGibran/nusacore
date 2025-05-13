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

public class TokenCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final List<String> subcommands = Arrays.asList("give", "take", "set", "reset");
    
    public TokenCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if player is just checking their own tokens
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cKonsol tidak memiliki tokens! Gunakan /tokens <player> untuk memeriksa tokens pemain."));
                return true;
            }
            
            Player player = (Player) sender;
            int tokens = plugin.getTokenManager().getTokens(player);
            String formattedTokens = plugin.getTokenManager().formatTokens(tokens);
            
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aAnda memiliki &e" + formattedTokens));
            return true;
        }
        
        // Check if player is viewing someone else's tokens
        if (args.length == 1 && !subcommands.contains(args[0].toLowerCase())) {
            if (!sender.hasPermission("nusatown.command.tokens.others")) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk melihat tokens pemain lain!"));
                return true;
            }
            
            String targetName = args[0];
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            
            if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut tidak ditemukan!"));
                return true;
            }
            
            int tokens = plugin.getTokenManager().getTokens(targetPlayer);
            String formattedTokens = plugin.getTokenManager().formatTokens(tokens);
            
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&e" + targetPlayer.getName() + " &amemiliki &e" + formattedTokens));
            return true;
        }
        
        // Below this is admin commands that require permissions
        if (!sender.hasPermission("nusatown.command.tokens.admin")) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini!"));
            return true;
        }
        
        // Show help if invalid args
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
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /tokens give <pemain> <jumlah>"));
            return true;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka bulat!"));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0!"));
            return true;
        }
        
        // Add tokens to player
        boolean success = plugin.getTokenManager().addTokens(target, amount);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil menambahkan " + 
                    plugin.getTokenManager().formatTokens(amount) + " &akepada &e" + target.getName() + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aAnda menerima " + plugin.getTokenManager().formatTokens(amount) + " &adari admin."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat menambahkan tokens."));
            return true;
        }
    }
    
    /**
     * Handle 'take' subcommand
     */
    private boolean handleTakeCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /tokens take <pemain> <jumlah>"));
            return true;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka bulat!"));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus lebih besar dari 0!"));
            return true;
        }
        
        // Check if player has enough tokens
        int playerTokens = plugin.getTokenManager().getTokens(target);
        if (playerTokens < amount) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&c" + target.getName() + " tidak memiliki cukup tokens! " + 
                    "Saldo saat ini: " + plugin.getTokenManager().formatTokens(playerTokens)));
            return true;
        }
        
        // Remove tokens from player
        boolean success = plugin.getTokenManager().removeTokens(target, amount);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil mengambil " + 
                    plugin.getTokenManager().formatTokens(amount) + " &adari &e" + target.getName() + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&c" + plugin.getTokenManager().formatTokens(amount) + " &ctelah dikurangi dari tokens Anda oleh admin."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat mengurangi tokens."));
            return true;
        }
    }
    
    /**
     * Handle 'set' subcommand
     */
    private boolean handleSetCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /tokens set <pemain> <jumlah>"));
            return true;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah harus berupa angka bulat!"));
            return true;
        }
        
        if (amount < 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cJumlah tidak boleh negatif!"));
            return true;
        }
        
        // Set player tokens
        boolean success = plugin.getTokenManager().setTokens(target, amount);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil menetapkan tokens &e" + target.getName() + 
                    " &amenjadi " + plugin.getTokenManager().formatTokens(amount) + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aTokens Anda telah diubah menjadi " + plugin.getTokenManager().formatTokens(amount) + " &aoleh admin."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat menetapkan tokens."));
            return true;
        }
    }
    
    /**
     * Handle 'reset' subcommand
     */
    private boolean handleResetCommand(CommandSender sender, OfflinePlayer target) {
        int defaultTokens = plugin.getTokenManager().getDefaultTokens();
        
        // Reset player tokens to default
        boolean success = plugin.getTokenManager().resetTokens(target);
        
        if (success) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aBerhasil mengatur ulang tokens &e" + target.getName() + 
                    " &ake nilai default " + plugin.getTokenManager().formatTokens(defaultTokens) + "&a."));
            
            // Notify player if online
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aTokens Anda telah diatur ulang ke nilai default " + plugin.getTokenManager().formatTokens(defaultTokens) + "&a."));
            }
            return true;
        } else {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat mengatur ulang tokens."));
            return true;
        }
    }
    
    /**
     * Show command help
     */
    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&b&lBantuan Perintah Tokens"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&6/" + label + " &7- Lihat tokens Anda"));
        sender.sendMessage(ColorUtils.colorize("&6/" + label + " <pemain> &7- Lihat tokens pemain lain"));
        
        if (sender.hasPermission("nusatown.command.tokens.admin")) {
            sender.sendMessage(ColorUtils.colorize("&6/" + label + " give <pemain> <jumlah> &7- Beri tokens ke pemain"));
            sender.sendMessage(ColorUtils.colorize("&6/" + label + " take <pemain> <jumlah> &7- Ambil tokens dari pemain"));
            sender.sendMessage(ColorUtils.colorize("&6/" + label + " set <pemain> <jumlah> &7- Atur tokens pemain"));
            sender.sendMessage(ColorUtils.colorize("&6/" + label + " reset <pemain> &7- Reset tokens pemain ke default"));
        }
        
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            
            // Admin commands
            if (sender.hasPermission("nusatown.command.tokens.admin")) {
                suggestions.addAll(subcommands);
            }
            
            // Add online player names for checking tokens
            if (sender.hasPermission("nusatown.command.tokens.others")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
            }
            
            // Filter by current input
            String input = args[0].toLowerCase();
            return suggestions.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            
            // All admin subcommands need player name
            if (sender.hasPermission("nusatown.command.tokens.admin") && subcommands.contains(subCmd)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            String input = args[2].toLowerCase();
            
            if (sender.hasPermission("nusatown.command.tokens.admin") && 
                (subCmd.equals("give") || subCmd.equals("take") || subCmd.equals("set"))) {
                // Suggest common amounts
                List<String> suggestions = new ArrayList<>();
                suggestions.add("1");
                suggestions.add("5");
                suggestions.add("10");
                suggestions.add("25");
                suggestions.add("50");
                suggestions.add("100");
                
                return suggestions.stream()
                        .filter(amount -> amount.startsWith(input))
                        .collect(Collectors.toList());
            }
        }
        
        return Collections.emptyList();
    }
}