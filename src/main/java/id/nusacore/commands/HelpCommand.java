package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.managers.HelpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final HelpManager helpManager;
    
    public HelpCommand(NusaCore plugin) {
        this.plugin = plugin;
        this.helpManager = plugin.getHelpManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int page = 1;
        String category = null;
        
        // Parse arguments (page number or category)
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // If not a number, treat as category
                category = args[0].toLowerCase();
            }
            
            // Check for page number as second argument when category is specified
            if (category != null && args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    // Invalid page number, use default
                }
            }
        }
        
        // Display help
        if (category != null) {
            helpManager.showCategoryHelp(sender, category, page);
        } else {
            helpManager.showHelp(sender, page);
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            
            // Add categories as suggestions
            suggestions.addAll(helpManager.getCategories());
            
            // Filter suggestions
            return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        return new ArrayList<>();
    }
}