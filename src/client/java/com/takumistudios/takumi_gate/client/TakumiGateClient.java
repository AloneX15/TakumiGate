package com.takumistudios.takumi_gate.client;

import com.takumistudios.takumi_gate.Signals;
import com.takumistudios.takumi_gate.TakumiGate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//? if >=26.1 {
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
//?} else {
/*import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
*///?}
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
        boolean launchedByTakumi = System.getenv(Signals.VERSION_ENV) != null;
        if (launchedByTakumi) {
            try {
                SignalWatcher.start();
                ClientLifecycleEvents.CLIENT_STARTED.register(client -> SignalWatcher.onClientStarted());
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SignalWatcher.stop());
            } catch (RuntimeException e) {
                TakumiGate.LOGGER.error("No se pudieron activar los avisos de Takumi Launcher", e);
            }
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("takumi").then(literal("reiniciar").executes(context -> {
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
