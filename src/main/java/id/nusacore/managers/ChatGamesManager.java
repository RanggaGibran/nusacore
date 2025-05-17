package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

public class ChatGamesManager {

    private final NusaCore plugin;
    private FileConfiguration config;
    private BukkitTask gameTask;
    private BukkitTask timeoutTask;
    private Random random;
    private boolean gameActive = false;
    private String currentAnswer;
    private String currentQuestion;
    private String currentGameType;
    private String prefix;
    
    // Struktur data untuk pertanyaan
    private Map<String, List<GameQuestion>> games;
    private List<String> gameTypesByWeight;
    
    public ChatGamesManager(NusaCore plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.games = new HashMap<>();
        this.gameTypesByWeight = new ArrayList<>();
        loadConfig();
        
        // Jika interval > 0, mulai jadwal otomatis
        if (config.getBoolean("settings.enabled", true) && config.getInt("settings.interval", 15) > 0) {
            scheduleNextGame();
        }
    }
    
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "chatgames.yml");
        
        // Simpan file default jika tidak ada
        if (!configFile.exists()) {
            plugin.saveResource("chatgames.yml", false);
        }
        
        // Load konfigurasi
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.prefix = ColorUtils.colorize(config.getString("settings.prefix", "&8[&bChatGames&8] &r"));
        
        // Muat game questions
        this.games.clear();
        this.gameTypesByWeight.clear();
        
        ConfigurationSection gamesSection = config.getConfigurationSection("games");
        if (gamesSection != null) {
            for (String gameType : gamesSection.getKeys(false)) {
                ConfigurationSection gameSection = gamesSection.getConfigurationSection(gameType);
                if (gameSection != null && gameSection.getBoolean("enabled", true)) {
                    List<GameQuestion> questions = new ArrayList<>();
                    ConfigurationSection questionsSection = gameSection.getConfigurationSection("questions");
                    
                    if (questionsSection == null) {
                        // Try loading as list
                        List<Map<?, ?>> questionsList = gameSection.getMapList("questions");
                        for (Map<?, ?> questionMap : questionsList) {
                            String question = String.valueOf(questionMap.get("question"));
                            String answer = String.valueOf(questionMap.get("answer")).toLowerCase();
                            Object difficultyObj = questionMap.get("difficulty");
                            String difficulty = difficultyObj != null ? String.valueOf(difficultyObj) : "easy";
                            
                            questions.add(new GameQuestion(question, answer, difficulty));
                        }
                    } else {
                        for (String key : questionsSection.getKeys(false)) {
                            ConfigurationSection questionSection = questionsSection.getConfigurationSection(key);
                            if (questionSection != null) {
                                String question = questionSection.getString("question", "");
                                String answer = questionSection.getString("answer", "").toLowerCase();
                                String difficulty = questionSection.getString("difficulty", "easy");
                                
                                questions.add(new GameQuestion(question, answer, difficulty));
                            }
                        }
                    }
                    
                    if (!questions.isEmpty()) {
                        // Tambahkan ke map
                        this.games.put(gameType, questions);
                        
                        // Tambahkan ke weighted list berdasarkan nilai weight
                        int weight = gameSection.getInt("weight", 10);
                        for (int i = 0; i < weight; i++) {
                            this.gameTypesByWeight.add(gameType);
                        }
                    }
                }
            }
        }
        
        plugin.getLogger().info("ChatGames: Loaded " + games.size() + " game types and " + 
                gameTypesByWeight.size() + " weighted entries.");
    }
    
    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void scheduleNextGame() {
        // Cancel the current task if exists
        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }
        
        if (!isEnabled() || games.isEmpty()) {
            return;
        }
        
        long interval = config.getInt("settings.interval", 15) * 20L * 60L; // Minutes to ticks
        
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                startRandomGame();
            }
        }.runTaskLater(plugin, interval);
        
        plugin.getLogger().info("ChatGames: Next game scheduled in " + (interval/20) + " seconds.");
    }
    
    public void startRandomGame() {
        if (gameActive || gameTypesByWeight.isEmpty()) {
            return;
        }
        
        // Pilih jenis game secara acak dari gameTypesByWeight
        String gameType = gameTypesByWeight.get(random.nextInt(gameTypesByWeight.size()));
        startGame(gameType);
    }
    
    public void startGame(String gameType) {
        if (gameActive || !games.containsKey(gameType)) {
            return;
        }
        
        List<GameQuestion> questions = games.get(gameType);
        if (questions.isEmpty()) {
            return;
        }
        
        // Pilih pertanyaan acak
        GameQuestion question = questions.get(random.nextInt(questions.size()));
        
        // Ambil pengaturan game
        ConfigurationSection gameSection = config.getConfigurationSection("games." + gameType);
        String gamePrefix = "";
        if (gameSection != null) {
            gamePrefix = ColorUtils.colorize(gameSection.getString("prefix", ""));
        }
        
        // Set status game
        this.gameActive = true;
        this.currentAnswer = question.getAnswer().toLowerCase();
        this.currentQuestion = question.getQuestion();
        this.currentGameType = gameType;
        
        // Broadcast pertanyaan
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtils.colorize(prefix + gamePrefix + question.getQuestion()));
        Bukkit.broadcastMessage(ColorUtils.colorize("&7Ketik jawabannya di chat untuk memenangkan hadiah!"));
        Bukkit.broadcastMessage("");
        
        // Play sound untuk semua player
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
        }
        
        // Set timeout
        int duration = config.getInt("settings.duration", 60);
        timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameActive) {
                    endGame(null);
                }
            }
        }.runTaskLater(plugin, duration * 20L); // Duration in seconds to ticks
    }
    
    public void endGame(Player winner) {
        if (!gameActive) {
            return;
        }
        
        gameActive = false;
        
        // Cancel timeout task
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        
        if (winner == null) {
            // Game berakhir karena timeout
            Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&cWaktu habis! Tidak ada yang menjawab dengan benar."));
            Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&eJawaban yang benar: &f" + currentAnswer));
        } else {
            // Game berakhir karena ada pemenang
            if (config.getBoolean("settings.broadcast-winner", true)) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&a" + winner.getName() + " &fmenjawab dengan benar!"));
                
                // Berikan hadiah
                giveRandomReward(winner);
            }
            
            // Play sound untuk pemenang
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
        
        // Schedule the next game
        scheduleNextGame();
    }
    
    public void giveRandomReward(Player player) {
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection == null) {
            return;
        }
        
        // Build rewards based on chances
        List<String> possibleRewards = new ArrayList<>();
        
        for (String rewardType : rewardsSection.getKeys(false)) {
            ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(rewardType);
            if (rewardSection != null) {
                int chance = rewardSection.getInt("chance", 0);
                for (int i = 0; i < chance; i++) {
                    possibleRewards.add(rewardType);
                }
            }
        }
        
        if (possibleRewards.isEmpty()) {
            return;
        }
        
        // Select random reward
        String selectedRewardType = possibleRewards.get(random.nextInt(possibleRewards.size()));
        ConfigurationSection selectedReward = rewardsSection.getConfigurationSection(selectedRewardType);
        
        if (selectedReward == null) {
            return;
        }
        
        // Process reward based on type
        if (selectedRewardType.equals("money") || selectedRewardType.equals("tokens")) {
            int min = selectedReward.getInt("min", 1);
            int max = selectedReward.getInt("max", 10);
            int amount = min + random.nextInt(max - min + 1);
            
            String command = selectedReward.getString("command", "").replace("{player}", player.getName()).replace("{amount}", String.valueOf(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            // Notify player
            player.sendMessage(ColorUtils.colorize(prefix + "&aAnda mendapatkan &f" + amount + " " + 
                    (selectedRewardType.equals("money") ? "koin" : "token") + "&a!"));
        } else if (selectedRewardType.equals("items")) {
            List<String> commands = selectedReward.getStringList("commands");
            if (!commands.isEmpty()) {
                String command = commands.get(random.nextInt(commands.size())).replace("{player}", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                // Extract item name from command for notification
                String itemName = extractItemName(command);
                player.sendMessage(ColorUtils.colorize(prefix + "&aAnda mendapatkan &f" + itemName + "&a!"));
            }
        }
    }
    
    private String extractItemName(String command) {
        // Simple extraction of item name from command like "give {player} diamond 3"
        if (command.startsWith("give")) {
            String[] parts = command.split(" ");
            if (parts.length >= 3) {
                String itemName = parts[2];
                int amount = 1;
                
                if (parts.length >= 4) {
                    try {
                        amount = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        // Ignore error, use default amount
                    }
                }
                
                return amount + " " + itemName.replace("_", " ");
            }
        } else if (command.contains("key")) {
            return "kunci crate";
        }
        
        return "hadiah misteri";
    }
    
    public void checkAnswer(Player player, String message) {
        if (!gameActive || currentAnswer == null) {
            return;
        }
        
        if (message.toLowerCase().trim().equals(currentAnswer.toLowerCase())) {
            endGame(player);
        }
    }
    
    public void stopGame() {
        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }
        
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        
        gameActive = false;
    }
    
    public void startManualGame(String gameType) {
        if (!games.containsKey(gameType)) {
            plugin.getLogger().warning("ChatGames: Game type '" + gameType + "' not found!");
            return;
        }
        
        if (gameActive) {
            stopGame();
        }
        
        startGame(gameType);
    }
    
    public List<String> getGameTypes() {
        return new ArrayList<>(games.keySet());
    }
    
    // Helper class untuk menyimpan pertanyaan
    public static class GameQuestion {
        private final String question;
        private final String answer;
        private final String difficulty;
        
        public GameQuestion(String question, String answer, String difficulty) {
            this.question = question;
            this.answer = answer;
            this.difficulty = difficulty;
        }
        
        public String getQuestion() {
            return question;
        }
        
        public String getAnswer() {
            return answer;
        }
        
        public String getDifficulty() {
            return difficulty;
        }
    }
}