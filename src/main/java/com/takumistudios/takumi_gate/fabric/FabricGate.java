package com.takumistudios.takumi_gate.fabric;

import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.network.LoginBarrier;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.server.GateServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric and Quilt lifecycle adapter. Login uses the same vanilla barrier on all loaders. */
public final class FabricGate implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        GatePlatform.initialize(loader.getGameDir(), loader.getConfigDir(), loader.getModContainer(TakumiGate.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("desconocida"), true);
        try {
            ServerLifecycleEvents.SERVER_STARTING.register(GateServer::onServerStarting);
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> GateServer.stop());
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                    GateServer.disconnected(handler.getPlayer().getUUID()));
            ServerLoginNetworking.registerGlobalReceiver(GateServer.CHANNEL,
                    (server, handler, understood, buf, sync, sender) -> {
                        LoginBarrier barrier = (LoginBarrier) handler;
                        try {
                            int protocol = understood ? buf.readUnsignedByte() : 0;
                            String ticket = understood ? buf.readUtf(GateProtocol.MAX_TICKET_LENGTH + 16) : "";
                            boolean valid = !understood || !buf.isReadable();
                            sync.waitFor(barrier.takumi_gate$authorize(understood && valid, protocol, ticket));
                        } catch (RuntimeException e) {
                            TakumiGate.LOGGER.debug("Respuesta de login no válida", e);
                            sync.waitFor(barrier.takumi_gate$authorize(false, 0, ""));
                        }
                    });
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudo registrar Takumi Gate", e);
        }
    }
}
