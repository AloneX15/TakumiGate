package com.takumistudios.takumi_gate.mixin;

import com.takumistudios.takumi_gate.server.GateServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class NativeServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void takumi_gate$start(CallbackInfo ci) {
        GateServer.onServerStarting((MinecraftServer) (Object) this);
    }
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void takumi_gate$stop(CallbackInfo ci) {
        GateServer.stop();
    }
}
