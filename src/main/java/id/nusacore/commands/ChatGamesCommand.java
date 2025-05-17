package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGamesCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    
    public ChatGamesCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;
                
            case "start":
                if (!sender.hasPermission("nusatown.chatgames.admin")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk ini!"));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " start <jenis_game>"));
                    return true;
                }
                
                plugin.getChatGamesManager().startManualGame(args[1]);
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aGame " + args[1] + " dimulai!"));
                break;
                
            case "stop":
                if (!sender.hasPermission("nusatown.chatgames.admin")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk ini!"));
                    return true;
                }
                
                plugin.getChatGamesManager().stopGame();
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aGame dihentikan."));
                break;
                
            case "reload":
                if (!sender.hasPermission("nusatown.chatgames.admin")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk ini!"));
                    return true;
                }
                
                plugin.getChatGamesManager().loadConfig();
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aKonfigurasi ChatGames dimuat ulang."));
                break;
                
            case "event":
                if (!sender.hasPermission("nusatown.chatgames.admin")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk ini!"));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " event <start|stop|list>"));
                    return true;
                }
                
                switch(args[1].toLowerCase()) {
                    case "start":
                        if (args.length < 3) {
                            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " event start <eventId>"));
                            return true;
                        }
                        // Start specific event
                        plugin.getChatGamesManager().startEvent(args[2]);
                        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aEvent " + args[2] + " dimulai!"));
                        break;
                        
                    case "stop":
                        // Stop current event
                        plugin.getChatGamesManager().stopEvent();
                        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aEvent dihentikan."));
                        break;
                        
                    case "list":
                        // List available events
                        plugin.getChatGamesManager().listEvents(sender);
                        break;
                }
                break;
                
            case "tournament":
                if (!sender.hasPermission("nusatown.chatgames.admin")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk ini!"));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cGunakan: /" + label + " tournament <start|stop|status>"));
                    return true;
                }
                
                switch(args[1].toLowerCase()) {
                    case "start":
                        // Start tournament
                        plugin.getChatGamesManager().initTournament();
                        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aTurnamen dimulai!"));
                        break;
                        
                    case "stop":
                        // Stop tournament
                        plugin.getChatGamesManager().endTournament();
                        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aTurnamen dihentikan."));
                        break;
                        
                    case "status":
                        // Show tournament status
                        plugin.getChatGamesManager().showTournamentStatus(sender);
                        break;
                }
                break;
                
            default:
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah tidak dikenal! Gunakan /" + label + " help"));
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize("&b&l=== ChatGames Help ==="));
        sender.sendMessage(ColorUtils.colorize("&b/chatgames help &7- Lihat bantuan perintah"));
        
        if (sender.hasPermission("nusatown.chatgames.admin")) {
            sender.sendMessage(ColorUtils.colorize("&b/chatgames start <jenis> &7- Mulai game tertentu"));
            sender.sendMessage(ColorUtils.colorize("&b/chatgames stop &7- Hentikan game yang sedang berjalan"));
            sender.sendMessage(ColorUtils.colorize("&b/chatgames reload &7- Muat ulang konfigurasi"));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("help");
            
            if (sender.hasPermission("nusatown.chatgames.admin")) {
                completions.addAll(Arrays.asList("start", "stop", "reload"));
            }
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start") && 
                sender.hasPermission("nusatown.chatgames.admin")) {
            // Return list of game types
            List<String> gameTypes = new ArrayList<>(plugin.getChatGamesManager().getGameTypes());
            return gameTypes.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}