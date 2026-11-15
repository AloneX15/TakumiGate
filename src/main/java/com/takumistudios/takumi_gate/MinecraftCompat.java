package com.takumistudios.takumi_gate;

import com.mojang.authlib.GameProfile;
import java.util.UUID;

/** Small API differences between the obfuscated 1.21.1 line and 26.x. */
public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static UUID id(GameProfile profile) {
        //? if >=26.1 {
        return profile.id();
        //?} else {
        /*return profile.getId();
        *///?}
    }

    public static String name(GameProfile profile) {
        //? if >=26.1 {
        return profile.name();
        //?} else {
        /*return profile.getName();
        *///?}
    }
}
