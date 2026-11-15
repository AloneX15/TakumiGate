package com.takumistudios.takumi_gate.test;

import net.minecraft.gametest.framework.GameTestHelper;

/** Utilidades comunes de los gametests. */
final class TestSupport {
    private TestSupport() {
    }

    /** Excepción que marca la prueba como fallida: {@code throw TestSupport.fail(helper, "...")}. */
    static RuntimeException fail(GameTestHelper helper, String message) {
        return helper.assertionException(message);
    }
}
