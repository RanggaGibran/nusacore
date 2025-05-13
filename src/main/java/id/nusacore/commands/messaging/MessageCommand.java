package id.nusacore.commands.messaging;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    
    public MessageCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.msg")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /" + label + " <pemain> <pesan>"));
            return true;
        }
        
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tersebut tidak ditemukan atau sedang offline."));
            return true;
        }
        
        if (targetPlayer.equals(player)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak dapat mengirim pesan kepada diri sendiri."));
            return true;
        }
        
        // Gabungkan argumen sisa menjadi pesan utuh
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();
        
        // Kirim pesan menggunakan message manager
        plugin.getMessageManager().sendMessage(player, targetPlayer, message);
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            // Saran nama pemain online
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            
            // Filter berdasarkan input
            return playerNames.stream()
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}