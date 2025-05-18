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
import java.util.stream.Collectors;

public class TokenShopGUI implements Listener {
    
    private final NusaCore plugin;
    private FileConfiguration config;
    private final Map<String, ShopCategory> categories = new HashMap<>();
    private String shopTitle;
    private int mainMenuSize;
    private ItemStack fillerItem;
    
    private final Map<UUID, String> playerCurrentCategory = new HashMap<>();
    
    // Tambahkan logging untuk membantu debugging
    public TokenShopGUI(NusaCore plugin) {
        this.plugin = plugin;
        
        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Load config
        loadConfig();
        
        plugin.getLogger().info("TokenShopGUI initialized with " + categories.size() + " categories.");
        for (ShopCategory category : categories.values()) {
            plugin.getLogger().info("Category: " + category.getId() + " with " + category.getItems().size() + " items.");
        }
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
        categories.clear();
        
        // Load shop settings
        shopTitle = ColorUtils.colorize(config.getString("settings.title", "&6&lToken Shop"));
        mainMenuSize = config.getInt("settings.main-menu-size", 36);
        
        // Ensure mainMenuSize is multiple of 9
        if (mainMenuSize % 9 != 0) {
            mainMenuSize = Math.max(9, mainMenuSize - (mainMenuSize % 9));
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
        
        // Load categories
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String categoryId : categoriesSection.getKeys(false)) {
                ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryId);
                if (categorySection != null) {
                    String name = ColorUtils.colorize(categorySection.getString("name", categoryId));
                    int size = categorySection.getInt("size", 36);
                    
                    // Ensure size is multiple of 9
                    if (size % 9 != 0) {
                        size = Math.max(9, size - (size % 9));
                    }
                    
                    // Parse icon
                    ConfigurationSection iconSection = categorySection.getConfigurationSection("icon");
                    ItemStack icon;
                    if (iconSection != null) {
                        icon = parseItemStack(iconSection);
                    } else {
                        // Default icon
                        icon = new ItemStack(Material.CHEST);
                        ItemMeta iconMeta = icon.getItemMeta();
                        if (iconMeta != null) {
                            iconMeta.setDisplayName(name);
                            icon.setItemMeta(iconMeta);
                        }
                    }
                    
                    // Create category
                    ShopCategory category = new ShopCategory(categoryId, name, size, icon);
                    
                    // Load items
                    ConfigurationSection itemsSection = categorySection.getConfigurationSection("items");
                    if (itemsSection != null) {
                        for (String itemId : itemsSection.getKeys(false)) {
                            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                            if (itemSection != null) {
                                ShopItem item = parseShopItem(itemSection);
                                if (item != null) {
                                    category.addItem(item);
                                }
                            }
                        }
                    }
                    
                    // Add category
                    categories.put(categoryId, category);
                }
            }
        }
    }
    
    /**
     * Parse shop item from configuration
     */
    private ShopItem parseShopItem(ConfigurationSection section) {
        String material = section.getString("material");
        if (material == null) {
            plugin.getLogger().warning("Shop item missing material!");
            return null;
        }
        
        Material itemMaterial;
        try {
            itemMaterial = Material.valueOf(material.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + material);
            return null;
        }
        
        String name = ColorUtils.colorize(section.getString("name", "Item"));
        int amount = section.getInt("amount", 1);
        int price = section.getInt("price", 10);
        List<String> lore = section.getStringList("lore").stream()
                .map(ColorUtils::colorize)
                .collect(Collectors.toList());
        
        // Build display item
        ItemBuilder builder = new ItemBuilder(itemMaterial, amount)
                .name(name)
                .lore(lore);
        
        // Check for enchantments
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            for (String enchantName : enchantSection.getKeys(false)) {
                try {
                    Enchantment enchant = Enchantment.getByName(enchantName.toUpperCase());
                    if (enchant != null) {
                        int level = enchantSection.getInt(enchantName);
                        builder.enchant(enchant, level);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid enchantment: " + enchantName);
                }
            }
        }
        
        // Add item flags
        builder.flag(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        
        // Get commands
        List<String> commands = section.getStringList("commands");
        
        // Create shop item
        return new ShopItem(builder.build(), price, commands);
    }
    
    /**
     * Parse ItemStack from configuration
     */
    private ItemStack parseItemStack(ConfigurationSection section) {
        String materialName = section.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
            plugin.getLogger().warning("Invalid material: " + materialName + ", using STONE instead.");
        }
        
        ItemBuilder builder = new ItemBuilder(material)
                .name(ColorUtils.colorize(section.getString("name", "Item")));
        
        // Add lore
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            builder.lore(lore.stream().map(ColorUtils::colorize).collect(Collectors.toList()));
        }
        
        // Glow effect
        if (section.getBoolean("glow", false)) {
            builder.enchant(Enchantment.UNBREAKING, 1)
                  .flag(ItemFlag.HIDE_ENCHANTS);
        }
        
        return builder.build();
    }
    
    /**
     * Open main shop menu for player
     */
    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, mainMenuSize, shopTitle);
        
        // Fill inventory with filler item
        GUIUtils.fillInventory(inventory, fillerItem);
        
        // Add category buttons
        int slot = 10;
        for (ShopCategory category : categories.values()) {
            inventory.setItem(slot, category.getIcon());
            
            // Next slot
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2; // Skip the border
            }
            if (slot >= mainMenuSize - 9) {
                break;
            }
        }
        
        // Display token balance
        int tokens = plugin.getTokenManager().getTokens(player);
        ItemStack tokenDisplay = new ItemBuilder(Material.GOLD_NUGGET)
                .name(ColorUtils.colorize("&6&lTokens: &e" + tokens))
                .lore(ColorUtils.colorize("&7Gunakan token untuk membeli item"))
                .build();
        inventory.setItem(mainMenuSize - 5, tokenDisplay);
        
        // Open inventory for player
        player.openInventory(inventory);
        
        // Remove from current category tracking
        playerCurrentCategory.remove(player.getUniqueId());
    }
    
    /**
     * Open specific category for player
     */
    public void openCategory(Player player, String categoryId) {
        ShopCategory category = categories.get(categoryId);
        if (category == null) {
            player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cKategori tidak ditemukan!"));
            return;
        }
        
        Inventory inventory = Bukkit.createInventory(null, category.getSize(), 
                ColorUtils.colorize(shopTitle + " &7- " + category.getName()));
        
        // Fill with filler items
        GUIUtils.fillInventory(inventory, fillerItem);
        
        // Add shop items
        int slot = 10;
        for (ShopItem item : category.getItems()) {
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
            
            // Place item in inventory
            inventory.setItem(slot, displayItem);
            
            // Increment slot, skip border slots
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
            
            if (slot >= inventory.getSize() - 9) {
                break; // Prevent overflow of items
            }
        }
        
        // Player tokens display
        int tokens = plugin.getTokenManager().getTokens(player);
        ItemStack tokenDisplay = new ItemBuilder(Material.GOLD_NUGGET)
                .name(ColorUtils.colorize("&6&lTokens: &e" + tokens))
                .lore(Collections.singletonList(ColorUtils.colorize("&7Gunakan token untuk membeli item")))
                .build();
        inventory.setItem(inventory.getSize() - 5, tokenDisplay);
        
        // Back button (ensure this is placed)
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .name(ColorUtils.colorize("&c&lKembali"))
                .lore(Collections.singletonList(ColorUtils.colorize("&7Kembali ke menu utama")))
                .build();
        inventory.setItem(inventory.getSize() - 1, backButton);
        
        // Also add a back button in center bottom
        if (inventory.getSize() > 45) {
            inventory.setItem(49, backButton);
        }
        
        // Open inventory for player
        player.openInventory(inventory);
        
        // Store current category
        playerCurrentCategory.put(player.getUniqueId(), categoryId);
    }
    
    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        String title = event.getView().getTitle();
        
        // Check if this is our GUI
        if (!title.startsWith(ColorUtils.colorize(shopTitle))) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        // Handle main menu clicks
        if (!title.contains(" - ")) {
            // Find which category was clicked
            for (ShopCategory category : categories.values()) {
                // Verifikasi berdasarkan nama dan material
                ItemStack icon = category.getIcon();
                if (clickedItem.getType() == icon.getType() && 
                    clickedItem.getItemMeta() != null && 
                    icon.getItemMeta() != null &&
                    clickedItem.getItemMeta().getDisplayName().equals(icon.getItemMeta().getDisplayName())) {
                    openCategory(player, category.getId());
                    return;
                }
            }
        } else {
            // Handle category menu clicks
            
            // Back button check - improved detection
            if ((event.getSlot() == event.getInventory().getSize() - 1 || event.getSlot() == 49) && 
                clickedItem.getType() == Material.ARROW && 
                clickedItem.getItemMeta() != null && 
                clickedItem.getItemMeta().getDisplayName().contains("Kembali")) {
                // Back button clicked
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                openMainMenu(player);
                return;
            }
            
            // Handle item purchase
            String categoryId = playerCurrentCategory.get(player.getUniqueId());
            if (categoryId != null) {
                ShopCategory category = categories.get(categoryId);
                if (category != null) {
                    for (ShopItem item : category.getItems()) {
                        ItemStack displayItem = item.getDisplayItem();
                        
                        // Compare the clicked item with shop items
                        if (clickedItem.getType() == displayItem.getType() && 
                            clickedItem.getItemMeta() != null && 
                            displayItem.getItemMeta() != null &&
                            clickedItem.getItemMeta().getDisplayName().equals(displayItem.getItemMeta().getDisplayName())) {
                            
                            // Process purchase
                            int price = item.getPrice();
                            
                            // Check if player has enough tokens
                            if (!plugin.getTokenManager().hasTokens(player, price)) {
                                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                        "&cAnda tidak memiliki cukup token! Dibutuhkan &e" + price + " tokens&c."));
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                return;
                            }
                            
                            // Process purchase
                            if (plugin.getTokenManager().removeTokens(player, price)) {
                                // Execute commands
                                for (String cmd : item.getCommands()) {
                                    String processedCmd = cmd.replace("{player}", player.getName());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                                }
                                
                                // Success message
                                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                        "&aPembelian berhasil! &e" + price + " tokens &atelah digunakan."));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                
                                // Refresh inventory to update token count
                                openCategory(player, categoryId);
                            } else {
                                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                        "&cGagal memproses pembelian. Silakan coba lagi."));
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Reload the shop configuration
     */
    public void reloadConfig() {
        loadConfig();
    }
    
    /**
     * Check if a category exists
     */
    public boolean categoryExists(String categoryId) {
        return categories.containsKey(categoryId);
    }
    
    /**
     * Get list of category IDs
     */
    public List<String> getCategoryIds() {
        return new ArrayList<>(categories.keySet());
    }
    
    /**
     * Inner class representing a shop category
     */
    private static class ShopCategory {
        private final String id;
        private final String name;
        private final int size;
        private final ItemStack icon;
        private final List<ShopItem> items = new ArrayList<>();
        
        public ShopCategory(String id, String name, int size, ItemStack icon) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.icon = icon;
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public int getSize() {
            return size;
        }
        
        public ItemStack getIcon() {
            return icon;
        }
        
        public List<ShopItem> getItems() {
            return items;
        }
        
        public void addItem(ShopItem item) {
            items.add(item);
        }
    }
    
    /**
     * Inner class representing a shop item
     */
    private static class ShopItem {
        private final ItemStack displayItem;
        private final int price;
        private final List<String> commands;
        
        public ShopItem(ItemStack displayItem, int price, List<String> commands) {
            this.displayItem = displayItem;
            this.price = price;
            this.commands = commands;
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