package id.nusacore.commands.voting;

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

public class VoteRewardCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    
    public VoteRewardCommand(NusaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("nusatown.votereward.execute")) {
            sender.sendMessage(ColorUtils.colorize("&c&lᴀɴᴅᴀ ᴛɪᴅᴀᴋ ᴍᴇᴍɪʟɪᴋɪ ɪᴢɪɴ ᴜɴᴛᴜᴋ ᴍᴇɴᴊᴀʟᴀɴᴋᴀɴ ᴘᴇʀɪɴᴛᴀʜ ɪɴɪ."));
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(ColorUtils.colorize("&cPenggunaan: /" + label + " <player>"));
            return true;
        }
        
        // Find the player
        Player targetPlayer = Bukkit.getPlayerExact(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(ColorUtils.colorize("&cPemain tidak ditemukan!"));
            return true;
        }
        
        // Process the vote
        plugin.getVoteManager().processVote(targetPlayer);
        
        if (sender != targetPlayer) {
            sender.sendMessage(ColorUtils.colorize("&aVote reward telah diberikan kepada " + targetPlayer.getName()));
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Get all online player names for tab completion
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}