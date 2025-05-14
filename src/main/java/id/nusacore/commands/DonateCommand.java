package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.gui.RankGUI;
import id.nusacore.utils.ColorUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

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
        
        // Tambahkan sub-command untuk apply rank (admin only)
        if (args.length >= 3 && args[1].equalsIgnoreCase("apply")) {
            if (!sender.hasPermission("nusatown.admin.ranks")) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin!"));
                return true;
            }
            
            String rankId = args[0].toLowerCase();
            String targetName = args[2];
            Player targetPlayer = Bukkit.getPlayer(targetName);
            
            if (targetPlayer == null) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPemain tidak ditemukan atau offline!"));
                return true;
            }
            
            if (plugin.getRankManager().hasRank(rankId)) {
                Map<String, Object> rankData = plugin.getRankManager().getRankData(rankId);
                String displayName = (String) rankData.get("display-name");
                
                // Jalankan commands
                plugin.getRankManager().executeRankCommands(targetPlayer, rankId);
                
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aRank " + displayName + " &atelah diterapkan ke " + targetPlayer.getName()));
                targetPlayer.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aAnda telah menerima rank " + displayName + "&a!"));
                
                // Efek visual dan suara
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                
                // Spawn firework jika player online
                spawnFirework(targetPlayer);
                
                return true;
            } else {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cRank tidak ditemukan!"));
                return true;
            }
        }

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
     * Buat firework untuk pemain saat mendapatkan rank
     */
    private void spawnFirework(Player player) {
        Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        
        // Warna random untuk firework
        Random r = new Random();
        int r1 = r.nextInt(255);
        int g1 = r.nextInt(255);
        int b1 = r.nextInt(255);
        
        FireworkEffect effect = FireworkEffect.builder()
                .flicker(true)
                .trail(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.fromRGB(r1, g1, b1))
                .withFade(Color.WHITE)
                .build();
        
        meta.addEffect(effect);
        meta.setPower(1);
        fw.setFireworkMeta(meta);
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
        
        // Tambahkan informasi commands untuk admin
        if (player.hasPermission("nusatown.admin.ranks")) {
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            player.sendMessage(ColorUtils.colorize("&e&lPerintah yang dijalankan (Admin Only):"));
            
            List<String> commands = plugin.getRankManager().getRankCommands(rankId);
            if (commands.isEmpty()) {
                player.sendMessage(ColorUtils.colorize("&7Tidak ada perintah yang terdaftar."));
            } else {
                for (String cmd : commands) {
                    player.sendMessage(ColorUtils.colorize("&7- &f" + cmd));
                }
            }
            
            // Tambahkan tombol untuk menerapkan rank
            TextComponent applyButton = new TextComponent(ColorUtils.colorize("&a&l[TERAPKAN RANK]"));
            applyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new Text(ColorUtils.colorize("&7Klik untuk menerapkan rank ini ke pemain"))));
            applyButton.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                    "/donate " + rankId + " apply <nama_pemain>"));
            
            player.sendMessage("");
            player.spigot().sendMessage(applyButton);
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
        if (!sender.hasPermission("nusatown.command.donate")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(plugin.getRankManager().getRankIds());
            completions.add("help"); // Tambahkan opsi help
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && sender.hasPermission("nusatown.admin.ranks")) {
            List<String> subCommands = Arrays.asList("apply", "gui");
            
            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[1].equalsIgnoreCase("apply") && sender.hasPermission("nusatown.admin.ranks")) {
            // Tab complete untuk nama pemain
            return null; // Biarkan bukkit menangani tab completion nama pemain
        }
        
        return new ArrayList<>();
    }
}