package com.takumistudios.takumi_gate.client;

import com.takumistudios.takumi_gate.Signals;
import com.takumistudios.takumi_gate.TakumiGate;
import com.takumistudios.takumi_gate.UpdatePolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.takumistudios.takumi_gate.GatePlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Watches {@code <game>/.takumi/} (written by Takumi Launcher) every two seconds on its own daemon thread and hands the
 * results to the game thread. Only runs for private instances started by Takumi Launcher.
 */
public final class SignalWatcher {
    private static final long PERIOD_SECONDS = 2;
    private static final long REMINDER_SECONDS = 60;

    private static ScheduledExecutorService executor;
    private static Path dir;
    private static FileTime noticeSeen;
    private static FileTime reloadSeen;
    private static Signals.Notice notice;
    private static Instant lastReminder = Instant.EPOCH;
    private static boolean enabled = true;

    private SignalWatcher() {
    }

    public static void start() {
        dir = GatePlatform.gameDir().resolve(Signals.DIR);
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Takumi Gate-Signals");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(SignalWatcher::check, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public static void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Once the game is up: open every mod jar ({@link JarWarmup}) and tell the launcher, which then locks the mod
     * folders until the game exits. Off the game thread.
     */
    public static void onClientStarted() {
        if (executor == null) {
            return;
        }
        executor.execute(() -> {
            try {
                int jars = JarWarmup.run(GatePlatform.gameDir());
                write(Signals.LOADED, "{\"jars\":" + jars + ",\"at\":\"" + Instant.now() + "\"}\n");
            } catch (RuntimeException e) {
                // Without the signal the launcher simply does not lock the folders.
                TakumiGate.LOGGER.error("No se pudieron preparar los mods para el bloqueo", e);
            }
        });
    }

    /** {@code /takumi reiniciar}: the launcher restarts the game updated once it closes. */
    public static boolean requestRestart() {
        if (dir == null) {
            return false;
        }
        String body = "{\"at\":\"" + Instant.now() + "\",\"from\":\"game\"}\n";
        return write(Signals.RESTART, body) && write(Signals.QUIT, body);
    }

    private static boolean write(String name, String body) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            Path tmp = dir.resolve(name + ".tmp");
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            TakumiGate.LOGGER.warn("No se pudo escribir {}", name, e);
            return false;
        }
    }

    private static void check() {
        if (!enabled) {
            return;
        }
        try {
            Path quit = dir.resolve(Signals.QUIT);
            if (Files.exists(quit)) {
                Files.deleteIfExists(quit);
                Minecraft.getInstance().execute(SignalWatcher::quit);
                return;
            }
            readIfChanged(Signals.NOTICE, true).flatMap(Signals::parseNotice).ifPresent(parsed -> {
                notice = parsed;
                lastReminder = Instant.now();
                Minecraft.getInstance().execute(() -> showNotice(parsed));
            });
            readIfChanged(Signals.RELOAD, false).map(Signals::parsePacks).ifPresent(packs ->
                    Minecraft.getInstance().execute(() -> reloadPacks(packs)));
            Signals.Notice current = notice;
            if (current != null && Instant.now().isAfter(lastReminder.plusSeconds(REMINDER_SECONDS))) {
                lastReminder = Instant.now();
                int minutes = UpdatePolicy.minutesLeft(Instant.now(), current.deadline());
                Minecraft.getInstance().execute(() -> remind(minutes));
            }
        } catch (IOException | RuntimeException e) {
            TakumiGate.LOGGER.error("Error leyendo los avisos de Takumi Launcher; se desactivan", e);
            enabled = false;
        }
    }

    /** Contents of {@code name} when its modification time changed since the last read. */
    private static Optional<String> readIfChanged(String name, boolean isNotice) throws IOException {
        Path file = dir.resolve(name);
        if (!Files.isRegularFile(file) || Files.size(file) > 64 * 1024) {
            return Optional.empty();
        }
        FileTime modified = Files.getLastModifiedTime(file);
        FileTime seen = isNotice ? noticeSeen : reloadSeen;
        if (modified.equals(seen)) {
            return Optional.empty();
        }
        if (isNotice) {
            noticeSeen = modified;
        } else {
            reloadSeen = modified;
        }
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    // ─── Game thread ───────────────────────────────────────────────────────────

    private static void showNotice(Signals.Notice parsed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Component restart = Component.translatable("takumi_gate.client.restart_button")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(
                        //? if >=26.1 {
                        new ClickEvent.SuggestCommand("/takumi reiniciar")
                        //?} else {
                        /*new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/takumi reiniciar")
                        *///?}
                        ));
        minecraft.player.sendSystemMessage(Component.translatable("takumi_gate.client.notice", parsed.version())
                .withStyle(ChatFormatting.GOLD).append(" ").append(restart));
        if (!parsed.notes().isBlank()) {
            minecraft.player.sendSystemMessage(Component.literal(parsed.notes()).withStyle(ChatFormatting.GRAY));
        }
        remind(UpdatePolicy.minutesLeft(Instant.now(), parsed.deadline()));
    }

    private static void remind(int minutes) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Component message = minutes > 0
                    ? Component.translatable("takumi_gate.client.minutes_left", minutes).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("takumi_gate.client.restart_now").withStyle(ChatFormatting.RED);
            //? if >=26.1 {
            minecraft.player.sendOverlayMessage(message);
            //?} else {
            /*minecraft.player.displayClientMessage(message, true);
            *///?}
        }
    }

    private static void reloadPacks(List<String> packs) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            PackRepository repository = minecraft.getResourcePackRepository();
            Set<String> before = new HashSet<>(repository.getSelectedIds());
            repository.reload();
            for (String id : packs) {
                if (repository.isAvailable(id)) {
                    repository.addPack(id);
                }
            }
            boolean selectionChanged = !before.equals(new HashSet<>(repository.getSelectedIds()));
            minecraft.options.updateResourcePacks(repository);
            if (!selectionChanged) {
                // Same packs, new contents: updateResourcePacks only reloads when the selection changes.
                minecraft.reloadResourcePacks();
            }
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable("takumi_gate.client.reloaded")
                        .withStyle(ChatFormatting.GREEN));
            }
        } catch (RuntimeException e) {
            TakumiGate.LOGGER.error("No se pudieron recargar los packs de recursos del evento", e);
        }
    }

    private static void quit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("takumi_gate.client.restarting"));
        }
        minecraft.stop();
    }
}
