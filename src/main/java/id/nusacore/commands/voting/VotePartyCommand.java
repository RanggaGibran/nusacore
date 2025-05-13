package id.nusacore.commands.voting;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VotePartyCommand implements CommandExecutor {

    private final NusaCore plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_TIME = 3 * 1000; // 3 seconds in milliseconds
    
    public VotePartyCommand(NusaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        
        // Check cooldown
        if (cooldowns.containsKey(playerUUID)) {
            long timeLeft = ((cooldowns.get(playerUUID) / 1000) + COOLDOWN_TIME / 1000) - (System.currentTimeMillis() / 1000);
            if (timeLeft > 0) {
                player.sendMessage(ColorUtils.colorize("&c&lᴍᴏʜᴏɴ ᴛᴜɴɢɢᴜ sᴇʙᴇʟᴜᴍ ᴍᴇɴɢɢᴜɴᴀᴋᴀɴ ᴘᴇʀɪɴᴛᴀʜ ɪɴɪ ʟᴀɢɪ."));
                return true;
            }
        }
        
        // Set cooldown
        cooldowns.put(playerUUID, System.currentTimeMillis());
        
        // Show VoteParty info
        player.sendMessage("");
        player.sendMessage(ColorUtils.colorize("&b&l✦ &8&l[ &b&lᴠᴏᴛᴇᴘᴀʀᴛʏ &8&l] &b&l✦"));
        player.sendMessage("");
        player.sendMessage(ColorUtils.colorize("&7ᴘʀᴏɢʀᴇss ᴠᴏᴛᴇᴘᴀʀᴛʏ: &a" + plugin.getVoteManager().getVotePartyCount() + 
                           "&f/&a" + plugin.getVoteManager().getVotePartyTarget()));
        player.sendMessage(ColorUtils.colorize("&7ᴠᴏᴛᴇ ᴅɪ &b&n" + plugin.getVoteManager().getVoteUrl() + 
                           " &7ᴜɴᴛᴜᴋ ᴍᴇᴍᴘᴇʀᴄᴇᴘᴀᴛ ᴠᴏᴛᴇᴘᴀʀᴛʏ!"));
        player.sendMessage("");
        
        // Play sound
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        
        return true;
    }
}