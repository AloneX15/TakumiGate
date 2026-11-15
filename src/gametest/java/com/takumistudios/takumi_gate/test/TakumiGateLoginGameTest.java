package com.takumistudios.takumi_gate.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.takumi_gate.GateConfig;
import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.TakumiGate;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.UUIDUtil;

/**
 * End-to-end check of the login gate against a running TakumiWeb: a dedicated server with Takumi Gate is started and
 * this client tries to join it without a ticket, with another account's ticket and with a valid one. Skipped unless
 * these environment variables are set (the instance must be published and the token's user a member):
 *
 * <pre>
 * TAKUMI_GATE_E2E_API=http://localhost:5173
 * TAKUMI_GATE_E2E_KEY=tgk_…            server key from the instance settings
 * TAKUMI_GATE_E2E_INSTANCE=&lt;uuid&gt;
 * TAKUMI_GATE_E2E_TOKEN=&lt;launcher session token of a member&gt;
 * TAKUMI_GATE_E2E_KICK=1               optional: stay connected until a release with grace 0 is published and the
 *                                      server disconnects the player (writes run/takumi-gate-e2e.joined meanwhile)
 * </pre>
 */
public class TakumiGateLoginGameTest implements FabricClientGameTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    @Override
    public void runTest(ClientGameTestContext context) {
        String api = System.getenv("TAKUMI_GATE_E2E_API");
        String key = System.getenv("TAKUMI_GATE_E2E_KEY");
        String instance = System.getenv("TAKUMI_GATE_E2E_INSTANCE");
        String token = System.getenv("TAKUMI_GATE_E2E_TOKEN");
        if (api == null || key == null || instance == null || token == null) {
            TakumiGate.LOGGER.info("TakumiGateLoginGameTest omitido: faltan las variables TAKUMI_GATE_E2E_*");
            return;
        }
        Path config = FabricLoader.getInstance().getConfigDir().resolve(GateConfig.FILE_NAME);
        Path joinedMarker = FabricLoader.getInstance().getGameDir().resolve("takumi-gate-e2e.joined");
        try {
            Files.createDirectories(config.getParent());
            Files.writeString(config, """
                    {"config_version":1,"enabled":true,"api_url":"%s","server_key":"%s","poll_seconds":10,
                     "allow_when_api_down":false,"bypass_uuids":[]}
                    """.formatted(api, key), StandardCharsets.UTF_8);
            String name = context.computeOnClient(mc -> mc.getUser().getName());
            // The test server runs offline: the gate matches the ticket by name there.
            UUID profile = UUIDUtil.createOfflinePlayerUUID(name);
            int version = publishedVersion(api, instance, token);

            Properties properties = new Properties();
            properties.setProperty("online-mode", "false");
            try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
                String address = "localhost:" + server.computeOnServer(s -> s.getPort());

                System.clearProperty(GateProtocol.TICKET_PROPERTY);
                expectRefused(context, address, "sin ticket");

                System.setProperty(GateProtocol.TICKET_PROPERTY, ticket(api, instance, token, UUID.randomUUID(), "OtroJugador", version));
                expectRefused(context, address, "ticket de otra cuenta");

                System.setProperty(GateProtocol.TICKET_PROPERTY, ticket(api, instance, token, profile, name, version));
                var connection = server.connect();
                boolean kicked = false;
                try {
                    context.waitTicks(20);
                    int players = server.computeOnServer(s -> s.getPlayerCount());
                    if (players != 1) {
                        throw new AssertionError("Con un ticket válido debería haber 1 jugador y hay " + players);
                    }
                    context.takeScreenshot("takumi_gate_joined");
                    if ("1".equals(System.getenv("TAKUMI_GATE_E2E_KICK"))) {
                        Files.writeString(joinedMarker, "joined\n");
                        // The organizer publishes a release that needs a restart (grace 0): the gate disconnects us.
                        // The gate polls every 10 s and enforces every 5 s: allow up to a minute.
                        for (int second = 0; second < 60 && !kicked; second++) {
                            context.waitTicks(20);
                            kicked = context.computeOnClient(mc -> mc.getConnection() == null);
                        }
                        if (!kicked) {
                            throw new AssertionError("El gate no desconectó al jugador tras la versión obligatoria");
                        }
                        context.waitForScreen(DisconnectedScreen.class);
                        context.takeScreenshot("takumi_gate_kicked_after_update");
                        TakumiGate.LOGGER.info("E2E: expulsado tras publicar una versión obligatoria");
                        context.setScreen(TitleScreen::new);
                    }
                } finally {
                    // The server already closed it after a kick; closing again is an error in the test API.
                    if (!kicked) {
                        connection.close();
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("Fallo de E/S en el test de Takumi Gate", e);
        } finally {
            System.clearProperty(GateProtocol.TICKET_PROPERTY);
            try {
                Files.deleteIfExists(config);
                Files.deleteIfExists(joinedMarker);
            } catch (IOException e) {
                TakumiGate.LOGGER.warn("No se pudo limpiar el test", e);
            }
        }
    }

    private static void expectRefused(ClientGameTestContext context, String address, String what) {
        context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                new ServerData("Takumi Gate e2e", address, ServerData.Type.OTHER), false, null));
        context.waitForScreen(DisconnectedScreen.class);
        context.takeScreenshot("takumi_gate_refused_" + what.replace(' ', '_'));
        TakumiGate.LOGGER.info("E2E: rechazado {}", what);
        context.setScreen(TitleScreen::new);
    }

    private static int publishedVersion(String api, String instance, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api + "/api/launcher/instances/" + instance + "/status?since=0"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AssertionError("status " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("version").getAsInt();
    }

    private static String ticket(String api, String instance, String token, UUID uuid, String name, int version)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", GateProtocol.compactUuid(uuid));
        body.addProperty("name", name);
        body.addProperty("version", version);
        HttpRequest request = HttpRequest.newBuilder(URI.create(api + "/api/launcher/instances/" + instance + "/ticket"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AssertionError("ticket " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("ticket").getAsString();
    }
}
