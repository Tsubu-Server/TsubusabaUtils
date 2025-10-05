package net.tsubu.tsubusabautils.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ChatManager implements Listener {

    private final TsubusabaUtils plugin;
    private final LuckPerms luckPerms;
    private static final String GOOGLE_INPUTTOOLS_API =
            "https://inputtools.google.com/request?itc=ja-t-i0-und&num=1&text=";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private final Map<String, String> customDictionary = new HashMap<>();

    public ChatManager(TsubusabaUtils plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        loadDictionary();
    }

    private void loadDictionary() {
        FileConfiguration config = plugin.getConfig();
        if (config.isConfigurationSection("dictionary")) {
            for (String key : config.getConfigurationSection("dictionary").getKeys(false)) {
                String value = config.getString("dictionary." + key, key);
                customDictionary.put(key, value);
            }
            plugin.getLogger().info("Loaded " + customDictionary.size() + " dictionary entries.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(@NotNull AsyncChatEvent event) {
        String originalText = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (originalText == null || originalText.isEmpty()) return;
        Component originalMessageComponent = event.message();

        if (originalText.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*")) {
            Component comp = Component.text(originalText).style(originalMessageComponent.style());
            event.setCancelled(true);
            sendFinalMessage(event, comp, originalText, false);
            return;
        }

        if (originalText.startsWith(".")) {
            String raw = originalText.substring(1);
            Component comp = Component.text(raw).style(originalMessageComponent.style());
            event.setCancelled(true);
            sendFinalMessage(event, comp, raw, false);
            return;
        }

        event.setCancelled(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                String dictApplied = applyDictionary(originalText);
                String converted = convertTextWithWordSplit(dictApplied);

                if (converted == null || converted.isEmpty()) {
                    return dictApplied + "§§FAIL§§" + originalText;
                }

                return converted + "§§API§§" + originalText;
            } catch (Exception e) {
                return applyDictionary(originalText) + "§§ERR§§" + originalText;
            }
        }).thenAccept(result -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                String[] parts = result.split("§§(API|ERR|FAIL)§§");
                String convertedText = parts[0];
                String original = parts.length > 1 ? parts[1] : originalText;

                Component convertedComponent = Component.text(convertedText)
                        .hoverEvent(Component.text("原文: " + original));

                sendFinalMessage(event, convertedComponent, original, true);
            });
        }).exceptionally(ex -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Component comp = Component.text(originalText).style(originalMessageComponent.style());
                sendFinalMessage(event, comp, originalText, false);
            });
            return null;
        });
    }

    /**
     * 辞書適用（部分一致すべて置換）
     */
    private String applyDictionary(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : customDictionary.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * テキストを単語ごとに分割して変換（カンマ、スペースなどを保持）
     */
    private String convertTextWithWordSplit(String text) {
        StringBuilder result = new StringBuilder();
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                currentWord.append(c);
            } else {
                if (currentWord.length() > 0) {
                    String word = currentWord.toString();
                    String converted = convertWithGoogleInputTools(word);
                    result.append(converted != null ? converted : word);
                    currentWord.setLength(0);
                }
                if (c == ',') {
                    result.append('、');
                } else {
                    result.append(c);
                }
            }
        }

        if (currentWord.length() > 0) {
            String word = currentWord.toString();
            String converted = convertWithGoogleInputTools(word);
            result.append(converted != null ? converted : word);
        }

        return result.toString();
    }

    private void sendFinalMessage(@NotNull AsyncChatEvent event, @NotNull Component messageComponent,
                                  @NotNull String plainText, boolean wasConverted) {
        event.setCancelled(true);

        Player sender = event.getPlayer();
        Component displayName = getPlayerDisplayName(sender);

        for (Audience audience : event.viewers()) {
            Component rendered = event.renderer().render(sender, displayName, messageComponent, audience);
            audience.sendMessage(rendered);
        }

    }

    private Component getPlayerDisplayName(@NotNull Player player) {
        Component nameComponent = Component.text(player.getName());

        if (luckPerms != null) {
            try {
                User lpUser = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                if (lpUser != null) {
                    String prefix = lpUser.getCachedData().getMetaData().getPrefix();
                    String suffix = lpUser.getCachedData().getMetaData().getSuffix();

                    Component prefixComponent = prefix != null ? LEGACY_SERIALIZER.deserialize(prefix) : Component.empty();
                    Component suffixComponent = suffix != null ? LEGACY_SERIALIZER.deserialize(suffix) : Component.empty();
                    return prefixComponent.append(nameComponent).append(suffixComponent);
                }
            } catch (Exception ignored) {}
        }
        return nameComponent;
    }

    private String convertWithGoogleInputTools(String text) {
        HttpURLConnection connection = null;
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            URL url = new URL(GOOGLE_INPUTTOOLS_API + encodedText);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String respStr = response.toString();

            if (!respStr.startsWith("[") || !respStr.endsWith("]")) {
                return null;
            }

            JsonArray jsonArray = JsonParser.parseString(respStr).getAsJsonArray();
            if (jsonArray.size() < 2) {
                return null;
            }

            String status = jsonArray.get(0).getAsString();
            if (!"SUCCESS".equals(status)) {
                return null;
            }

            JsonArray resultArray = jsonArray.get(1).getAsJsonArray();
            if (resultArray.size() == 0) {
                return null;
            }

            JsonArray firstCandidateArray = resultArray.get(0).getAsJsonArray();
            if (firstCandidateArray.size() < 2) {
                return null;
            }

            JsonArray candidates = firstCandidateArray.get(1).getAsJsonArray();
            if (candidates.size() == 0) {
                return null;
            }

            return candidates.get(0).getAsString();

        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}