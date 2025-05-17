package id.nusacore;

import id.nusacore.commands.BackCommand;
import id.nusacore.commands.MainCommand;
import id.nusacore.commands.SpawnCommand;
import id.nusacore.commands.teleport.TPACommand;
import id.nusacore.commands.teleport.TPAHereCommand;
import id.nusacore.commands.teleport.TPAcceptCommand;
import id.nusacore.commands.teleport.TPCancelCommand;
import id.nusacore.commands.teleport.TPDenyCommand;
import id.nusacore.commands.teleport.TPToggleCommand;
import id.nusacore.commands.DonateCommand;
import id.nusacore.commands.RTPCommand;
import id.nusacore.commands.RTPCancelCommand;
import id.nusacore.commands.AFKCommand;
import id.nusacore.commands.HelpCommand;
import id.nusacore.commands.FPSBoosterCommand;
import id.nusacore.commands.economy.BalanceCommand;
import id.nusacore.commands.economy.MoneyCommand;
import id.nusacore.commands.economy.PayCommand;
import id.nusacore.commands.economy.TokenCommand;
import id.nusacore.commands.messaging.MessageCommand;
import id.nusacore.commands.messaging.ReplyCommand;
import id.nusacore.commands.economy.BalanceTopCommand;
import id.nusacore.commands.messaging.SocialSpyCommand;
import id.nusacore.commands.RankUpCommand;
import id.nusacore.commands.voting.VoteCommand;
import id.nusacore.commands.voting.VotePartyCommand;
import id.nusacore.commands.voting.VotePartyAdminCommand;
import id.nusacore.commands.voting.VoteRewardCommand;
import id.nusacore.commands.ChatGamesCommand;
import id.nusacore.listeners.ChatListener;
import id.nusacore.listeners.CombatListener;
import id.nusacore.listeners.PlayerEventListener;
import id.nusacore.listeners.TPAListener;
import id.nusacore.listeners.TPToggleListener;
import id.nusacore.listeners.GUIListener;
import id.nusacore.listeners.AFKListener;
import id.nusacore.listeners.CommandInterceptListener;
import id.nusacore.listeners.AFKRegionListener;
import id.nusacore.listeners.EconomyListener;
import id.nusacore.listeners.RankGUIListener;
import id.nusacore.listeners.ChatGamesListener;
import id.nusacore.managers.CombatTagManager;
import id.nusacore.managers.TPAManager;
import id.nusacore.managers.RankManager;
import id.nusacore.managers.AFKManager;
import id.nusacore.managers.HelpManager;
import id.nusacore.managers.AFKRegionManager;
import id.nusacore.managers.FPSBoosterManager;
import id.nusacore.managers.EconomyManager;
import id.nusacore.managers.TokenManager;
import id.nusacore.managers.MessageManager;
import id.nusacore.managers.RankupManager;
import id.nusacore.managers.VoteManager;
import id.nusacore.managers.ChatGamesManager;
import id.nusacore.utils.ColorUtils;
import id.nusacore.hooks.TownyHook;
import id.nusacore.hooks.RankUpPlaceholder;
import id.nusacore.placeholders.VotePlaceholders;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class NusaCore extends JavaPlugin {
  private static final Logger LOGGER = Logger.getLogger("NusaTown");
  private static NusaCore instance;
  private Economy economy = null;
  private BackCommand backCommand;
  private boolean economyEnabled;
  private TPAManager tpaManager;
  private CombatTagManager combatTagManager;
  private RankManager rankManager;
  private boolean placeholderAPIEnabled = false;
  private GUIListener guiListener;
  private RTPCommand rtpCommand;
  private AFKManager afkManager;
  private HelpManager helpManager;
  private boolean customHelpEnabled;
  private AFKRegionManager afkRegionManager;
  private FPSBoosterManager fpsBoosterManager;
  private FPSBoosterCommand fpsBoosterCommand;
  private EconomyManager economyManager;
  private TokenManager tokenManager;
  private MessageManager messageManager;
  private RankupManager RankupManager;
  private VoteManager voteManager;
  private ChatGamesManager chatGamesManager;
  private FileConfiguration ranksConfig;
  private Map<String, AtomicInteger> worldActiveRTPs = new HashMap<>();
  private ChatListener chatListener;
  private RankGUIListener rankGUIListener;
  
  public static String PREFIX;
  
  @Override
  public void onEnable() {
    instance = this;
    
    // Save default config if it doesn't exist
    saveDefaultConfig();
    
    // Load prefix from config
    PREFIX = ColorUtils.colorize(getConfig().getString("messages.prefix", "<gradient:#00A3FF:#00FFD1>NusaTown</gradient>&8 » &f"));
    
    // Check for PlaceholderAPI
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      placeholderAPIEnabled = true;
      LOGGER.info("PlaceholderAPI detected, placeholder support enabled!");
    } else {
      LOGGER.warning("PlaceholderAPI not found, placeholder support disabled.");
    }
    
    // Check if economy is enabled in config
    economyEnabled = getConfig().getBoolean("economy.enabled", true);
    
    // Initialize economy manager SEBELUM setupEconomy
    if (economyEnabled) {
        // Buat EconomyManager terlebih dahulu
        economyManager = new EconomyManager(this);
        
        // Kemudian setup Vault Economy
        if (!setupEconomy()) {
            LOGGER.warning("Vault not found or no economy plugin detected");
        } else {
            LOGGER.info("Economy features enabled and Vault hooked successfully");
        }
        
        // Register economy commands
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("money").setExecutor(new MoneyCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        
        // Register economy listeners
        getServer().getPluginManager().registerEvents(new EconomyListener(this), this);
    } else {
        LOGGER.info("Economy features disabled in config");
    }
    
    // Cek status ekonomi setelah setup
    getServer().getScheduler().runTaskLater(this, this::checkEconomyStatus, 40L); // Check setelah 2 detik
    
    // Initialize managers
    backCommand = new BackCommand(this);
    tpaManager = new TPAManager(this);
    tpaManager.loadPreferences(); // Muat preferensi saat plugin diaktifkan
    combatTagManager = new CombatTagManager(this);
    rankManager = new RankManager(this);
    afkManager = new AFKManager(this);
    afkRegionManager = new AFKRegionManager(this);
    helpManager = new HelpManager(this);
    fpsBoosterManager = new FPSBoosterManager(this);
    fpsBoosterCommand = new FPSBoosterCommand(this);
    customHelpEnabled = getConfig().getBoolean("help.enabled", true);
    
    // Initialize token manager
    tokenManager = new TokenManager(this);
    getCommand("tokens").setExecutor(new TokenCommand(this));
    
    // Initialize message manager
    messageManager = new MessageManager(this);
    
    // Initialize RankupManager
    RankupManager = new RankupManager(this);
    
    // Initialize VoteManager
    voteManager = new VoteManager(this);
    
    // Initialize ChatGames manager
    chatGamesManager = new ChatGamesManager(this);
    getServer().getPluginManager().registerEvents(new ChatGamesListener(this), this);
    getCommand("chatgames").setExecutor(new ChatGamesCommand(this));
    getCommand("chatgames").setTabCompleter((ChatGamesCommand) getCommand("chatgames").getExecutor());
    
    // Register message commands
    getCommand("msg").setExecutor(new MessageCommand(this));
    getCommand("r").setExecutor(new ReplyCommand(this));
    getCommand("baltop").setExecutor(new BalanceTopCommand(this));
    
    // Register PlaceholderAPI expansion jika tersedia
    if (placeholderAPIEnabled && economyEnabled) {
        new id.nusacore.placeholders.EconomyPlaceholders(this).register();
        LOGGER.info("Economy placeholders registered with PlaceholderAPI!");
    }
    
    if (placeholderAPIEnabled) {
        new RankUpPlaceholder(this).register();
        new VotePlaceholders(this).register();
        getLogger().info("Vote placeholders registered with PlaceholderAPI!");
    }
    
    // Register commands
    getCommand("nusatown").setExecutor(new MainCommand(this));
    getCommand("spawn").setExecutor(new SpawnCommand(this, backCommand));
    getCommand("back").setExecutor(backCommand);
    
    // Register teleport commands
    getCommand("tpa").setExecutor(new TPACommand(this));
    getCommand("tpahere").setExecutor(new TPAHereCommand(this));
    getCommand("tpaccept").setExecutor(new TPAcceptCommand(this));
    getCommand("tpdeny").setExecutor(new TPDenyCommand(this));
    getCommand("tpcancel").setExecutor(new TPCancelCommand(this));
    
    // Register TPToggle command
    getCommand("tptoggle").setExecutor(new TPToggleCommand(this));
    
    // Register additional commands
    getCommand("donate").setExecutor(new DonateCommand(this));
    getCommand("buy").setExecutor(getCommand("donate").getExecutor());
    
    // Register RTP command
    rtpCommand = new RTPCommand(this);
    getCommand("rtp").setExecutor(rtpCommand);
    getCommand("rtp").setTabCompleter(rtpCommand);
    
    // Register RTPCancel command
    getCommand("rtpcancel").setExecutor(new RTPCancelCommand(this));
    
    // Register AFK command
    getCommand("afk").setExecutor(new AFKCommand(this));
    
    // Register help command
    getCommand("help").setExecutor(new HelpCommand(this));
    
    // Register FPSBooster command
    getCommand("fpsbooster").setExecutor(fpsBoosterCommand);
    
    // Register SocialSpy command
    getCommand("socialspy").setExecutor(new SocialSpyCommand(this));
    
    // Register RankUp command
    getCommand("rankup").setExecutor(new RankUpCommand(this));
    
    // Register vote commands
    getCommand("vote").setExecutor(new VoteCommand(this));
    getCommand("voteparty").setExecutor(new VotePartyCommand(this));
    getCommand("votepartyadmin").setExecutor(new VotePartyAdminCommand(this));
    getCommand("votereward").setExecutor(new VoteRewardCommand(this));
    
    // Register tab completers
    getCommand("tpa").setTabCompleter((TPACommand) getCommand("tpa").getExecutor());
    getCommand("tpahere").setTabCompleter((TPAHereCommand) getCommand("tpahere").getExecutor());
    getCommand("donate").setTabCompleter((DonateCommand) getCommand("donate").getExecutor());
    getCommand("buy").setTabCompleter(getCommand("donate").getTabCompleter());
    getCommand("votepartyadmin").setTabCompleter((VotePartyAdminCommand) getCommand("votepartyadmin").getExecutor());
    getCommand("votereward").setTabCompleter((VoteRewardCommand) getCommand("votereward").getExecutor());
    
    // Register event listeners
    getServer().getPluginManager().registerEvents(new PlayerEventListener(this, backCommand), this);
    getServer().getPluginManager().registerEvents(new TPAListener(this), this);
    getServer().getPluginManager().registerEvents(new CombatListener(this), this);
    
    // Store reference to ChatListener when creating it
    chatListener = new ChatListener(this);
    getServer().getPluginManager().registerEvents(chatListener, this);
    
    guiListener = new GUIListener(this);
    getServer().getPluginManager().registerEvents(guiListener, this);
    
    // Register listener
    getServer().getPluginManager().registerEvents(new TPToggleListener(this), this);
    
    // Register AFK listener
    getServer().getPluginManager().registerEvents(new AFKListener(this), this);
    
    // Register interceptor
    getServer().getPluginManager().registerEvents(new CommandInterceptListener(this), this);
    
    // Register AFKRegion listener
    getServer().getPluginManager().registerEvents(new AFKRegionListener(this), this);
    
    // Register economy provider re-registration listener
    getServer().getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            if (event.getPlugin().getName().equals("Towny")) {
                // Re-register economy provider when Towny loads
                getServer().getScheduler().runTaskLater(NusaCore.this, () -> {
                    setupEconomy();
                    LOGGER.info("Re-registered economy provider after Towny loaded");
                }, 20L);
            }
        }
    }, this);
    
    // Register RankUp listeners
    Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            RankupManager.loadPlayerRankOnJoin(event.getPlayer());
        }
        
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            RankupManager.unloadPlayerRank(event.getPlayer());
        }
    }, this);
    
    // Register RankGUIListener
    rankGUIListener = new RankGUIListener(this);
    getServer().getPluginManager().registerEvents(rankGUIListener, this);
    
    // Di onEnable() setelah inisialisasi ekonomi
    if (getServer().getPluginManager().getPlugin("Towny") != null) {
        new TownyHook(this).initialize();
    }
    
    // Reset RTP counters pada startup
    for (World world : getServer().getWorlds()) {
        worldActiveRTPs.put(world.getName(), new AtomicInteger(0));
    }
    
    LOGGER.info(ColorUtils.stripColor(PREFIX) + "Plugin enabled successfully!");
  }

  @Override
  public void onDisable() {
    // Clean up combat tags
    if (combatTagManager != null) {
        combatTagManager.clearAllTags();
    }
    
    // Simpan preferensi teleport
    if (tpaManager != null) {
        tpaManager.savePreferences();
    }
    
    // Simpan RTP cooldowns
    if (rtpCommand != null) {
        rtpCommand.saveAllCooldowns();
    }
    
    // Cleanup AFKManager
    if (afkManager != null) {
        afkManager.cleanup();
    }
    
    // Cleanup AFKRegionManager
    if (afkRegionManager != null) {
        afkRegionManager.cleanup();
    }
    
    // Cleanup FPSBoosterManager
    if (fpsBoosterManager != null) {
        fpsBoosterManager.cleanup();
    }
    
    // Cleanup EconomyManager
    if (economyManager != null) {
        economyManager.cleanup();
    }
    
    // Cleanup TokenManager
    if (tokenManager != null) {
        tokenManager.cleanup();
    }
    
    // Save vote data
    if (voteManager != null) {
        voteManager.saveData();
    }
    
    // Stop chat games
    if (chatGamesManager != null) {
        chatGamesManager.stopGame();
    }
    
    LOGGER.info(ColorUtils.stripColor(PREFIX) + "Plugin disabled!");
  }
  
  /**
   * Muat ulang prefix dari config
   */
  public void reloadPrefix() {
    PREFIX = ColorUtils.colorize(getConfig().getString("messages.prefix", "<gradient:#00A3FF:#00FFD1>NusaTown</gradient>&8 » &f"));
  }
  
  @Override
  public void reloadConfig() {
    super.reloadConfig();
    reloadPrefix();
    
    // Reload rank configuration
    if (rankManager != null) {
        rankManager.loadConfig();
    }
    
    // Reinisialize GUIListener mappings
    if (guiListener != null) {
        guiListener.initializeRankMapping();
    }
    
    // Reinitialize managers with new settings
    if (combatTagManager != null) {
        combatTagManager = new CombatTagManager(this);
    }
    
    if (tpaManager != null) {
        tpaManager = new TPAManager(this);
    }
    
    if (rtpCommand != null) {
        rtpCommand.loadConfig();
    }
    
    // Other managers...
  }
  
  /**
   * Reload semua konfigurasi plugin
   */
  public void reloadAllConfigs() {
    getLogger().info("Reloading all configurations...");
    
    // Reload main config
    super.reloadConfig();
    reloadPrefix();
    
    // Reload manager configurations
    if (rankManager != null) rankManager.loadConfig();
    if (afkRegionManager != null) afkRegionManager.loadConfig();
    if (fpsBoosterManager != null) fpsBoosterManager.loadConfig();
    if (helpManager != null) helpManager.reloadConfig();
    if (combatTagManager != null) combatTagManager.reloadConfig();
    if (economyManager != null) economyManager.reloadConfig();
    if (tokenManager != null) tokenManager.reloadConfig();
    if (tpaManager != null) tpaManager.loadPreferences();
    if (rtpCommand != null) rtpCommand.loadConfig();
    if (voteManager != null) voteManager.loadConfig();
    
    // Reload chat games
    if (chatGamesManager != null) {
        chatGamesManager.loadConfig();
    }
    
    // Use the stored ChatListener reference
    if (chatListener != null) {
        chatListener.reloadConfig();
    }
    
    // Fix variable name - it should match your field name 
    if (RankupManager != null) RankupManager.loadConfig();
    
    // Check if SpawnCommand is registered and reload it
    if (getCommand("spawn") != null && getCommand("spawn").getExecutor() instanceof SpawnCommand) {
        ((SpawnCommand) getCommand("spawn").getExecutor()).reloadCache();
    }
    
    // Reinitialize GUI if needed
    if (guiListener != null) guiListener.initializeRankMapping();
    
    getLogger().info("All configurations have been reloaded successfully!");
  }
  
  public static NusaCore getInstance() {
    return instance;
  }
  
  private boolean setupEconomy() {
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
        return false;
    }
    
    // Clear existing registrations if any - ini membantu jika ada konflik
    try {
        getServer().getServicesManager().unregisterAll(this);
    } catch (Exception ignored) {
        // Ignore any errors during unregistration
    }
    
    // Daftarkan provider kita dengan prioritas NORMAL (lebih dapat dikenali oleh plugin lain)
    try {
        getServer().getServicesManager().register(Economy.class, 
                new id.nusacore.economy.VaultEconomyProvider(this), 
                this, 
                org.bukkit.plugin.ServicePriority.Normal);
        
        // Get registered provider for verification
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        
        // Extra logging untuk debug
        LOGGER.info("Registered NusaCore economy provider with Vault successfully");
        LOGGER.info("Economy provider class: " + economy.getClass().getName());
        return true;
    } catch (Exception e) {
        LOGGER.severe("Failed to register economy provider with Vault: " + e.getMessage());
        return false;
    }
  }
  
  public Economy getEconomy() {
    return economy;
  }
  
  /**
   * Memeriksa apakah fitur ekonomi aktif
   * @return true jika fitur ekonomi aktif
   */
  public boolean isEconomyEnabled() {
    return economyEnabled;
  }
  
  /**
   * Memeriksa apakah PlaceholderAPI tersedia
   * @return true jika PlaceholderAPI tersedia
   */
  public boolean hasPlaceholderAPI() {
    return placeholderAPIEnabled;
  }
  
  public BackCommand getBackCommand() {
    return backCommand;
  }
  
  public TPAManager getTPAManager() {
    return tpaManager;
  }
  
  public CombatTagManager getCombatTagManager() {
    return combatTagManager;
  }
  
  public RankManager getRankManager() {
    return rankManager;
  }
  
  public AFKManager getAFKManager() {
    return afkManager;
  }
  
  public HelpManager getHelpManager() {
    return helpManager;
  }
  
  public boolean isCustomHelpEnabled() {
    return customHelpEnabled;
  }
  
  public AFKRegionManager getAFKRegionManager() {
    return afkRegionManager;
  }
  
  public FPSBoosterManager getFPSBoosterManager() {
    return fpsBoosterManager;
  }
  
  public FPSBoosterCommand getFPSBoosterCommand() {
    return fpsBoosterCommand;
  }
  
  public EconomyManager getEconomyManager() {
    return economyManager;
  }
  
  /**
   * Get the token manager
   * @return Token manager
   */
  public TokenManager getTokenManager() {
    return tokenManager;
  }
  
  /**
   * Get the message manager
   * @return Message manager
   */
  public MessageManager getMessageManager() {
    return messageManager;
  }
  
  /**
   * Get the RankUp manager
   * @return RankUp manager
   */
  public RankupManager getRankupManager() {
    return RankupManager;
  }
  
  /**
   * Get the Vote manager
   * @return Vote manager
   */
  public VoteManager getVoteManager() {
    return voteManager;
  }
  
  /**
   * Get the ranks configuration
   * @return Ranks configuration
   */
  public FileConfiguration getRanksConfig() {
    if (RankupManager != null) {
        return RankupManager.getRanksConfig();
    }
    return null;
  }
  
  /**
   * Periksa status provider ekonomi dan tampilkan informasi debug
   */
  public void checkEconomyStatus() {
    LOGGER.info("Checking economy provider status...");
    
    // Periksa Vault
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
        LOGGER.warning("Vault plugin not found!");
        return;
    }
    
    // Periksa provider ekonomi
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp == null) {
        LOGGER.warning("No economy service provider registered!");
        return;
    }
    
    // Tampilkan informasi provider
    Economy eco = rsp.getProvider();
    LOGGER.info("Economy provider name: " + eco.getName());
    LOGGER.info("Economy provider class: " + eco.getClass().getName());
    LOGGER.info("Economy provider is enabled: " + eco.isEnabled());
    
    // Test beberapa operasi dasar
    if (Bukkit.getOnlinePlayers().size() > 0) {
        Player testPlayer = Bukkit.getOnlinePlayers().iterator().next();
        LOGGER.info("Test player balance: " + eco.getBalance(testPlayer));
    }
    
    LOGGER.info("Currency name: " + eco.currencyNameSingular() + "/" + eco.currencyNamePlural());
    LOGGER.info("Economy check completed.");
  }
  
  /**
   * Mendapatkan instance GUIListener
   * @return instance GUIListener
   */
  public GUIListener getGuiListener() {
    return guiListener;
  }
  
  /**
   * Get the ChatGames manager
   * @return ChatGames manager
   */
  public ChatGamesManager getChatGamesManager() {
    return chatGamesManager;
  }
}
