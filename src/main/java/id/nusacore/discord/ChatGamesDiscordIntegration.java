package id.nusacore.discord;

import id.nusacore.NusaCore;
import id.nusacore.utils.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatGamesDiscordIntegration {
    
    private final NusaCore plugin;
    private final boolean enabled;
    private final String integrationMethod;
    private final String webhookUrl;
    private final String webhookUsername;
    private final String webhookAvatarUrl;
    private final String discordSrvChannel;
    
    public ChatGamesDiscordIntegration(NusaCore plugin) {
        this.plugin = plugin;
        
        ConfigurationSection config = plugin.getChatGamesManager().getConfig().getConfigurationSection("discord");
        if (config == null) {
            this.enabled = false;
            this.integrationMethod = "webhook";
            this.webhookUrl = "";
            this.webhookUsername = "ChatGames Bot";
            this.webhookAvatarUrl = "";
            this.discordSrvChannel = "chatgames";
            return;
        }
        
        this.enabled = config.getBoolean("enabled", false);
        this.integrationMethod = config.getString("integration-method", "webhook");
        this.webhookUrl = config.getString("webhook.url", "");
        this.webhookUsername = config.getString("webhook.username", "ChatGames Bot");
        this.webhookAvatarUrl = config.getString("webhook.avatar-url", "");
        this.discordSrvChannel = config.getString("channel", "chatgames");
    }
    
    /**
     * Kirim notifikasi saat game dimulai
     * @param gameType Tipe permainan yang dimulai
     * @param question Pertanyaan permainan
     */
    public void sendGameStart(String gameType, String question) {
        if (!isEnabled() || !getNotificationConfig("game-start.enabled", true)) return;
        
        String format = getNotificationFormat("game-start.format", 
                ":game_die: **{game_type}** dimulai!\n> {question}\n\nKetik jawabannya di server Minecraft untuk menang!");
        
        format = format.replace("{game_type}", getGameDisplayName(gameType))
                      .replace("{question}", question);
        
        boolean useEmbed = getNotificationConfig("game-start.embed", true);
        String colorHex = getNotificationConfig("game-start.embed-color", "#3498db");
        
        sendDiscordMessage(format, useEmbed, colorHex);
    }
    
    /**
     * Kirim notifikasi saat ada pemenang
     * @param player Pemain yang menang
     * @param gameType Tipe permainan
     * @param rewardAmount Jumlah hadiah
     * @param rewardType Tipe hadiah
     */
    public void sendGameWinner(Player player, String gameType, int rewardAmount, String rewardType) {
        if (!isEnabled() || !getNotificationConfig("game-winner.enabled", true)) return;
        
        String format = getNotificationFormat("game-winner.format", 
                ":trophy: **{player}** memenangkan game **{game_type}**!\nHadiah: **{reward_amount} {reward_type}**");
        
        format = format.replace("{player}", player.getName())
                      .replace("{game_type}", getGameDisplayName(gameType))
                      .replace("{reward_amount}", String.valueOf(rewardAmount))
                      .replace("{reward_type}", rewardType);
        
        boolean useEmbed = getNotificationConfig("game-winner.embed", true);
        String colorHex = getNotificationConfig("game-winner.embed-color", "#2ecc71");
        
        sendDiscordMessage(format, useEmbed, colorHex);
    }
    
    /**
     * Kirim notifikasi saat waktu habis
     * @param gameType Tipe permainan
     * @param answer Jawaban yang benar
     */
    public void sendGameTimeout(String gameType, String answer) {
        if (!isEnabled() || !getNotificationConfig("game-timeout.enabled", true)) return;
        
        String format = getNotificationFormat("game-timeout.format", 
                ":alarm_clock: Waktu habis! Game **{game_type}** berakhir tanpa pemenang.\nJawaban: **{answer}**");
        
        format = format.replace("{game_type}", getGameDisplayName(gameType))
                      .replace("{answer}", answer);
        
        boolean useEmbed = getNotificationConfig("game-timeout.embed", true);
        String colorHex = getNotificationConfig("game-timeout.embed-color", "#e74c3c");
        
        sendDiscordMessage(format, useEmbed, colorHex);
    }
    
    /**
     * Kirim notifikasi saat turnamen dimulai
     * @param duration Durasi turnamen dalam hari
     */
    public void sendTournamentStart(int duration) {
        if (!isEnabled() || !getNotificationConfig("tournament-start.enabled", true)) return;
        
        String format = getNotificationFormat("tournament-start.format", 
                ":tada: **Turnamen ChatGames** telah dimulai! Berlangsung selama {duration} hari.\nBergabunglah di server Minecraft untuk berpartisipasi!");
        
        format = format.replace("{duration}", String.valueOf(duration));
        
        boolean useEmbed = getNotificationConfig("tournament-start.embed", true);
        String colorHex = getNotificationConfig("tournament-start.embed-color", "#f1c40f");
        
        sendDiscordMessage(format, useEmbed, colorHex);
    }
    
    /**
     * Kirim notifikasi saat turnamen berakhir
     * @param winners List pemenang dengan format [uuid, points]
     */
    public void sendTournamentEnd(List<Map.Entry<UUID, Integer>> winners) {
        if (!isEnabled() || !getNotificationConfig("tournament-end.enabled", true) || winners.isEmpty()) return;
        
        String format = getNotificationFormat("tournament-end.format", 
                ":crown: **Turnamen ChatGames** telah berakhir!\n\n:first_place: **{winner}** - {points} poin\n:second_place: **{second}** - {second_points} poin\n:third_place: **{third}** - {third_points} poin");
        
        String winner = winners.size() >= 1 ? getPlayerName(winners.get(0).getKey()) : "Tidak ada";
        String second = winners.size() >= 2 ? getPlayerName(winners.get(1).getKey()) : "Tidak ada";
        String third = winners.size() >= 3 ? getPlayerName(winners.get(2).getKey()) : "Tidak ada";
        
        int winnerPoints = winners.size() >= 1 ? winners.get(0).getValue() : 0;
        int secondPoints = winners.size() >= 2 ? winners.get(1).getValue() : 0;
        int thirdPoints = winners.size() >= 3 ? winners.get(2).getValue() : 0;
        
        format = format.replace("{winner}", winner)
                      .replace("{second}", second)
                      .replace("{third}", third)
                      .replace("{points}", String.valueOf(winnerPoints))
                      .replace("{second_points}", String.valueOf(secondPoints))
                      .replace("{third_points}", String.valueOf(thirdPoints));
        
        boolean useEmbed = getNotificationConfig("tournament-end.embed", true);
        String colorHex = getNotificationConfig("tournament-end.embed-color", "#9b59b6");
        
        sendDiscordMessage(format, useEmbed, colorHex);
    }
    
    /**
     * Update leaderboard di Discord
     * @param tournamentScores Map skor turnamen [uuid -> points]
     */
    public void updateLeaderboard(Map<UUID, Integer> tournamentScores) {
        if (!isEnabled() || !getLeaderboardConfig("enabled", true) || tournamentScores.isEmpty()) return;
        
        int displayCount = getLeaderboardConfig("display-count", 10);
        
        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(tournamentScores.entrySet());
        sortedEntries.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        
        StringBuilder leaderboardText = new StringBuilder("# :trophy: **LEADERBOARD CHATGAMES** :trophy:\n\n");
        
        int count = 0;
        for (Map.Entry<UUID, Integer> entry : sortedEntries) {
            if (count >= displayCount) break;
            
            String playerName = getPlayerName(entry.getKey());
            int points = entry.getValue();
            
            String medal;
            if (count == 0) medal = ":first_place:";
            else if (count == 1) medal = ":second_place:";
            else if (count == 2) medal = ":third_place:";
            else medal = "`#" + (count + 1) + "`";
            
            leaderboardText.append(medal).append(" **").append(playerName).append("** - ").append(points).append(" poin\n");
            
            count++;
        }
        
        // Add timestamp
        leaderboardText.append("\n*Terakhir diperbarui: ").append(new Date().toString()).append("*");
        
        // Send as embed
        sendDiscordMessage(leaderboardText.toString(), true, "#f39c12");
    }
    
    /**
     * Kirim pesan ke Discord
     */
    private void sendDiscordMessage(String content, boolean useEmbed, String colorHex) {
        if (integrationMethod.equalsIgnoreCase("webhook")) {
            sendWebhookMessage(content, useEmbed, colorHex);
        } else if (integrationMethod.equalsIgnoreCase("discordsrv")) {
            sendDiscordSRVMessage(content, useEmbed, colorHex);
        }
    }
    
    /**
     * Kirim pesan menggunakan webhook Discord
     */
    private void sendWebhookMessage(String content, boolean useEmbed, String colorHex) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().warning("ChatGames: Webhook URL tidak dikonfigurasi!");
            return;
        }
        
        // Run in async thread untuk mencegah lag
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject json = new JSONObject();
                
                // Set username dan avatar jika tersedia
                if (webhookUsername != null && !webhookUsername.isEmpty()) {
                    json.put("username", webhookUsername);
                }
                
                if (webhookAvatarUrl != null && !webhookAvatarUrl.isEmpty()) {
                    json.put("avatar_url", webhookAvatarUrl);
                }
                
                if (useEmbed) {
                    JSONObject embed = new JSONObject();
                    
                    // Parse color hex to integer
                    try {
                        String hexColor = colorHex.replace("#", "");
                        Color color = Color.decode("#" + hexColor);
                        int colorInt = color.getRGB() & 0xFFFFFF; // Convert to Discord color format
                        embed.put("color", colorInt);
                    } catch (Exception e) {
                        embed.put("color", 3447003); // Default Discord blue
                    }
                    
                    embed.put("description", content);
                    
                    JSONArray embeds = new JSONArray();
                    embeds.add(embed);
                    
                    json.put("embeds", embeds);
                } else {
                    json.put("content", content);
                }
                
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "NusaCore-ChatGames/1.0");
                connection.setDoOutput(true);
                
                String jsonString = json.toJSONString();
                
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode != 204) { // Discord returns 204 No Content on success
                    plugin.getLogger().warning("ChatGames: Discord webhook error! Response code: " + responseCode);
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                plugin.getLogger().warning("ChatGames: Discord webhook error! " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Kirim pesan menggunakan DiscordSRV
     */
    private void sendDiscordSRVMessage(String content, boolean useEmbed, String colorHex) {
        // Cek apakah DiscordSRV terpasang
        if (plugin.getServer().getPluginManager().getPlugin("DiscordSRV") == null) {
            plugin.getLogger().warning("ChatGames: DiscordSRV tidak terpasang!");
            return;
        }
        
        // Gunakan API DiscordSRV
        try {
            // Untuk penggunaan dengan DiscordSRV, kode ini harus disesuaikan dengan versi DiscordSRV yang digunakan
            // Contoh implementasi dasar:
            github.scarsz.discordsrv.DiscordSRV discord = github.scarsz.discordsrv.DiscordSRV.getPlugin();
            
            if (useEmbed) {
                // Convert color hex to Java Color
                java.awt.Color embedColor;
                try {
                    embedColor = java.awt.Color.decode(colorHex);
                } catch (Exception e) {
                    embedColor = java.awt.Color.BLUE; // Default color
                }
                
                discord.getMainTextChannel().sendMessageEmbeds(
                    new github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder()
                        .setDescription(content)
                        .setColor(embedColor)
                        .build()
                ).queue();
            } else {
                discord.getDestinationTextChannelForGameChannelName(discordSrvChannel)
                       .sendMessage(content).queue();
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("ChatGames: Error sending message via DiscordSRV: " + e.getMessage());
            
            // Fallback to webhook if DiscordSRV fails
            if (!webhookUrl.isEmpty()) {
                plugin.getLogger().info("ChatGames: Fallback to webhook method...");
                sendWebhookMessage(content, useEmbed, colorHex);
            }
        }
    }
    
    /**
     * Dapatkan nama pemain dari UUID
     */
    private String getPlayerName(UUID uuid) {
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }
    
    /**
     * Dapatkan nama tampilan untuk tipe game
     */
    private String getGameDisplayName(String gameType) {
        ConfigurationSection gameSection = plugin.getChatGamesManager().getConfig().getConfigurationSection("games." + gameType);
        if (gameSection != null) {
            return ColorUtils.stripColor(ColorUtils.colorize(gameSection.getString("display-name", gameType)));
        }
        return gameType;
    }
    
    /**
     * Dapatkan konfigurasi notifikasi
     */
    private String getNotificationFormat(String path, String defaultValue) {
        return plugin.getChatGamesManager().getConfig().getString("discord.notifications." + path, defaultValue);
    }
    
    /**
     * Dapatkan konfigurasi notifikasi boolean
     */
    private boolean getNotificationConfig(String path, boolean defaultValue) {
        return plugin.getChatGamesManager().getConfig().getBoolean("discord.notifications." + path, defaultValue);
    }
    
    /**
     * Dapatkan konfigurasi leaderboard
     */
    private boolean getLeaderboardConfig(String path, boolean defaultValue) {
        return plugin.getChatGamesManager().getConfig().getBoolean("discord.leaderboard." + path, defaultValue);
    }
    
    /**
     * Dapatkan konfigurasi leaderboard integer
     */
    private int getLeaderboardConfig(String path, int defaultValue) {
        return plugin.getChatGamesManager().getConfig().getInt("discord.leaderboard." + path, defaultValue);
    }
    
    /**
     * Cek apakah integrasi Discord diaktifkan
     */
    public boolean isEnabled() {
        return enabled;
    }
}