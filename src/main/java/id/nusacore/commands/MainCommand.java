package id.nusacore.commands;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MainCommand implements CommandExecutor {
    private final NusaCore plugin;
    
    public MainCommand(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            // No arguments, show help
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "help":
                showHelp(sender);
                break;
                
            case "reload":
                if (!sender.hasPermission("nusatown.admin.reload")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk reload plugin!"));
                    return true;
                }
                
                // Start reload process
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&eMulai reload konfigurasi plugin..."));
                
                // Reload all configs
                plugin.reloadAllConfigs();
                
                // Notify completion with more details
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aReload berhasil!"));
                sender.sendMessage(ColorUtils.colorize("&7- &fChat formatter: &aDireload"));
                sender.sendMessage(ColorUtils.colorize("&7- &fRank system: &aDireload"));
                sender.sendMessage(ColorUtils.colorize("&7- &fRankup system: &aDireload"));
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aSemua konfigurasi telah dimuat ulang."));
                break;
                
            case "economy":
                if (!sender.hasPermission("nusatown.admin.economy")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Status ekonomi: " + 
                        (plugin.isEconomyEnabled() ? "&aAktif" : "&cNonaktif")));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown economy <enable/disable>"));
                    return true;
                }
                
                boolean enable = args[1].equalsIgnoreCase("enable");
                boolean disable = args[1].equalsIgnoreCase("disable");
                
                if (!enable && !disable) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown economy <enable/disable>"));
                    return true;
                }
                
                // Update konfigurasi
                plugin.getConfig().set("economy.enabled", enable);
                plugin.saveConfig();
                
                // Perlu restart plugin untuk mengaktifkan/menonaktifkan ekonomi
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Fitur ekonomi " + (enable ? "&adiaktifkan" : "&cdinonaktifkan") + 
                    "&f. Restart server diperlukan untuk mengaplikasikan perubahan."));
                break;
                
            case "protection":
                if (!sender.hasPermission("nusatown.admin.protection")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
                    return true;
                }
                
                if (args.length < 2) {
                    boolean protectionEnabled = plugin.getConfig().getBoolean("spawn.protection.enabled", true);
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Status proteksi spawn: " + 
                        (protectionEnabled ? "&aAktif" : "&cNonaktif")));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown protection <enable/disable>"));
                    return true;
                }
                
                boolean enableProtection = args[1].equalsIgnoreCase("enable");
                boolean disableProtection = args[1].equalsIgnoreCase("disable");
                
                if (!enableProtection && !disableProtection) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown protection <enable/disable>"));
                    return true;
                }
                
                // Update konfigurasi
                plugin.getConfig().set("spawn.protection.enabled", enableProtection);
                plugin.saveConfig();
                
                // Efek langsung tanpa perlu restart
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    "Fitur proteksi spawn " + (enableProtection ? "&adiaktifkan" : "&cdinonaktifkan") + 
                    "&f. Perubahan langsung diterapkan."));
                break;
                
            case "combat":
                if (!sender.hasPermission("nusatown.admin.combat")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
                    return true;
                }
                
                if (args.length < 2) {
                    boolean combatEnabled = plugin.getConfig().getBoolean("combat.enabled", true);
                    int duration = plugin.getConfig().getInt("combat.duration", 10);
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Status combat tag: " + 
                        (combatEnabled ? "&aAktif" : "&cNonaktif")));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Durasi combat tag: &e" + duration + " &fdetik"));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown combat <enable/disable> atau /nusatown combat duration <detik>"));
                    return true;
                }
                
                if (args[1].equalsIgnoreCase("enable")) {
                    plugin.getConfig().set("combat.enabled", true);
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aCombat tag diaktifkan."));
                    return true;
                } else if (args[1].equalsIgnoreCase("disable")) {
                    plugin.getConfig().set("combat.enabled", false);
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cCombat tag dinonaktifkan."));
                    return true;
                } else if (args[1].equalsIgnoreCase("duration") && args.length >= 3) {
                    try {
                        int duration = Integer.parseInt(args[2]);
                        if (duration < 0) {
                            sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cNilai durasi tidak boleh negatif."));
                            return true;
                        }
                        
                        plugin.getConfig().set("combat.duration", duration);
                        plugin.saveConfig();
                        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aDurasi combat tag diatur menjadi &e" + duration + " &adetik."));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cNilai durasi harus berupa angka."));
                    }
                    return true;
                } else {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown combat <enable/disable> atau /nusatown combat duration <detik>"));
                    return true;
                }
                
            case "chat":
                if (!sender.hasPermission("nusatown.admin.chat")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Anda tidak memiliki izin untuk menggunakan perintah ini."));
                    return true;
                }
                
                if (args.length < 2) {
                    boolean chatEnabled = plugin.getConfig().getBoolean("chat.enabled", true);
                    boolean usePlaceholders = plugin.getConfig().getBoolean("chat.use-placeholders", true);
                    String chatFormat = plugin.getConfig().getString("chat.format", "%player_name% &8» &f%message%");
                    
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Status format chat: " + 
                        (chatEnabled ? "&aAktif" : "&cNonaktif")));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Format: &r" + chatFormat));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Placeholder: " + 
                        (usePlaceholders ? "&aAktif" : "&cNonaktif")));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "PlaceholderAPI terdeteksi: " + 
                        (plugin.hasPlaceholderAPI() ? "&aYa" : "&cTidak")));
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown chat <enable/disable> atau /nusatown chat format <format>"));
                    return true;
                }
                
                if (args[1].equalsIgnoreCase("enable")) {
                    plugin.getConfig().set("chat.enabled", true);
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aFormat chat kustom diaktifkan."));
                    return true;
                } else if (args[1].equalsIgnoreCase("disable")) {
                    plugin.getConfig().set("chat.enabled", false);
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFormat chat kustom dinonaktifkan."));
                    return true;
                } else if (args[1].equalsIgnoreCase("format") && args.length >= 3) {
                    // Gabungkan semua argumen mulai dari index 2 sebagai format
                    StringBuilder format = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        format.append(args[i]).append(" ");
                    }
                    
                    String chatFormat = format.toString().trim();
                    plugin.getConfig().set("chat.format", chatFormat);
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aFormat chat diubah menjadi: &r" + chatFormat));
                    return true;
                } else if (args[1].equalsIgnoreCase("placeholders")) {
                    boolean newValue = !plugin.getConfig().getBoolean("chat.use-placeholders", true);
                    plugin.getConfig().set("chat.use-placeholders", newValue);
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Dukungan placeholder " + 
                        (newValue ? "&adiaktifkan" : "&cdinonaktifkan") + "&f."));
                    return true;
                } else {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Gunakan: /nusatown chat <enable/disable> atau /nusatown chat format <format>"));
                    return true;
                }
                
            case "votereload":
                if (!sender.hasPermission("nusatown.voteparty.admin")) {
                    sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cAnda tidak memiliki izin untuk reload sistem vote!"));
                    return true;
                }
                
                // Save current data first
                plugin.getVoteManager().saveData();
                
                // Reload configuration
                plugin.getVoteManager().loadConfig();
                
                // Notification
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aKonfigurasi vote berhasil dimuat ulang!"));
                sender.sendMessage(ColorUtils.colorize("&8» &7Target VoteParty: &a" + plugin.getVoteManager().getVotePartyTarget()));
                sender.sendMessage(ColorUtils.colorize("&8» &7Progress saat ini: &a" + plugin.getVoteManager().getVotePartyCount()));
                break;
                
            default:
                sender.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "Perintah tidak dikenal. Ketik <gradient:#00A3FF:#00FFD1>/nusatown help</gradient> untuk bantuan."));
                break;
        }
        
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("<gradient:#00A3FF:#00FFD1>NusaTown</gradient> <dark_gray>-</dark_gray> <white>Bantuan Perintah</white>"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&f/nusatown help &8- &7Menampilkan menu bantuan ini"));
        
        if (sender.hasPermission("nusatown.admin.reload")) {
            sender.sendMessage(ColorUtils.colorize("&f/nusatown reload &8- &7Muat ulang konfigurasi plugin"));
        }
        
        if (sender.hasPermission("nusatown.admin.economy")) {
            sender.sendMessage(ColorUtils.colorize("&f/nusatown economy &8- &7Cek status fitur ekonomi"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown economy <enable/disable> &8- &7Aktifkan/nonaktifkan ekonomi"));
        }
        
        if (sender.hasPermission("nusatown.admin.protection")) {
            sender.sendMessage(ColorUtils.colorize("&f/nusatown protection &8- &7Cek status proteksi spawn"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown protection <enable/disable> &8- &7Aktifkan/nonaktifkan proteksi spawn"));
        }
        
        if (sender.hasPermission("nusatown.admin.teleport")) {
            sender.sendMessage(ColorUtils.colorize("&f/nusatown teleport &8- &7Cek status countdown teleport"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown teleport <enable/disable> &8- &7Aktifkan/nonaktifkan countdown teleport"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown teleport delay <detik> &8- &7Atur durasi countdown teleport"));
        }
        
        if (sender.hasPermission("nusatown.admin.combat")) {
            sender.sendMessage(ColorUtils.colorize("&f/nusatown combat &8- &7Cek status combat tag"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown combat <enable/disable> &8- &7Aktifkan/nonaktifkan combat tag"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown combat duration <detik> &8- &7Atur durasi combat tag"));
        }
        
        if (sender.hasPermission("nusatown.admin.chat")) {
            sender.sendMessage(ColorUtils.colorize("&f/nusatown chat &8- &7Cek status format chat"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown chat <enable/disable> &8- &7Aktifkan/nonaktifkan format chat kustom"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown chat format <format> &8- &7Atur format chat"));
            sender.sendMessage(ColorUtils.colorize("&f/nusatown chat placeholders &8- &7Toggle placeholder"));
        }
        
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }
}