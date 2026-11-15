package com.takumistudios.takumi_gate;

import com.takumistudios.takumi_gate.server.GateServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. On dedicated servers it runs the gate (see {@link GateServer}); on clients the work happens in
 * {@code TakumiGateClient}. A failure here never stops the game: the gate just stays off and says why in the log.
 */
public final class TakumiGate implements ModInitializer {
    public static final String MOD_ID = "takumi_gate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Takumi Gate {} cargado. Creado por TakumiStudios.", version());
        try {
            GateServer.init();
        } catch (RuntimeException e) {
            LOGGER.error("No se pudo iniciar Takumi Gate en el servidor; queda desactivado", e);
        }
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("desconocida");
    }
}
