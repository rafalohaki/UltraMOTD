package org.rafalohaki.ultramotd.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;

import java.util.List;

/**
 * Serializes ServerPing objects to JSON format compatible with Minecraft protocol.
 * Used by PacketPingCache to create cached status response packets.
 */
final class ServerPingSerializer {

    private static final Gson GSON = new GsonBuilder().create();
    private static final JSONComponentSerializer JSON = JSONComponentSerializer.json();

    private ServerPingSerializer() {}

    /**
     * Converts ServerPing to JSON string matching vanilla Minecraft status response format.
     */
    public static String toJson(ServerPing ping) {
        var version = new VersionObj(
                ping.getVersion().getName(),
                ping.getVersion().getProtocol()
        );

        var players = ping.getPlayers()
                .map(p -> new PlayersObj(
                        p.getOnline(),
                        p.getMax(),
                        p.getSample().stream()
                                .map(sp -> new SamplePlayerObj(sp.getName(), sp.getId().toString()))
                                .toList()
                ))
                .orElse(null);

        // 1) Adventure component -> JSON string
        var descriptionComponent = ping.getDescriptionComponent();
        if (descriptionComponent == null) {
            descriptionComponent = Component.text("Server MOTD");
        }
        String descriptionJson = JSON.serialize(descriptionComponent);
        // 2) Parse to JsonElement so it serializes as object, not string
        JsonElement descriptionElement = JsonParser.parseString(descriptionJson);

        String faviconData = ping.getFavicon()
                .map(f -> {
                    String base64 = f.getBase64Url();
                    return base64 != null ? "data:image/png;base64," + base64 : null;
                })
                .orElse(null);

        var root = new RootObj(version, players, descriptionElement, faviconData);
        return GSON.toJson(root);
    }

    private record VersionObj(String name, int protocol) {}
    private record SamplePlayerObj(String name, String id) {}
    private record PlayersObj(int online, int max, List<SamplePlayerObj> sample) {}
    private record RootObj(VersionObj version, PlayersObj players, JsonElement description, String favicon) {}
}
