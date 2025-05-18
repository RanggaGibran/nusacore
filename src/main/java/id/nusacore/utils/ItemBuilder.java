package id.nusacore.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;
    
    public ItemBuilder(Material material) {
        this(material, 1);
    }
    
    public ItemBuilder(Material material, int amount) {
        item = new ItemStack(material, amount);
        meta = item.getItemMeta();
    }
    
    public ItemBuilder name(String name) {
        meta.setDisplayName(name);
        return this;
    }
    
    public ItemBuilder lore(String... lore) {
        return lore(Arrays.asList(lore));
    }
    
    public ItemBuilder lore(List<String> lore) {
        List<String> itemLore = meta.getLore();
        if (itemLore == null) {
            itemLore = new ArrayList<>();
        }
        itemLore.addAll(lore);
        meta.setLore(itemLore);
        return this;
    }
    
    public ItemBuilder enchant(Enchantment enchant, int level) {
        meta.addEnchant(enchant, level, true);
        return this;
    }
    
    public ItemBuilder flag(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }
    
    public ItemBuilder customModelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }
    
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}