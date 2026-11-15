package com.takumistudios.takumi_gate.client;

import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.Signals;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.server.GateServer;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Client side of Takumi Gate:
 * <ul>
 *   <li>answers the server's login query with the ticket Takumi Launcher put in {@code TAKUMI_GATE_TICKET};</li>
 *   <li>shows the launcher's mandatory-update notices in game, applies live resource pack updates and closes the
 *   game cleanly when the player chooses "Reiniciar y actualizar" ({@link SignalWatcher});</li>
 *   <li>adds {@code /takumi reiniciar}, which asks the launcher to restart the game updated.</li>
 * </ul>
 */
public final class TakumiGateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            ClientLoginNetworking.registerGlobalReceiver(GateServer.CHANNEL, (client, handler, buf, listeners) -> {
                FriendlyByteBuf answer = new FriendlyByteBuf(Unpooled.buffer());
                answer.writeByte(GateProtocol.VERSION);
                answer.writeUtf(GateProtocol.launcherTicket().orElse(""), GateProtocol.MAX_TICKET_LENGTH + 16);
                return CompletableFuture.completedFuture(answer);
            });
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudo registrar el ticket de Takumi Gate", e);
        }

        boolean launchedByTakumi = System.getenv(Signals.VERSION_ENV) != null;
        if (launchedByTakumi) {
            try {
                SignalWatcher.start();
                ClientLifecycleEvents.CLIENT_STARTED.register(client -> SignalWatcher.onClientStarted());
            } catch (RuntimeException e) {
                TakumiGate.LOGGER.error("No se pudieron activar los avisos de Takumi Launcher", e);
            }
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("takumi").then(ClientCommands.literal("reiniciar").executes(context -> {
                    if (!launchedByTakumi) {
                        context.getSource().sendError(Component.translatable("takumi_gate.client.not_takumi"));
                        return 0;
                    }
                    if (!SignalWatcher.requestRestart()) {
                        context.getSource().sendError(Component.translatable("takumi_gate.client.restart_failed"));
                        return 0;
                    }
                    context.getSource().sendFeedback(Component.translatable("takumi_gate.client.restarting"));
                    return 1;
                }))));
    }
}
