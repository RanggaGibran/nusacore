package id.nusacore.commands.teleport;

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

public class TPACommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    
    public TPACommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.tpa")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /tpa <pemain>"));
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        
        if (target == null || !target.isOnline()) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Pemain &e" + args[0] + " &ftidak ditemukan atau sedang offline."));
            return true;
        }
        
        if (target.equals(player)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak dapat teleport ke diri sendiri."));
            return true;
        }
        
        // Kirim permintaan teleport
        plugin.getTPAManager().sendTeleportRequest(player, target);
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> playerNames = new ArrayList<>();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial) && !player.equals(sender)) {
                    playerNames.add(player.getName());
                }
            }
            
            return playerNames;
        }
        
        return new ArrayList<>();
    }
}