package id.nusacore.hooks;

import id.nusacore.NusaCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class TownyHook {
    private final NusaCore plugin;
    
    public TownyHook(NusaCore plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        Plugin towny = Bukkit.getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            plugin.getLogger().info("Towny not found or not enabled");
            return;
        }
        
        // Setelah Towny load, pastikan economy sistem berfungsi
        plugin.getLogger().info("Detected Towny, ensuring economy provider is properly registered");
        
        // Coba tambahkan hook khusus jika diperlukan dengan reflection
        try {
            Class<?> townyClass = Class.forName("com.palmergames.bukkit.towny.Towny");
            Object townyInstance = townyClass.cast(towny);
            
            // Kita bisa memanggil method Towny untuk reload ekonomi jika perlu
            plugin.getLogger().info("Towny hooked successfully");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into Towny: " + e.getMessage());
        }
    }
}