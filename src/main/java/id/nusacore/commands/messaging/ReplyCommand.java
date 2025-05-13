package id.nusacore.commands.messaging;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ReplyCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    
    public ReplyCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.reply")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPenggunaan: /" + label + " <pesan>"));
            return true;
        }
        
        // Dapatkan pemain terakhir yang mengirim pesan
        Player lastSender = plugin.getMessageManager().getLastMessageSender(player);
        
        if (lastSender == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTidak ada pemain yang dapat dibalas."));
            return true;
        }
        
        // Gabungkan argumen menjadi pesan utuh
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();
        
        // Kirim pesan balasan
        plugin.getMessageManager().sendMessage(player, lastSender, message);
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return new ArrayList<>();
    }
}