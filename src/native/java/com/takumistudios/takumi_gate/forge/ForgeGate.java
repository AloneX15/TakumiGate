package com.takumistudios.takumi_gate.forge;

import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.TakumiGate;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(TakumiGate.MOD_ID)
public final class ForgeGate {
    public ForgeGate() {
        GatePlatform.initialize(FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get(),
                ForgeGate.class.getPackage().getImplementationVersion() == null ? "dev" :
                        ForgeGate.class.getPackage().getImplementationVersion());
    }
}
