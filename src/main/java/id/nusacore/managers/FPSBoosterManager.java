package id.nusacore.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Sets;
import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FPSBoosterManager {
    private final NusaCore plugin;
    private final Map<UUID, PlayerBoostSettings> playerSettings = new ConcurrentHashMap<>();
    private ProtocolManager protocolManager;
    private FileConfiguration config;
    
    // Debug flag
    private boolean debug = false;
    
    // Default settings
    private boolean filterParticlesDefault = true;
    private boolean filterItemFramesDefault = false;
    private boolean filterPaintingsDefault = false;
    private boolean filterAnimationsDefault = false;
    private int particleReductionRate = 70; // Default 70% reduction
    
    // Packet filtering state
    private boolean registeredPacketListeners = false;
    
    private Set<String> filteredParticleTypes = Sets.newHashSet(
        // Explosion particles
        "poof", "explosion", "explosion_emitter", 
        
        // Water related particles
        "bubble", "bubble_pop", "bubble_column_up", "current_down", "splash",
        "fishing", "underwater",
        
        // Dripping particles
        "dripping_water", "dripping_lava", "dripping_honey", "dripping_obsidian_tear",
        "falling_water", "falling_lava", "falling_honey", "falling_obsidian_tear",
        "landing_water", "landing_lava", "landing_honey", "landing_obsidian_tear",
        
        // Environmental particles
        "cloud", "dust", "dust_color_transition", 
        "smoke", "large_smoke", "campfire_cosy_smoke", "campfire_signal_smoke",
        
        // Effect particles
        "crit", "enchanted_hit", "damage_indicator", 
        "heart", "angry_villager", "happy_villager",
        "instant_effect", "effect", "entity_effect",
        "note", "enchant",
        
        // Fire and lava related
        "flame", "lava", "soul_fire_flame", "soul",
        
        // Item particles
        "item", "item_slime", "item_snowball",
        
        // Portal particles
        "portal", "nautilus", "end_rod", "dragon_breath", "witch",
        
        // Combat
        "sweep_attack", "sonic_boom",
        
        // World particles
        "ash", "white_ash", "crimson_spore", "warped_spore", 
        "spore_blossom_air", "mycelium", "cherry_leaves",
        
        // Fireworks
        "firework",
        
        // Block related
        "block", "block_marker", "falling_dust", "scrape", "wax_on", "wax_off", 
        
        // Sculk
        "sculk_charge", "sculk_charge_pop", "sculk_soul", "vibration", "shriek"
    );
    
    private final Set<EntityType> filteredEntityTypes = Sets.newHashSet(
        EntityType.ITEM_FRAME,
        EntityType.GLOW_ITEM_FRAME,
        EntityType.PAINTING,
        EntityType.ARMOR_STAND
    );
    
    public FPSBoosterManager(NusaCore plugin) {
        this.plugin = plugin;
        loadConfig();
        
        // Initialize ProtocolLib if available
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            registerPacketListeners();
        } else {
            plugin.getLogger().warning("ProtocolLib tidak ditemukan. Fitur FPS Booster tidak akan berfungsi maksimal.");
        }
    }
    
    /**
     * Load FPS Booster configuration
     */
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "fps_booster.yml");
        
        if (!configFile.exists()) {
            // Create default config
            plugin.saveResource("fps_booster.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load basic settings
        filterParticlesDefault = config.getBoolean("defaults.filter-particles", true);
        filterItemFramesDefault = config.getBoolean("defaults.filter-item-frames", false);
        filterPaintingsDefault = config.getBoolean("defaults.filter-paintings", false);
        filterAnimationsDefault = config.getBoolean("defaults.filter-animations", false);
        particleReductionRate = config.getInt("particle-reduction-rate", 70);
        
        // Load filtered particle types
        List<String> configParticleTypes = config.getStringList("filtered-particle-types");
        if (!configParticleTypes.isEmpty()) {
            filteredParticleTypes = new HashSet<>(configParticleTypes);
        }
        
        // Load player settings
        if (config.isConfigurationSection("players")) {
            for (String uuid : config.getConfigurationSection("players").getKeys(false)) {
                UUID playerUUID = UUID.fromString(uuid);
                String path = "players." + uuid + ".";
                
                PlayerBoostSettings settings = new PlayerBoostSettings();
                settings.setBoostEnabled(config.getBoolean(path + "enabled", false));
                settings.setFilterParticles(config.getBoolean(path + "filter-particles", filterParticlesDefault));
                settings.setFilterItemFrames(config.getBoolean(path + "filter-item-frames", filterItemFramesDefault));
                settings.setFilterPaintings(config.getBoolean(path + "filter-paintings", filterPaintingsDefault));
                settings.setFilterAnimations(config.getBoolean(path + "filter-animations", filterAnimationsDefault));
                
                playerSettings.put(playerUUID, settings);
            }
        }
        
        // Re-register packet listeners with new settings
        if (protocolManager != null && registeredPacketListeners) {
            unregisterPacketListeners();
            registerPacketListeners();
        }
    }
    
    /**
     * Save FPS Booster configuration
     */
    public void saveConfig() {
        File configFile = new File(plugin.getDataFolder(), "fps_booster.yml");
        
        // Save player settings
        for (Map.Entry<UUID, PlayerBoostSettings> entry : playerSettings.entrySet()) {
            String path = "players." + entry.getKey().toString() + ".";
            PlayerBoostSettings settings = entry.getValue();
            
            config.set(path + "enabled", settings.isBoostEnabled());
            config.set(path + "filter-particles", settings.isFilterParticles());
            config.set(path + "filter-item-frames", settings.isFilterItemFrames());
            config.set(path + "filter-paintings", settings.isFilterPaintings());
            config.set(path + "filter-animations", settings.isFilterAnimations());
        }
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Tidak dapat menyimpan konfigurasi FPS Booster: " + e.getMessage());
        }
    }
    
    /**
     * Register ProtocolLib packet listeners
     */
    public void registerPacketListeners() {
        if (protocolManager == null) return;
        
        // Register particle packet listener
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, 
                PacketType.Play.Server.WORLD_PARTICLES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                PlayerBoostSettings settings = getPlayerSettings(player.getUniqueId());
                
                // Check if player has particles filter enabled
                if (settings.isBoostEnabled() && settings.isFilterParticles()) {
                    try {
                        // Get particle type from packet - safely
                        PacketContainer packet = event.getPacket();
                        Object particleObj = packet.getParticles().read(0);
                        
                        // Skip if particle object is null
                        if (particleObj == null) {
                            // Use random chance for non-standard particles
                            if (Math.random() * 100 < particleReductionRate) {
                                event.setCancelled(true);
                            }
                            return;
                        }
                        
                        // Get particle type name
                        String particleType = particleObj.toString();
                        if (particleObj instanceof Enum<?>) {
                            particleType = ((Enum<?>)particleObj).name();
                        }
                        
                        // If this particle type should be filtered
                        if (filteredParticleTypes.contains(particleType)) {
                            // Use random sampling to reduce particles by the configured percentage
                            if (Math.random() * 100 < particleReductionRate) {
                                event.setCancelled(true);
                            }
                        } else {
                            // Filter other particles at a lower rate
                            if (Math.random() * 100 < (particleReductionRate / 2)) {
                                event.setCancelled(true);
                            }
                        }
                    } catch (Exception e) {
                        if (debug) {
                            plugin.getLogger().warning("Error processing particle packet: " + e.getMessage());
                        }
                    }
                }
            }
        });
        
        // Register entity spawn packet listener (for item frames, paintings)
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SPAWN_ENTITY) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                PlayerBoostSettings settings = getPlayerSettings(player.getUniqueId());
                
                // Only process if player has entity filtering enabled
                if (!settings.isBoostEnabled()) return;
                
                PacketContainer packet = event.getPacket();
                Entity entity = packet.getEntityModifier(event).read(0);
                
                if (entity == null) return;
                
                // Filter item frames and glow item frames
                if ((entity.getType() == EntityType.ITEM_FRAME || 
                     entity.getType() == EntityType.GLOW_ITEM_FRAME) && 
                    settings.isFilterItemFrames()) {
                    event.setCancelled(true);
                }
                
                // Filter paintings
                if (entity.getType() == EntityType.PAINTING && settings.isFilterPaintings()) {
                    event.setCancelled(true);
                }
            }
        });
        
        // Register animation packet listener (for entity animations)
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.ANIMATION) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                PlayerBoostSettings settings = getPlayerSettings(player.getUniqueId());
                
                // Check if player has animations filter enabled
                if (settings.isBoostEnabled() && settings.isFilterAnimations()) {
                    // Randomly filter animations based on reduction rate
                    if (Math.random() * 100 < particleReductionRate) {
                        event.setCancelled(true);
                    }
                }
            }
        });
        
        registeredPacketListeners = true;
    }
    
    /**
     * Unregister all packet listeners
     */
    public void unregisterPacketListeners() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(plugin);
            registeredPacketListeners = false;
        }
    }
    
    /**
     * Get settings for a player, creating default settings if needed
     */
    public PlayerBoostSettings getPlayerSettings(UUID playerId) {
        return playerSettings.computeIfAbsent(playerId, id -> {
            PlayerBoostSettings settings = new PlayerBoostSettings();
            settings.setBoostEnabled(false);
            settings.setFilterParticles(filterParticlesDefault);
            settings.setFilterItemFrames(filterItemFramesDefault);
            settings.setFilterPaintings(filterPaintingsDefault);
            settings.setFilterAnimations(filterAnimationsDefault);
            return settings;
        });
    }
    
    /**
     * Toggle FPS boost for a player
     * 
     * @param playerId Player UUID
     * @param enabled true to enable, false to disable
     */
    public void setBoostEnabled(UUID playerId, boolean enabled) {
        PlayerBoostSettings settings = getPlayerSettings(playerId);
        settings.setBoostEnabled(enabled);
        playerSettings.put(playerId, settings);
    }
    
    /**
     * Toggle particle filtering for a player
     * 
     * @param playerId Player UUID
     * @param enabled true to enable, false to disable
     */
    public void setParticleFilterEnabled(UUID playerId, boolean enabled) {
        PlayerBoostSettings settings = getPlayerSettings(playerId);
        settings.setFilterParticles(enabled);
        playerSettings.put(playerId, settings);
    }
    
    /**
     * Toggle item frame filtering for a player
     * 
     * @param playerId Player UUID
     * @param enabled true to enable, false to disable
     */
    public void setItemFrameFilterEnabled(UUID playerId, boolean enabled) {
        PlayerBoostSettings settings = getPlayerSettings(playerId);
        settings.setFilterItemFrames(enabled);
        playerSettings.put(playerId, settings);
    }
    
    /**
     * Toggle painting filtering for a player
     * 
     * @param playerId Player UUID
     * @param enabled true to enable, false to disable
     */
    public void setPaintingFilterEnabled(UUID playerId, boolean enabled) {
        PlayerBoostSettings settings = getPlayerSettings(playerId);
        settings.setFilterPaintings(enabled);
        playerSettings.put(playerId, settings);
    }
    
    /**
     * Toggle animation filtering for a player
     * 
     * @param playerId Player UUID
     * @param enabled true to enable, false to disable
     */
    public void setAnimationFilterEnabled(UUID playerId, boolean enabled) {
        PlayerBoostSettings settings = getPlayerSettings(playerId);
        settings.setFilterAnimations(enabled);
        playerSettings.put(playerId, settings);
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        saveConfig();
        unregisterPacketListeners();
    }
    
    /**
     * Inner class to store player boost settings
     */
    public static class PlayerBoostSettings {
        private boolean boostEnabled = false;
        private boolean filterParticles = true;
        private boolean filterItemFrames = false;
        private boolean filterPaintings = false;
        private boolean filterAnimations = false;
        
        public boolean isBoostEnabled() {
            return boostEnabled;
        }
        
        public void setBoostEnabled(boolean boostEnabled) {
            this.boostEnabled = boostEnabled;
        }
        
        public boolean isFilterParticles() {
            return filterParticles;
        }
        
        public void setFilterParticles(boolean filterParticles) {
            this.filterParticles = filterParticles;
        }
        
        public boolean isFilterItemFrames() {
            return filterItemFrames;
        }
        
        public void setFilterItemFrames(boolean filterItemFrames) {
            this.filterItemFrames = filterItemFrames;
        }
        
        public boolean isFilterPaintings() {
            return filterPaintings;
        }
        
        public void setFilterPaintings(boolean filterPaintings) {
            this.filterPaintings = filterPaintings;
        }
        
        public boolean isFilterAnimations() {
            return filterAnimations;
        }
        
        public void setFilterAnimations(boolean filterAnimations) {
            this.filterAnimations = filterAnimations;
        }
    }
}