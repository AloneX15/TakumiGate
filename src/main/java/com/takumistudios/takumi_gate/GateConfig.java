package com.takumistudios.takumi_gate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * {@code config/takumi_gate.json} on the event server. The website shows the whole file (with the server key) when
 * the organizer generates a key in the instance settings.
 *
 * @param enabled         the gate checks players (false = everybody can join)
 * @param apiUrl          Takumi website, e.g. {@code https://takumilabs.org}
 * @param serverKey       {@code tgk_…} key of this event's instance
 * @param pollSeconds     how often to check for newly published versions (10–600)
 * @param allowWhenApiDown let players in when the website cannot be reached (default: refuse)
 * @param bypass          Minecraft profile ids allowed without a ticket (staff without Takumi Launcher)
 */
public record GateConfig(
        boolean enabled,
        String apiUrl,
        String serverKey,
        int pollSeconds,
        boolean allowWhenApiDown,
        Set<UUID> bypass) {
    public static final int CONFIG_VERSION = 1;
    public static final String FILE_NAME = "takumi_gate.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static GateConfig defaults() {
        return new GateConfig(true, "https://takumilabs.org", "", 30, false, Set.of());
    }

    /** The gate only acts with a well-formed key and a safe URL. */
    public boolean active() {
        return enabled && problem() == null;
    }

    /** Why the configuration cannot be used, or null. */
    public String problem() {
        if (!serverKey.startsWith("tgk_") || serverKey.length() < 20 || serverKey.length() > 100) {
            return "falta server_key (genérala en la web: Instancias privadas → Ajustes → Takumi Gate)";
        }
        if (!isSafeUrl(apiUrl)) {
            return "api_url debe ser https:// (http solo para localhost)";
        }
        return null;
    }

    static boolean isSafeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
            return "https".equals(uri.getScheme()) && !host.isEmpty() || "http".equals(uri.getScheme()) && local;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** {@code apiUrl} without trailing slashes. */
    public String baseUrl() {
        String url = apiUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    // ─── File ───────────────────────────────────────────────────────────────

    /**
     * Reads the config from {@code dir}, creating it with defaults when missing. A corrupt file is kept as
     * {@code .bak} and replaced with defaults (the gate stays disabled until the key is filled in again).
     */
    public static GateConfig load(Path dir) {
        Path file = dir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            GateConfig config = defaults();
            save(dir, config);
            return config;
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            TakumiGate.LOGGER.warn("config/{} no es válido ({}); se guarda una copia .bak y se regenera", FILE_NAME,
                    e.getMessage());
            try {
                Files.copy(file, file.resolveSibling(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copy) {
                TakumiGate.LOGGER.warn("No se pudo copiar la config dañada", copy);
            }
            GateConfig config = defaults();
            save(dir, config);
            return config;
        }
    }

    static GateConfig parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        GateConfig d = defaults();
        boolean enabled = root.has("enabled") ? root.get("enabled").getAsBoolean() : d.enabled();
        String apiUrl = root.has("api_url") ? root.get("api_url").getAsString().trim() : d.apiUrl();
        String key = root.has("server_key") ? root.get("server_key").getAsString().trim() : d.serverKey();
        int poll = root.has("poll_seconds") ? root.get("poll_seconds").getAsInt() : d.pollSeconds();
        boolean down = root.has("allow_when_api_down") ? root.get("allow_when_api_down").getAsBoolean()
                : d.allowWhenApiDown();
        List<UUID> bypass = new ArrayList<>();
        if (root.has("bypass_uuids")) {
            for (JsonElement element : root.getAsJsonArray("bypass_uuids")) {
                parseUuid(element.getAsString()).ifPresentOrElse(bypass::add,
                        () -> TakumiGate.LOGGER.warn("bypass_uuids: '{}' no es un UUID", element.getAsString()));
            }
        }
        return new GateConfig(enabled, apiUrl, key, Math.clamp(poll, 10, 600), down, Set.copyOf(bypass));
    }

    static java.util.Optional<UUID> parseUuid(String raw) {
        String hex = raw.trim().replace("-", "");
        if (!hex.matches("[0-9a-fA-F]{32}")) {
            return java.util.Optional.empty();
        }
        String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
        return java.util.Optional.of(UUID.fromString(dashed));
    }

    static String toJson(GateConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("config_version", CONFIG_VERSION);
        root.addProperty("enabled", config.enabled());
        root.addProperty("api_url", config.apiUrl());
        root.addProperty("server_key", config.serverKey());
        root.addProperty("poll_seconds", config.pollSeconds());
        root.addProperty("allow_when_api_down", config.allowWhenApiDown());
        JsonArray bypass = new JsonArray();
        config.bypass().stream().map(UUID::toString).sorted().forEach(bypass::add);
        root.add("bypass_uuids", bypass);
        return GSON.toJson(root) + "\n";
    }

    /** Atomic write: a crash never leaves a half-written config. */
    static void save(Path dir, GateConfig config) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(FILE_NAME);
            Path tmp = target.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, toJson(config), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            TakumiGate.LOGGER.warn("No se pudo guardar config/{}", FILE_NAME, e);
        }
    }
}
