package net.tsubu.tsubusabautils.manager;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChatSyncManager implements Listener, PluginMessageListener {

    private final TsubusabaUtils plugin;
    private static final String CHANNEL = "tsubu:chatsync";
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    public ChatSyncManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncChat(@NotNull AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Component renderedMessage;
        try {
            Player viewer = null;
            for (var v : event.viewers()) {
                if (v instanceof Player) {
                    viewer = (Player) v;
                    break;
                }
            }

            if (viewer != null) {
                renderedMessage = event.renderer().render(
                        player,
                        player.displayName(),
                        event.message(),
                        viewer
                );
            } else {
                renderedMessage = Component.text("<")
                        .append(player.displayName())
                        .append(Component.text("> "))
                        .append(event.message());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to render chat message: " + e.getMessage());
            return;
        }

        String serialized = GSON.serialize(renderedMessage);
        sendToBungee(player, serialized);
    }

    private void sendToBungee(Player player, String serializedComponent) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);

        try {
            out.writeUTF("ChatMessage");
            out.writeUTF(getServerName());
            out.writeUTF(serializedComponent);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send chat to BungeeCord: " + e.getMessage());
            return;
        }

        player.sendPluginMessage(plugin, CHANNEL, stream.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        if (subChannel.equals("ReceiveChat")) {
            String sourceServerName = in.readUTF();
            String serializedComponent = in.readUTF();

            if (sourceServerName.equals(getServerName())) {
                return;
            }
            Component component;
            try {
                component = GSON.deserialize(serializedComponent);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize chat component: " + e.getMessage());
                return;
            }
            Component finalMessage = Component.text("[" + sourceServerName + "] ")
                    .append(component);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalMessage));
        }
    }

    private String getServerName() {
        String serverName = plugin.getConfig().getString("server-name");
        if (serverName == null || serverName.isEmpty()) {
            serverName = "unknown";
            plugin.getLogger().warning("server-name is not set in config.yml!");
        }
        return serverName;
    }

    public void disable() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }
}