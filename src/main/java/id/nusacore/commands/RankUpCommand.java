package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RankUpCommand implements CommandExecutor {

    private final NusaCore plugin;
    
    public RankUpCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        // Handle subcommand reload untuk console atau admin
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!(sender.hasPermission("nusatown.admin.rankup"))) {
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk me-reload sistem rank!"));
                return true;
            }
            
            // Reload konfigurasi rank
            reloadRankConfigurations(sender);
            return true;
        }
        
        // Pastikan player setelah ini
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Menampilkan informasi rank dalam GUI secara default
        if (args.length == 0) {
            openRankGUI(player);
            return true;
        }
        
        // Sub-commands
        switch (args[0].toLowerCase()) {
            case "up":
            case "upgrade":
            case "rankup":
                // Naik rank
                plugin.getRankupManager().rankUp(player);
                break;
                
            case "info":
                if (args.length >= 2) {
                    // Info rank tertentu
                    showRankDetails(player, args[1].toUpperCase());
                } else {
                    // Info rank pemain
                    showRankInfo(player);
                }
                break;
            
            case "gui":
                // Buka GUI
                openRankGUI(player);
                break;
                
            case "help":
            default:
                showHelpMessage(player);
                break;
        }
        
        return true;
    }
    
    /**
     * Reload konfigurasi rank dan GUI
     */
    private void reloadRankConfigurations(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&eMulai reloading sistem rank..."));
        
        // Reload RankupManager (progression system)
        if (plugin.getRankupManager() != null) {
            plugin.getRankupManager().loadConfig();
            plugin.getRankupManager().reloadOnlinePlayersRank();
        }
        
        // Reload RankManager (donation ranks)
        if (plugin.getRankManager() != null) {
            plugin.getRankManager().loadConfig();
        }
        
        // Reload GUI mapping
        if (plugin.getGuiListener() != null) {
            plugin.getGuiListener().initializeRankMapping();
        }
        
        // Notification
        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aReload sistem rank berhasil!"));
        sender.sendMessage(ColorUtils.colorize("&8» &7Rank progression: &aDireload"));
        sender.sendMessage(ColorUtils.colorize("&8» &7Donation ranks: &aDireload"));
        sender.sendMessage(ColorUtils.colorize("&8» &7GUI interface: &aDireload"));
    }
    
    /**
     * Tampilkan informasi rank pemain
     */
    private void showRankInfo(Player player) {
        String currentRank = plugin.getRankupManager().getPlayerRank(player);
        String nextRank = "";
        
        // Akses ranks.yml melalui RankupManager untuk menghindari NPE
        if (plugin.getRankupManager().getRanksConfig() != null) {
            nextRank = plugin.getRankupManager().getRanksConfig().getString("ranks." + currentRank + ".next-rank", "");
        }
        
        // Ambil display name dari RankupManager
        String displayName = plugin.getRankupManager().getDisplayName(currentRank);
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&6Status Rank Anda:"));
        player.sendMessage(ColorUtils.colorize("&7Rank saat ini: " + displayName));
        
        // Benefits
        player.sendMessage(ColorUtils.colorize("&7Benefit rank:"));
        List<String> perks = plugin.getRankupManager().getRankPerks(currentRank);
        for (String perk : perks) {
            player.sendMessage(ColorUtils.colorize(perk));
        }
        
        // Next rank info
        if (nextRank != null && !nextRank.isEmpty()) {
            String nextDisplayName = plugin.getRankupManager().getDisplayName(nextRank);
            double cost = plugin.getRankupManager().getRankCost(nextRank);
            int playtimeRequired = plugin.getRankupManager().getPlaytimeRequired(nextRank);
            
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            player.sendMessage(ColorUtils.colorize("&6Rank berikutnya: &e" + nextDisplayName));
            player.sendMessage(ColorUtils.colorize("&7Persyaratan:"));
            player.sendMessage(ColorUtils.colorize("&7- Biaya: &e" + plugin.getEconomyManager().formatAmount(cost)));
            player.sendMessage(ColorUtils.colorize("&7- Playtime: &e" + playtimeRequired + " jam"));
            
            if (plugin.getRankupManager().canRankUp(player)) {
                player.sendMessage(ColorUtils.colorize("&a✓ Anda memenuhi syarat untuk naik rank!"));
                player.sendMessage(ColorUtils.colorize("&7Ketik &e/rankup up &7untuk naik rank."));
            } else {
                player.sendMessage(ColorUtils.colorize("&c✗ Anda belum memenuhi syarat untuk naik rank."));
            }
        } else {
            player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
            player.sendMessage(ColorUtils.colorize("&eAnda sudah mencapai rank tertinggi!"));
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    /**
     * Tampilkan detail rank tertentu
     */
    private void showRankDetails(Player player, String rankId) {
        if (plugin.getRankupManager().getRanksConfig() == null || 
            !plugin.getRankupManager().getRanksConfig().contains("ranks." + rankId)) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cRank tersebut tidak ditemukan."));
            return;
        }
        
        String displayName = plugin.getRankupManager().getDisplayName(rankId);
        double cost = plugin.getRankupManager().getRankCost(rankId);
        int playtimeRequired = plugin.getRankupManager().getPlaytimeRequired(rankId);
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&6Detail Rank: " + displayName));
        player.sendMessage(ColorUtils.colorize("&7Biaya: &e" + plugin.getEconomyManager().formatAmount(cost)));
        player.sendMessage(ColorUtils.colorize("&7Playtime: &e" + playtimeRequired + " jam"));
        
        // Benefits
        player.sendMessage(ColorUtils.colorize("&7Benefit:"));
        List<String> perks = plugin.getRankupManager().getRankPerks(rankId);
        for (String perk : perks) {
            player.sendMessage(ColorUtils.colorize(perk));
        }
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    /**
     * Tampilkan pesan bantuan
     */
    private void showHelpMessage(Player player) {
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&6Perintah RankUp:"));
        player.sendMessage(ColorUtils.colorize("&e/rankup &7- Lihat informasi rank Anda dalam GUI"));
        player.sendMessage(ColorUtils.colorize("&e/rankup up &7- Naik ke rank berikutnya"));
        player.sendMessage(ColorUtils.colorize("&e/rankup info <rank> &7- Lihat detail rank tertentu"));
        player.sendMessage(ColorUtils.colorize("&e/rankup gui &7- Buka menu GUI rank"));
        
        // Tambahkan perintah reload jika player punya izin admin
        if (player.hasPermission("nusatown.admin.rankup")) {
            player.sendMessage(ColorUtils.colorize("&e/rankup reload &7- Reload konfigurasi rank"));
        }
        
        player.sendMessage(ColorUtils.colorize("&e/rankup help &7- Tampilkan pesan bantuan ini"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
    
    /**
     * Buka GUI Rank untuk pemain
     */
    private void openRankGUI(Player player) {
        // Ambil informasi rank pemain saat ini
        String currentRank = plugin.getRankupManager().getPlayerRank(player);
        String nextRank = plugin.getRankupManager().getNextRank(currentRank);
        
        // Buat inventory 3 baris (27 slot)
        Inventory gui = Bukkit.createInventory(null, 36, 
            ColorUtils.colorize("&8✦ &b&lNusaTown Rank System &8✦"));
        
        // Isi border inventory dengan panel kaca hitam
        GUIUtils.fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        
        // Letakkan rank saat ini di tengah
        ItemStack currentRankItem = createRankItem(currentRank, true);
        gui.setItem(13, currentRankItem);
        
        // Tambahkan rank-rank sebelumnya (jika ada)
        String[] allRanks = {"I", "II", "III", "IV"};
        int currentRankIndex = -1;
        
        // Temukan posisi rank saat ini
        for (int i = 0; i < allRanks.length; i++) {
            if (allRanks[i].equals(currentRank)) {
                currentRankIndex = i;
                break;
            }
        }
        
        // Letakkan rank sebelumnya
        for (int i = 0; i < currentRankIndex; i++) {
            gui.setItem(11 - (currentRankIndex - i), createRankItem(allRanks[i], false));
        }
        
        // Letakkan rank berikutnya
        for (int i = currentRankIndex + 1; i < allRanks.length; i++) {
            gui.setItem(15 + (i - (currentRankIndex + 1)), createRankItem(allRanks[i], false));
        }
        
        // Tambahkan tombol rankup jika bisa naik rank
        if (nextRank != null && !nextRank.isEmpty() && plugin.getRankupManager().canRankUp(player)) {
            double cost = plugin.getRankupManager().getRankCost(nextRank);
            ItemStack rankupButton = GUIUtils.createItem(
                Material.EMERALD, 
                "&a&lNAIK RANK", 
                Arrays.asList(
                    "&7Klik untuk naik ke rank berikutnya:",
                    "&f" + plugin.getRankupManager().getDisplayName(nextRank),
                    "",
                    "&7Biaya: &e" + plugin.getEconomyManager().formatAmount(cost),
                    "",
                    "&aKlik untuk confirm!"
                ),
                true
            );
            gui.setItem(31, rankupButton);
        } else if (nextRank != null && !nextRank.isEmpty()) {
            // Tombol "Belum memenuhi syarat"
            double cost = plugin.getRankupManager().getRankCost(nextRank);
            double balance = plugin.getEconomyManager().getBalance(player);
            int playtimeRequired = plugin.getRankupManager().getPlaytimeRequired(nextRank);
            int playedHours = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60);
            
            ItemStack notEligibleButton = GUIUtils.createItem(
                Material.BARRIER, 
                "&c&lBELUM MEMENUHI SYARAT", 
                Arrays.asList(
                    "&7Persyaratan untuk rank berikutnya:",
                    "&7- Biaya: &c" + plugin.getEconomyManager().formatAmount(cost) + 
                    (balance < cost ? " &7(Kurang: &c" + plugin.getEconomyManager().formatAmount(cost - balance) + "&7)" : " &a✓"),
                    "&7- Playtime: &c" + playtimeRequired + " jam" + 
                    (playedHours < playtimeRequired ? " &7(Kurang: &c" + (playtimeRequired - playedHours) + " jam&7)" : " &a✓"),
                    "",
                    "&cAnda belum memenuhi syarat untuk naik rank!"
                ), 
                true
            );
            gui.setItem(31, notEligibleButton);
        } else {
            // Rank terakhir
            ItemStack maxRankButton = GUIUtils.createItem(
                Material.NETHER_STAR, 
                "&e&lRANK TERTINGGI", 
                Arrays.asList(
                    "&7Selamat! Anda telah mencapai",
                    "&7rank tertinggi di server!",
                    "",
                    "&eTerima kasih atas dedikasi Anda!"
                ),
                true
            );
            gui.setItem(31, maxRankButton);
        }
        
        // Tambahkan tombol close
        gui.setItem(27, GUIUtils.createCloseButton());
        
        // Buka GUI
        player.openInventory(gui);
    }
    
    /**
     * Buat ItemStack untuk representasi rank
     */
    private ItemStack createRankItem(String rankId, boolean isCurrentRank) {
        String displayName = plugin.getRankupManager().getDisplayName(rankId);
        List<String> perks = plugin.getRankupManager().getRankPerks(rankId);
        double cost = plugin.getRankupManager().getRankCost(rankId);
        
        Material material;
        switch (rankId) {
            case "II":
                material = Material.IRON_INGOT;
                break;
            case "III":
                material = Material.GOLD_INGOT;
                break;
            case "IV":
                material = Material.DIAMOND;
                break;
            default: // I
                material = Material.COAL;
                break;
        }
        
        // Buat lore dengan max 5 perks agar tidak terlalu panjang
        List<String> lore = Arrays.asList(
            isCurrentRank ? "&a&lRANK SAAT INI" : "",
            "",
            "&7Biaya: &f" + (cost == 0 ? "Gratis" : plugin.getEconomyManager().formatAmount(cost)),
            "",
            "&6Benefits:"
        );
        
        List<String> finalLore = new java.util.ArrayList<>(lore);
        
        // Tambahkan maksimum 5 perks
        int count = 0;
        for (String perk : perks) {
            if (count < 5) {
                finalLore.add(perk);
                count++;
            } else {
                finalLore.add("&7...dan benefits lainnya");
                break;
            }
        }
        
        return GUIUtils.createItem(material, displayName, finalLore, true);
    }
}