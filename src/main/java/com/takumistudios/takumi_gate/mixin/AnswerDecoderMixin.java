package com.takumistudios.takumi_gate.mixin;

import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.network.LoginPayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerboundCustomQueryAnswerPacket.class, priority = 1500)
public abstract class AnswerDecoderMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void takumi_gate$decode(int transaction, FriendlyByteBuf buf,
            CallbackInfoReturnable<CustomQueryAnswerPayload> cir) {
        if (!GatePlatform.usesFabricNetworking() && transaction == LoginPayloads.TRANSACTION) {
            // readPayload receives the nullable marker too, before the protocol and UTF ticket.
            if (!buf.readBoolean()) {
                if (buf.isReadable()) {
                    throw new io.netty.handler.codec.DecoderException("Trailing absent Takumi Gate answer data");
                }
                cir.setReturnValue(null);
                return;
            }
            int protocol = buf.readUnsignedByte();
            String ticket = buf.readUtf(GateProtocol.MAX_TICKET_LENGTH + 16);
            if (buf.isReadable()) {
                throw new io.netty.handler.codec.DecoderException("Trailing Takumi Gate answer data");
            }
            cir.setReturnValue(new LoginPayloads.Answer(protocol, ticket));
        }
    }
}
