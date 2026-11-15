package com.takumistudios.takumi_gate.test;

import com.takumistudios.takumi_gate.Signals;
import com.takumistudios.takumi_gate.TakumiGate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * What Takumi Launcher sends to a running game: a live resource pack update gets the pack enabled, and a mandatory
 * update notice is shown in chat (checked on the screenshot). Runs only when the game is started as a Takumi private
 * instance ({@code TAKUMI_INSTANCE_VERSION=1 ./gradlew :26.3:runClientGameTest}).
 */
public class TakumiGateSignalsGameTest implements FabricClientGameTest {
    private static final String PACK = "TakumiE2E.zip";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv(Signals.VERSION_ENV) == null) {
            TakumiGate.LOGGER.info("TakumiGateSignalsGameTest omitido: falta {}", Signals.VERSION_ENV);
            return;
        }
        Path game = FabricLoader.getInstance().getGameDir();
        Path signals = game.resolve(Signals.DIR);
        Path pack = game.resolve("resourcepacks").resolve(PACK);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(40);
            Files.createDirectories(pack.getParent());
            try (OutputStream out = Files.newOutputStream(pack); ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("pack.mcmeta"));
                zip.write("{\"pack\":{\"description\":\"Takumi e2e\",\"min_format\":1,\"max_format\":999}}"
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            Files.createDirectories(signals);
            Files.writeString(signals.resolve(Signals.RELOAD),
                    "{\"version\":2,\"packs\":[\"file/" + PACK + "\"]}", StandardCharsets.UTF_8);

            boolean enabled = false;
            for (int second = 0; second < 20 && !enabled; second++) {
                context.waitTicks(20);
                enabled = context.computeOnClient(mc -> mc.getResourcePackRepository().getSelectedIds().contains("file/" + PACK));
            }
            if (!enabled) {
                throw new AssertionError("El pack actualizado en directo no se activó");
            }
            TakumiGate.LOGGER.info("E2E: pack activado en directo");

            Files.writeString(signals.resolve(Signals.NOTICE), """
                    {"version":3,"grace_minutes":5,"deadline":"%s","notes":"Nuevo jefe final"}
                    """.formatted(Instant.now().plusSeconds(300)), StandardCharsets.UTF_8);
            context.waitTicks(80);
            context.takeScreenshot("takumi_gate_update_notice");
        } catch (IOException e) {
            throw new AssertionError("Fallo de E/S en el test de avisos", e);
        } finally {
            try {
                Files.deleteIfExists(signals.resolve(Signals.RELOAD));
                Files.deleteIfExists(signals.resolve(Signals.NOTICE));
            } catch (IOException e) {
                TakumiGate.LOGGER.warn("No se pudo limpiar el test de avisos", e);
            }
        }
    }
}
