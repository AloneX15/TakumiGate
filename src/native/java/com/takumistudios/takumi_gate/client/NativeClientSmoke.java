package com.takumistudios.takumi_gate.client;

import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.TakumiGate;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Opt-in CI startup check: loads the client networking mixins and translated mod resources, then exits. */
public final class NativeClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("takumi_gate.clientSmoke");
    private static boolean checked;

    private NativeClientSmoke() {
    }

    public static void tick() {
        if (checked || !ENABLED) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        //? if >=26.2 {
        boolean loading = client.gui.overlay() != null;
        //?} else {
        /*boolean loading = client.getOverlay() != null;
        *///?}
        if (loading) {
            return;
        }
        checked = true;
        boolean success;
        try {
            Class.forName("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
            Class.forName("net.minecraft.client.multiplayer.ClientPacketListener");
            String key = "takumi_gate.client.restarting";
            if (Component.translatable(key).getString().equals(key)) {
                throw new IllegalStateException("Takumi Gate translations are not loaded");
            }
            success = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            TakumiGate.LOGGER.error("Native client startup check failed", error);
            success = false;
        }
        boolean result = success;
        Thread writer = new Thread(() -> {
            try {
                var target = GatePlatform.gameDir().resolve("takumi-client-smoke.json");
                var temporary = target.resolveSibling("takumi-client-smoke.json.tmp");
                Files.writeString(temporary, "{\"success\":" + result + "}\n");
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException error) {
                TakumiGate.LOGGER.error("Could not write client smoke result", error);
            } finally {
                client.execute(client::stop);
            }
        }, "Takumi Gate-ClientSmoke");
        writer.setDaemon(true);
        writer.start();
    }
}
