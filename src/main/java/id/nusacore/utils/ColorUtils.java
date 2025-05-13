package id.nusacore.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class ColorUtils {
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</gradient>");
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern TAG_PATTERN = Pattern.compile("<([a-zA-Z_]+)>(.*?)</\\1>");
    
    // Map untuk mengkonversi nama warna ke kode warna Minecraft
    private static final Map<String, ChatColor> COLOR_MAP = new HashMap<>();
    
    static {
        // Inisialisasi map warna
        COLOR_MAP.put("black", ChatColor.BLACK);
        COLOR_MAP.put("dark_blue", ChatColor.DARK_BLUE);
        COLOR_MAP.put("dark_green", ChatColor.DARK_GREEN);
        COLOR_MAP.put("dark_aqua", ChatColor.DARK_AQUA);
        COLOR_MAP.put("dark_red", ChatColor.DARK_RED);
        COLOR_MAP.put("dark_purple", ChatColor.DARK_PURPLE);
        COLOR_MAP.put("gold", ChatColor.GOLD);
        COLOR_MAP.put("gray", ChatColor.GRAY);
        COLOR_MAP.put("dark_gray", ChatColor.DARK_GRAY);
        COLOR_MAP.put("blue", ChatColor.BLUE);
        COLOR_MAP.put("green", ChatColor.GREEN);
        COLOR_MAP.put("aqua", ChatColor.AQUA);
        COLOR_MAP.put("red", ChatColor.RED);
        COLOR_MAP.put("light_purple", ChatColor.LIGHT_PURPLE);
        COLOR_MAP.put("yellow", ChatColor.YELLOW);
        COLOR_MAP.put("white", ChatColor.WHITE);
        
        // Format tambahan
        COLOR_MAP.put("bold", ChatColor.BOLD);
        COLOR_MAP.put("italic", ChatColor.ITALIC);
        COLOR_MAP.put("underline", ChatColor.UNDERLINE);
        COLOR_MAP.put("strikethrough", ChatColor.STRIKETHROUGH);
        COLOR_MAP.put("obfuscated", ChatColor.MAGIC);
        COLOR_MAP.put("reset", ChatColor.RESET);
    }
    
    public static String colorize(String message) {
        if (message == null) return "";
        
        // Process gradients first
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        while (matcher.find()) {
            String start = matcher.group(1);
            String end = matcher.group(2);
            String content = matcher.group(3);
            
            message = message.replace(matcher.group(0), applyGradient(content, start, end));
        }
        
        // Process hex colors
        matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String hex = matcher.group(1);
            message = message.replace(matcher.group(0), ChatColor.of("#" + hex).toString());
        }
        
        // Process color tags
        matcher = TAG_PATTERN.matcher(message);
        while (matcher.find()) {
            String colorName = matcher.group(1);
            String content = matcher.group(2);
            
            if (COLOR_MAP.containsKey(colorName)) {
                ChatColor color = COLOR_MAP.get(colorName);
                message = message.replace(matcher.group(0), color + content + ChatColor.RESET);
            }
        }
        
        // Process standard color codes
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public static String stripColor(String message) {
        if (message == null) return "";
        
        // Remove gradients
        message = GRADIENT_PATTERN.matcher(message).replaceAll("$3");
        
        // Remove hex colors
        message = HEX_PATTERN.matcher(message).replaceAll("");
        
        // Remove standard color codes
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    private static String applyGradient(String message, String startHex, String endHex) {
        // Convert hex strings to RGB values
        int startRed = Integer.parseInt(startHex.substring(1, 3), 16);
        int startGreen = Integer.parseInt(startHex.substring(3, 5), 16);
        int startBlue = Integer.parseInt(startHex.substring(5, 7), 16);
        
        int endRed = Integer.parseInt(endHex.substring(1, 3), 16);
        int endGreen = Integer.parseInt(endHex.substring(3, 5), 16);
        int endBlue = Integer.parseInt(endHex.substring(5, 7), 16);
        
        // Calculate the gradients
        char[] chars = message.toCharArray();
        StringBuilder builder = new StringBuilder();
        
        for (int i = 0; i < chars.length; i++) {
            float ratio = (float)i / (chars.length - 1);
            
            int red = (int)(startRed + ratio * (endRed - startRed));
            int green = (int)(startGreen + ratio * (endGreen - startGreen));
            int blue = (int)(startBlue + ratio * (endBlue - startBlue));
            
            String hex = String.format("#%02x%02x%02x", red, green, blue);
            builder.append(ChatColor.of(hex)).append(chars[i]);
        }
        
        return builder.toString();
    }
}