package id.nusacore.placeholders;

import id.nusacore.NusaCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VotePlaceholders extends PlaceholderExpansion {

    private final NusaCore plugin;
    
    public VotePlaceholders(NusaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nusavote";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public boolean canRegister() {
        return true;
    }
    
    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        switch (identifier.toLowerCase()) {
            case "voteparty_count":
                return String.valueOf(plugin.getVoteManager().getVotePartyCount());
                
            case "voteparty_target":
                return String.valueOf(plugin.getVoteManager().getVotePartyTarget());
                
            case "voteparty_remaining":
                int remaining = plugin.getVoteManager().getVotePartyTarget() - plugin.getVoteManager().getVotePartyCount();
                return String.valueOf(Math.max(0, remaining));
                
            case "voteparty_percentage":
                double percentage = (double) plugin.getVoteManager().getVotePartyCount() / plugin.getVoteManager().getVotePartyTarget() * 100;
                return String.valueOf(Math.round(percentage));
                
            case "can_vote":
                if (player == null) return "false";
                return String.valueOf(plugin.getVoteManager().canVote(player));
                
            case "next_vote_time":
                if (player == null) return "N/A";
                long timeLeft = plugin.getVoteManager().getNextVoteTime(player);
                if (timeLeft <= 0) return "Sekarang";
                
                long hours = timeLeft / (60 * 60 * 1000);
                long minutes = (timeLeft % (60 * 60 * 1000)) / (60 * 1000);
                return hours + "h " + minutes + "m";
        }
        
        return null;
    }
}