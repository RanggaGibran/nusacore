package id.nusacore.gui;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.GUIUtils;
import id.nusacore.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;

import java.io.File;
import java.util.*;

public class TokenShopGUI implements Listener {
    
    private final NusaCore plugin;
    private FileConfiguration config;
    private final List<ShopItem> shopItems = new ArrayList<>();
    private String shopTitle;
    private int shopSize;
    private ItemStack fillerItem;
    
    public TokenShopGUI(NusaCore plugin) {
        this.plugin = plugin;
        
        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Load config
        loadConfig();
        
        plugin.getLogger().info("TokenShopGUI initialized with " + shopItems.size() + " items.");
    }
    
    /**
     * Load shop configuration from tokenshop.yml
     */
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "tokenshop.yml");
        
        if (!configFile.exists()) {
            plugin.saveResource("tokenshop.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Clear existing data
        shopItems.clear();
        
        // Load shop settings
        shopTitle = ColorUtils.colorize(config.getString("settings.title", "&6&lToken Shop"));
        shopSize = config.getInt("settings.shop-size", 54);
        
        // Ensure shopSize is multiple of 9
        if (shopSize % 9 != 0) {
            shopSize = Math.max(9, shopSize - (shopSize % 9));
        }
        
        // Load filler item
        String fillerMaterial = config.getString("settings.filler-item", "BLACK_STAINED_GLASS_PANE");
        Material material;
        try {
            material = Material.valueOf(fillerMaterial.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.BLACK_STAINED_GLASS_PANE;
            plugin.getLogger().warning("Invalid filler item material: " + fillerMaterial + ", using default.");
        }
        fillerItem = new ItemStack(material);
        ItemMeta meta = fillerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            fillerItem.setItemMeta(meta);
        }
        
        // Load items
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection != null) {
                    ShopItem item = parseShopItem(itemSection, itemId);
                    if (item != null) {
                        shopItems.add(item);
                    }
                }
            }
        }
    }
    
    /**
     * Parse shop item from configuration
     */
    private ShopItem parseShopItem(ConfigurationSection section, String itemId) {
        String materialName = section.getString("material");
        if (materialName == null) {
            plugin.getLogger().warning("Missing material for item: " + itemId);
            return null;
        }
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for item: " + itemId + ", " + materialName);
            return null;
        }
        
        String name = ColorUtils.colorize(section.getString("name", itemId));
        int amount = section.getInt("amount", 1);
        int price = section.getInt("price", 100);
        boolean glow = section.getBoolean("glow", false);
        
        List<String> lore = section.getStringList("lore");
        List<String> colorizedLore = new ArrayList<>();
        for (String line : lore) {
            colorizedLore.add(ColorUtils.colorize(line));
        }
        
        List<String> commands = section.getStringList("commands");
        
        ItemStack displayItem = new ItemStack(material, amount);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(colorizedLore);
            
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            displayItem.setItemMeta(meta);
        }
        
        return new ShopItem(itemId, displayItem, price, commands);
    }
    
    /**
     * Open shop menu for player
     */
    public void openShop(Player player) {
        // Create inventory with configured size
        Inventory inventory = Bukkit.createInventory(null, shopSize, shopTitle);
        
        // Fill border with filler items
        GUIUtils.fillBorder(inventory, fillerItem);
        
        // Place items in inventory
        int slot = 10;
        int row = 1;
        int col = 1;
        
        for (ShopItem item : shopItems) {
            // Display item with price
            ItemStack displayItem = item.getDisplayItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                
                lore.add("");
                lore.add(ColorUtils.colorize("&6Harga: &e" + item.getPrice() + " Tokens"));
                lore.add(ColorUtils.colorize("&7Klik untuk membeli"));
                
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            
            // Calculate slot - skip border slots
            slot = 9 * row + col;
            
            // Place item in inventory
            inventory.setItem(slot, displayItem);
            
            // Increment column
            col++;
            
            // Move to next row if needed
            if (col > 7) {
                col = 1;
                row++;
                
                // Skip last row (for navigation buttons)
                if (row >= shopSize / 9 - 1) {
                    break;
                }
            }
        }
        
        // Add token display
        int tokens = plugin.getTokenManager().getTokens(player);
        ItemStack tokenDisplay = new ItemBuilder(Material.GOLD_INGOT)
                .name(ColorUtils.colorize("&6&lYour Tokens: &e" + tokens))
                .lore(ColorUtils.colorize("&7Use tokens to purchase items"))
                .flag(ItemFlag.HIDE_ATTRIBUTES)
                .build();
        inventory.setItem(shopSize - 5, tokenDisplay);
        
        // Add close button
        inventory.setItem(shopSize - 1, GUIUtils.createCloseButton());
        
        // Open inventory for player
        player.openInventory(inventory);
    }
    
    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(shopTitle)) {
            return;
        }
        
        // Prevent taking items
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        // Check if it's the close button
        if (event.getSlot() == shopSize - 1 && 
            clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
        
        // Check for shop item purchase
        for (ShopItem item : shopItems) {
            ItemStack displayItem = item.getDisplayItem();
            
            // Compare item types and display names
            if (clickedItem.getType() == displayItem.getType() && 
                clickedItem.hasItemMeta() && displayItem.hasItemMeta() &&
                clickedItem.getItemMeta().hasDisplayName() && displayItem.getItemMeta().hasDisplayName() &&
                clickedItem.getItemMeta().getDisplayName().equals(displayItem.getItemMeta().getDisplayName())) {
                
                // Process purchase
                int price = item.getPrice();
                int tokens = plugin.getTokenManager().getTokens(player);
                
                // Check if player has enough tokens
                if (tokens < price) {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&cAnda tidak memiliki cukup token! Dibutuhkan &e" + price + " tokens&c."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                
                // Process purchase - remove tokens first
                if (plugin.getTokenManager().removeTokens(player, price)) {
                    // Execute commands
                    for (String cmd : item.getCommands()) {
                        String processedCmd = cmd.replace("{player}", player.getName());
                        plugin.getLogger().info("Executing command: " + processedCmd);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                    }
                    
                    // Success message
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&aPembelian berhasil! &e" + price + " tokens &atelah digunakan."));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    
                    // Refresh token display without reopening the entire inventory
                    refreshTokenDisplay(player);
                } else {
                    player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                        "&cGagal memproses pembelian. Silakan coba lagi."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
        }
    }
    
    /**
     * Refresh token display in player's current inventory without reopening
     * @param player Player whose inventory needs refreshing
     */
    private void refreshTokenDisplay(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getSize() != shopSize || !player.getOpenInventory().getTitle().equals(shopTitle)) {
            return;
        }
        
        int tokens = plugin.getTokenManager().getTokens(player);
        ItemStack tokenDisplay = new ItemBuilder(Material.GOLD_INGOT)
                .name(ColorUtils.colorize("&6&lYour Tokens: &e" + tokens))
                .lore(ColorUtils.colorize("&7Use tokens to purchase items"))
                .flag(ItemFlag.HIDE_ATTRIBUTES)
                .build();
        inventory.setItem(shopSize - 5, tokenDisplay);
    }
    
    /**
     * Reload the shop configuration
     */
    public void reloadConfig() {
        loadConfig();
    }
    
    /**
     * Inner class representing a shop item
     */
    private static class ShopItem {
        private final String id;
        private final ItemStack displayItem;
        private final int price;
        private final List<String> commands;
        
        public ShopItem(String id, ItemStack displayItem, int price, List<String> commands) {
            this.id = id;
            this.displayItem = displayItem;
            this.price = price;
            this.commands = commands;
        }
        
        public String getId() {
            return id;
        }
        
        public ItemStack getDisplayItem() {
            return displayItem;
        }
        
        public int getPrice() {
            return price;
        }
        
        public List<String> getCommands() {
            return commands;
        }
    }
}