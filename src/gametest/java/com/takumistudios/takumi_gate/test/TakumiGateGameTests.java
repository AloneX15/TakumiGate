package com.takumistudios.takumi_gate.test;

import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.server.GateServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests de servidor: ./gradlew :26.3:runGameTest (y con -PcompatPack en la CI). Indica siempre {@code maxTicks}:
 * el valor por defecto es de 20 ticks.
 */
public class TakumiGateGameTests {
    @GameTest(maxTicks = 100)
    public void modIsLoaded(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded(TakumiGate.MOD_ID)) {
            throw TestSupport.fail(helper, "Takumi Gate no está cargado");
        }
        helper.succeed();
    }

    /**
     * Only dedicated servers with a server key are gated: the gametest server (like singleplayer and LAN worlds) must
     * never lock players out.
     */
    @GameTest(maxTicks = 100)
    public void gateStaysOffOutsideConfiguredDedicatedServers(GameTestHelper helper) {
        if (GateServer.isActive()) {
            throw TestSupport.fail(helper, "El gate está activo sin server_key");
        }
        helper.succeed();
    }
}
