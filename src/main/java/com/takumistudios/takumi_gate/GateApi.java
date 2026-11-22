package com.takumistudios.takumi_gate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client for the website's Takumi Gate API ({@code /api/gate/*}), authenticated with the server key. Every call is
 * asynchronous on the gate's IO executor; nothing here touches the game thread.
 */
public final class GateApi {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REASON = 300;

    private final GateConfig config;
    private final HttpClient http;

    public GateApi(GateConfig config, Executor executor) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .build();
    }

    /** Result of checking a player's ticket. */
    public sealed interface Verdict {
        /** Member with a valid ticket. {@code restartRequired}: their version is older than a restart-only release. */
        record Allowed(String username, int version, boolean restartRequired) implements Verdict {
        }

        /** The website refused the player; {@code reason} is shown to them. */
        record Denied(String reason) implements Verdict {
        }

        /** The website could not be asked (network, server key, unexpected answer). */
        record Unavailable(String detail) implements Verdict {
        }
    }

    /**
     * Checks a ticket. On online-mode servers the profile id is authenticated by Mojang and is what counts; on
     * offline-mode servers the id is derived from the name, so only the name is sent.
     */
    public CompletableFuture<Verdict> verify(String ticket, UUID profileId, String name, boolean onlineMode) {
        JsonObject body = new JsonObject();
        body.addProperty("ticket", ticket);
        if (onlineMode) {
            body.addProperty("uuid", GateProtocol.compactUuid(profileId));
        } else {
            body.addProperty("name", name);
        }
        HttpRequest request = request("/api/gate/verify")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseVerify(response.statusCode(), response.body()))
                .exceptionally(error -> new Verdict.Unavailable(String.valueOf(error.getMessage())));
    }

    /** Recent releases, newest first. Fails the future when the website cannot be asked. */
    public CompletableFuture<List<UpdatePolicy.Release>> status() {
        HttpRequest request = request("/api/gate/status").GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 401) {
                throw new IllegalStateException("la web no reconoce la server_key de este servidor");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return parseStatus(response.body());
        });
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                .timeout(TIMEOUT)
                .header("authorization", "Bearer " + config.serverKey())
                .header("user-agent", "TakumiGate/" + TakumiGate.version())
                .header("accept", "application/json");
    }

    // ─── Parsing (pure, unit-tested) ───────────────────────────────────────────

    /**
     * Never throws: a malformed answer must not turn into {@link Verdict.Unavailable}, which
     * {@code allow_when_api_down} would let in. Any explicit refusal ({@code 403} or {@code ok: false}) is a denial.
     */
    static Verdict parseVerify(int status, String body) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            json = new JsonObject();
        }
        Boolean ok = bool(json, "ok");
        if (status == 200 && Boolean.TRUE.equals(ok)) {
            String username = string(json, "username");
            JsonElement version = json.get("version");
            Boolean restart = bool(json, "restart_required");
            if (username == null || version == null || !version.isJsonPrimitive()
                    || !version.getAsJsonPrimitive().isNumber()) {
                return new Verdict.Unavailable("respuesta incompleta");
            }
            return new Verdict.Allowed(username, version.getAsInt(), Boolean.TRUE.equals(restart));
        }
        if (status == 403 || (status >= 200 && status < 300 && Boolean.FALSE.equals(ok))) {
            String reason = string(json, "error");
            if (reason == null || reason.isBlank()) {
                reason = "Acceso denegado.";
            }
            return new Verdict.Denied(reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
        }
        if (status == 401) {
            return new Verdict.Unavailable("la web no reconoce la server_key de este servidor");
        }
        // Any other client error means the website will not admit this login (e.g. 400 for a name that is not a
        // Minecraft account name on an offline server). Fail closed: allow_when_api_down is only for outages.
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
            return new Verdict.Denied("Acceso denegado.");
        }
        return new Verdict.Unavailable("HTTP " + status);
    }

    private static Boolean bool(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : null;
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    static List<UpdatePolicy.Release> parseStatus(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        List<UpdatePolicy.Release> releases = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("releases")) {
            JsonObject release = element.getAsJsonObject();
            releases.add(new UpdatePolicy.Release(
                    release.get("version").getAsInt(),
                    release.get("restart_required").getAsBoolean(),
                    release.get("grace_minutes").getAsInt(),
                    Instant.parse(release.get("published_at").getAsString())));
        }
        return List.copyOf(releases);
    }
}
