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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class TokenShopGUI implements Listener {
    
    private final NusaCore plugin;
    private FileConfiguration config;
    private final Map<String, ShopCategory> categories = new HashMap<>();
    private String shopTitle;
    private int mainMenuSize;
    private ItemStack fillerItem;
    
    private final Map<UUID, String> playerCurrentCategory = new HashMap<>();
    
    public TokenShopGUI(NusaCore plugin) {
        this.plugin = plugin;
        
        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Load config
        loadConfig();
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
        shopTitle = ColorUtils.colorize(config.getString("settings.title", "&8&lToken Shop"));
        mainMenuSize = config.getInt("settings.main-menu-size", 36);
        
        // Create filler item
        String fillerMaterial = config.getString("settings.filler-item", "BLACK_STAINED_GLASS_PANE");
        fillerItem = new ItemStack(Material.valueOf(fillerMaterial));
        ItemMeta fillerMeta = fillerItem.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            fillerItem.setItemMeta(fillerMeta);
        }
        
        // Load categories
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String categoryId : categoriesSection.getKeys(false)) {
                ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryId);
                if (categorySection != null) {
                    ShopCategory category = new ShopCategory(
                            categoryId,
                            ColorUtils.colorize(categorySection.getString("name", categoryId)),
                            categorySection.getInt("size", 36),
                            parseItemStack(categorySection.getConfigurationSection("icon"))
                    );
                    
                    // Load items in this category
                    ConfigurationSection itemsSection = categorySection.getConfigurationSection("items");
                    if (itemsSection != null) {
                        for (String itemId : itemsSection.getKeys(false)) {
                            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                            if (itemSection != null) {
                                ShopItem item = parseShopItem(itemSection);
                                category.addItem(item);
                            }
                        }
                    }
                    
                    categories.put(categoryId, category);
                }
            }
        }
    }
    
    /**
     * Parse shop item from configuration
     */
    private ShopItem parseShopItem(ConfigurationSection section) {
        ItemStack itemStack = parseItemStack(section);
        int price = section.getInt("price", 100);
        List<String> commands = section.getStringList("commands");
        
        return new ShopItem(itemStack, price, commands);
    }
    
    /**
     * Parse ItemStack from configuration
     */
    private ItemStack parseItemStack(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.STONE);
        
        Material material = Material.valueOf(section.getString("material", "STONE"));
        int amount = section.getInt("amount", 1);
        
        ItemBuilder builder = new ItemBuilder(material, amount);
        
        // Set name if specified
        if (section.contains("name")) {
            builder.name(ColorUtils.colorize(section.getString("name")));
        }
        
        // Set lore if specified
        if (section.contains("lore")) {
            List<String> lore = section.getStringList("lore");
            builder.lore(lore.stream().map(ColorUtils::colorize).toList());
        }
        
        // Add enchant glow if specified
        if (section.getBoolean("glow", false)) {
            builder.enchant(Enchantment.UNBREAKING, 1);
            builder.flag(ItemFlag.HIDE_ENCHANTS);
        }
        
        // Add custom model data if specified
        if (section.contains("custom-model-data")) {
            builder.customModelData(section.getInt("custom-model-data"));
        }
        
        return builder.build();
    }
    
    /**
     * Open main shop menu for player
     */
    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, mainMenuSize, shopTitle);
        
        // Fill with filler items
        GUIUtils.fillInventory(inventory, fillerItem);
        
        // Add category icons
        int slot = 10;
        for (ShopCategory category : categories.values()) {
            inventory.setItem(slot, category.getIcon());
            
            // Increment slot, skip border slots
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
        }
        
        // Player tokens display
        int tokens = plugin.getTokenManager().getTokens(player);
        ItemStack tokenDisplay = new ItemBuilder(Material.GOLD_NUGGET)
                .name(ColorUtils.colorize("&6&lTokens: &e" + tokens))
                .lore(Collections.singletonList(ColorUtils.colorize("&7Gunakan token untuk membeli item")))
                .build();
        inventory.setItem(inventory.getSize() - 5, tokenDisplay);
        
        // Open inventory
        player.openInventory(inventory);
        playerCurrentCategory.remove(player.getUniqueId()); // Clear category tracking
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
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
            ItemStack displayItem = item.getDisplayItem();
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
            
            inventory.setItem(slot, displayItem);
            
            // Increment slot, skip border slots
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
        }
        
        // Player tokens display
        int tokens = plugin.getTokenManager().getTokens(player);
        ItemStack tokenDisplay = new ItemBuilder(Material.GOLD_NUGGET)
                .name(ColorUtils.colorize("&6&lTokens: &e" + tokens))
                .lore(Collections.singletonList(ColorUtils.colorize("&7Gunakan token untuk membeli item")))
                .build();
        inventory.setItem(inventory.getSize() - 5, tokenDisplay);
        
        // Back button
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .name(ColorUtils.colorize("&c&lKembali"))
                .lore(Collections.singletonList(ColorUtils.colorize("&7Kembali ke menu utama")))
                .build();
        inventory.setItem(inventory.getSize() - 1, backButton);
        
        // Open inventory
        player.openInventory(inventory);
        playerCurrentCategory.put(player.getUniqueId(), categoryId);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
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
                if (clickedItem.isSimilar(category.getIcon())) {
                    openCategory(player, category.getId());
                    return;
                }
            }
        } else {
            // Handle category menu clicks
            if (event.getSlot() == event.getInventory().getSize() - 1 && clickedItem.getType() == Material.ARROW) {
                // Back button
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
                        
                        // Compare item types, names and amounts 
                        if (clickedItem.getType() == displayItem.getType() &&
                                clickedItem.getItemMeta() != null && 
                                displayItem.getItemMeta() != null &&
                                clickedItem.getItemMeta().getDisplayName().equals(displayItem.getItemMeta().getDisplayName())) {
                            
                            // Check if player has enough tokens
                            int playerTokens = plugin.getTokenManager().getTokens(player);
                            if (playerTokens < item.getPrice()) {
                                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                        "&cAnda tidak memiliki cukup token! Dibutuhkan &e" + item.getPrice() + " tokens&c."));
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                return;
                            }
                            
                            // Process purchase
                            boolean success = plugin.getTokenManager().removeTokens(player, item.getPrice());
                            if (success) {
                                // Execute commands
                                for (String cmd : item.getCommands()) {
                                    String processedCmd = cmd.replace("{player}", player.getName());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                                }
                                
                                // Success message
                                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                                        "&aPembelian berhasil! &e" + item.getPrice() + " tokens &atelah digunakan."));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                
                                // Update tokens display and refresh GUI
                                openCategory(player, categoryId);
                            } else {
                                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cTerjadi kesalahan saat memproses pembelian."));
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
        
        public void addItem(ShopItem item) {
            items.add(item);
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
            return displayItem.clone();
        }
        
        public int getPrice() {
            return price;
        }
        
        public List<String> getCommands() {
            return commands;
        }
    }
}