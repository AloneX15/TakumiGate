package com.takumistudios.takumi_gate.mixin.client;

import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.network.LoginPayloads;
import com.takumistudios.takumi_gate.server.GateServer;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class NativeClientLoginMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void takumi_gate$ticket(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        if (GateServer.CHANNEL.equals(packet.payload().id())) {
            connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(),
                    new LoginPayloads.Answer(GateProtocol.VERSION, GateProtocol.launcherTicket().orElse(""))));
            ci.cancel();
        }
    }
}
