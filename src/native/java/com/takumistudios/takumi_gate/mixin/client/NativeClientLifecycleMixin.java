package com.takumistudios.takumi_gate.mixin.client;

import com.takumistudios.takumi_gate.Signals;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.client.SignalWatcher;
import com.takumistudios.takumi_gate.client.NativeClientSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class NativeClientLifecycleMixin {

    @Inject(method = "run", at = @At("HEAD"))
    private void takumi_gate$start(CallbackInfo ci) {
        if (System.getenv(Signals.VERSION_ENV) != null) {
            try {
                SignalWatcher.start();
            } catch (RuntimeException e) {
                TakumiGate.LOGGER.error("No se pudieron iniciar los avisos del launcher", e);
            }
        }
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void takumi_gate$stop(CallbackInfo ci) {
        SignalWatcher.stop();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void takumi_gate$smoke(CallbackInfo ci) {
        NativeClientSmoke.tick();
    }
}
