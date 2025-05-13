package id.nusacore.hooks;

import id.nusacore.NusaCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class RankUpPlaceholder extends PlaceholderExpansion {
    
    private final NusaCore plugin;
    
    public RankUpPlaceholder(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getIdentifier() {
        return "nusarank";
    }
    
    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        
        switch (identifier.toLowerCase()) {
            case "rank":
                // Format rank untuk ditampilkan
                return plugin.getRankupManager().formatRankPlaceholder(player);
                
            case "rank_name":
                // Nama rank (tanpa format)
                return plugin.getRankupManager().getPlayerRank(player);
                
            case "rank_display":
                // Display name rank
                String rank = plugin.getRankupManager().getPlayerRank(player);
                return plugin.getRanksConfig().getString("ranks." + rank + ".display-name", rank);
                
            case "rank_prefix":
                // Prefix rank
                return plugin.getRankupManager().getRankPrefix(player);
                
            case "rank_color":
                // Warna rank
                return plugin.getRankupManager().getRankColor(player);
                
            case "next_rank":
                // Rank berikutnya
                String currentRank = plugin.getRankupManager().getPlayerRank(player);
                return plugin.getRanksConfig().getString("ranks." + currentRank + ".next-rank", "");
                
            case "next_rank_cost":
                // Biaya rank berikutnya
                return String.valueOf(plugin.getRankupManager().getNextRankCost(player));
                
            case "can_rankup":
                // Apakah player bisa naik rank
                return plugin.getRankupManager().canRankUp(player) ? "true" : "false";
        }
        
        return null;
    }
}