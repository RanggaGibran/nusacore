package id.nusacore.commands.economy;

import id.nusacore.NusaCore;
import id.nusacore.gui.TokenShopGUI;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TokenShopCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final TokenShopGUI tokenShopGUI;
    
    public TokenShopCommand(NusaCore plugin) {
        this.plugin = plugin;
        this.tokenShopGUI = new TokenShopGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cHanya pemain yang dapat menggunakan perintah ini."));
            return true;
        }
        
        if (!player.hasPermission("nusatown.command.tokenshop")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        // Check for admin reload command
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("nusatown.command.tokenshop.admin")) {
            tokenShopGUI.reloadConfig();
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aKonfigurasi token shop berhasil dimuat ulang!"));
            return true;
        }
        
        // Open the shop
        tokenShopGUI.openShop(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("nusatown.command.tokenshop.admin")) {
            return Arrays.asList("reload").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}