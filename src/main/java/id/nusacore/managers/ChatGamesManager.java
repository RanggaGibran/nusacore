package id.nusacore.managers;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import id.nusacore.discord.ChatGamesDiscordIntegration;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

public class ChatGamesManager {

    private final NusaCore plugin;
    private FileConfiguration config;
    private BukkitTask gameTask;
    private BukkitTask timeoutTask;
    private BukkitTask countdownTask;
    private Random random;
    private boolean gameActive = false;
    private String currentAnswer;
    private String currentQuestion;
    private String currentGameType;
    private String prefix;
    private BossBar timerBossBar;
    private ChatGamesDiscordIntegration discordIntegration;

    // Struktur data untuk pertanyaan
    private Map<String, List<GameQuestion>> games;
    private List<String> gameTypesByWeight;

    // Tambahkan properti untuk event dan turnamen
    private boolean eventActive = false;
    private String currentEvent = null;
    private Map<String, Map<String, Object>> events = new HashMap<>();
    private boolean tournamentActive = false;
    private Map<UUID, Integer> tournamentScores = new HashMap<>();
    private LocalDateTime tournamentEndTime;

    public ChatGamesManager(NusaCore plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.games = new HashMap<>();
        this.gameTypesByWeight = new ArrayList<>();
        loadConfig();
        this.loadEvents();

        // Jika interval > 0, mulai jadwal otomatis
        if (config.getBoolean("settings.enabled", true) && config.getInt("settings.interval", 15) > 0) {
            scheduleNextGame();
        }

        if (config.getBoolean("tournaments.enabled", true) &&
            config.getBoolean("tournaments.auto-start", true)) {
            this.initTournament();
        }
    }

