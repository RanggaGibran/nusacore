package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class VoteManager {
    
    private final NusaCore plugin;
    private int votePartyCount;
    private int votePartyTarget;
    private String voteUrl;
    private List<String> voteRewards;
    private List<String> votePartyRewards;
    private List<String> voteBonusRewards;
    private int voteBonusChance;
    private Map<UUID, Long> lastVoteTime;
    private File dataFile;
    private FileConfiguration dataConfig;
    
    public VoteManager(NusaCore plugin) {
        this.plugin = plugin;
        this.lastVoteTime = new HashMap<>();
        loadConfig();
    }
    
    public void loadConfig() {
        // Load settings from config
        FileConfiguration config = plugin.getConfig();
        votePartyTarget = config.getInt("voting.voteparty.target", 10);
        voteUrl = config.getString("voting.url", "vote.nusatown.com");
        voteBonusChance = config.getInt("voting.bonus-chance", 30);
        
        // Load vote rewards commands
        voteRewards = config.getStringList("voting.rewards.regular");
        if (voteRewards.isEmpty()) {
            voteRewards = new ArrayList<>();
            voteRewards.add("give %player% diamond 5");
            voteRewards.add("give %player% emerald 10");
            voteRewards.add("eco give %player% 3500");
            voteRewards.add("crate key give %player% vote_key 3");
        }
        
        // Load VoteParty rewards commands
        votePartyRewards = config.getStringList("voting.voteparty.rewards");
        if (votePartyRewards.isEmpty()) {
            votePartyRewards = new ArrayList<>();
            votePartyRewards.add("give %player% diamond 3");
            votePartyRewards.add("give %player% emerald 5");
            votePartyRewards.add("crate key give %player% vote_key 1");
        }
        
        // Load vote bonus rewards commands
        voteBonusRewards = config.getStringList("voting.rewards.bonus");
        if (voteBonusRewards.isEmpty()) {
            voteBonusRewards = new ArrayList<>();
            voteBonusRewards.add("give %player% netherite_ingot 1");
        }
        
        // Load saved data
        loadData();
    }
    
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "votedata.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create votedata.yml: " + e.getMessage());
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        votePartyCount = dataConfig.getInt("voteparty-count", 0);
        
        // Load last vote times
        if (dataConfig.isConfigurationSection("last-vote-times")) {
            for (String uuidStr : dataConfig.getConfigurationSection("last-vote-times").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long time = dataConfig.getLong("last-vote-times." + uuidStr);
                    lastVoteTime.put(uuid, time);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in votedata.yml: " + uuidStr);
                }
            }
        }
    }
    
    public void saveData() {
        dataConfig.set("voteparty-count", votePartyCount);
        
        // Save last vote times
        for (Map.Entry<UUID, Long> entry : lastVoteTime.entrySet()) {
            dataConfig.set("last-vote-times." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save votedata.yml: " + e.getMessage());
        }
    }
    
    public void processVote(Player player) {
        // Record vote time
        lastVoteTime.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Execute vote rewards
        for (String command : voteRewards) {
            Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(), 
                command.replace("%player%", player.getName())
            );
        }
        
        // Send messages to player
        player.sendMessage(ColorUtils.colorize("&a&lᴛᴇʀɪᴍᴀᴋᴀsɪʜ ᴛᴇʟᴀʜ ᴠᴏᴛᴇ! &r&fᴀɴᴅᴀ ᴍᴇɴᴅᴀᴘᴀᴛᴋᴀɴ:"));
        player.sendMessage(ColorUtils.colorize("&f- &b5 ᴅɪᴀᴍᴏɴᴅ"));
        player.sendMessage(ColorUtils.colorize("&f- &a10 ᴇᴍᴇʀᴀʟᴅ"));
        player.sendMessage(ColorUtils.colorize("&f- &eʀᴘ3.500"));
        player.sendMessage(ColorUtils.colorize("&f- &d3 ᴋᴜɴᴄɪ ᴄʀᴀᴛᴇ ᴠᴏᴛᴇ"));
        player.sendMessage(ColorUtils.colorize("&7ᴠᴏᴛᴇ ʟᴀɢɪ ᴅᴀʟᴀᴍ 24 ᴊᴀᴍ ᴜɴᴛᴜᴋ ᴍᴇɴᴅᴀᴘᴀᴛᴋᴀɴ ʜᴀᴅɪᴀʜ ʟᴀɢɪ!"));
        
        // Play effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        launchParticles(player);
        
        // Add to vote party count
        incrementVotePartyCount();
        
        // Announce the vote
        Bukkit.broadcastMessage(ColorUtils.colorize("&b&lᴠᴏᴛᴇ &8» &e" + player.getName() + " &aʙᴀʀᴜ sᴀᴊᴀ ᴠᴏᴛᴇ ᴅᴀɴ ᴍᴇɴᴅᴀᴘᴀᴛᴋᴀɴ ʜᴀᴅɪᴀʜ!"));
        
        // Check for bonus reward
        Random random = new Random();
        if (random.nextInt(100) < voteBonusChance) {
            // Give bonus rewards
            for (String command : voteBonusRewards) {
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), 
                    command.replace("%player%", player.getName())
                );
            }
            
            // Inform player about bonus
            player.sendMessage(ColorUtils.colorize("&d&lʙᴏɴᴜs! &r&fᴀɴᴅᴀ ᴍᴇɴᴅᴀᴘᴀᴛᴋᴀɴ &7ɴᴇᴛʜᴇʀɪᴛᴇ ɪɴɢᴏᴛ&f!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            Bukkit.broadcastMessage(ColorUtils.colorize("&b&lᴠᴏᴛᴇ &8» &e" + player.getName() + " &aᴍᴇɴᴅᴀᴘᴀᴛᴋᴀɴ &d&lʜᴀᴅɪᴀʜ ʙᴏɴᴜs &aᴅᴀʀɪ ᴠᴏᴛɪɴɢ!"));
        }
    }
    
    public void incrementVotePartyCount() {
        votePartyCount++;
        Bukkit.broadcastMessage(ColorUtils.colorize("&b&lᴠᴏᴛᴇᴘᴀʀᴛʏ &8» &fᴘʀᴏɢʀᴇss: &a" + votePartyCount + "&f/&a" + votePartyTarget));
        
        // Check if VoteParty should be triggered
        if (votePartyCount >= votePartyTarget) {
            triggerVoteParty();
        }
        
        // Save data
        saveData();
    }
    
    public void triggerVoteParty() {
        // Reset counter
        votePartyCount = 0;
        
        // Announce vote party
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtils.colorize("&b&l✦ &8&l[ &b&lᴠᴏᴛᴇᴘᴀʀᴛʏ &8&l] &b&l✦"));
        Bukkit.broadcastMessage(ColorUtils.colorize("&aVoteParty telah tercapai! Semua pemain online mendapatkan hadiah!"));
        Bukkit.broadcastMessage("");
        
        // Schedule multiple fireworks and effects
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 5) {
                    this.cancel();
                    return;
                }
                
                // Launch fireworks at random online players
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                    Player randomPlayer = players.get(new Random().nextInt(players.size()));
                    launchFirework(randomPlayer);
                }
                
                count++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        
        // Give rewards to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Execute vote party rewards
            for (String command : votePartyRewards) {
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), 
                    command.replace("%player%", player.getName())
                );
            }
            
            // Send messages
            player.sendMessage(ColorUtils.colorize("&a&lᴠᴏᴛᴇᴘᴀʀᴛʏ &r&fᴀɴᴅᴀ ᴍᴇɴᴅᴀᴘᴀᴛᴋᴀɴ:"));
            player.sendMessage(ColorUtils.colorize("&f- &b3 ᴅɪᴀᴍᴏɴᴅ"));
            player.sendMessage(ColorUtils.colorize("&f- &a5 ᴇᴍᴇʀᴀʟᴅ"));
            player.sendMessage(ColorUtils.colorize("&f- &d1 ᴋᴜɴᴄɪ ᴄʀᴀᴛᴇ ᴠᴏᴛᴇ"));
            
            // Play effects
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            launchParticles(player);
        }
        
        // Save data
        saveData();
    }
    
    private void launchParticles(Player player) {
        // Use Bukkit's particle API instead of command to spawn particles
        Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(), 
            "execute at " + player.getName() + " run particle minecraft:totem_of_undying ~ ~1 ~ 0.5 0.5 0.5 0.1 50"
        );
    }
    
    private void launchFirework(Player player) {
        Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta fwMeta = fw.getFireworkMeta();
        
        // Create random effect
        Random random = new Random();
        FireworkEffect.Type type = FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)];
        
        // Random colors
        Color color1 = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        Color color2 = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(color1, color2)
                .with(type)
                .withFlicker()
                .withTrail()
                .build();
        
        fwMeta.addEffect(effect);
        fwMeta.setPower(1); // Set power (flight duration)
        fw.setFireworkMeta(fwMeta);
    }
    
    public boolean canVote(Player player) {
        if (!lastVoteTime.containsKey(player.getUniqueId())) {
            return true;
        }
        
        long lastVote = lastVoteTime.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();
        long hoursPassed = (currentTime - lastVote) / (1000 * 60 * 60);
        
        // Can vote again after 24 hours
        return hoursPassed >= 24;
    }
    
    public long getNextVoteTime(Player player) {
        if (!lastVoteTime.containsKey(player.getUniqueId())) {
            return 0;
        }
        
        long lastVote = lastVoteTime.get(player.getUniqueId());
        long nextVote = lastVote + (24 * 60 * 60 * 1000); // 24 hours in milliseconds
        return Math.max(0, nextVote - System.currentTimeMillis());
    }
    
    public int getVotePartyCount() {
        return votePartyCount;
    }
    
    public void setVotePartyCount(int count) {
        this.votePartyCount = count;
        saveData();
    }
    
    public int getVotePartyTarget() {
        return votePartyTarget;
    }
    
    public void setVotePartyTarget(int target) {
        this.votePartyTarget = target;
        plugin.getConfig().set("voting.voteparty.target", target);
        plugin.saveConfig();
    }
    
    public String getVoteUrl() {
        return voteUrl;
    }
    
    /**
     * Get the chance of receiving a bonus reward when voting
     * @return The chance as a percentage (0-100)
     */
    public int getVoteBonusChance() {
        return voteBonusChance;
    }
}