package com.takumistudios.takumi_gate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Files Takumi Launcher writes in {@code <game>/.takumi/} while a private instance runs. Pure parsing, unit-tested;
 * anything malformed is ignored.
 */
public final class Signals {
    public static final String DIR = ".takumi";
    public static final String NOTICE = "notice.json";
    public static final String RELOAD = "reload.json";
    public static final String QUIT = "quit.request";
    public static final String RESTART = "restart.request";
    /** Written by the game once every mod jar is open; the launcher then locks the mod folders. */
    public static final String LOADED = "loaded.json";
    /** Set by the launcher for private instances; the client features only run then. */
    public static final String VERSION_ENV = "TAKUMI_INSTANCE_VERSION";
    private static final int MAX_PACKS = 64;

    private Signals() {
    }

    /** A mandatory update was published while playing. */
    public record Notice(int version, int graceMinutes, Instant deadline, String notes) {
    }

    public static Optional<Notice> parseNotice(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String notes = root.has("notes") && !root.get("notes").isJsonNull() ? root.get("notes").getAsString() : "";
            return Optional.of(new Notice(
                    root.get("version").getAsInt(),
                    Math.max(0, root.get("grace_minutes").getAsInt()),
                    Instant.parse(root.get("deadline").getAsString()),
                    notes.length() > 300 ? notes.substring(0, 300) : notes));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Pack ids ({@code file/<name>}) to enable after a live resource pack update. */
    public static List<String> parsePacks(String json) {
        List<String> packs = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("packs")) {
                String id = element.getAsString();
                if (packs.size() < MAX_PACKS && isPackId(id)) {
                    packs.add(id);
                }
            }
        } catch (RuntimeException e) {
            return List.of();
        }
        return List.copyOf(packs);
    }

    /** {@code file/<one folder or zip name>}: nothing that could point outside {@code resourcepacks/}. */
    static boolean isPackId(String id) {
        if (!id.startsWith("file/")) {
            return false;
        }
        String name = id.substring(5);
        return !name.isEmpty() && name.length() <= 200 && !name.contains("/") && !name.contains("\\")
                && !name.equals(".") && !name.equals("..") && name.chars().noneMatch(c -> c < 32);
    }
}
