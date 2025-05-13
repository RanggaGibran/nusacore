package id.nusacore.utils;

import id.nusacore.NusaCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GUIUtils {
    
    /**
     * Buat item untuk GUI
     * 
     * @param material Material item
     * @param name Nama item
     * @param lore Deskripsi item
     * @param enchanted Apakah item di-enchant untuk efek glowing
     * @return ItemStack yang sudah diatur
     */
    public static ItemStack createItem(Material material, String name, List<String> lore, boolean enchanted) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ColorUtils.colorize(line));
            }
            meta.setLore(coloredLore);
            
            if (enchanted) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Buat item untuk GUI dengan lore yang otomatis di-wrap
     * 
     * @param material Material item
     * @param name Nama item
     * @param description Deskripsi item yang akan di-wrap
     * @param enchanted Apakah item di-enchant untuk efek glowing
     * @return ItemStack yang sudah diatur
     */
    public static ItemStack createItemWithWrappedLore(Material material, String name, String description, boolean enchanted) {
        List<String> wrappedLore = wrapText(description, 40);
        return createItem(material, name, wrappedLore, enchanted);
    }
    
    /**
     * Wrap teks menjadi baris-baris dengan panjang maksimum
     * 
     * @param text Teks yang akan di-wrap
     * @param maxLength Panjang maksimum per baris
     * @return List baris teks yang sudah di-wrap
     */
    private static List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() > maxLength) {
                lines.add("&7" + currentLine.toString());
                currentLine = new StringBuilder();
            }
            
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        
        if (currentLine.length() > 0) {
            lines.add("&7" + currentLine.toString());
        }
        
        return lines;
    }
    
    /**
     * Mengisi inventory dengan item border
     * 
     * @param inventory Inventory yang akan diisi border
     * @param borderMaterial Material untuk border
     */
    public static void fillBorder(Inventory inventory, Material borderMaterial) {
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            // Jika slot di tepi inventory
            if (i < 9 || i % 9 == 0 || i % 9 == 8 || i >= size - 9) {
                inventory.setItem(i, createItem(borderMaterial, " ", new ArrayList<>(), false));
            }
        }
    }
    
    /**
     * Buat item "Kembali" untuk navigasi
     * 
     * @return ItemStack tombol kembali
     */
    public static ItemStack createBackButton() {
        return createItem(Material.ARROW, "&c« Kembali", Arrays.asList("&7Klik untuk kembali ke menu sebelumnya"), false);
    }
    
    /**
     * Buat item "Tutup" untuk menutup menu
     * 
     * @return ItemStack tombol tutup
     */
    public static ItemStack createCloseButton() {
        return createItem(Material.BARRIER, "&cTutup", Arrays.asList("&7Klik untuk menutup menu"), false);
    }
}