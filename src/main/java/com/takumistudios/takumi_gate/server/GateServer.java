package com.takumistudios.takumi_gate.server;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_gate.GateApi;
import com.takumistudios.takumi_gate.GateConfig;
import com.takumistudios.takumi_gate.GateProtocol;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.UpdatePolicy;
import com.takumistudios.takumi_gate.mixin.ServerLoginPacketListenerImplAccessor;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.LoginPacketSender;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

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
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(TakumiGate.MOD_ID, GateProtocol.CHANNEL_PATH);
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

    /**
     * Registers the global hooks once. They do nothing until a dedicated server starts with a usable
     * {@code config/takumi_gate.json}; singleplayer and LAN worlds are never gated.
     */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(GateServer::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            GateServer current = session;
            session = null;
            if (current != null) {
                current.io.shutdownNow();
            }
        });
        ServerLoginConnectionEvents.QUERY_START.register((handler, srv, sender, sync) -> {
            GateServer current = session;
            if (current != null) {
                current.onQueryStart(handler, sender);
            }
        });
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL, (srv, handler, understood, buf, sync, sender) -> {
            GateServer current = session;
            if (current != null) {
                current.onResponse(srv, handler, understood, buf, sync);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            GateServer current = session;
            if (current != null) {
                UUID id = handler.getPlayer().getUUID();
                current.versions.remove(id);
                current.notified.remove(id);
            }
        });
    }

    private static void onServerStarting(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }
        try {
            GateConfig config = GateConfig.load(FabricLoader.getInstance().getConfigDir());
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
            TakumiGate.LOGGER.debug("Takumi Gate activo: solo entran miembros de la instancia ({})", config.baseUrl());
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudo iniciar Takumi Gate; el servidor queda sin control de acceso", e);
        }
    }

    /** For gametests: whether the gate is checking players on the running server. */
    public static boolean isActive() {
        return session != null;
    }


    // ─── Login ────────────────────────────────────────────────────────────────

    private void onQueryStart(ServerLoginPacketListenerImpl handler, LoginPacketSender sender) {
        try {
            FriendlyByteBuf request = new FriendlyByteBuf(Unpooled.buffer());
            request.writeByte(GateProtocol.VERSION);
            sender.sendPacket(CHANNEL, request);
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudo pedir el ticket al cliente", e);
            handler.disconnect(Messages.unavailable());
        }
    }

    private void onResponse(MinecraftServer srv, ServerLoginPacketListenerImpl handler, boolean understood,
            FriendlyByteBuf buf, ServerLoginNetworking.LoginSynchronizer synchronizer) {
        try {
            GameProfile profile = ((ServerLoginPacketListenerImplAccessor) handler).takumi_gate$profile();
            if (profile == null) {
                handler.disconnect(Messages.noLauncher());
                return;
            }
            if (config.bypass().contains(profile.id())) {
                TakumiGate.LOGGER.info("{} entra sin ticket (bypass_uuids)", profile.name());
                return;
            }
            if (!understood) {
                handler.disconnect(Messages.noLauncher());
                return;
            }
            int protocol = buf.readByte();
            String raw = buf.readUtf(GateProtocol.MAX_TICKET_LENGTH + 16);
            if (protocol != GateProtocol.VERSION) {
                handler.disconnect(Messages.otherProtocol());
                return;
            }
            Optional<String> ticket = GateProtocol.cleanTicket(raw);
            if (ticket.isEmpty()) {
                handler.disconnect(Messages.noLauncher());
                return;
            }
            CompletableFuture<Void> done = api.verify(ticket.get(), profile.id(), profile.name(), srv.usesAuthentication())
                    .thenCompose(verdict -> {
                        Component refusal = decide(profile, verdict);
                        return refusal == null ? CompletableFuture.completedFuture(null)
                                : CompletableFuture.runAsync(() -> handler.disconnect(refusal), srv);
                    });
            synchronizer.waitFor(done);
        } catch (RuntimeException e) {
            // Malformed answer or a bug: refuse this player, keep the server running.
            TakumiGate.LOGGER.debug("Respuesta de login no válida", e);
            handler.disconnect(Messages.noLauncher());
        }
    }

    /** Message to refuse the player with, or null to let them in. */
    private Component decide(GameProfile profile, GateApi.Verdict verdict) {
        return switch (verdict) {
            case GateApi.Verdict.Allowed allowed when allowed.restartRequired() -> Messages.outdated();
            case GateApi.Verdict.Allowed allowed -> {
                versions.put(profile.id(), allowed.version());
                notified.remove(profile.id());
                TakumiGate.LOGGER.debug("{} (Takumi: {}) entra con la versión {}", profile.name(), allowed.username(),
                        allowed.version());
                yield null;
            }
            case GateApi.Verdict.Denied denied -> {
                TakumiGate.LOGGER.info("{} rechazado: {}", profile.name(), denied.reason());
                yield Component.literal(denied.reason());
            }
            case GateApi.Verdict.Unavailable unavailable -> {
                TakumiGate.LOGGER.warn("No se pudo comprobar a {} con la web: {}", profile.name(), unavailable.detail());
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
