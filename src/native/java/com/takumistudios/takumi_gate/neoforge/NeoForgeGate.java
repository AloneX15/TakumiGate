package com.takumistudios.takumi_gate.neoforge;

import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.TakumiGate;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(TakumiGate.MOD_ID)
public final class NeoForgeGate {
    public NeoForgeGate() {
        GatePlatform.initialize(FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get(),
                NeoForgeGate.class.getPackage().getImplementationVersion() == null ? "dev" :
                        NeoForgeGate.class.getPackage().getImplementationVersion());
    }
}
