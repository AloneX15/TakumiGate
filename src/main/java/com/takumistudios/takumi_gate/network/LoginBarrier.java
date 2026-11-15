package com.takumistudios.takumi_gate.network;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;

/** Fabric's login addon delivers its decoded answer to the same barrier used by the native loaders. */
public interface LoginBarrier {
    CompletableFuture<Component> takumi_gate$authorize(boolean understood, int protocol, String ticket);
}
