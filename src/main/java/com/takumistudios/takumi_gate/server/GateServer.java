package com.takumistudios.takumi_gate.server;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_gate.GateApi;
import com.takumistudios.takumi_gate.GateConfig;
import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.GatePlatform;
import com.takumistudios.takumi_gate.MinecraftCompat;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.UpdatePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of Takumi Gate (dedicated servers only):
 * <ul>
 *   <li>During login, asks the client for its Takumi Launcher ticket and checks it with the website. Players without
 *   a valid ticket (not members, expelled, another Minecraft account, launched without Takumi Launcher, outdated
 *   instance) are refused before they enter the world.</li>
 *   <li>Polls the website for new versions. When one needs a restart, players on the old version get a title and chat
 *   notice, reminders at 10, 5 and 1 minutes, and are disconnected when the organizer's grace period ends.</li>
 * </ul>
 */
public final class GateServer {
    //? if >=26.1 {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(TakumiGate.MOD_ID, GateProtocol.CHANNEL_PATH);
    //?} else {
    /*public static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath(TakumiGate.MOD_ID, GateProtocol.CHANNEL_PATH);
    *///?}
    private static final long ENFORCE_SECONDS = 5;

    /** Gate of the dedicated server that is running, or null (no server, integrated server, or not configured). */
    private static volatile GateServer session;

    private final GateConfig config;
    private final ScheduledExecutorService io;
    private final GateApi api;
    /** Instance version each verified player launched with. Players without an entry (bypass) are never kicked. */
    private final Map<UUID, Integer> versions = new ConcurrentHashMap<>();
    private final Map<UUID, UpdatePolicy.Notified> notified = new ConcurrentHashMap<>();
    private volatile List<UpdatePolicy.Release> releases = List.of();
    private final MinecraftServer server;
    private volatile boolean enforcing = true;
    /** Logs each status outage once instead of every poll. */
    private volatile boolean statusFailing;

