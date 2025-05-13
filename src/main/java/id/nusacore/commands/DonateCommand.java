package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.gui.RankGUI;
import id.nusacore.utils.ColorUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DonateCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final RankGUI rankGUI;
    
    public DonateCommand(NusaCore plugin) {
        this.plugin = plugin;
        this.rankGUI = new RankGUI(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Mode GUI jika tidak ada argumen
        if (args.length == 0) {
            rankGUI.openMainMenu(player);
            return true;
        } else {
            // Mode text atau GUI untuk menampilkan detail rank tertentu
            String rankId = args[0].toLowerCase();
            
            if (plugin.getRankManager().hasRank(rankId)) {
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("gui")) {
                        // Cek apakah ada nomor halaman
                        if (args.length >= 3) {
                            try {
                                int page = Integer.parseInt(args[2]) - 1; // Convert to 0-based
                                rankGUI.openRankDetails(player, rankId, page);
                            } catch (NumberFormatException e) {
                                rankGUI.openRankDetails(player, rankId);
                            }
                        } else {
                            rankGUI.openRankDetails(player, rankId);
                        }
                    } else {
                        showRankDetails(player, rankId);
                    }
                } else {
                    showRankDetails(player, rankId);
                }
            } else {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Rank &e" + rankId + " &ftidak ditemukan."));
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Ketik &e/" + label + " &funtuk melihat daftar rank."));
            }
            return true;
        }
    }
    
    /**
     * Tampilkan daftar rank donasi
     * @param player Pemain yang akan melihat daftar
     */
    private void showRankList(Player player) {
        // Header
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize(plugin.getRankManager().getTitle()));
        player.sendMessage(ColorUtils.colorize(plugin.getRankManager().getHeader()));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Daftar rank
        List<String> rankIds = plugin.getRankManager().getRankIds();
        
        for (String rankId : rankIds) {
            Map<String, Object> rankData = plugin.getRankManager().getRankData(rankId);
            String displayName = (String) rankData.get("display-name");
            String cost = (String) rankData.get("cost");
            String description = (String) rankData.get("description");
            
            // Buat komponen klik
            TextComponent rankComponent = new TextComponent(ColorUtils.colorize(
                    displayName + " &7- &f" + description + " &7(&f" + cost + "&7)"));
            
            rankComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new Text(ColorUtils.colorize("&7Klik untuk melihat detail " + displayName))));
            rankComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/donate " + rankId));
            
            player.spigot().sendMessage(rankComponent);
        }
        
        // Footer
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Link pembelian
        TextComponent purchaseLink = new TextComponent(ColorUtils.colorize(plugin.getRankManager().getFooter()));
        purchaseLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, plugin.getRankManager().getPurchaseLink()));
        purchaseLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ColorUtils.colorize("&7Klik untuk mengunjungi store"))));
        
        player.spigot().sendMessage(purchaseLink);
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    /**
     * Tampilkan detail rank tertentu
     * @param player Pemain yang akan melihat detail
     * @param rankId ID rank yang akan ditampilkan
     */
    private void showRankDetails(Player player, String rankId) {
        Map<String, Object> rankData = plugin.getRankManager().getRankData(rankId);
        
        String displayName = (String) rankData.get("display-name");
        String cost = (String) rankData.get("cost");
        String description = (String) rankData.get("description");
        String color = (String) rankData.get("color");
        @SuppressWarnings("unchecked")
        List<String> benefits = (List<String>) rankData.get("benefits");
        
        // Header
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize(color + "✦ &lRank " + displayName + color + " ✦"));
        player.sendMessage(ColorUtils.colorize("&7" + description));
        player.sendMessage(ColorUtils.colorize("&7Harga: &f" + cost));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Benefits
        player.sendMessage(ColorUtils.colorize("&fBenefit Rank " + displayName + "&f:"));
        for (String benefit : benefits) {
            player.sendMessage(ColorUtils.colorize(benefit));
        }
        
        // Footer
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        
        // Link pembelian dengan tombol kembali
        TextComponent backButton = new TextComponent(ColorUtils.colorize("&7« &fKembali ke daftar"));
        backButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ColorUtils.colorize("&7Klik untuk kembali ke daftar rank"))));
        backButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/donate"));
        
        TextComponent separator = new TextComponent(ColorUtils.colorize("   &8|   "));
        
        TextComponent buyButton = new TextComponent(ColorUtils.colorize("&a&lBELI SEKARANG"));
        buyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ColorUtils.colorize("&7Klik untuk membeli rank " + displayName))));
        buyButton.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, plugin.getRankManager().getPurchaseLink()));
        
        player.spigot().sendMessage(backButton, separator, buyButton);
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String rankId : plugin.getRankManager().getRankIds()) {
                if (rankId.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(rankId);
                }
            }
            return completions;
        } else if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            if ("gui".startsWith(args[1].toLowerCase())) {
                completions.add("gui");
            }
            return completions;
        }
        return new ArrayList<>();
    }
}