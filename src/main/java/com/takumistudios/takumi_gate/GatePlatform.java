package com.takumistudios.takumi_gate;

import java.nio.file.Path;
import java.util.Objects;

/** Paths and metadata supplied by the loader, without loading client classes on a dedicated server. */
public final class GatePlatform {
    private static Path gameDir;
    private static Path configDir;
    private static String version = "desconocida";
    private static boolean fabricNetworking;

    private GatePlatform() {
    }

    public static void initialize(Path game, Path config, String modVersion) {
        initialize(game, config, modVersion, false);
    }

    public static void initialize(Path game, Path config, String modVersion, boolean fabric) {
        gameDir = Objects.requireNonNull(game).toAbsolutePath().normalize();
        configDir = Objects.requireNonNull(config).toAbsolutePath().normalize();
        version = Objects.requireNonNull(modVersion);
        fabricNetworking = fabric;
        TakumiGate.LOGGER.info("Takumi Gate {} cargado. Creado por TakumiStudios.", version);
    }

    public static Path gameDir() {
        return Objects.requireNonNull(gameDir, "Loader has not initialized Takumi Gate");
    }

    public static Path configDir() {
        return Objects.requireNonNull(configDir, "Loader has not initialized Takumi Gate");
    }

    public static String version() {
        return version;
    }

    public static boolean usesFabricNetworking() {
        return fabricNetworking;
    }
}
