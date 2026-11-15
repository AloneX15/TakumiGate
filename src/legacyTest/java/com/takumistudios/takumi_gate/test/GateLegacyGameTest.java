package com.takumistudios.takumi_gate.test;

import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.server.GateServer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class GateLegacyGameTest implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void loadsWithoutGatingUnconfiguredServer(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded(TakumiGate.MOD_ID), "Takumi Gate must be loaded");
        helper.assertTrue(!GateServer.isActive(), "An unconfigured server must remain open");
        helper.succeed();
    }
}
