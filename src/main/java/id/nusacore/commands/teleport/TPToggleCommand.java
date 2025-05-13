package id.nusacore.commands.teleport;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TPToggleCommand implements CommandExecutor {

    private final NusaCore plugin;
    
    public TPToggleCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.tptoggle")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        // Buka GUI untuk pengaturan teleport
        plugin.getTPAManager().openTPToggleGUI(player);
        
        return true;
    }
}