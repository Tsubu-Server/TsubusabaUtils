package net.tsubu.tsubusabautils.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.util.*;

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

        if (originalText.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*")) {
            return;
        }
        if (originalText.startsWith(".")) {
            String raw = originalText.substring(1);
            Component comp = Component.text(raw).style(event.message().style());
            event.message(comp);
            return;
        }

        Component converted = processWordWithDictionaryComponent(originalText);
        Component finalComponent = converted.hoverEvent(Component.text("原文: " + originalText));
        event.message(finalComponent);
    }

    /**
     * 辞書 + API変換を通した Component 生成
     */
    private Component processWordWithDictionaryComponent(String text) {
        text = text.replace("~", "〜");
        List<Component> components = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (Character.isLetterOrDigit(c) || c == '-') {
                currentWord.append(c);
            } else {
                if (currentWord.length() > 0) {
                    components.add(processSingleWordComponent(currentWord.toString()));
                    currentWord.setLength(0);
                }

                if (c == ',') components.add(Component.text('、'));
                else components.add(Component.text(c));
            }
        }

        if (currentWord.length() > 0) {
            components.add(processSingleWordComponent(currentWord.toString()));
        }

        Component result = Component.empty();
        for (Component comp : components) result = result.append(comp);
        return result;
    }

    /**
     * 単語単位で辞書 or API を確認し Component を返す
     */
    private Component processSingleWordComponent(String word) {
        for (Map.Entry<String, String> entry : customDictionary.entrySet()) {
            String key = entry.getKey();
            if (word.contains(key)) {
                int index = word.indexOf(key);
                Component before = index > 0 ? processSingleWordComponent(word.substring(0, index)) : Component.empty();
                Component after = (index + key.length() < word.length()) ?
                        processSingleWordComponent(word.substring(index + key.length())) : Component.empty();

                String value = entry.getValue();
                Component main;
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    main = Component.text(value)
                            .clickEvent(ClickEvent.openUrl(value))
                            .hoverEvent(Component.text("クリックで開く: " + value));
                } else {
                    main = Component.text(value);
                }

                return Component.empty().append(before).append(main).append(after);
            }
        }

        if (word.matches("^[A-Z0-9\\-]+$")) return Component.text(word);
        String converted = convertWithGoogleInputTools(word);
        if (converted == null || converted.isEmpty()) converted = word;

        return Component.text(converted);
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

            if (connection.getResponseCode() != 200) return text;

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }

            String respStr = response.toString();
            if (!respStr.startsWith("[") || !respStr.endsWith("]")) return text;

            JsonArray jsonArray = JsonParser.parseString(respStr).getAsJsonArray();
            if (jsonArray.size() < 2) return text;
            if (!"SUCCESS".equals(jsonArray.get(0).getAsString())) return text;

            JsonArray resultArray = jsonArray.get(1).getAsJsonArray();
            if (resultArray.size() == 0) return text;

            JsonArray firstCandidateArray = resultArray.get(0).getAsJsonArray();
            if (firstCandidateArray.size() < 2) return text;

            JsonArray candidates = firstCandidateArray.get(1).getAsJsonArray();
            if (candidates.size() == 0) return text;

            return candidates.get(0).getAsString();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to convert with Google Input Tools: " + e.getMessage());
            return text;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}