package com.takumistudios.takumi_gate.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to the profile Minecraft authenticated during login, so the ticket can be checked against the
 * Minecraft account that is really joining. Fabric API has no public getter for it. See MIXINS.md.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerImplAccessor {
    @Accessor("authenticatedProfile")
    GameProfile takumi_gate$profile();
}
