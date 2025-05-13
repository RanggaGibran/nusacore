package id.nusacore.commands.messaging;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SocialSpyCommand implements CommandExecutor {

    private final NusaCore plugin;
    
    public SocialSpyCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.socialspy")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        // Toggle social spy
        boolean status = plugin.getMessageManager().toggleSocialSpy(player);
        
        player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                (status ? "&aSocial spy diaktifkan. Anda akan melihat semua pesan privat." 
                       : "&cSocial spy dinonaktifkan.")));
        
        return true;
    }
}