    private GateServer(GateConfig config, MinecraftServer server) {
        this.config = config;
        this.server = server;
        this.io = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "Takumi Gate-IO");
            thread.setDaemon(true);
            return thread;
        });
        this.api = new GateApi(config, io);
    }

    public static void stop() {
        GateServer current = session;
        session = null;
        if (current != null) {
            current.io.shutdownNow();
            current.versions.clear();
            current.notified.clear();
        }
    }

    public static void disconnected(UUID id) {
        GateServer current = session;
        if (current != null) {
            current.versions.remove(id);
            current.notified.remove(id);
        }
    }

    public static void onServerStarting(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }
        try {
            GateConfig config = GateConfig.load(GatePlatform.configDir());
            if (!config.active()) {
                if (config.enabled()) {
                    TakumiGate.LOGGER.warn("Takumi Gate desactivado: {}", config.problem());
                }
                return;
            }
            GateServer gate = new GateServer(config, server);
            gate.io.scheduleWithFixedDelay(gate::poll, 0, config.pollSeconds(), TimeUnit.SECONDS);
            gate.io.scheduleWithFixedDelay(() -> {
                if (gate.enforcing && session == gate) {
                    server.execute(gate::enforce);
                }
            }, ENFORCE_SECONDS, ENFORCE_SECONDS, TimeUnit.SECONDS);
            session = gate;
            TakumiGate.LOGGER.info("Takumi Gate activo: solo entran miembros de la instancia ({})", config.baseUrl());
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudo iniciar Takumi Gate; el servidor queda sin control de acceso", e);
        }
    }

    /** For gametests: whether the gate is checking players on the running server. */
    public static boolean isActive() {
        return session != null;
    }


    // ─── Login ────────────────────────────────────────────────────────────────

    /** Common authorization, used by each loader before the player enters the world. */
    public static CompletableFuture<Component> authorize(GameProfile profile, boolean understood, int protocol, String raw) {
        GateServer current = session;
        if (current == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (profile == null) {
            return CompletableFuture.completedFuture(Messages.noLauncher());
        }
        if (current.config.bypass().contains(MinecraftCompat.id(profile))) {
            return CompletableFuture.completedFuture(null);
        }
        if (!understood) {
            return CompletableFuture.completedFuture(Messages.noLauncher());
        }
        if (protocol != GateProtocol.VERSION) {
            return CompletableFuture.completedFuture(Messages.otherProtocol());
        }
        Optional<String> ticket = GateProtocol.cleanTicket(raw);
        if (ticket.isEmpty()) {
            return CompletableFuture.completedFuture(Messages.noLauncher());
        }
        return current.api.verify(ticket.get(), MinecraftCompat.id(profile), MinecraftCompat.name(profile), current.server.usesAuthentication())
                .thenApply(verdict -> current.decide(profile, verdict));
    }

    /** Message to refuse the player with, or null to let them in. */
    private Component decide(GameProfile profile, GateApi.Verdict verdict) {
        return switch (verdict) {
            case GateApi.Verdict.Allowed allowed when allowed.restartRequired() -> Messages.outdated();
            case GateApi.Verdict.Allowed allowed -> {
                versions.put(MinecraftCompat.id(profile), allowed.version());
                notified.remove(MinecraftCompat.id(profile));
                TakumiGate.LOGGER.debug("{} (Takumi: {}) entra con la versión {}", MinecraftCompat.name(profile), allowed.username(),
                        allowed.version());
                yield null;
            }
            case GateApi.Verdict.Denied denied -> {
                TakumiGate.LOGGER.info("{} rechazado: {}", MinecraftCompat.name(profile), denied.reason());
                yield Component.literal(denied.reason());
            }
            case GateApi.Verdict.Unavailable unavailable -> {
                TakumiGate.LOGGER.warn("No se pudo comprobar a {} con la web: {}", MinecraftCompat.name(profile), unavailable.detail());
                yield config.allowWhenApiDown() ? null : Messages.unavailable();
            }
        };
    }

    // ─── Mandatory updates ──────────────────────────────────────────────────────

    private void poll() {
        try {
            api.status().whenComplete((list, error) -> {
                if (error == null) {
                    releases = list;
                    if (statusFailing) {
                        statusFailing = false;
                        TakumiGate.LOGGER.info("Takumi Gate vuelve a recibir el estado de la instancia");
                    }
                } else if (!statusFailing) {
                    // Once per outage: without the status, mandatory updates are not enforced.
                    statusFailing = true;
                    TakumiGate.LOGGER.warn("No se pudo consultar el estado de la instancia ({}); las actualizaciones "
                            + "obligatorias no se aplican hasta que vuelva", error.getMessage());
                } else {
                    TakumiGate.LOGGER.debug("Estado de la instancia no disponible: {}", error.getMessage());
                }
            });
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.debug("No se pudo consultar el estado", e);
        }
    }

    /** Runs on the server thread every few seconds; cheap (one pass over the players with a version). */
    private void enforce() {
        MinecraftServer current = server;
        if (versions.isEmpty() || releases.isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            for (ServerPlayer player : List.copyOf(current.getPlayerList().getPlayers())) {
                Integer version = versions.get(player.getUUID());
                if (version == null) {
                    continue;
                }
                Optional<Instant> deadline = UpdatePolicy.deadline(version, releases);
                if (deadline.isEmpty()) {
                    continue;
                }
                UpdatePolicy.Notified state = notified.computeIfAbsent(player.getUUID(), id -> new UpdatePolicy.Notified());
                int target = UpdatePolicy.targetVersion(version, releases);
                int minutes = UpdatePolicy.minutesLeft(now, deadline.get());
                switch (UpdatePolicy.decide(now, deadline.get(), state)) {
                    case ANNOUNCE -> announce(player, target, minutes);
                    case WARN -> player.connection.send(new ClientboundSetActionBarTextPacket(Messages.warning(minutes)));
                    case KICK -> player.connection.disconnect(Messages.outdated());
                    case NONE -> {
                    }
                }
            }
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("Error al aplicar las actualizaciones obligatorias; se desactiva esta función", e);
            enforcing = false;
        }
    }

    private static void announce(ServerPlayer player, int version, int minutes) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Messages.title()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Messages.subtitle()));
        player.sendSystemMessage(Messages.announcement(version, minutes).withStyle(ChatFormatting.GOLD));
    }
}
