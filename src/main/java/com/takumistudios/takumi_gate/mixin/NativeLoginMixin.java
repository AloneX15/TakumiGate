package com.takumistudios.takumi_gate.mixin;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.network.LoginPayloads;
import com.takumistudios.takumi_gate.network.LoginBarrier;
import com.takumistudios.takumi_gate.server.GateServer;
import com.takumistudios.takumi_gate.server.Messages;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds vanilla's VERIFYING state until the website authorizes the authenticated profile. */
@Mixin(value = ServerLoginPacketListenerImpl.class, priority = 1500)
public abstract class NativeLoginMixin implements LoginBarrier {
    @Shadow @Final private Connection connection;
    @Shadow @Final private MinecraftServer server;
    @Unique private GameProfile takumi_gate$profile;
    @Shadow public abstract void disconnect(Component reason);
    @Unique private volatile boolean takumi_gate$asked;
    @Unique private boolean takumi_gate$answered;
    @Unique private volatile CompletableFuture<Component> takumi_gate$verdict;

    @Override
    public CompletableFuture<Component> takumi_gate$authorize(boolean understood, int protocol, String ticket) {
        takumi_gate$answered = true;
        takumi_gate$verdict = GateServer.authorize(takumi_gate$profile, understood, protocol, ticket);
        return takumi_gate$verdict;
    }

    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
    private void takumi_gate$hold(GameProfile profile, CallbackInfo ci) {
        if (!GateServer.isActive()) {
            return;
        }
        try {
            if (!takumi_gate$asked) {
                takumi_gate$profile = profile;
                takumi_gate$asked = true;
                connection.send(new ClientboundCustomQueryPacket(LoginPayloads.TRANSACTION,
                        new LoginPayloads.Query(GateProtocol.VERSION)));
            }
            CompletableFuture<Component> verdict = takumi_gate$verdict;
            if (verdict == null || !verdict.isDone()) {
                ci.cancel();
                return;
            }
            Component refusal = verdict.join();
            if (refusal != null) {
                disconnect(refusal);
                ci.cancel();
            }
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudo verificar el acceso de Takumi Gate", e);
            disconnect(Messages.unavailable());
            ci.cancel();
        }
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void takumi_gate$answer(ServerboundCustomQueryAnswerPacket packet, CallbackInfo ci) {
        if (GatePlatform.usesFabricNetworking() || packet.transactionId() != LoginPayloads.TRANSACTION || !takumi_gate$asked) {
            return;
        }
        ci.cancel();
        // Packet handler can be called on Netty: keep login state and authorization on the server thread.
        server.execute(() -> {
            if (takumi_gate$answered || !connection.isConnected()) {
                return;
            }
            takumi_gate$answered = true;
            try {
                LoginPayloads.Answer answer = packet.payload() instanceof LoginPayloads.Answer a ? a : null;
                takumi_gate$verdict = GateServer.authorize(takumi_gate$profile, answer != null,
                        answer == null ? 0 : answer.protocol(), answer == null ? "" : answer.ticket());
            } catch (RuntimeException e) {
                TakumiGate.LOGGER.debug("Respuesta de login no v?lida", e);
                takumi_gate$verdict = CompletableFuture.completedFuture(Messages.noLauncher());
            }
        });
    }
}
