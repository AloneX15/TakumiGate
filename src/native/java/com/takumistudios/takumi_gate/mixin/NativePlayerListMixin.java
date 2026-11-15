package com.takumistudios.takumi_gate.mixin;

import com.takumistudios.takumi_gate.server.GateServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class NativePlayerListMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void takumi_gate$disconnect(ServerPlayer player, CallbackInfo ci) {
        GateServer.disconnected(player.getUUID());
    }
}
