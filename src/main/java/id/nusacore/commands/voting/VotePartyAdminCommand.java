package id.nusacore.commands.voting;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VotePartyAdminCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    
    public VotePartyAdminCommand(NusaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("nusatown.voteparty.admin")) {
            sender.sendMessage(ColorUtils.colorize("&c&lᴀɴᴅᴀ ᴛɪᴅᴀᴋ ᴍᴇᴍɪʟɪᴋɪ ɪᴢɪɴ ᴜɴᴛᴜᴋ ᴍᴇɴɢᴀᴛᴜʀ ᴠᴏᴛᴇᴘᴀʀᴛʏ."));
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(ColorUtils.colorize("&cPenggunaan: /" + label + " <set|reset|target|start|reload> [nilai]"));
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "set":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize("&cHarap tentukan angka!"));
                    return true;
                }
                
                try {
                    int value = Integer.parseInt(args[1]);
                    plugin.getVoteManager().setVotePartyCount(value);
                    sender.sendMessage(ColorUtils.colorize("&aVoteParty counter diatur menjadi " + value));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.colorize("&cNilai harus berupa angka!"));
                }
                break;
                
            case "reset":
                plugin.getVoteManager().setVotePartyCount(0);
                sender.sendMessage(ColorUtils.colorize("&aVoteParty counter direset ke 0"));
                break;
                
            case "target":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize("&cHarap tentukan angka!"));
                    return true;
                }
                
                try {
                    int value = Integer.parseInt(args[1]);
                    plugin.getVoteManager().setVotePartyTarget(value);
                    sender.sendMessage(ColorUtils.colorize("&aTarget VoteParty diatur menjadi " + value));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.colorize("&cNilai harus berupa angka!"));
                }
                break;
                
            case "start":
                plugin.getVoteManager().triggerVoteParty();
                sender.sendMessage(ColorUtils.colorize("&aVoteParty telah dijalankan secara manual!"));
                break;
                
            case "reload":
                // Save current data first
                plugin.getVoteManager().saveData();
                
                // Reload configuration
                plugin.getVoteManager().loadConfig();
                
                // Notification
                sender.sendMessage(ColorUtils.colorize("&a&lᴠᴏᴛᴇ &8» &aKonfigurasi vote berhasil dimuat ulang!"));
                sender.sendMessage(ColorUtils.colorize("&8» &7Target VoteParty: &a" + plugin.getVoteManager().getVotePartyTarget()));
                sender.sendMessage(ColorUtils.colorize("&8» &7Progress saat ini: &a" + plugin.getVoteManager().getVotePartyCount()));
                sender.sendMessage(ColorUtils.colorize("&8» &7Bonus chance: &a" + plugin.getVoteManager().getVoteBonusChance() + "%"));
                sender.sendMessage(ColorUtils.colorize("&8» &7URL Vote: &a" + plugin.getVoteManager().getVoteUrl()));
                break;
                
            default:
                sender.sendMessage(ColorUtils.colorize("&cPenggunaan: /" + label + " <set|reset|target|start|reload> [nilai]"));
                break;
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nusatown.voteparty.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = Arrays.asList("set", "reset", "target", "start", "reload");
            List<String> result = new ArrayList<>();
            
            for (String s : completions) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            
            return result;
        }
        
        return new ArrayList<>();
    }
}