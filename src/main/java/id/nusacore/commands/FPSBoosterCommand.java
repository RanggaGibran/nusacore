package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.gui.FPSBoosterGUI;
import id.nusacore.managers.FPSBoosterManager;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FPSBoosterCommand implements CommandExecutor, TabCompleter {

    private final NusaCore plugin;
    private final FPSBoosterManager fpsBoosterManager;
    private final FPSBoosterGUI gui;
    
    public FPSBoosterCommand(NusaCore plugin) {
        this.plugin = plugin;
        this.fpsBoosterManager = plugin.getFPSBoosterManager();
        this.gui = new FPSBoosterGUI(plugin);
    }
    
    /**
     * Get the FPSBoosterGUI instance
     * @return The GUI instance
     */
    public FPSBoosterGUI getGUI() {
        return gui;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cPerintah ini hanya dapat digunakan oleh pemain."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("nusatown.command.fpsbooster")) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
            return true;
        }
        
        UUID playerId = player.getUniqueId();
        FPSBoosterManager.PlayerBoostSettings settings = fpsBoosterManager.getPlayerSettings(playerId);
        
        // If no arguments, open the GUI
        if (args.length == 0) {
            gui.openGUI(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "gui":
                gui.openGUI(player);
                break;
                
            case "on":
                fpsBoosterManager.setBoostEnabled(playerId, true);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aFPS Booster diaktifkan! Partikel dan efek visual akan dikurangi."));
                showStatus(player);
                break;
                
            case "off":
                fpsBoosterManager.setBoostEnabled(playerId, false);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFPS Booster dinonaktifkan. Semua efek visual akan normal."));
                break;
                
            case "status":
                showStatus(player);
                break;
                
            case "particles":
                boolean particlesState = !settings.isFilterParticles();
                fpsBoosterManager.setParticleFilterEnabled(playerId, particlesState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (particlesState ? "&aFilter partikel &fdiaktifkan!" : "&cFilter partikel &fdinonaktifkan.")));
                break;
                
            case "itemframes":
                boolean itemFramesState = !settings.isFilterItemFrames();
                fpsBoosterManager.setItemFrameFilterEnabled(playerId, itemFramesState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (itemFramesState ? "&aFilter item frame &fdiaktifkan!" : "&cFilter item frame &fdinonaktifkan.")));
                break;
                
            case "paintings":
                boolean paintingsState = !settings.isFilterPaintings();
                fpsBoosterManager.setPaintingFilterEnabled(playerId, paintingsState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (paintingsState ? "&aFilter lukisan &fdiaktifkan!" : "&cFilter lukisan &fdinonaktifkan.")));
                break;
                
            case "animations":
                boolean animationsState = !settings.isFilterAnimations();
                fpsBoosterManager.setAnimationFilterEnabled(playerId, animationsState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (animationsState ? "&aFilter animasi &fdiaktifkan!" : "&cFilter animasi &fdinonaktifkan.")));
                break;
                
            case "reload":
                if (player.hasPermission("nusatown.command.fpsbooster.admin")) {
                    fpsBoosterManager.loadConfig();
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aKonfigurasi FPS Booster berhasil dimuat ulang."));
                } else {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk menggunakan perintah ini."));
                }
                break;
                
            default:
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cParameter tidak valid. Gunakan &f/fps &catau &f/fps gui &cuntuk membuka menu."));
                break;
        }
        
        return true;
    }
    
    /**
     * Show current status of FPS Booster settings
     */
    private void showStatus(Player player) {
        FPSBoosterManager.PlayerBoostSettings settings = fpsBoosterManager.getPlayerSettings(player.getUniqueId());
        
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&b&lFPS Booster Status"));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&7Status: " + (settings.isBoostEnabled() ? "&aAKTIF" : "&cNONAKTIF")));
        player.sendMessage(ColorUtils.colorize("&7Filter Partikel: " + (settings.isFilterParticles() ? "&aAktif" : "&cNonaktif")));
        player.sendMessage(ColorUtils.colorize("&7Filter Item Frame: " + (settings.isFilterItemFrames() ? "&aAktif" : "&cNonaktif")));
        player.sendMessage(ColorUtils.colorize("&7Filter Lukisan: " + (settings.isFilterPaintings() ? "&aAktif" : "&cNonaktif")));
        player.sendMessage(ColorUtils.colorize("&7Filter Animasi: " + (settings.isFilterAnimations() ? "&aAktif" : "&cNonaktif")));
        player.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtils.colorize("&7Gunakan &f/fps gui &7untuk membuka menu pengaturan."));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = Arrays.asList("gui", "on", "off", "status", "particles", "itemframes", "paintings", "animations");
            
            // Add admin command if player has permission
            if (sender.hasPermission("nusatown.command.fpsbooster.admin")) {
                options = new ArrayList<>(options);
                options.add("reload");
            }
            
            String input = args[0].toLowerCase();
            return options.stream()
                    .filter(option -> option.startsWith(input))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}