    public void initDiscordIntegration() {
        if (this.discordIntegration == null) {
            this.discordIntegration = new ChatGamesDiscordIntegration(plugin, this.config);
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

        if (this.discordIntegration != null) {
            this.discordIntegration = new ChatGamesDiscordIntegration(plugin);
        }
    }

    public void loadEvents() {
        events.clear();
        ConfigurationSection eventsSection = config.getConfigurationSection("events.types");
        if (eventsSection != null) {
            for (String eventId : eventsSection.getKeys(false)) {
                ConfigurationSection eventSection = eventsSection.getConfigurationSection(eventId);
                if (eventSection != null && eventSection.getBoolean("enabled", true)) {
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("name", ColorUtils.colorize(eventSection.getString("name", eventId)));
                    eventData.put("description", ColorUtils.colorize(eventSection.getString("description", "")));
                    eventData.put("multiplier", eventSection.getDouble("multiplier", 1.0));
                    eventData.put("announcement", ColorUtils.colorize(eventSection.getString("announcement", "")));
                    eventData.put("difficulty-increase", eventSection.getBoolean("difficulty-increase", false));

                    // Tambahkan data lainnya seperti jadwal, dll
                    events.put(eventId, eventData);
                }
            }
        }

        // Check active events on startup
        this.checkActiveEvents();
    }

    public void checkActiveEvents() {
        // Implementation for checking if any event is currently active based on time/date
        // This would run on a schedule and set eventActive and currentEvent when appropriate
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

        // Tampilkan efek visual saat game dimulai
        showGameStartEffects(gameType);

        // Kirim notifikasi ke Discord
        if (discordIntegration != null) {
            discordIntegration.sendGameStart(gameType, question.getQuestion());
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

        // Inisialisasi boss bar untuk countdown
        initCountdownBar(duration);
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

        // Hapus boss bar
        if (timerBossBar != null) {
            timerBossBar.removeAll();
        }

        if (winner == null) {
            // Game berakhir karena timeout
            Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&cWaktu habis! Tidak ada yang menjawab dengan benar."));
            Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&eJawaban yang benar: &f" + currentAnswer));

            // Kirim notifikasi ke Discord
            if (discordIntegration != null) {
                discordIntegration.sendGameTimeout(currentGameType, currentAnswer);
            }
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

        int amount = 0;

        // Process reward based on type
        if (selectedRewardType.equals("money") || selectedRewardType.equals("tokens")) {
            int min = selectedReward.getInt("min", 1);
            int max = selectedReward.getInt("max", 10);
            amount = min + random.nextInt(max - min + 1);

            // Apply event multiplier if active
            if (eventActive && currentEvent != null && events.containsKey(currentEvent)) {
                double multiplier = (double) events.get(currentEvent).getOrDefault("multiplier", 1.0);
                amount = (int) (amount * multiplier);
            }

            String command = selectedReward.getString("command", "").replace("{player}", player.getName()).replace("{amount}", String.valueOf(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            // Notify player
            player.sendMessage(ColorUtils.colorize(prefix + "&aAnda mendapatkan &f" + amount + " " +
                    (selectedRewardType.equals("money") ? "koin" : "token") + "&a!"));

            // Tampilkan efek untuk pemenang
            showWinnerEffects(player, amount, selectedRewardType.equals("money") ? "koin" : "token");
        } else if (selectedRewardType.equals("items")) {
            List<String> commands = selectedReward.getStringList("commands");
            if (!commands.isEmpty()) {
                String command = commands.get(random.nextInt(commands.size())).replace("{player}", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                // Extract item name from command for notification
                String itemName = extractItemName(command);
                player.sendMessage(ColorUtils.colorize(prefix + "&aAnda mendapatkan &f" + itemName + "&a!"));

                // Tampilkan efek untuk pemenang
                showWinnerEffects(player, 1, itemName);
            }
        }

        // Record tournament points if tournament is active
        if (tournamentActive) {
            addTournamentPoints(player.getUniqueId(), 1);
        }

        // Kirim notifikasi ke Discord
        if (discordIntegration != null) {
            discordIntegration.sendGameWinner(player, currentGameType, amount, selectedRewardType.equals("money") ? "koin" : "token");
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

    public void initTournament() {
        tournamentScores.clear();
        tournamentActive = true;

        int duration = config.getInt("tournaments.duration", 7);
        tournamentEndTime = LocalDateTime.now().plusDays(duration);

        // Announce tournament start
        String startMsg = ColorUtils.colorize(config.getString("tournaments.announcement.start", ""))
                .replace("{duration}", String.valueOf(duration));
        Bukkit.broadcastMessage(prefix + startMsg);

        // Kirim notifikasi ke Discord
        if (discordIntegration != null) {
            discordIntegration.sendTournamentStart(duration);
        }

        // Schedule tournament end
        long endTicks = duration * 24 * 60 * 60 * 20L; // Days to ticks
        Bukkit.getScheduler().runTaskLater(plugin, this::endTournament, endTicks);
    }

    public void addTournamentPoints(UUID playerId, int points) {
        int currentPoints = tournamentScores.getOrDefault(playerId, 0);
        tournamentScores.put(playerId, currentPoints + points);
    }

    public void endTournament() {
        if (!tournamentActive) return;

        // Find winners
        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(tournamentScores.entrySet());
        sortedEntries.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        // Award prizes to top players
        ConfigurationSection rewardsSection = config.getConfigurationSection("tournaments.rewards");
        if (!sortedEntries.isEmpty() && rewardsSection != null) {
            // First place
            if (sortedEntries.size() >= 1) {
                UUID firstId = sortedEntries.get(0).getKey();
                String firstName = Bukkit.getOfflinePlayer(firstId).getName();
                int points = sortedEntries.get(0).getValue();

                // Announce winner
                String endMsg = ColorUtils.colorize(config.getString("tournaments.announcement.end", ""))
                        .replace("{winner}", firstName)
                        .replace("{points}", String.valueOf(points));
                Bukkit.broadcastMessage(prefix + endMsg);

                // Execute commands for winners
                executeRewards(firstId, "first");
                if (sortedEntries.size() >= 2) executeRewards(sortedEntries.get(1).getKey(), "second");
                if (sortedEntries.size() >= 3) executeRewards(sortedEntries.get(2).getKey(), "third");
            }
        }

        // Award participation prizes
        int minAnswers = config.getInt("tournaments.rewards.participation.min-answers", 5);
        for (Map.Entry<UUID, Integer> entry : tournamentScores.entrySet()) {
            if (entry.getValue() >= minAnswers) {
                executeRewards(entry.getKey(), "participation");
            }
        }

        // Kirim hasilnya ke Discord
        if (discordIntegration != null) {
            discordIntegration.sendTournamentEnd(sortedEntries);
        }

        // Reset tournament
        tournamentActive = false;
        tournamentScores.clear();

        // Start new tournament if auto-start enabled
        if (config.getBoolean("tournaments.auto-start", true)) {
            // Delay next tournament by a day
            Bukkit.getScheduler().runTaskLater(plugin, this::initTournament, 24 * 60 * 60 * 20L);
        }
    }

    private void executeRewards(UUID playerId, String place) {
        ConfigurationSection rewardsSection = config.getConfigurationSection("tournaments.rewards." + place);
        if (rewardsSection == null) return;

        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        if (playerName == null) return;

        List<String> commands = rewardsSection.getStringList("commands");
        if (place.equals("participation")) {
            commands = config.getStringList("tournaments.rewards.participation.commands");
        }

        for (String command : commands) {
            command = command.replace("{player}", playerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
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

    /**
     * Tampilkan efek visual saat game dimulai
     */
    private void showGameStartEffects(String gameType) {
        if (!config.getBoolean("visual.game-start.title.enabled", true)) {
            return;
        }

        String title = ColorUtils.colorize(config.getString("visual.game-start.title.text", "&b&lChatGames"));
        String subtitle = ColorUtils.colorize(config.getString("visual.game-start.title.subtitle", "&f{game_type}")
                .replace("{game_type}", getGameDisplayName(gameType)));

        int fadeIn = config.getInt("visual.game-start.title.fade-in", 10);
        int stay = config.getInt("visual.game-start.title.stay", 40);
        int fadeOut = config.getInt("visual.game-start.title.fade-out", 10);

        // Tampilkan title untuk semua pemain
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

            // Play sound jika diaktifkan
            if (config.getBoolean("visual.game-start.sound.enabled", true)) {
                String soundName = config.getString("visual.game-start.sound.sound", "BLOCK_NOTE_BLOCK_PLING");
                float volume = (float) config.getDouble("visual.game-start.sound.volume", 1.0);
                float pitch = (float) config.getDouble("visual.game-start.sound.pitch", 1.0);

                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound: " + soundName);
                }
            }

            // Tampilkan action bar
            if (config.getBoolean("visual.game-start.actionbar.enabled", true)) {
                String actionbar = ColorUtils.colorize(config.getString("visual.game-start.actionbar.text",
                        "&7Ketik jawabannya di chat untuk menang!"));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(actionbar));
            }
        }
    }

    /**
     * Inisialisasi boss bar untuk countdown
     */
    private void initCountdownBar(int duration) {
        if (!config.getBoolean("visual.timer.enabled", true)) {
            return;
        }

        // Hapus boss bar sebelumnya jika ada
        if (timerBossBar != null) {
            timerBossBar.removeAll();
        }

        String title = ColorUtils.colorize(config.getString("visual.timer.display", "&e&lWaktu Tersisa: &f{time}")
                .replace("{time}", String.valueOf(duration)));

        String colorName = config.getString("visual.timer.color", "YELLOW");
        String styleName = config.getString("visual.timer.style", "SOLID");

        BarColor barColor;
        BarStyle barStyle;

        try {
            barColor = BarColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            barColor = BarColor.YELLOW;
            plugin.getLogger().warning("Invalid boss bar color: " + colorName);
        }

        try {
            barStyle = BarStyle.valueOf(styleName);
        } catch (IllegalArgumentException e) {
            barStyle = BarStyle.SOLID;
            plugin.getLogger().warning("Invalid boss bar style: " + styleName);
        }

        timerBossBar = Bukkit.createBossBar(title, barColor, barStyle);

        // Tambahkan semua pemain online
        for (Player player : Bukkit.getOnlinePlayers()) {
            timerBossBar.addPlayer(player);
        }

        // Jadwalkan update
        countdownTask = new BukkitRunnable() {
            int timeLeft = duration;

            @Override
            public void run() {
                if (timeLeft <= 0 || !gameActive) {
                    timerBossBar.removeAll();
                    this.cancel();
                    return;
                }

                timerBossBar.setProgress((double) timeLeft / duration);
                timerBossBar.setTitle(ColorUtils.colorize(config.getString("visual.timer.display",
                        "&e&lWaktu Tersisa: &f{time}").replace("{time}", String.valueOf(timeLeft))));

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Tampilkan efek untuk pemenang
     * @param winner Pemain yang menang
     * @param rewardAmount Jumlah hadiah
     * @param rewardType Tipe hadiah (money, token, item)
     */
    private void showWinnerEffects(Player winner, int rewardAmount, String rewardType) {
        // Title untuk pemenang
        if (config.getBoolean("visual.winner.title.enabled", true)) {
            String title = ColorUtils.colorize(config.getString("visual.winner.title.text", "&a&lKAMU MENANG!"));
            String subtitle = ColorUtils.colorize(config.getString("visual.winner.title.subtitle",
                    "&f+{reward_amount} {reward_type}")
                    .replace("{reward_amount}", String.valueOf(rewardAmount))
                    .replace("{reward_type}", rewardType));

            int fadeIn = config.getInt("visual.winner.title.fade-in", 10);
            int stay = config.getInt("visual.winner.title.stay", 60);
            int fadeOut = config.getInt("visual.winner.title.fade-out", 20);

            winner.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        // Sound effects
        if (config.getBoolean("visual.winner.sounds.enabled", true)) {
            String winnerSound = config.getString("visual.winner.sounds.winner", "ENTITY_PLAYER_LEVELUP");
            String otherSound = config.getString("visual.winner.sounds.others", "ENTITY_EXPERIENCE_ORB_PICKUP");

            try {
                // Sound untuk pemenang
                Sound winSound = Sound.valueOf(winnerSound);
                winner.playSound(winner.getLocation(), winSound, 1.0f, 1.0f);

                // Sound untuk pemain lain
                Sound othersSound = Sound.valueOf(otherSound);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.equals(winner)) {
                        player.playSound(player.getLocation(), othersSound, 0.5f, 1.0f);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound: " + e.getMessage());
            }
        }

        // Particle effects
        if (config.getBoolean("visual.winner.particles.enabled", true)) {
            String particleType = config.getString("visual.winner.particles.type", "TOTEM");
            int count = config.getInt("visual.winner.particles.count", 50);
            int duration = config.getInt("visual.winner.particles.duration", 2);

            try {
                Particle particle = Particle.valueOf(particleType);
                Location loc = winner.getLocation().add(0, 1, 0);

                // Spawn particles selama duration detik
                new BukkitRunnable() {
                    int ticks = 0;
                    final int maxTicks = duration * 4; // 4 updates per second

                    @Override
                    public void run() {
                        if (ticks >= maxTicks) {
                            this.cancel();
                            return;
                        }

                        for (int i = 0; i < count / maxTicks; i++) {
                            double offsetX = Math.random() - 0.5;
                            double offsetY = Math.random() * 2;
                            double offsetZ = Math.random() - 0.5;
                            winner.getWorld().spawnParticle(particle, loc.clone().add(offsetX, offsetY, offsetZ),
                                    1, 0, 0, 0, 0);
                        }

                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 5L);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid particle type: " + particleType);
            }
        }

        // Firework effect
        if (config.getBoolean("visual.winner.firework.enabled", true)) {
            List<String> types = config.getStringList("visual.winner.firework.types");
            List<String> colorStrings = config.getStringList("visual.winner.firework.colors");

            if (types.isEmpty()) {
                types = Arrays.asList("BALL", "BALL_LARGE", "BURST", "STAR");
            }

            if (colorStrings.isEmpty()) {
                colorStrings = Arrays.asList("255,0,0", "0,255,0", "0,0,255", "255,255,0");
            }

            // Random type
            String typeStr = types.get(random.nextInt(types.size()));
            FireworkEffect.Type type;
            try {
                type = FireworkEffect.Type.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                type = FireworkEffect.Type.BALL;
            }

            // Random colors
            List<Color> colors = new ArrayList<>();
            for (String colorStr : colorStrings) {
                try {
                    String[] rgb = colorStr.split(",");
                    int r = Integer.parseInt(rgb[0].trim());
                    int g = Integer.parseInt(rgb[1].trim());
                    int b = Integer.parseInt(rgb[2].trim());
                    colors.add(Color.fromRGB(r, g, b));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid color format: " + colorStr);
                }
            }

            if (colors.isEmpty()) {
                colors.add(Color.RED);
            }

            Location loc = winner.getLocation();
            Firework fw = winner.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();

            FireworkEffect effect = FireworkEffect.builder()
                    .flicker(true)
                    .trail(true)
                    .with(type)
                    .withColor(colors)
                    .withFade(Color.WHITE)
                    .build();

            meta.addEffect(effect);
            meta.setPower(1); // Power 1 = jangkauan kecil
            fw.setFireworkMeta(meta);
        }

        // Run special commands
        List<String> commands = config.getStringList("visual.winner.commands");
        for (String command : commands) {
            command = command.replace("{player}", winner.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private String getGameDisplayName(String gameType) {
        ConfigurationSection gameSection = config.getConfigurationSection("games." + gameType);
        if (gameSection != null) {
            return ColorUtils.colorize(gameSection.getString("display-name", gameType));
        }
        return gameType;
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

    public FileConfiguration getConfig() {
        return this.config;
    }

    // Tambahkan getter untuk tournamentScores dan discordIntegration
    public Map<UUID, Integer> getTournamentScores() {
        return tournamentScores;
    }

    public ChatGamesDiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }

    /**
     * Periksa apakah integrasi Discord diaktifkan
     * @return true jika integrasi Discord diaktifkan
     */
    public boolean isDiscordEnabled() {
        return discordIntegration != null && discordIntegration.isEnabled();
    }

    /**
     * Mulai event tertentu berdasarkan ID
     * @param eventId ID event yang akan dimulai
     */
    public void startEvent(String eventId) {
        if (!events.containsKey(eventId)) {
            plugin.getLogger().warning("ChatGames: Event ID '" + eventId + "' tidak ditemukan!");
            return;
        }
        
        if (eventActive) {
            stopEvent(); // Hentikan event yang sedang berjalan
        }
        
        Map<String, Object> eventData = events.get(eventId);
        currentEvent = eventId;
        eventActive = true;
        
        // Broadcast event start message
        String name = (String) eventData.get("name");
        String announcement = (String) eventData.get("announcement");
        
        Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&a&lEvent " + name + " &atelah dimulai!"));
        if (announcement != null && !announcement.isEmpty()) {
            Bukkit.broadcastMessage(ColorUtils.colorize(prefix + announcement));
        }
        
        // Notifikasi Discord jika tersedia
        if (discordIntegration != null && discordIntegration.isEnabled()) {
            // Discord notification implementation
        }
        
        plugin.getLogger().info("ChatGames: Event '" + eventId + "' dimulai.");
    }

    /**
     * Hentikan event yang sedang berjalan
     */
    public void stopEvent() {
        if (!eventActive) {
            return;
        }
        
        String previousEvent = currentEvent;
        eventActive = false;
        currentEvent = null;
        
        // Announce event end
        if (previousEvent != null && events.containsKey(previousEvent)) {
            String name = (String) events.get(previousEvent).get("name");
            Bukkit.broadcastMessage(ColorUtils.colorize(prefix + "&c&lEvent " + name + " &ctelah berakhir."));
        }
        
        plugin.getLogger().info("ChatGames: Event dihentikan.");
    }

    /**
     * Tampilkan daftar event yang tersedia
     * @param sender Pengirim perintah
     */
    public void listEvents(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize("&b&l=== Daftar Event ChatGames ==="));
        
        if (events.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize("&cTidak ada event yang tersedia."));
            return;
        }
        
        for (Map.Entry<String, Map<String, Object>> entry : events.entrySet()) {
            String eventId = entry.getKey();
            Map<String, Object> eventData = entry.getValue();
            
            String name = (String) eventData.getOrDefault("name", eventId);
            String description = (String) eventData.getOrDefault("description", "");
            double multiplier = (double) eventData.getOrDefault("multiplier", 1.0);
            
            sender.sendMessage(ColorUtils.colorize("&e• &f" + name + 
                (eventActive && eventId.equals(currentEvent) ? " &a[AKTIF]" : "")));
            sender.sendMessage(ColorUtils.colorize("  &7ID: &f" + eventId + " &7| Pengali: &f" + multiplier + "x"));
            if (!description.isEmpty()) {
                sender.sendMessage(ColorUtils.colorize("  &7" + description));
            }
        }
    }

    /**
     * Tampilkan status turnamen
     * @param sender Pengirim perintah
     */
    public void showTournamentStatus(CommandSender sender) {
        if (!tournamentActive) {
            sender.sendMessage(ColorUtils.colorize(prefix + "&cTurnamen ChatGames tidak sedang berlangsung."));
            return;
        }
        
        // Sort players by score
        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(tournamentScores.entrySet());
        sortedEntries.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        
        sender.sendMessage(ColorUtils.colorize("&b&l=== Status Turnamen ChatGames ==="));
        
        // Calculate remaining time
        String timeRemaining = "Unknown";
        if (tournamentEndTime != null) {
            LocalDateTime now = LocalDateTime.now();
            long days = java.time.Duration.between(now, tournamentEndTime).toDays();
            long hours = java.time.Duration.between(now, tournamentEndTime).toHours() % 24;
            
            if (days > 0) {
                timeRemaining = days + " hari " + hours + " jam";
            } else {
                long minutes = java.time.Duration.between(now, tournamentEndTime).toMinutes() % 60;
                timeRemaining = hours + " jam " + minutes + " menit";
            }
        }
        
        sender.sendMessage(ColorUtils.colorize("&fWaktu tersisa: &e" + timeRemaining));
        sender.sendMessage(ColorUtils.colorize("&fJumlah peserta: &e" + tournamentScores.size() + " pemain"));
        
        // Display top 5 players
        int count = 0;
        sender.sendMessage(ColorUtils.colorize("&f&lTop 5 Pemain:"));
        
        for (Map.Entry<UUID, Integer> entry : sortedEntries) {
            if (count >= 5) break;
            
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) playerName = "Unknown";
            
            int points = entry.getValue();
            
            String medal = "";
            if (count == 0) medal = "&6&l1. ";
            else if (count == 1) medal = "&7&l2. ";
            else if (count == 2) medal = "&c&l3. ";
            else medal = "&f" + (count + 1) + ". ";
            
            sender.sendMessage(ColorUtils.colorize(medal + "&f" + playerName + " - &e" + points + " poin"));
            count++;
        }
        
        // If sender is a player, show their position
        if (sender instanceof Player) {
            Player player = (Player) sender;
            int playerScore = tournamentScores.getOrDefault(player.getUniqueId(), 0);
            
            // Find player position
            int position = 1;
            for (Map.Entry<UUID, Integer> entry : sortedEntries) {
                if (entry.getKey().equals(player.getUniqueId())) {
                    break;
                }
                position++;
            }
            
            sender.sendMessage(ColorUtils.colorize("&f&lPeringkat Anda: &e#" + position + " &fdengan &e" + playerScore + " poin"));
        }
    }
}