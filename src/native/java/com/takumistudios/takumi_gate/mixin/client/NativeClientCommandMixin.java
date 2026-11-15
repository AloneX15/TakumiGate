package com.takumistudios.takumi_gate.mixin.client;

import com.takumistudios.takumi_gate.Signals;
import com.takumistudios.takumi_gate.client.SignalWatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class NativeClientCommandMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void takumi_gate$restart(String command, CallbackInfo ci) {
        if (!command.trim().equals("takumi reiniciar")) {
            return;
        }
        ci.cancel();
        String key = System.getenv(Signals.VERSION_ENV) == null ? "takumi_gate.client.not_takumi"
                : SignalWatcher.requestRestart() ? "takumi_gate.client.restarting" : "takumi_gate.client.restart_failed";
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable(key));
        }
    }
}
