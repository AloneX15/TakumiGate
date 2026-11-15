package com.takumistudios.takumi_gate.server;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts the server sends to players. Translatable (the client mod has the language files) with a Spanish fallback for
 * players who join without the mod and are refused.
 */
final class Messages {
    private Messages() {
    }

    static MutableComponent noLauncher() {
        return Component.translatableWithFallback("takumi_gate.kick.no_launcher",
                "Entra desde Takumi Launcher para jugar en este servidor.");
    }

    static MutableComponent otherProtocol() {
        return Component.translatableWithFallback("takumi_gate.kick.protocol",
                "Tu versión de Takumi Gate no es compatible con este servidor. Actualiza la instancia desde Takumi Launcher.");
    }

    static MutableComponent unavailable() {
        return Component.translatableWithFallback("takumi_gate.kick.unavailable",
                "No se ha podido comprobar tu acceso. Inténtalo de nuevo en un minuto.");
    }

    static MutableComponent outdated() {
        return Component.translatableWithFallback("takumi_gate.kick.outdated",
                "Hay una actualización obligatoria de la instancia. Actualiza desde Takumi Launcher.");
    }

    static MutableComponent title() {
        return Component.translatableWithFallback("takumi_gate.update.title", "Actualización obligatoria");
    }

    static MutableComponent subtitle() {
        return Component.translatableWithFallback("takumi_gate.update.subtitle", "Reinicia el juego para evitar errores");
    }

    static MutableComponent announcement(int version, int minutes) {
        return minutes <= 0
                ? Component.translatableWithFallback("takumi_gate.update.chat_now",
                        "Se ha publicado una actualización obligatoria (versión %s). Reinicia el juego desde Takumi Launcher.",
                        version)
                : Component.translatableWithFallback("takumi_gate.update.chat",
                        "Se ha publicado una actualización obligatoria (versión %s). Reinicia el juego desde Takumi Launcher: dentro de %s min se desconectará a quien siga con la versión anterior.",
                        version, minutes);
    }

    static MutableComponent warning(int minutes) {
        return Component.translatableWithFallback("takumi_gate.update.warn",
                "Actualización obligatoria: te desconectarán en %s min. Reinicia desde Takumi Launcher.", minutes);
    }
}
