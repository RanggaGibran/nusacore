package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.managers.AFKManager;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AFKCommand implements CommandExecutor {

    private final NusaCore plugin;
    
    public AFKCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.afk")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        // Toggle AFK state
        plugin.getAFKManager().toggleAFK(player, args.length > 0 ? String.join(" ", args) : null);
        
        return true;
    }
}