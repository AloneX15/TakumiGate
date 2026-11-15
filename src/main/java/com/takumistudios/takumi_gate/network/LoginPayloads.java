package com.takumistudios.takumi_gate.network;

import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.server.GateServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** Vanilla login wire format, shared with Fabric/Quilt; never registers an FML-only network channel. */
public final class LoginPayloads {
    // Negative transaction id keeps clear of Fabric/FML's sequential nonnegative query ids.
    public static final int TRANSACTION = -0x54414B;

    private LoginPayloads() {
    }

    public record Query(int protocol) implements CustomQueryPayload {
        //? if >=26.1 {
        @Override public Identifier id() {
        //?} else {
        /*@Override public ResourceLocation id() {
        *///?}
            return GateServer.CHANNEL;
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeByte(protocol);
        }
    }

    public record Answer(int protocol, String ticket) implements CustomQueryAnswerPayload {
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeByte(protocol);
            buf.writeUtf(ticket, GateProtocol.MAX_TICKET_LENGTH + 16);
        }
    }
}
