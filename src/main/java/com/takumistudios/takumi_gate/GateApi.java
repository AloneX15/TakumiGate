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

    static Verdict parseVerify(int status, String body) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            json = new JsonObject();
        }
        if (status == 200 && json.has("ok") && json.get("ok").getAsBoolean()) {
            try {
                return new Verdict.Allowed(
                        json.get("username").getAsString(),
                        json.get("version").getAsInt(),
                        json.has("restart_required") && json.get("restart_required").getAsBoolean());
            } catch (RuntimeException e) {
                return new Verdict.Unavailable("respuesta incompleta");
            }
        }
        if (status == 403) {
            String reason = json.has("error") ? json.get("error").getAsString() : "Acceso denegado.";
            return new Verdict.Denied(reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
        }
        if (status == 401) {
            return new Verdict.Unavailable("la web no reconoce la server_key de este servidor");
        }
        return new Verdict.Unavailable("HTTP " + status);
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
