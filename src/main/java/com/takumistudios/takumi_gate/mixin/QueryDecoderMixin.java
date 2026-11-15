package com.takumistudios.takumi_gate.mixin;

import com.takumistudios.takumi_gate.network.LoginPayloads;
import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.server.GateServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientboundCustomQueryPacket.class, priority = 1500)
public abstract class QueryDecoderMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    //? if >=26.1 {
    private static void takumi_gate$decode(Identifier id, FriendlyByteBuf buf,
    //?} else {
    /*private static void takumi_gate$decode(ResourceLocation id, FriendlyByteBuf buf,
    *///?}
            CallbackInfoReturnable<CustomQueryPayload> cir) {
        if (!GatePlatform.usesFabricNetworking() && GateServer.CHANNEL.equals(id)) {
            if (buf.readableBytes() != 1) {
                throw new io.netty.handler.codec.DecoderException("Invalid Takumi Gate query size");
            }
            cir.setReturnValue(new LoginPayloads.Query(buf.readUnsignedByte()));
        }
    }
}
