package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;

public class RTPCancelCommand implements CommandExecutor {
    
    private final NusaCore plugin;
    
    public RTPCancelCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "This command can only be used by players."));
            return true;
        }
        
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        
        // Mencoba membatalkan RTP
        boolean cancelled = ((RTPCommand)plugin.getCommand("rtp").getExecutor()).cancelRTP(playerUUID);
        
        if (cancelled) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aPencarian lokasi RTP dibatalkan."));
        } else {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak sedang dalam proses RTP."));
        }
        
        return true;
    }
}