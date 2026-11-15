package com.takumistudios.takumi_gate;

import com.takumistudios.takumi_gate.server.GateServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. On dedicated servers it runs the gate (see {@link GateServer}); on clients the work happens in
 * {@code TakumiGateClient}. A failure here never stops the game: the gate just stays off and says why in the log.
 */
public final class TakumiGate {
    public static final String MOD_ID = "takumi_gate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TakumiGate() {
    }

    public static String version() {
        return GatePlatform.version();
    }
}
