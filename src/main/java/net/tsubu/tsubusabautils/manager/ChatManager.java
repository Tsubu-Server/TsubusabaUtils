package net.tsubu.tsubusabautils.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import java.util.*;
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

        if (originalText.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*")) {
            Component comp = Component.text(originalText).style(event.message().style());
            event.setCancelled(true);
            sendFinalMessage(event, comp, originalText, false);
            return;
        }

        if (originalText.startsWith(".")) {
            String raw = originalText.substring(1);
            Component comp = Component.text(raw).style(event.message().style());
            event.setCancelled(true);
            sendFinalMessage(event, comp, raw, false);
            return;
        }

        event.setCancelled(true);

        CompletableFuture.supplyAsync(() -> processWordWithDictionaryComponent(originalText))
                .thenAccept(component -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Component finalComponent = component.hoverEvent(Component.text("原文: " + originalText));
                    sendFinalMessage(event, finalComponent, originalText, true);
                }))
                .exceptionally(ex -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Component comp = Component.text(originalText).style(event.message().style());
                        sendFinalMessage(event, comp, originalText, false);
                    });
                    return null;
                });
    }

    /**
     * 辞書 + API 変換を通した Component 生成
     */
    private Component processWordWithDictionaryComponent(String text) {
        text = text.replace("~", "～");
        StringBuilder buffer = new StringBuilder();
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

        String base = word.replace("-", "");
        String converted = convertWithGoogleInputTools(base);
        if (converted == null || converted.isEmpty()) converted = word.replace("-", "ー");

        if (word.contains("-")) {
            List<Integer> hyphenPositions = new ArrayList<>();
            int countLetters = 0;
            for (char c : word.toCharArray()) {
                if (c == '-') hyphenPositions.add(countLetters);
                else countLetters++;
            }

            StringBuilder sb = new StringBuilder(converted);
            int convertedLen = converted.length();
            int inserted = 0;

            for (Integer lettersBeforeHyphen : hyphenPositions) {
                int baseLen = Math.max(1, base.length());
                int kanaIndex = (int) Math.round(((double) lettersBeforeHyphen / baseLen) * convertedLen);
                int insertAt = kanaIndex + inserted;
                if (insertAt < 0) insertAt = 0;
                if (insertAt > sb.length()) insertAt = sb.length();

                if (!(insertAt > 0 && sb.charAt(insertAt - 1) == 'ー') &&
                        !(insertAt < sb.length() && sb.charAt(insertAt) == 'ー')) {
                    sb.insert(insertAt, 'ー');
                    inserted++;
                }
            }
            converted = sb.toString();
        }

        return Component.text(converted);
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

            if (connection.getResponseCode() != 200) return null;

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }

            String respStr = response.toString();
            if (!respStr.startsWith("[") || !respStr.endsWith("]")) return null;

            JsonArray jsonArray = JsonParser.parseString(respStr).getAsJsonArray();
            if (jsonArray.size() < 2) return null;
            if (!"SUCCESS".equals(jsonArray.get(0).getAsString())) return null;

            JsonArray resultArray = jsonArray.get(1).getAsJsonArray();
            if (resultArray.size() == 0) return null;

            JsonArray firstCandidateArray = resultArray.get(0).getAsJsonArray();
            if (firstCandidateArray.size() < 2) return null;

            JsonArray candidates = firstCandidateArray.get(1).getAsJsonArray();
            if (candidates.size() == 0) return null;

            return candidates.get(0).getAsString();

        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
