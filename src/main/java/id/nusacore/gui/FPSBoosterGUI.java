package id.nusacore.gui;

import id.nusacore.NusaCore;
import id.nusacore.managers.FPSBoosterManager;
import id.nusacore.utils.ColorUtils;
import id.nusacore.utils.GUIUtils;
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
import java.util.UUID;

public class FPSBoosterGUI {

    private final NusaCore plugin;
    
    public FPSBoosterGUI(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Open the FPS Booster GUI for a player
     * @param player Player to open the GUI for
     */
    public void openGUI(Player player) {
        UUID playerId = player.getUniqueId();
        FPSBoosterManager.PlayerBoostSettings settings = plugin.getFPSBoosterManager().getPlayerSettings(playerId);
        
        // Create inventory with 5 rows (45 slots)
        Inventory inventory = Bukkit.createInventory(null, 45, 
                ColorUtils.colorize("&8⚙ &b&lFPS Booster"));
        
        // Fill border with black glass panes
        GUIUtils.fillBorder(inventory, Material.BLACK_STAINED_GLASS_PANE);
        
        // Add main toggle button
        boolean boostEnabled = settings.isBoostEnabled();
        ItemStack toggleButton = createToggleButton(
            boostEnabled ? Material.LIME_DYE : Material.RED_DYE,
            "&b&lFPS Booster",
            boostEnabled ? "&aAKTIF" : "&cNONAKTIF",
            Arrays.asList(
                "",
                "&7Mengaktifkan FPS Booster akan mengurangi",
                "&7partikel dan efek visual untuk meningkatkan",
                "&7performa pada perangkat Anda.",
                "",
                boostEnabled 
                    ? "&a✓ &7FPS Booster sedang aktif!" 
                    : "&c✗ &7FPS Booster tidak aktif.",
                "",
                "&eKlik untuk " + (boostEnabled ? "&cmatikan" : "&ahidupkan") + " &eFPS Booster"
            ),
            boostEnabled
        );
        inventory.setItem(4, toggleButton);
        
        // Section title
        inventory.setItem(9, createInfoItem(Material.NAME_TAG, "&f&lPengaturan Filter", 
            Arrays.asList("&7Konfigurasi filter yang ingin digunakan")));
        
        // Add filter settings
        boolean filterParticles = settings.isFilterParticles();
        inventory.setItem(20, createToggleButton(
            filterParticles ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&e&lFilter Partikel", 
            filterParticles ? "&aAKTIF" : "&cNONAKTIF",
            Arrays.asList(
                "&7Mengurangi partikel seperti asap,",
                "&7ledakan, efek, dll.",
                "",
                "&eKlik untuk mengubah pengaturan"
            ),
            filterParticles
        ));
        
        boolean filterItemFrames = settings.isFilterItemFrames();
        inventory.setItem(22, createToggleButton(
            filterItemFrames ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&e&lFilter Item Frame", 
            filterItemFrames ? "&aAKTIF" : "&cNONAKTIF",
            Arrays.asList(
                "&7Sembunyikan item frame untuk",
                "&7mengurangi lag pada base yang ramai.",
                "",
                "&eKlik untuk mengubah pengaturan"
            ),
            filterItemFrames
        ));
        
        boolean filterPaintings = settings.isFilterPaintings();
        inventory.setItem(24, createToggleButton(
            filterPaintings ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&e&lFilter Lukisan", 
            filterPaintings ? "&aAKTIF" : "&cNONAKTIF",
            Arrays.asList(
                "&7Sembunyikan lukisan untuk",
                "&7meningkatkan performa.",
                "",
                "&eKlik untuk mengubah pengaturan"
            ),
            filterPaintings
        ));
        
        boolean filterAnimations = settings.isFilterAnimations();
        inventory.setItem(31, createToggleButton(
            filterAnimations ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&e&lFilter Animasi", 
            filterAnimations ? "&aAKTIF" : "&cNONAKTIF",
            Arrays.asList(
                "&7Kurangi animasi dari mob dan",
                "&7entitas lain di dunia.",
                "",
                "&eKlik untuk mengubah pengaturan"
            ),
            filterAnimations
        ));
        
        // Add information displays
        inventory.setItem(13, createInfoItem(Material.PAPER, "&f&lInformasi", 
            Arrays.asList(
                "&7FPS Booster mengurangi efek visual",
                "&7untuk meningkatkan performa game.",
                "",
                "&7Perubahan hanya berlaku untuk anda",
                "&7dan tidak mempengaruhi pemain lain.",
                "",
                "&c&lCatatan:",
                "&7Beberapa opsi dapat menyebabkan",
                "&7visual game berkurang."
            )));
        
        // Add close button
        inventory.setItem(40, GUIUtils.createCloseButton());
        
        // Open inventory
        player.openInventory(inventory);
    }
    
    /**
     * Create a toggle button item
     */
    private ItemStack createToggleButton(Material material, String name, String statusText, List<String> lore, boolean active) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            
            List<String> fullLore = new ArrayList<>();
            fullLore.add(ColorUtils.colorize("&8Status: " + statusText));
            
            for (String line : lore) {
                fullLore.add(ColorUtils.colorize(line));
            }
            
            meta.setLore(fullLore);
            
            if (active) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Create an information item
     */
    private ItemStack createInfoItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ColorUtils.colorize(line));
            }
            meta.setLore(coloredLore);
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Handle clicks on the FPS Booster GUI
     * @param player Player who clicked
     * @param slot Slot that was clicked
     */
    public void handleClick(Player player, int slot) {
        UUID playerId = player.getUniqueId();
        FPSBoosterManager fpsManager = plugin.getFPSBoosterManager();
        FPSBoosterManager.PlayerBoostSettings settings = fpsManager.getPlayerSettings(playerId);
        
        // Main toggle button
        if (slot == 4) {
            boolean newState = !settings.isBoostEnabled();
            fpsManager.setBoostEnabled(playerId, newState);
            
            // Provide feedback
            if (newState) {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&aFPS Booster diaktifkan!"));
            } else {
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + "&cFPS Booster dinonaktifkan."));
            }
            
            // Refresh the GUI
            openGUI(player);
            return;
        }
        
        // Filter toggles
        switch (slot) {
            case 20: // Particles toggle
                boolean particlesState = !settings.isFilterParticles();
                fpsManager.setParticleFilterEnabled(playerId, particlesState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (particlesState ? "&aFilter partikel &fdiaktifkan!" : "&cFilter partikel &fdinonaktifkan.")));
                openGUI(player);
                break;
                
            case 22: // Item frames toggle
                boolean itemFramesState = !settings.isFilterItemFrames();
                fpsManager.setItemFrameFilterEnabled(playerId, itemFramesState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (itemFramesState ? "&aFilter item frame &fdiaktifkan!" : "&cFilter item frame &fdinonaktifkan.")));
                openGUI(player);
                break;
                
            case 24: // Paintings toggle
                boolean paintingsState = !settings.isFilterPaintings();
                fpsManager.setPaintingFilterEnabled(playerId, paintingsState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (paintingsState ? "&aFilter lukisan &fdiaktifkan!" : "&cFilter lukisan &fdinonaktifkan.")));
                openGUI(player);
                break;
                
            case 31: // Animations toggle
                boolean animationsState = !settings.isFilterAnimations();
                fpsManager.setAnimationFilterEnabled(playerId, animationsState);
                player.sendMessage(ColorUtils.colorize(NusaCore.PREFIX + 
                    (animationsState ? "&aFilter animasi &fdiaktifkan!" : "&cFilter animasi &fdinonaktifkan.")));
                openGUI(player);
                break;
                
            case 40: // Close button
                player.closeInventory();
                break;
        }
    }
}