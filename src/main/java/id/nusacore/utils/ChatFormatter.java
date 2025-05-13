package id.nusacore.utils;

import id.nusacore.NusaCore;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatFormatter {

    private final NusaCore plugin;
    private boolean placeholdersEnabled;
    private boolean hoverEnabled;
    private boolean clickEnabled;
    private String chatFormat;
    private List<String> hoverText;
    private String clickAction;
    private String clickValue;
    private static final Pattern NAME_PATTERN = Pattern.compile("%player_name%");

    public ChatFormatter(NusaCore plugin) {
        this.plugin = plugin;
        reloadConfig();
    }
    
    /**
     * Reload konfigurasi format chat dari config.yml
     */
    public void reloadConfig() {
        this.placeholdersEnabled = plugin.getConfig().getBoolean("chat.use-placeholders", true);
        this.chatFormat = plugin.getConfig().getString("chat.format", "%player_name% &8» &f%message%");
        this.hoverEnabled = plugin.getConfig().getBoolean("chat.hover.enabled", true);
        this.hoverText = new ArrayList<>(plugin.getConfig().getStringList("chat.hover.text"));
        this.clickEnabled = plugin.getConfig().getBoolean("chat.click.enabled", true);
        this.clickAction = plugin.getConfig().getString("chat.click.action", "SUGGEST_COMMAND");
        this.clickValue = plugin.getConfig().getString("chat.click.value", "/msg %player_name% ");
        
        plugin.getLogger().info("Chat formatter configuration reloaded");
    }
    
    /**
     * Format pesan chat dengan placeholder dan komponen interaktif
     * 
     * @param player Pemain yang mengirim pesan
     * @param message Pesan yang dikirim
     * @return TextComponent yang sudah diformat untuk dikirim ke semua pemain
     */
    public TextComponent formatMessage(Player player, String message) {
        // Gantikan placeholder dalam format chat
        String format = chatFormat;
        
        if (placeholdersEnabled && plugin.hasPlaceholderAPI()) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }
        
        // Ganti %player_name% dengan nama pemain jika tidak ada PlaceholderAPI
        format = format.replace("%player_name%", player.getName());
        
        // Ganti %message% dengan pesan pemain
        format = format.replace("%message%", message);
        
        // Proses format warna
        String coloredFormat = ColorUtils.colorize(format);
        
        // Buat komponen teks dasar
        TextComponent component = new TextComponent();
        
        // Cari bagian nama pemain untuk ditambahkan hover dan click
        Matcher matcher = NAME_PATTERN.matcher(format);
        
        if (matcher.find() && (hoverEnabled || clickEnabled)) {
            // Bagi format menjadi bagian sebelum nama, nama, dan setelah nama
            String[] parts = coloredFormat.split(player.getName(), 2);
            
            // Tambahkan bagian sebelum nama
            component.addExtra(parts[0]);
            
            // Buat komponen nama pemain dengan hover dan click
            TextComponent playerNameComponent = new TextComponent(player.getName());
            
            // Tambahkan hover jika diaktifkan
            if (hoverEnabled && !hoverText.isEmpty()) {
                String hoverContent = hoverText.stream()
                    .map(line -> placeholdersEnabled && plugin.hasPlaceholderAPI() ? 
                        PlaceholderAPI.setPlaceholders(player, line) : line.replace("%player_name%", player.getName()))
                    .map(ColorUtils::colorize)
                    .collect(Collectors.joining("\n"));
                
                playerNameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new Text(hoverContent)));
            }
            
            // Tambahkan click event jika diaktifkan
            if (clickEnabled) {
                String clickValueWithName = clickValue.replace("%player_name%", player.getName());
                
                try {
                    ClickEvent.Action action = ClickEvent.Action.valueOf(clickAction);
                    playerNameComponent.setClickEvent(new ClickEvent(action, clickValueWithName));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid click action in chat config: " + clickAction);
                }
            }
            
            // Tambahkan komponen nama pemain
            component.addExtra(playerNameComponent);
            
            // Tambahkan bagian setelah nama
            if (parts.length > 1) {
                component.addExtra(parts[1]);
            }
        } else {
            // Jika tidak ada format khusus, gunakan semua teks
            component.setText(coloredFormat);
        }
        
        return component;
    }
    
    /**
     * Dapatkan format plain text untuk digunakan dengan DiscordSRV
     */
    public String getPlainTextFormat() {
        // Escape all percent signs by doubling them
        String format = chatFormat.replace("%", "%%");
        
        // Then un-escape just the player and message placeholders
        // by replacing the doubled percent signs with singles for these specific placeholders
        format = format.replace("%%player_name%%", "%1$s");
        format = format.replace("%%message%%", "%2$s");
        
        return format;
    }
}